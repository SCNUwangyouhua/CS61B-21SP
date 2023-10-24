package gitlet;


import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static gitlet.Utils.*;
import static java.lang.System.exit;

//代表整个gitlet仓库
public class Repository {
    /**工作目录 Current Working Directory*/
    public static final File CWD = new File(System.getProperty("user.dir"));

    /**文件夹*/
    //主文件夹
    public static final File GITLET_FOLDER = Utils.join(CWD, ".gitlet");
    //objects文件夹
    private static final File OBJECTS_FOLDER = Utils.join(GITLET_FOLDER, "objects");
    //Blobs文件夹根目录
    public static final File BLOBS_FOLDER = Utils.join(GITLET_FOLDER, "objects", "Blobs");
    //Commits文件夹根目录
    public static final File COMMITS_FOLDER = Utils.join(GITLET_FOLDER, "objects", "Commits");
    //存所有分支信息的文件夹，分成两部分来mkdir，否则会报错
    public static final File REFS_FOLDER = Utils.join(GITLET_FOLDER, "refs");
    public static final File ALL_HEADS_FOLD = Utils.join(REFS_FOLDER, "heads");

    /**文件*/
    //当前所指向的branch
    public static final File HEAD_FOLD = Utils.join(GITLET_FOLDER, "HEAD");
    //暂存区(add/remove)
    public static final File StageAreaFile = Utils.join(GITLET_FOLDER, "StageArea");//add或remove后才会创建，commit后删除

    /**变量*/
    //默认分支名

    private static final String DEFAULT_BRANCH_NAME = "master";
    //暂存区变量
    public static StageArea a_stage_area = StageArea.fromfile();
    //当前的branch
    private static String now_branch = get_current_branch();
    //当前分支最新的commit
    public static Commit last_commit_obj = get_last_commit();

    //------------------------------------------------------------------------------
    //下面为function 函数/功能 部分
    //------------------------------------------------------------------------------

    //仓库接受merge指令
    public static void merge(String other_branch) {
        //检查四种错误情况，并返回other branch上最新commit对象
        Commit other_commit = check_error_for_merge(other_branch);
        //找公共祖先
        Commit lca = Find_LCA_with_two_commits(last_commit_obj, other_commit);
        //判断2个特殊情况
        check_special_in_merge(lca, other_commit, other_branch);
        Map<String, String> cur_track = last_commit_obj.getTracking();
        Map<String, String> other_track = other_commit.getTracking();
        Map<String, String> lca_track =  lca.getTracking();
        boolean isConflict = false;
        Set<String> filepath_in_lca = new HashSet<>();
        for (String lca_filepath : lca_track.keySet()) {
            //遍历过，加入filepath_in_lca集合，用于后续删除
            filepath_in_lca.add(lca_filepath);
            //每一次循环都是判断堆当前这个文件进行什么操作
            File lca_file = new File(lca_filepath);
            //获取此filepath对应三个commit的Blobid
            //判断文件在other/cur处存不存在，只需要看get到的blobid是否为null，如果为null说明不存在，反之存在
            //判断文件在other/cur有没有被更改，只需要将文件在other/cur处的blobid与lca处的id进行对比，一样就说明没更改，反之说明更改了
            String lca_file_blobid = lca_track.get(lca_filepath);
            String other_file_blobid = other_track.get(lca_filepath);
            String cur_file_blobid = cur_track.get(lca_filepath);

            if (other_file_blobid != null) {
                //该文件存在于other分支
                if (lca_file_blobid.equals(other_file_blobid)) {
                    //该文件在other处没被更改：啥也不干
                    continue;
                }
                //该文件在other处被更改了
                if (cur_file_blobid == null) {
                    //该文件在other处被更改了+该文件在cur处不存在
                    isConflict = true;
                    solve_conflict_file(lca_file, cur_file_blobid, other_file_blobid);
                    continue;
                }
                //该文件在other处被更改了+该文件存在于cur
                if (lca_file_blobid.equals(cur_file_blobid)) {
                    //该文件在other处被更改了+该文件在cur处没被更改
                    Blob.fromFile(other_file_blobid).save_back_in_CWD();
                    a_stage_area.add(lca_file);
                    continue;
                }
                //该文件在other处被更改了+该文件在cur处被更改了
                if (other_file_blobid.equals(cur_file_blobid)) {
                    //该文件在other处被更改了+该文件在cur处被更改了+BlobID相等：啥也不干
                    continue;
                }
                //该文件在other处被更改了+该文件在cur处被更改了+BlobID不相等
                isConflict = true;
                solve_conflict_file(lca_file, cur_file_blobid, other_file_blobid);
                continue;
            }
            //该文件在other处不存在
            if (cur_file_blobid == null) {
                //该文件在other处不存在+该文件在cur处不存在：啥也不干
                continue;
            }
            //该文件在other处不存在+该文件存在于cur处
            if (lca_file_blobid.equals(cur_file_blobid)) {
                //该文件在other处不存在+该文件在cur处没被改
                a_stage_area.remove(lca_file);
                continue;
            }
            //该文件在other处不存在+该文件在cur处被修改了
            isConflict = true;
            solve_conflict_file(lca_file, cur_file_blobid, other_file_blobid);
        }
        //要把cur_track和other_track的keySet() 减去 Set<String> filepath_in_lca 后继续下面操作
        for (String single_filepath : filepath_in_lca) {
            cur_track.remove(single_filepath);
            other_track.remove(single_filepath);
        }
        //剩下的都是lca中没有的文件(后面新增的文件)
        for (String other_filepath : other_track.keySet()) {
            File other_file = new File(other_filepath);
            String other_file_blobid = other_track.get(other_filepath);
            String cur_file_blobid = cur_track.get(other_filepath);
            //因为从other_track遍历，所以
            //文件被新增于other中
            if (cur_file_blobid == null) {
                //文件被新增于other中+文件没有新增于cur中
                //TODO: add file
                Blob.fromFile(other_file_blobid).save_back_in_CWD();
                a_stage_area.add(other_file);
                continue;
            }
            //文件被新增于other中+文件被新增于cur中
            if (cur_file_blobid.equals(other_file_blobid)) {
                //文件被新增于other中+文件没有新增于cur中+两文件相等(Blobid即内容)
                continue;
            }
            //文件被新增于other中+文件被新增于cur中+两文件不相等
            isConflict = true;
            solve_conflict_file(other_file, cur_file_blobid, other_file_blobid);
        }
        //调用commit 提交有2个参数 所以调用merge版commit
        //第一个参数是message 必须是 "Merged other_branch_name into current_branch_name"
        //第二个参数是被合并进来的分支的最新的Commit对象的ID
        String merge_commit_message = "Merged" + " " + other_branch + " into " + get_current_branch() + ".";
        commit(merge_commit_message, other_commit.getCommitObjectID());

        //最后，如果有发生冲突，sout打印"Encountered a merge conflict."
        if (isConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    //传入 (File冲突文件，cur当前分支这个文件对应ID，other被合并分支这个文件对应ID)
    //获得冲突文件应该的内容，并将冲突写入CWD中然后保存至暂存区，等merge完commit时保存在记录中
    private static void solve_conflict_file(File conflict_file, String cur_blobid, String other_blobid) {
        String Conflict_Content = get_conflict_content(cur_blobid, other_blobid);
        Utils.writeContents(conflict_file, Conflict_Content);
        a_stage_area.add(conflict_file);
    }
    //传入cur_blobid, other_blobid 因为冲突，根据blobid生成冲突格式的文件内容
    private static String get_conflict_content(String cur_blobid, String other_blobid) {
        StringBuilder target_content = new StringBuilder();
        target_content.append("<<<<<<< HEAD");
        target_content.append("\n");
        if (cur_blobid != null) {
            Blob cur_blobobj = Blob.fromFile(cur_blobid);
            target_content.append(cur_blobobj.getContent());
        }
        target_content.append("=======");
        target_content.append("\n");
        if (other_blobid != null) {
            Blob other_blobobj = Blob.fromFile(other_blobid);
            target_content.append(other_blobobj.getContent());
        }
        target_content.append(">>>>>>>");
        return target_content.toString();
    }


    //在文件合并前判断特殊情况
    public static void check_special_in_merge(Commit LCA, Commit other_last_commit, String target_branch_name) {
        //如果LCA和Master最新Commit相同，只需要将当前commit更新到other_last_commit，实际就是切换分支到被merge进来的分支
        if (LCA.getCommitObjectID().equals(last_commit_obj.getCommitObjectID())) {
            //更新最新分支，修改CWD下文件
            checkout_commit(other_last_commit);
            //切换branch
            setCurrentBranchtoHEAD(target_branch_name);
            System.out.println("Current branch fast-forwarded.");
            exit(0);
        }
        if (LCA.getCommitObjectID().equals(other_last_commit.getCommitObjectID())) {
            //本身就是merge好的，不需要任何操作
            System.out.println("Given branch is an ancestor of the current branch");
            exit(0);
        }
    }
    //找最近公共祖先的函数
    public static Commit Find_LCA_with_two_commits(Commit commit_first, Commit commit_second) {
        //Step 1: 每个Commit对象往前遍历，遍历到的 <commit_ID, 深度> 放入各自Map (Map first/ Map second)
        //Step 2: 遍历Map first的keySet 每次拿Map first的一个key去匹配Map second
        //如果Map second包含此key，记录下此时Map first的 <key, depth>
        //整个过程，找到Map first中value最小的 <minkey, mindepth> 就是找到的LCA

        //Step 1: 先构建Map first和Map second
        Commit iter1 = commit_first;
        Map<String, Integer> Map_first = new HashMap<>();
        Map<String, Integer> Map_second = new HashMap<>();
        go_to_init(commit_first.getCommitObjectID(), 0, Map_first);
        go_to_init(commit_second.getCommitObjectID(), 0, Map_second);

        //创建minkey mindepth记录
        String minkey = null;
        int mindepth = -1;
        //Step 2: 遍历Map first的keySet 每次拿Map first的一个key去匹配Map second
        for (String key_first : Map_first.keySet()) {
            if (!Map_second.containsKey(key_first)) {
                continue;
            }
            //key_first在Map_second中也存在
            if (mindepth == -1) {
                //找到的第一个符合条件的key val
                mindepth = Map_first.get(key_first);
                minkey = key_first;
                continue;
            }
            if (Map_first.get(key_first) < mindepth) {
                //不是第一个 与先前比较，存储最小的深度
                mindepth = Map_first.get(key_first);
                minkey = key_first;
            }
        }
        //通过找到的minkey返回最近公共祖先(Commit对象)
        return Commit.fromFile(minkey);
    }
    //接受一个commit节点，递归所有路径并回到根节点，返回过程中记录的<commit ID, depth>Map
    public static void go_to_init(String arrive_commit_id, int depth, Map<String, Integer> store_map) {
        Commit arrive_commit_obj = Commit.fromFile(arrive_commit_id);
        //判断是否到达init
        if (arrive_commit_obj.getParents().isEmpty()) {
            //达到init 记如入init后递归停止
            store_map.put(arrive_commit_id, depth);
            return;
        }
        List<String> arrive_commit_par = arrive_commit_obj.getParents();
        String par1_id = arrive_commit_par.get(0);
        if (depth != 0) {
            store_map.put(par1_id, depth);
        }
        go_to_init(par1_id, depth+1, store_map);
        //如果有第二个parent就遍历
        if (arrive_commit_par.size() > 1) {
            String par2_id = arrive_commit_par.get(1);
            if (depth != 0) {
                store_map.put(par2_id, depth);
                go_to_init(par2_id, depth+1, store_map);
            }
        }
    }
    //检查merge之前所有的error情况
    public static Commit check_error_for_merge(String other_branch) {
        File other_branch_file = Utils.join(ALL_HEADS_FOLD, other_branch);
        if (!other_branch_file.exists()) {
            System.out.println("A branch with that name does not exist.");
            exit(0);
        }
        if (other_branch.equals(get_current_branch())) {
            System.out.println("Cannot merge a branch with itself.");
            exit(0);
        }
        if (!a_stage_area.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            exit(0);
        }
        String other_branch_last_commit_ID = Utils.readContentsAsString(other_branch_file);
        Commit other_branch_last_commit_obj = Commit.fromFile(other_branch_last_commit_ID);
        check_untracked_file(other_branch_last_commit_obj);
        return other_branch_last_commit_obj;//返回传入的这个other分支的最新commit对象
    }

    //仓库接受 reset 指令
    public static void reset(String back_to_this_commit_ID) {
        File commit_file = Utils.join(COMMITS_FOLDER, back_to_this_commit_ID);
        if (!commit_file.exists()) {
            System.out.println("No commit with that id exists.");
            exit(0);
        }
        Commit target_commit_obj = Commit.fromFile(back_to_this_commit_ID);
        check_untracked_file(target_commit_obj);
        checkout_commit(target_commit_obj);
        //获得当前最新分支名
        String now_branch_name = get_current_branch();
        File now_branch_file = Utils.join(ALL_HEADS_FOLD, now_branch_name);
        Utils.writeContents(now_branch_file, back_to_this_commit_ID);
    }
    //检查untracked错误，思路与checkout的untrack检查相似
    public static void check_untracked_file(Commit target_commit_obj) {
        //先导出当前CWD下的文件
        List<String> ignoredFiles = Arrays.asList("Makefile", "pom.xml");
        //读取当前CWD 并排除了makefile文件和xml文件
        File[] current_cwd = Arrays.stream(CWD.listFiles())
                .filter(File::isFile)
                .filter(f -> !ignoredFiles.contains(f.getName()))
                .toArray(File[]::new);
        //这些file传入Blob的calculateID可以算出BlobID，这样构造一个current_map
        Map<String, String>current_map = new HashMap<>();
        for (File a_file_in_cwd : current_cwd) {
            String key = a_file_in_cwd.getPath();
            String val = Blob.calculateID(a_file_in_cwd);
            current_map.put(key, val);
        }
        //urrent_map存储的是当前CWD下 <文件路径，文件对应BlobID>
        //要拿这些文件与切换到的目标分支的最新commit的tracking Map进行对比
        //如果current_map的keyset(当前CWD文件)中，有文件，没被last_commit_obj追踪，被新分支最新commit追踪，
        //且Blob ID不一样，则报错 "There is an untracked file in the way; delete it, or add and commit it first."
        Map<String, String> new_commit_tracking = target_commit_obj.getTracking();
        Map<String, String> old_commit_tracking = last_commit_obj.getTracking();
        for (String file_in_now_cwd : current_map.keySet()) {
            if (!old_commit_tracking.containsKey(file_in_now_cwd)
            && new_commit_tracking.containsKey(file_in_now_cwd)) {
                String blob_id_in_now_cwd = current_map.get(file_in_now_cwd);
                String blob_id_ni_new_commit = new_commit_tracking.get(file_in_now_cwd);
                if (!blob_id_in_now_cwd.equals(blob_id_ni_new_commit)) {
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    exit(0);
                }
            }
        }
    }


    //仓库接受rm-branch指令
    public static void remove_branch(String to_be_removed_branch_name) {
        //不能删除当前所在的分支
        if (now_branch.equals(to_be_removed_branch_name)) {
            System.out.println("Cannot remove the current branch.");
            exit(0);
        }
        //判断这个分支是否存在
        File given_branch_file = Utils.join(ALL_HEADS_FOLD, to_be_removed_branch_name);
        if (!given_branch_file.exists()) {
            System.out.println("A branch with that name does not exist.");
            exit(0);
        }
        given_branch_file.delete();
    }

    //仓库接受新建branch指令
    public static void create_new_branch(String new_branch_name) {

        //传入一个新的分支的名字，在heads文件夹内创建新文件并写入当前commit ID
        File new_branch_file = Utils.join(ALL_HEADS_FOLD, new_branch_name);
        //先判断这个分支是否已经存在
        if (new_branch_file.exists()) {
            System.out.println("A branch with that name already exists.");
            exit(0);
        }

        String last_commit_obj_ID = last_commit_obj.getCommitObjectID();
        Utils.writeContents(new_branch_file, last_commit_obj_ID);

    }

    //仓库接受checkout branch_name 指令
    public static void checkout_branchname(String given_branch_name) {
        //判断这个branch是否与当前branch相等
        if (get_current_branch().equals(given_branch_name)) {
            System.out.println("No need to checkout the current branch.");
            exit(0);
        }
        //判断这个branch是否存在
        File given_branch_file = Utils.join(ALL_HEADS_FOLD, given_branch_name);
        if (!given_branch_file.exists()) {
            System.out.println("No such branch exists.");
            exit(0);
        }
        //然后要检查当前CWD内是否有没被新branch最新commit追踪的文件，有且BlopbID就报错error
        //为了复用代码，用新分支名，得到新分支最新commit对象，然后以此对象为参数传入新的复用代码的函数
        String new_branch_commit_ID = Utils.readContentsAsString(given_branch_file);
        Commit new_branch_commit_obj = Commit.fromFile(new_branch_commit_ID);
        check_untracked_file(new_branch_commit_obj);
        //check_untracked_file_in_now_CWD(given_branch_name);

        //至此，检查完没有untrack的文件的错误
        //剩下还有两个任务需要完成
        //任务A：把当前分支最新commit对象更改
        //任务B：把当前分支更改

        //首先 任务A：
        String given_branch_newest_commitID = Utils.readContentsAsString(given_branch_file);
        Commit given_branch_newest_commitOBJ = Commit.fromFile(given_branch_newest_commitID);
        checkout_commit(given_branch_newest_commitOBJ);
        //然后 任务B：
        writeContents(HEAD_FOLD, given_branch_name);
    }
    //任务A函数(把当前分支最新commit对象更改)
    public static void checkout_commit(Commit new_commit_obj) {
        //首先把暂存区清空了
        a_stage_area.clear();
        a_stage_area.save();
        Map<String, String> temp_commit_tracking = last_commit_obj.getTracking();
        Set<String> all_file_path = temp_commit_tracking.keySet();
        for (String single_file_path : all_file_path) {
            File to_del_file = new File(single_file_path);
            to_del_file.delete();
        }
        //然后把新的commitobj中全部写入当前CWD
        new_commit_obj.change_cwd_by_stored_cwd_in_commitOBJ();
    }

    //仓库接受checkout commit_id -- filename指令
    public static void checkout_filename_with_commit_id(String commit_ID, String to_checkout_filename) {
        String given_file_Blob_ID = Repository.check_commit_exist_and_has_filename(to_checkout_filename, commit_ID);
        //获得Blob ID之后，我们要覆盖当前CWD的文件
        Blob given_file_in_Blob_fold = Blob.fromFile(given_file_Blob_ID);
        given_file_in_Blob_fold.save_back_in_CWD();
    }
    //接受filename和commitID，首先判断是否存在这个commit，然后检查commit里是否追踪这个filename
    //如果两条件都成立，返回这个文件在这次commit里对应的Blob对象的ID
    public static String check_commit_exist_and_has_filename(String filename, String commit_ID) {
        List<String> commit_ID_List = Utils.plainFilenamesIn(COMMITS_FOLDER);
        for (String single_commit_ID : commit_ID_List) {
            if (commit_ID.equals(single_commit_ID) ) {
            }
            //进行ID匹配，不匹配就跳过
            if (!commit_ID.equals(single_commit_ID)) {
                continue;
            }
            Commit match_commit_obj = Commit.fromFile(commit_ID);
            //找到匹配的commit对象了
            //下一步，在这个commit对象追踪的文件里，查看是否有这个file
            File given_file_in_CWD = Utils.join(CWD, filename);
            String given_file_path = given_file_in_CWD.getPath();
            String given_file_BlobID = match_commit_obj.return_ID_of_path(given_file_path);
            if (given_file_BlobID == null) {
                //追踪的commit没有这个file，error
                System.out.println("File does not exist in that commit.");
                exit(0);
            }
            return given_file_BlobID;
        }
        //没有这个commit，error
        System.out.println("No commit with that id exists.");
        exit(0);
        return null;
    }

    //仓库接受checkout -- filename指令
    public static void checkout_lastest_filename(String to_check_filename) {
        String given_file_Blob_ID = Repository.check_lastest_commit_has_file(to_check_filename);
        if (given_file_Blob_ID == null) {
            //不被当前commit追踪
            System.out.println("File does not exist in that commit.");
            exit(0);
        }
        //将其覆盖至CWD中
        //首先获得这个文件对应的最近一次提交的Commit中所追踪的Blob对象
        Blob given_file_in_blob_obj = Blob.fromFile(given_file_Blob_ID);
        given_file_in_blob_obj.save_back_in_CWD();
    }
    //接受filename检查这个文件是否被最新commit追踪 返回对应BLob对象的ID如果追踪 返回null如果不被追踪
    public static String check_lastest_commit_has_file(String to_check_filename) {
        File given_file_in_CWD = Utils.join(CWD, to_check_filename);
        String given_file_path = given_file_in_CWD.getPath();
        String given_file_ID = last_commit_obj.return_ID_of_path(given_file_path);
        return given_file_ID;
    }

    //仓库接受status指令
    public static void status() {
        show_branches_status();
        show_stage_status();
        /**
         *
         * 暂时跳过实现这个
         * === Modifications Not Staged For Commit ===
         * junk.txt (deleted)
         * wug3.txt (modified)
         *
         * === Untracked Files ===
         * random.stuff
         *
         * */
        //因为跳过了，所以打印个空的展示
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files ===");
        System.out.println();
    }
    //status指令展示branch部分
    public static void show_branches_status() {
        System.out.println("=== Branches ===");
        //先从HEAD读取当前所在分支并打印
        String temp_now_branch = readContentsAsString(HEAD_FOLD);
        System.out.println("*" + temp_now_branch);
        //然后再遍历heads文件夹里所有文件，除去已经打印过的当前branch
        //一行打印一个
        List<String> branch_name_list = Utils.plainFilenamesIn(ALL_HEADS_FOLD);
        for (String single_branch_name : branch_name_list) {
            if (single_branch_name.equals(temp_now_branch)) {
                continue;
            }
            System.out.println(single_branch_name);
        }
        //来个空行 并结束这部分的打印
        System.out.println();
    }
    //status指令展示addstage部分和removestage部分
    public static void show_stage_status() {
        //stage file部分
        System.out.println("=== Staged Files ===");
        //读取所有的addstage文件并打印
        if (!a_stage_area.get_addstage().isEmpty()) {
            Set<String> key_set_of_addstage = a_stage_area.get_addstage().keySet();
            for (String tempkey : key_set_of_addstage) {
                String after_cut_file_name = Paths.get(tempkey).getFileName().toString();
                System.out.println(after_cut_file_name);
            }
        }
        //来个空行并结束
        System.out.println();

        //removed file部分
        System.out.println("=== Removed Files ===");
        //读取所有的removestage文件并打印
        if (!a_stage_area.get_removestage().isEmpty()) {
            for (String tempval_path : a_stage_area.get_removestage()) {
                String after_cut_file_name = Paths.get(tempval_path).getFileName().toString();
                System.out.println(after_cut_file_name);
            }
        }
        //来个空行结束
        System.out.println();
    }

    //仓库接受find指令
    public static void find(String Commit_message) {
        //所有commit ID列表
        List<String> commit_ID_List = Utils.plainFilenamesIn(COMMITS_FOLDER);
        boolean is_find = false;
        for (String single_commit_ID : commit_ID_List) {
            //根据这个ID访问文件
            File target_commit_file = Utils.join(COMMITS_FOLDER, single_commit_ID);
            Commit temp_commit_obj = readObject(target_commit_file, Commit.class);
            if (temp_commit_obj.getMessage().equals(Commit_message)) {
                is_find = true;
                System.out.println(single_commit_ID);
            }
        }
        if (!is_find) {
            System.out.println("Found no commit with that message.");
            exit(0);
        }
    }

    //仓库接受global-log指令
    public static void global_log() {
        List<String> commit_ID_List = Utils.plainFilenamesIn(COMMITS_FOLDER);
        for (String single_commit_ID : commit_ID_List) {
            //根据这个ID，去读取Commits文件夹内的文件
            File target_commit_file = Utils.join(COMMITS_FOLDER, single_commit_ID);
            Commit temp_commit_obj = readObject(target_commit_file, Commit.class);
            temp_commit_obj.show_in_format();
        }
    }

    //仓库接受log指令
    public static void log() {
        foreach_show_from_a_commit_obj(last_commit_obj);
    }
    //从接受的commit对象开始，先输出再遍历到parent1节点(有parent2就忽略，不遍历)
    public static void foreach_show_from_a_commit_obj(Commit start_commit_obj) {
        while(!start_commit_obj.getParents().isEmpty()) {
            start_commit_obj.show_in_format();
            //遍历到其parent上去
            String Parent1_ID = start_commit_obj.getParents().get(0);
            File to_read_commit_obj_file_in_Commits = Utils.join(Repository.COMMITS_FOLDER, Parent1_ID);
            start_commit_obj = readObject(to_read_commit_obj_file_in_Commits, Commit.class);
        }
        //剩下个init commit没打印
        start_commit_obj.show_in_format();;
    }
    //log指令的helper函数----------------------------------------

    //仓库接受rm指令
    public static void remove_file(String to_rm_file_name) {
        //情况1：传入的文件在addstage当中
        if (!a_stage_area.is_addstage_Empty() && a_stage_area.check_file_is_in_addstage(to_rm_file_name)) {
            a_stage_area.remove_file_in_addstage(to_rm_file_name);
            return;
        }
        //情况2：传入的文件被当前commit对象追踪
        if (last_commit_obj.hasfilename(to_rm_file_name)) {
            //加入removestage
            a_stage_area.remove(to_rm_file_name);
            return;
        }
        //情况3：错误情况——文件不在addstage也没被当前commit对象追踪，报错并exit退出程序
        System.out.println("No reason to remove the file.");
        exit(0);
    }

    //仓库接收一个add指令，并将其转交暂存区处理
    public static void add(String filename) {
        File tempfile = join(CWD, filename);
        if (!tempfile.exists()) {
            System.out.println("File does not exit.");
            exit(0);
        }
        //先将调用本次add的StageArea对象更新为上一次存储的StageArea
        if(Repository.StageAreaFile.exists()) {
            a_stage_area = Utils.readObject(Repository.StageAreaFile, StageArea.class);
        }
        //先判断这个文件在不在remove stage，如果在则从remove stage上删除即可
        if (!a_stage_area.is_removestage_Empty() && a_stage_area.check_file_is_in_removestage(filename)) {
            a_stage_area.remove_file_in_removestage(filename);
            return;
        }
        a_stage_area.add(tempfile);
    }


    public static void commit(String _message) {
        commit(_message, null);
    }
    public static void commit(String _message, String second_parent_commitID) {
        if (_message.length() == 0) {
            System.out.println("Please enter a commit message.");
            exit(0);
        }
        Map<String, String>tracking = a_stage_area.do_a_commit();
        List<String> Parent = new ArrayList<>();
        //从中读取ID存入parent
        Parent.add(last_commit_obj.getCommitObjectID());
        if (second_parent_commitID != null) {
            Parent.add(second_parent_commitID);
        }
        //有message、Parent、trackingMap 可以创建新的Commit对象了
        Commit now_branch_new_commit_obj = new Commit(_message, tracking, Parent);
        //保存新的commit对象
        now_branch_new_commit_obj.save();
        //更新HEAD指针
        update_head_pointer(now_branch, now_branch_new_commit_obj.getCommitObjectID());
    }


    private static Commit get_last_commit() {
        //如果还没有初始化过仓库则返回null
        if (!is_repository_init()) {
            return null;
        }
        //如果在这个branch上还没有过commit，则返回null
        if (!branch_has_commit(now_branch)) {
            return null;
        }
        //读取refs/heads/分支名 的 commit ID
        File now_branch_file_msg = Utils.join(ALL_HEADS_FOLD, now_branch);
        String now_branch_last_commit_ID = Utils.readContentsAsString(now_branch_file_msg);
        //根据objects/Commits/ID名 寻找对应Commits对象进行匹配
        File now_branch_last_commit_fold = Utils.join(COMMITS_FOLDER, now_branch_last_commit_ID);
        //匹配后readobject
        Commit last_commit_obj = readObject(now_branch_last_commit_fold, Commit.class);
        //返回这个object
        return last_commit_obj;
    }

    private static String get_current_branch() {
        //如果还未初始化仓库则返回null
        if (!is_repository_init()) {
            return null;
        }
        //读取HEAD文件里的分支名并返回
        return  Utils.readContentsAsString(Repository.HEAD_FOLD);
    }

    //传入分支名和最新commit对象的ID，更新这个分支的head指针
    private static void update_head_pointer(String branch_name, String new_commit_obj_ID) {
        File branch_head_pointer_file = Utils.join(ALL_HEADS_FOLD, branch_name);
        //覆盖原本file存储的ID，即更新head指针指向的commit对象
        writeContents(branch_head_pointer_file, new_commit_obj_ID);
    }

    //初始化函数init()的主体部分 ----------init----------init----------init----------
    public static void init() {
        //检查是否是重复init。是的话则报错
        initTwiceCheck();
        //设置文件结构
        setupPersistence();
        //在./gitlet/HEAD.txt 文件中写入当前的branch(默认branch:master)
        setCurrentBranchtoHEAD(DEFAULT_BRANCH_NAME);
        //当前的branch设置为默认值
        now_branch = DEFAULT_BRANCH_NAME;
        //新建默认Commit对象
        createFirstCommit();
    }
    //初始化函数init()的主体部分 ----------init----------init----------init----------

    //初始化函数init()的helper_function部分 ----------init----------init----------init----------
    //如果已创建过仓库，则报错
    private static void initTwiceCheck() {
        if (GITLET_FOLDER.exists() && GITLET_FOLDER.isDirectory()) {
            System.out.println("A Gitlet version-control system already exists in the current directory");
            exit(0);
        }
    }

    /** 用于最初创建gitlet初始文件夹目录
     * --.gitlet
     *          --objects
     *                     --Blobs
     *                              --Blob_1(file)
     *                              --Blob_2(file)
     *                              ......
     *                     --Commits
     *                              --Commit_1(file)
     *                              --Commit_2(file)
     *                              ......
     *          --refs
     *                     --heads
     *                              --master(file)
     *                              --branch2(file)
     *                              ......
     *          --HEAD(file)
     *          --StageArea(file)
     */
    private static void setupPersistence() {
        //创建文件
        //.gitlet
        GITLET_FOLDER.mkdir();
        //.gitlet/objects
        OBJECTS_FOLDER.mkdir();
        //.gitlet/HEAD.txt
//        HEAD_FOLD.createNewFile();
        //.gitlet/Blobs
        BLOBS_FOLDER.mkdir();
        //.gitlet/Commits
        COMMITS_FOLDER.mkdir();
        //.gitlet/refs
        REFS_FOLDER.mkdir();
        //.gitlet/refs/heads
        ALL_HEADS_FOLD.mkdir();
    }

    //新建默认commit
    private static void createFirstCommit()  {
        //创建无参数Commit对象，并保存
        Commit firstCommitObject = new Commit();
        firstCommitObject.save();
        //令当前所在分支指向当前Commit对象的ID(每个branch的HEAD指针都指向在这个branch上的末尾Commit对象)
        setBranchHead(Utils.join(ALL_HEADS_FOLD, DEFAULT_BRANCH_NAME), firstCommitObject.getCommitObjectID());
    }
    //初始化函数init()的helper_function部分 ----------init----------init----------init----------


    //向./gitlet/HEAD.txt传入Branch_Name，更改当前选中的Head指针为"Branch_Name"分支
    //当新创建仓库时，或者更改Branch时，要用到
    private static void setCurrentBranchtoHEAD(String Branch_Name) {
        writeContents(HEAD_FOLD, Branch_Name);
    }

    //根据传入的branch分支文件路径，更新该文件中内容，将该branch指针指向最新的Commit对象的ID
    //新创建仓库 或者 在当前branch新提交一个commit时要用到
    private static void setBranchHead(File branch_info_file, String new_commit_object_ID) {
        writeContents(branch_info_file, new_commit_object_ID);
    }


    //在所有命令开始前，检查是否创建仓库
    public static void checkAlreadyInit(String firstArg) {
        //.gitlet不存在 或者 .gitlet存在但不是文件夹 这两种情况都说明没有初始化
        if ((!GITLET_FOLDER.exists() || (GITLET_FOLDER.exists() && !GITLET_FOLDER.isDirectory()) )&& !firstArg.equals("init"))  {
            System.out.println("Not in an initialized Gitlet directory");
            exit(0);
        }
    }

    //直接判断是否初始化过仓库 return true如果初始化过
    public static boolean is_repository_init() {
        return Repository.GITLET_FOLDER.exists();
    }

    //传入一个branch的名字，返回true如果这个branch上有过commit
    public static boolean branch_has_commit(String check_branch_name) {
        //如果这个branch有过commit，heads文件夹里肯定有它的文件，Commits文件夹里肯定存在对应最新commitID的文件
        File branch_fold_in_heads_dir = Utils.join(ALL_HEADS_FOLD, check_branch_name);
        if (!branch_fold_in_heads_dir.exists()) {
            return false;
        }
        String last_commit_id_in_checkbranch = Utils.readContentsAsString(branch_fold_in_heads_dir);
        if (last_commit_id_in_checkbranch.isEmpty()) {
            return false;
        }
        return true;
    }


}
