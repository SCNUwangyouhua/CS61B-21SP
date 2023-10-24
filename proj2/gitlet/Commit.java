package gitlet;

// TODO: any imports you need here

import java.io.File;
import java.io.Serializable;
import java.sql.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.readObject;
import static gitlet.Utils.sha1;

/** Gitlet中的Commit对象 */
public class Commit implements Serializable {
    //UID bug 暂时的解决方法
    private static final long serialVersionUID = 5644380552482673459L;

    // commit附带信息
    private String message;
    //CWD下的文件路径 映射到 Blobs文件夹内的Blob.ID
    private Map<String, String>CWD_Path_to_BlobID;
    //parent列表，存储当前Commit的所有Parent
    private List<String> Parents;
    //Date信息
    private Date currentTime;
    //将Date信息格式化后打印的信息
    private String timestamp;
    //Commit的ID  也是存储一个Commit对象的文件名
    private String ID;
    //存储commit对象的文件
    private File CommitSaveFile;

    /**function 功能 */

    //初始化有参数构造，参数有：message, Map, Parents
    public Commit(String _message, Map<String, String>_Map, List<String>_Parents) {
        message = _message;
        CWD_Path_to_BlobID = _Map;
        Parents = _Parents;
        //设置默认时间
        currentTime = new Date();
        timestamp = dateToTimeStamp(currentTime);
        //获取当前commit对象的ID
        ID = this.generateId();
        //根据ID得到file
        CommitSaveFile = MyUtils.getCommitObjectFile(ID);
    }

    //初始化无参数创建
    public Commit() {
        //设置默认时间
        currentTime = new Date(0);
        timestamp = dateToTimeStamp(currentTime);
        //message是initial commit
        message = "initial commit";
        //Map和List先创建个空的
        CWD_Path_to_BlobID = new HashMap<>();
        Parents = new ArrayList<>();
        //获取当前Commit对象的ID
        ID = this.generateId();
        //根据ID得到file
        CommitSaveFile = MyUtils.getCommitObjectFile(ID);
    }


    //将当前Commit对象保存
    public void save() {
        Utils.writeObject(CommitSaveFile, this);
    }

    //返回它的parent列表
    public List<String> getParents() {
        return Parents;
    }

    //获得当前Commit对象的sha1值(该值就是该对象的ID)
    private String generateId() {
        return sha1(getTimestamp(), message, Parents.toString(), CWD_Path_to_BlobID.toString());
    }

    //返回CWD_path_to_BlobID
    public Map<String, String> getTracking() {
        return CWD_Path_to_BlobID;
    }

    //返回当前对象的Timestamp
    public String getTimestamp() {
        return this.timestamp;
    }

    //返回当前对象的ID
    public String getCommitObjectID() {
        return this.ID;
    }

    //返回当前对象的message
    public String getMessage() {
        return message;
    }

    //将date转timestamp
    private static String dateToTimeStamp(Date date) {
        DateFormat dateFormate = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        return dateFormate.format(date);
    }


    //传入文件名，判断该文件是否被这个commit对象追踪 如果被追踪返回true
    public boolean hasfilename(String file_being_checked) {
        File get_file_in_CWD = Utils.join(Repository.CWD, file_being_checked);
        String path = get_file_in_CWD.getPath();
        String val = CWD_Path_to_BlobID.get(path);
        return val != null;
    }

    //传入BlobID，判断该BlobID的文件是否被当前commit对象追踪，如果被追踪返回true
    public boolean contains(String BlobID) {
        return CWD_Path_to_BlobID.containsValue(BlobID);
    }

    //打印信息的函数，用于log指令
    public void show_in_format() {
        System.out.println("===");
        System.out.println("commit " + ID);
        if (Parents.size() > 1) {
            String parent1_substring = Parents.get(0).substring(0, 7);
            String parent2_substring = Parents.get(1).substring(0, 7);
            System.out.println("Merge: " + parent1_substring + " " + parent2_substring);
        }
        System.out.println("Date: " + timestamp);
        System.out.println(message);
        System.out.println();
    }

    //传入path，传出当前追踪的ID
    public String return_ID_of_path(String given_path) {
        return CWD_Path_to_BlobID.get(given_path);
    }

    //根据给定的commitID返回对应的Commit对象
    public static Commit fromFile(String given_commit_ID) {
        return Utils.readObject(Utils.join(Repository.COMMITS_FOLDER, given_commit_ID), Commit.class);
    }

    //将CWD_path_to_BlobID中的CWD_path对应文件全部写入CWD中
    //!!!!!会修改CWD
    public void change_cwd_by_stored_cwd_in_commitOBJ() {
        for (String single_blobid : CWD_Path_to_BlobID.values()) {
            Blob.fromFile(single_blobid).save_back_in_CWD();
        }
    }


}
