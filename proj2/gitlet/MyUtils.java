package gitlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.function.Supplier;

import static gitlet.Utils.*;

public class MyUtils {
    //输入id获得Commit对象的File
    public static File getCommitObjectFile(String ID) {
        return Utils.join(Repository.COMMITS_FOLDER, ID);
    }

    //输入id获得Blob对象的File
    public static File getBlobObjectFile(String ID) {
        return Utils.join(Repository.BLOBS_FOLDER, ID);
    }


}
