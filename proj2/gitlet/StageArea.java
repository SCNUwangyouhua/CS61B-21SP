package gitlet;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.System.exit;

public class StageArea implements Serializable {
    //更改class的UID，不然检测的时候反序列化后会出错
    private static final long serialVersionUID = 478463879402600788L;
    //当前Commit正在追踪的文件
    private Map<String, String>tracking_pathToBlobID;
    //代表addstage
    private Map<String, String>toadd_pathToBlobID;
    //代表removestage只存储待删除文件的CWD下路径
    private Set<String> toremove_pathToBlobID;

    public StageArea() {
        tracking_pathToBlobID = new HashMap<>();
        toadd_pathToBlobID = new HashMap<>();
        toremove_pathToBlobID = new HashSet<>();
    }

    public Map<String, String> do_a_commit() {
        if (isEmpty()) {
            System.out.println("No changes added to the commit.");
            exit(0);
        }
        tracking_pathToBlobID = Repository.last_commit_obj.getTracking();
        tracking_pathToBlobID.putAll(toadd_pathToBlobID);
        for (String filepath : toremove_pathToBlobID) {
            tracking_pathToBlobID.remove(filepath);
        }
        clear();
        return tracking_pathToBlobID;
    }

    //返回addstage
    public Map<String, String> get_addstage() {
        return toadd_pathToBlobID;
    }

    //返回removestage
    public Set<String> get_removestage() {
        return toremove_pathToBlobID;
    }


    //将文件add进addstage
    public void add(File FileInCWD) {

        //当前传进来的文件的路径(String形式)，用于作为map的key值
        String PathOfFileInCWD = FileInCWD.getPath();
        //用传进来的文件创建Blob对象，并得到其ID
        Blob newBlob = new Blob(FileInCWD);
        //当前新进来的文件对应Blob对象的ID
        String newBlobID = newBlob.getID();
        //当前Commit已追踪的此文件的BlobID
        String tracking_BlobID = Repository.last_commit_obj.return_ID_of_path(PathOfFileInCWD);
        //新file已经被当前Commit追踪且内容没有变动
        if (tracking_BlobID != null && newBlobID.equals(tracking_BlobID)) {
            //通过路径 获取addstage中的记录
            String already_add_blobID = toadd_pathToBlobID.remove(PathOfFileInCWD);
            //如果addstage中有记录过此路径，则删除，然后更新暂存区
            if (already_add_blobID != null) {
                //存在记录，且前面remove过了
                save();
                return;
            }
            //通过路径 获取removestage中的记录
            boolean Is_to_remove = toremove_pathToBlobID.remove(PathOfFileInCWD);
            //如果removestage中有记录过此路径，则删除，然后更新暂存区
            if (Is_to_remove) {
                save();
                return;
            }
            //至此，说明addstage和removstage都没有记录，什么都不发生
            return;
        }
        //情况A：新file被当前Commit追踪且内容变动 / 情况B：新file没被当前Commit追踪
        //在情况A下，先判断是否重复git add了
        String prev_add_blowID = toadd_pathToBlobID.get(PathOfFileInCWD);
        //重复add了，不更新缓存区
        if (prev_add_blowID != null && prev_add_blowID.equals(newBlobID)) {
            return;
        }
        //剩下的情况只有
        //1.新file被当前Commit追踪且内容变动，同时是第一次git add
        //2.新file没有被当前Commit追踪
        //这两种情况都需要先用文件存下这个Blob对象，因为后续commit过后会指向这个Blob
        if (!newBlob.getFileinBlobs().exists()) {
            newBlob.save();
        }
        //将<key val>存入/替换toadd(Map)
        toadd_pathToBlobID.put(PathOfFileInCWD, newBlobID);
        //更新暂存区
        save();
    }

    //将文件加入removestage
    public void remove(String file_name_to_rm) {
        File file_to_rm = Utils.join(Repository.CWD, file_name_to_rm);
        String path = file_to_rm.getPath();
        toremove_pathToBlobID.add(path);
        if (file_to_rm.exists()) {
            file_to_rm.delete();
        }
        //更新暂存区
        save();
    }
    //将文件加入removestage
    public void remove(File file_to_rm) {
        String path = file_to_rm.getPath();
        toremove_pathToBlobID.add(path);
        if (file_to_rm.exists()) {
            file_to_rm.delete();
        }
        //更新暂存区
        save();
    }

    //从文件中读取stagearea
    public static StageArea fromfile() {
        //只有StageAreaFile存在的时候才调用
        if  (!Repository.StageAreaFile.exists()) {
            return new StageArea();
        }
        return Utils.readObject(Repository.StageAreaFile, StageArea.class);
    }


    //更新暂存区，将暂存区存入.gitlet/StageArea(text)
    public void save() {
        //向暂存区文件写入当前Object
        Utils.writeObject(Repository.StageAreaFile, this);
    }


    //清除当前暂存区
    public void clear() {
        toadd_pathToBlobID.clear();
        toremove_pathToBlobID.clear();
        Repository.StageAreaFile.delete();
    }

    //判断暂存区是否为空
    public boolean isEmpty() {
        if (toadd_pathToBlobID.isEmpty() && toremove_pathToBlobID.isEmpty()) {
            return true;
        }
        return false;
    }

    //判断addstage是否为空
    public boolean is_addstage_Empty() {
        return toadd_pathToBlobID.isEmpty();
    }
    //判断removestage是否为空
    public boolean is_removestage_Empty() {
        return toremove_pathToBlobID.isEmpty();
    }

    //传入一个filename(String)，判断它在不在removestage中
    public boolean check_file_is_in_removestage(String to_check_file_name) {
        File file_to_check = Utils.join(Repository.CWD, to_check_file_name);
        String path = file_to_check.getPath();
        return toremove_pathToBlobID.contains(path);//如果包含就返回true，不包含返回false
    }
    //传入一个filename(String)，判断它在不在addstage中
    //返回true如果传入的文件名在addstage当中
    public boolean check_file_is_in_addstage(String to_check_file_name) {
        File file_to_check = Utils.join(Repository.CWD, to_check_file_name);
        String path = file_to_check.getPath();
        String val = toadd_pathToBlobID.get(path);
        return val != null;
    }

    //删除addstage当中的文件，慎用！！！！！！！！！！！！！！！！
    public void remove_file_in_addstage(String to_remove_file_name) {
        File file_to_remove = Utils.join(Repository.CWD, to_remove_file_name);
        String path = file_to_remove.getPath();
        toadd_pathToBlobID.remove(path);
        //删了后要保存暂存区
        save();
    }
    //删除removestage当中的文件，慎用！！！！！！！！！！！！！！！
    public void remove_file_in_removestage(String to_remove_file_name) {
        File file_to_remove = Utils.join(Repository.CWD, to_remove_file_name);
        String path = file_to_remove.getPath();
        toremove_pathToBlobID.remove(path);
        //删了之后要保存/更新暂存区
        save();
    }
}
