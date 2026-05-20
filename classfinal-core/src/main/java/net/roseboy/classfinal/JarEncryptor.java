package net.roseboy.classfinal;

import javassist.ClassPool;
import javassist.NotFoundException;
import net.roseboy.classfinal.util.*;

import java.io.File;
import java.util.*;

public class JarEncryptor {
    static Map<String, String> aopMap = new HashMap<>();

    static {
        aopMap.put("spring.class", "org.springframework.core.io.ClassPathResource#getInputStream");
        aopMap.put("spring.code", "is=net.roseboy.classfinal.JarDecryptor.getInstance().decryptConfigFile(this.path,is);");
        aopMap.put("spring.line", "999");

        aopMap.put("jfinal.class", "com.jfinal.kit.Prop#<Prop>(java.lang.String,java.lang.String)");
        aopMap.put("jfinal.code", "inputStream=net.roseboy.classfinal.JarDecryptor.getInstance().decryptConfigFile(fileName,inputStream);");
        aopMap.put("jfinal.line", "62");
    }

    private static final byte[] OBFUSCATED_KEY = {
            (byte)0x98, (byte)0xC0, (byte)0x8F, (byte)0xED, (byte)0xAC, (byte)0xF5, (byte)0xBA, (byte)0xD3,
            (byte)0xE2, (byte)0x2F, (byte)0x91, (byte)0x04, (byte)0x84, (byte)0x3D, (byte)0xB6, (byte)0x19,
            (byte)0x35, (byte)0x0F, (byte)0x1E, (byte)0x2C, (byte)0x6A, (byte)0x47, (byte)0x70, (byte)0xB3,
            (byte)0x16, (byte)0x89, (byte)0x5F, (byte)0xE2, (byte)0x0B, (byte)0xAC, (byte)0x38, (byte)0x91
    };
    private static final byte KEY_XOR = (byte)0xA7;
    public static final char[] ENCRYPT_KEY;

    static {
        ENCRYPT_KEY = new char[OBFUSCATED_KEY.length];
        for (int i = 0; i < OBFUSCATED_KEY.length; i++) {
            ENCRYPT_KEY[i] = (char)(OBFUSCATED_KEY[i] ^ KEY_XOR);
        }
    }

    private static final byte INDEX_XOR_KEY = (byte)0x5A;

    private String jarPath = null;
    private List<String> packages = null;
    private List<String> includeJars = null;
    private List<String> excludeClass = null;
    private List<String> classPath = null;
    private List<String> cfgfiles = null;

    private String jarOrWar = null;
    private File targetDir = null;
    private File targetLibDir = null;
    private File targetClassesDir = null;
    private Integer encryptFileCount = null;
    private Map<String, String> resolveClassName = new HashMap<>();
    private String randomPath = null;
    // 保存原始jar条目顺序，确保重新打包后Spring Boot类加载顺序不变
    private List<String> originalEntryOrder = new ArrayList<>();

    public JarEncryptor(String jarPath) {
        super();
        this.jarPath = jarPath;
    }

    public String doEncryptJar() {
        if (!jarPath.endsWith(".jar") && !jarPath.endsWith(".war")) {
            throw new RuntimeException("jar/war文件格式有误");
        }
        if (!new File(jarPath).exists()) {
            throw new RuntimeException("文件不存在:" + jarPath);
        }

        this.jarOrWar = jarPath.substring(jarPath.lastIndexOf(".") + 1);
        Log.debug("加密类型：" + jarOrWar);
        this.targetDir = new File(jarPath.replace("." + jarOrWar, Const.LIB_JAR_DIR));
        // 清理可能残留的临时目录，避免旧加密文件干扰
        IoUtils.delete(this.targetDir);
        this.targetDir.mkdirs();
        this.targetLibDir = new File(this.targetDir, ("jar".equals(jarOrWar) ? "BOOT-INF" : "WEB-INF")
                + File.separator + "lib");
        this.targetClassesDir = new File(this.targetDir, ("jar".equals(jarOrWar) ? "BOOT-INF" : "WEB-INF")
                + File.separator + "classes");
        Log.debug("临时目录：" + targetDir);

        this.randomPath = generateRandomPath();
        Log.debug("加密路径: META-INF/" + randomPath);

        List<String> allFile = JarUtils.unJar(jarPath, this.targetDir.getAbsolutePath());
        // 保存原始jar条目顺序
        this.originalEntryOrder = JarUtils.getEntryNames(jarPath);
        allFile.forEach(s -> Log.debug("释放：" + s));

        List<String> libJarFiles = new ArrayList<>();
        allFile.forEach(path -> {
            if (!path.toLowerCase().endsWith(".jar")) {
                return;
            }
            String name = path.substring(path.lastIndexOf(File.separator) + 1);
            if (StrUtils.isMatchs(this.includeJars, name, false)) {
                String targetPath = path.substring(0, path.length() - 4) + Const.LIB_JAR_DIR;
                List<String> files = JarUtils.unJar(path, targetPath);
                files.forEach(s -> Log.debug("释放：" + s));
                libJarFiles.add(path);
                libJarFiles.addAll(files);
            }
        });
        allFile.addAll(libJarFiles);

        List<File> classFiles = filterClasses(allFile);

        addClassFinalAgent();

        List<String> encryptClass = encryptClass(classFiles);
        this.encryptFileCount = encryptClass.size();

        writeEncryptIndex(encryptClass);

        clearClassMethod(classFiles);

        encryptConfigFile();

        writeManifestAttributes();

        String result = packageJar(libJarFiles);

        return result;
    }

    private String generateRandomPath() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(".");
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public List<File> filterClasses(List<String> allFile) {
        List<File> classFiles = new ArrayList<>();
        allFile.forEach(file -> {
            if (!file.endsWith(".class")) {
                return;
            }
            String className = resolveClassName(file, true);
            if (StrUtils.isMatchs(this.packages, className, false)
                    && !StrUtils.isMatchs(this.excludeClass, className, false)) {
                classFiles.add(new File(file));
                Log.debug("待加密: " + file);
            }
        });
        return classFiles;
    }

    private List<String> encryptClass(List<File> classFiles) {
        List<String> encryptClasses = new ArrayList<>();

        File metaDir = new File(this.targetDir, "META-INF" + File.separator + randomPath);
        if (!metaDir.exists()) {
            metaDir.mkdirs();
        }

        classFiles.forEach(classFile -> {
            String className = classFile.getName();
            if (className.endsWith(".class")) {
                className = resolveClassName(classFile.getAbsolutePath(), true);
            }
            byte[] bytes = IoUtils.readFileToByte(classFile);
            char[] pass = StrUtils.merger(ENCRYPT_KEY, className.toCharArray());
            bytes = EncryptUtils.en(bytes, pass, Const.ENCRYPT_TYPE);
            /* P2: 头部伪装 - 添加class文件魔数，让加密文件看起来像普通class */
            byte[] disguised = new byte[4 + bytes.length];
            disguised[0] = (byte)0xCA;
            disguised[1] = (byte)0xFE;
            disguised[2] = (byte)0xBA;
            disguised[3] = (byte)0xBE;
            System.arraycopy(bytes, 0, disguised, 4, bytes.length);
            File targetFile = new File(metaDir, className);
            IoUtils.writeFile(targetFile, disguised);
            encryptClasses.add(className);
            Log.debug("加密：" + className);
        });

        return encryptClasses;
    }

    private void writeEncryptIndex(List<String> encryptClasses) {
        File metaDir = new File(this.targetDir, "META-INF" + File.separator + randomPath);
        if (!metaDir.exists()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String name : encryptClasses) {
            sb.append(name).append("\n");
        }
        byte[] indexBytes = sb.toString().getBytes();
        for (int i = 0; i < indexBytes.length; i++) {
            indexBytes[i] = (byte)(indexBytes[i] ^ INDEX_XOR_KEY);
        }
        File indexFile = new File(metaDir, Const.ENCRYPT_INDEX);
        IoUtils.writeFile(indexFile, indexBytes);
        Log.debug("写入加密索引(已混淆): " + encryptClasses.size() + " 个类");
    }

    private void writeManifestAttributes() {
        File manifest = new File(this.targetDir, "META-INF/MANIFEST.MF");
        if (!manifest.exists()) {
            return;
        }
        try {
            java.util.jar.Manifest mf = new java.util.jar.Manifest(new java.io.FileInputStream(manifest));
            mf.getMainAttributes().putValue("ClassFinal-Path", randomPath);

            String osName = System.getProperty("os.name", "").toLowerCase();
            String libName;
            if (osName.contains("windows")) {
                libName = "classfinal_native.dll";
            } else if (osName.contains("mac")) {
                libName = "libclassfinal_native.jnilib";
            } else {
                libName = "libclassfinal_native.so";
            }

            File nativeLib = new File(this.targetDir, libName);
            if (nativeLib.exists()) {
                byte[] dllBytes = IoUtils.readFileToByte(nativeLib);
                int crc = crc32(dllBytes);
                mf.getMainAttributes().putValue("ClassFinal-CRC", String.valueOf(crc));
                Log.debug("Native库CRC32: " + crc);
            }

            java.io.FileOutputStream fos = new java.io.FileOutputStream(manifest);
            mf.write(fos);
            fos.close();
        } catch (Exception e) {
            Log.debug("写入manifest属性失败: " + e.getMessage());
        }
    }

    private static int crc32(byte[] data) {
        int crc = 0xFFFFFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xEDB88320;
                } else {
                    crc = crc >>> 1;
                }
            }
        }
        return crc ^ 0xFFFFFFFF;
    }

    private void clearClassMethod(List<File> classFiles) {
        ClassPool pool = ClassPool.getDefault();
        ClassUtils.loadClassPath(pool, this.targetLibDir);
        Log.debug("ClassPath: " + this.targetLibDir.getAbsolutePath());

        ClassUtils.loadClassPath(pool, this.classPath);
        this.classPath.forEach(classPath -> Log.debug("ClassPath: " + classPath));

        List<String> classPaths = new ArrayList<>();
        classFiles.forEach(classFile -> {
            String classPath = resolveClassName(classFile.getAbsolutePath(), false);
            if (classPaths.contains(classPath)) {
                return;
            }
            try {
                pool.insertClassPath(classPath);
            } catch (NotFoundException e) {
            }
            classPaths.add(classPath);
            Log.debug("ClassPath: " + classPath);
        });

        classFiles.forEach(classFile -> {
            String className = resolveClassName(classFile.getAbsolutePath(), true);
            byte[] bts = null;
            try {
                Log.debug("清除方法体: " + className);
                bts = ClassUtils.rewriteAllMethods(pool, className);
            } catch (Exception e) {
                Log.debug("ERROR:" + e.getMessage());
            }
            if (bts != null) {
                IoUtils.writeFile(classFile, bts);
            }
        });
    }

    public void addClassFinalAgent() {
        List<String> thisJarPaths = new ArrayList<>();
        thisJarPaths.add(this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());

        thisJarPaths.forEach(thisJar -> {
            File thisJarFile = new File(thisJar);
            if ("jar".equals(this.jarOrWar) && thisJar.endsWith(".jar")) {
                List<String> includeFiles = Arrays.asList(Const.CLASSFINAL_FILES);
                JarUtils.unJar(thisJar, this.targetDir.getAbsolutePath(), includeFiles);
            } else if ("war".equals(this.jarOrWar) && thisJar.endsWith(".jar")) {
                File targetClassFinalJar = new File(this.targetLibDir, thisJarFile.getName());
                byte[] bytes = IoUtils.readFileToByte(thisJarFile);
                IoUtils.writeFile(targetClassFinalJar, bytes);
            } else if (thisJar.endsWith("/classes/")) {
                List<File> files = new ArrayList<>();
                IoUtils.listFile(files, new File(thisJar));
                files.forEach(file -> {
                    String className = file.getAbsolutePath().substring(thisJarFile.getAbsolutePath().length());
                    File targetFile = "jar".equals(this.jarOrWar) ? this.targetDir : this.targetClassesDir;
                    targetFile = new File(targetFile, className);
                    if (file.isDirectory()) {
                        targetFile.mkdirs();
                    } else if (StrUtils.containsArray(file.getAbsolutePath(), Const.CLASSFINAL_FILES)) {
                        byte[] bytes = IoUtils.readFileToByte(file);
                        IoUtils.writeFile(targetFile, bytes);
                    }
                });
            }
        });

        File manifest = new File(this.targetDir, "META-INF/MANIFEST.MF");
        String preMain = "Premain-Class: " + CoreAgent.class.getName();
        String[] txts = {};
        if (manifest.exists()) {
            txts = IoUtils.readTxtFile(manifest).split("\r\n");
        }

        String str = StrUtils.insertStringArray(txts, preMain, "Main-Class:");
        IoUtils.writeTxtFile(manifest, str + "\r\n\r\n");
    }

    private void encryptConfigFile() {
        if (this.cfgfiles == null || this.cfgfiles.size() == 0) {
            return;
        }

        String[] supportFrame = {"spring"};
        List<File> aopClass = new ArrayList<>(supportFrame.length);

        Arrays.asList(supportFrame).forEach(name -> {
            String javaCode = aopMap.get(name + ".code");
            String clazz = aopMap.get(name + ".class");
            Integer line = Integer.parseInt(aopMap.get(name + ".line"));
            byte[] bytes = null;
            try {
                String thisJar = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                bytes = ClassUtils.insertCode(clazz, javaCode, line, this.targetLibDir, new File(thisJar));
            } catch (Exception e) {
                Log.debug("AOP insertCode FAILED: " + clazz + " error=" + e.getMessage());
            }
            if (bytes != null) {
                File cls = new File(this.targetDir, clazz.split("#")[0] + ".class");
                IoUtils.writeFile(cls, bytes);
                aopClass.add(cls);
            }
        });

        this.encryptClass(aopClass);
        aopClass.forEach(cls -> cls.delete());

        List<File> configFiles = new ArrayList<>();
        File[] files = this.targetClassesDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && StrUtils.isMatchs(this.cfgfiles, file.getName(), false)) {
                configFiles.add(file);
            }
        }
        this.encryptClass(configFiles);
        configFiles.forEach(file -> IoUtils.writeTxtFile(file, ""));
    }

    private String packageJar(List<String> libJarFiles) {
        libJarFiles.forEach(targetJar -> {
            if (!targetJar.endsWith(".jar")) {
                return;
            }
            String srcJarDir = targetJar.substring(0, targetJar.length() - 4) + Const.LIB_JAR_DIR;
            if (!new File(srcJarDir).exists()) {
                return;
            }
            JarUtils.doJar(srcJarDir, targetJar);
            IoUtils.delete(new File(srcJarDir));
            Log.debug("打包: " + targetJar);
        });

        IoUtils.delete(new File(this.targetDir, "META-INF/maven"));

        String targetJar = jarPath.replace("." + jarOrWar, "-encrypted." + jarOrWar);
        String result = JarUtils.doJar(this.targetDir.getAbsolutePath(), targetJar, this.originalEntryOrder);
        IoUtils.delete(this.targetDir);
        Log.debug("打包: " + targetJar);
        return result;
    }

    private String resolveClassName(String fileName, boolean classOrPath) {
        String result = resolveClassName.get(fileName + classOrPath);
        if (result != null) {
            return result;
        }
        String file = fileName.substring(0, fileName.length() - 6);
        String K_CLASSES = File.separator + "classes" + File.separator;
        String K_LIB = File.separator + "lib" + File.separator;

        String clsPath;
        String clsName;
        if (file.contains(K_LIB)) {
            clsName = file.substring(file.indexOf(Const.LIB_JAR_DIR, file.indexOf(K_LIB))
                    + Const.LIB_JAR_DIR.length() + 1);
            clsPath = file.substring(0, file.length() - clsName.length() - 1);
        } else if (file.contains(K_CLASSES)) {
            clsName = file.substring(file.indexOf(K_CLASSES) + K_CLASSES.length());
            clsPath = file.substring(0, file.length() - clsName.length() - 1);
        } else {
            clsName = file.substring(file.indexOf(Const.LIB_JAR_DIR) + Const.LIB_JAR_DIR.length() + 1);
            clsPath = file.substring(0, file.length() - clsName.length() - 1);
        }
        result = classOrPath ? clsName.replace(File.separator, ".") : clsPath;
        resolveClassName.put(fileName + classOrPath, result);
        return result;
    }

    public Integer getEncryptFileCount() {
        return encryptFileCount;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public void setIncludeJars(List<String> includeJars) {
        this.includeJars = includeJars;
    }

    public void setExcludeClass(List<String> excludeClass) {
        this.excludeClass = excludeClass;
    }

    public void setClassPath(List<String> classPath) {
        this.classPath = classPath;
    }

    public void setCfgfiles(List<String> cfgfiles) {
        this.cfgfiles = cfgfiles;
    }

}
