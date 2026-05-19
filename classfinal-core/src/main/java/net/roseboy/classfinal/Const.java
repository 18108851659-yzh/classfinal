package net.roseboy.classfinal;

public class Const {
    public static final String VERSION = "v1.2.1";

    public static final String FILE_NAME = ".classes";

    public static final String LIB_JAR_DIR = "__temp__";

    public static final int ENCRYPT_TYPE = 1;

    public static final String ENCRYPT_INDEX = ".index";

    public static final String[] CLASSFINAL_FILES = {"CoreAgent.class", "AgentTransformer.class", "Const.class",
            "JarDecryptor.class",
            "IoUtils.class", "JarUtils.class", "Log.class", "StrUtils.class",
            "NativeDecryptor.class",
            "classfinal_native.dll", "libclassfinal_native.so", "libclassfinal_native.jnilib"};

    public static boolean DEBUG = false;

    public static void pringInfo() {
        String sysName = System.getProperty("os.name");
        if (sysName.contains("Windows")) {
            System.out.println();
            System.out.println("=========================================================");
            System.out.println("=                                                       =");
            System.out.println("=      Java Class Encryption Tool " + VERSION + "   by Mr.K      =");
            System.out.println("=                                                       =");
            System.out.println("=========================================================");
            System.out.println();
            return;
        }


        String[] color = {"\033[31m", "\033[32m", "\033[33m", "\033[34m", "\033[35m", "\033[36m",
                "\033[90m", "\033[92m", "\033[93m", "\033[94m", "\033[95m", "\033[96m"};
        System.out.println();

        for (int i = 0; i < 57; i++) {
            System.out.print(color[i % color.length] + "=\033[0m");
        }
        System.out.println();
        System.out.println("\033[34m=                                                       \033[92m=");
        System.out.println("\033[35m=       \033[31mJava \033[92mClass \033[95mEncryption \033[96mTool\033[0m \033[37m"
                + VERSION + "\033[0m   by \033[91mMr.K\033[0m     \033[93m=");
        System.out.println("\033[36m=                                                       \033[94m=");
        for (int i = 56; i >= 0; i--) {
            System.out.print(color[i % color.length] + "=\033[0m");
        }
        System.out.println();
        System.out.println();
    }

}
