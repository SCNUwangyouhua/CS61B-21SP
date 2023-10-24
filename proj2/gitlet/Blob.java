package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class Blob implements Serializable {


    /**Blob文件存储的信息*/
    //Blob id
    private String ID;
    //对应CWD的文件中的内容的byte[]形式
    private byte[] content;
    //对应CWD的文件路径
    private File saveFileinCWD;
    //存储的文件的名字  .gitlet/objects/Blobs/ID(text)
    private File saveFileinBlobs;



    /**Function 功能*/

    //给定CWD路径下的文件，创建新的Blob对象
    public Blob(File FileToStoreInCWD) {
        saveFileinCWD = FileToStoreInCWD;
        content = Utils.readContents(saveFileinCWD);
        ID = Blob.calculateID(saveFileinCWD);
        saveFileinBlobs = MyUtils.getBlobObjectFile(ID);
    }

    //存入Blobs文件夹内
    public void save() {
        Utils.writeObject(saveFileinBlobs, this);
    }

    //传入一个File，计算其如果成为Blob对象的ID
    public static String calculateID(File sourceFile) {
        String sourceFilePath = sourceFile.getPath();
        byte[] sourceFileContent = Utils.readContents(sourceFile);
        return Utils.sha1(sourceFilePath, sourceFileContent);
    }

    //传入一个路径，计算其如果成为Blob对象的ID
    public static String calculateID(String file_path) {
        File given_file = new File(file_path);
        byte[] given_file_content = Utils.readContents(given_file);
        return Utils.sha1(file_path, given_file_content);
    }

    //获得当前Blob对象的ID
    public String getID() {
        return ID;
    }

    //获得当前Blob对象存储的对应CWD路径下的原文件
    public File getFileinCWD() {
        return saveFileinCWD;
    }

    //获得当前Blob对象以文件形式存入Blobs文件夹内的File文件
    public File getFileinBlobs() {
        return saveFileinBlobs;
    }

    public static Blob fromFile(String given_blob_ID) {
        return Utils.readObject(Utils.join(Repository.BLOBS_FOLDER, given_blob_ID), Blob.class);
    }

    public void WriteContentToCWDFile() {
        Utils.writeContents(saveFileinCWD, content);
    }

    public void save_back_in_CWD() {
        Utils.writeContents(saveFileinCWD, content);
    }

    public String getContent() {
        return new String(content, StandardCharsets.UTF_8);
    }
}
