package gitlet;

import java.io.File;
import java.io.IOException;

import static java.lang.System.exit;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {
    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Please enter a command");
            exit(0);
        }

        String firstArg = args[0];
        //检查是否已经init过，未初始化时输入其他指令 报错
        Repository.checkAlreadyInit(firstArg);
        switch(firstArg) {
            case "init":
                validateNumArgs("init", args, 1);
                Repository.init();
                break;
            case "add":
                validateNumArgs("add", args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                //TODO 只考虑了一个parent的情况
                validateNumArgs("commit", args, 2);
                Repository.commit(args[1]);
                break;
            case "rm":
                validateNumArgs("rm", args, 2);
                Repository.remove_file(args[1]);
                break;
            case "log":
                validateNumArgs("log", args, 1);
                Repository.log();
                break;
            case "global-log":
                validateNumArgs("global-log", args, 1);
                Repository.global_log();
                break;
            case "find":
                validateNumArgs("find", args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                validateNumArgs("status", args, 1);
                Repository.status();
                break;
            case "checkout":
                switch (args.length) {
                    case 2://checkout branch 切换branch
                        Repository.checkout_branchname(args[1]);
                        break;
                    case 3://checkout -- filename 回退filename为最新commit版本
                        if (!args[1].equals("--")) {
                            System.out.println("Incorrect operands.");
                            exit(0);
                        }
                        Repository.checkout_lastest_filename(args[2]);
                        break;
                    case 4://checkout commit_id -- filename 回退filename为对应commit_id版本
                        if (!args[2].equals("--")) {
                            System.out.println("Incorrect operands.");
                            exit(0);
                        }
                        Repository.checkout_filename_with_commit_id(args[1], args[3]);
                        break;
                    default:
                        System.out.println("Incorrect operands.");
                        exit(0);
                }
                break;
            case "branch":
                validateNumArgs("branch", args, 2);
                Repository.create_new_branch(args[1]);
                break;
            case "rm-branch":
                validateNumArgs("rm-branch", args, 2);
                Repository.remove_branch(args[1]);
                break;
            case "reset":
                validateNumArgs("reset", args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                validateNumArgs("merge", args, 2);
                Repository.merge(args[1]);
                break;

            default:
                System.out.println("No command with that name exists");
                exit(0);
        }
    }




    /**
     * 用于检测输入的指令所含的参数是否有足够多个
     */
    public static void validateNumArgs(String cmd, String[] args, int n) {
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            exit(0);
        }
    }

}
