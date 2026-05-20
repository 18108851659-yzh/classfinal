package net.roseboy.classfinal.util;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * jar/war操作工具类
 *
 * @author roseboy
 */
public class JarUtils {
    //打包时需要删除的文件
    public static final String[] DLE_FILES = {".DS_Store", "Thumbs.db"};

    /**
     * 获取jar文件中的条目名称列表（保持原始顺序）
     */
    public static List<String> getEntryNames(String jarPath) {
        List<String> names = new ArrayList<>();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(new File(jarPath));
            Enumeration<?> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                names.add(entry.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IoUtils.close(zipFile);
        }
        return names;
    }

    /**
     * 把目录压缩成jar
     *
     * @param jarDir    需要打包的目录
     * @param targetJar 打包出的jar/war文件路径
     * @return 打包出的jar/war文件路径
     */
    public static String doJar(String jarDir, String targetJar) {
        return doJar(jarDir, targetJar, null);
    }

    /**
     * 把目录压缩成jar，支持按原始条目顺序写入
     *
     * @param jarDir            需要打包的目录
     * @param targetJar         打包出的jar/war文件路径
     * @param originalEntryOrder 原始jar条目顺序，用于保持Spring Boot类加载顺序
     * @return 打包出的jar/war文件路径
     */
    public static String doJar(String jarDir, String targetJar, List<String> originalEntryOrder) {
        File jarDirFile = new File(jarDir);
        //枚举jarDir下的所有文件以及目录
        List<File> files = new ArrayList<>();
        IoUtils.listFile(files, jarDirFile);

        ZipOutputStream zos = null;
        OutputStream out = null;

        try {
            File jar = new File(targetJar);
            if (jar.exists()) {
                jar.delete();
            }

            out = new FileOutputStream(jar);
            zos = new ZipOutputStream(out);

            // 构建文件名到File的映射
            java.util.Map<String, File> fileMap = new java.util.LinkedHashMap<>();
            for (File file : files) {
                if (isDel(file)) {
                    continue;
                }
                String fileName = file.getAbsolutePath().substring(jarDirFile.getAbsolutePath().length() + 1);
                fileName = fileName.replace(File.separator, "/");
                fileMap.put(fileName, file);
            }

            // 已写入的条目名集合
            java.util.Set<String> written = new java.util.HashSet<>();

            // 如果有原始条目顺序，先按原始顺序写入
            if (originalEntryOrder != null && !originalEntryOrder.isEmpty()) {
                for (String entryName : originalEntryOrder) {
                    File file = fileMap.get(entryName);
                    if (file == null) {
                        // 原始条目在加密后可能不存在（如META-INF/maven/被删除），跳过
                        continue;
                    }
                    writeEntry(zos, entryName, file);
                    written.add(entryName);
                }
            }

            // 写入新增的条目（如META-INF/.xxxxxxxx/下的加密文件、ClassFinal agent文件等）
            for (java.util.Map.Entry<String, File> entry : fileMap.entrySet()) {
                if (!written.contains(entry.getKey())) {
                    writeEntry(zos, entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IoUtils.close(zos, out);
        }
        return targetJar;
    }

    /**
     * 写入一个zip条目
     */
    private static void writeEntry(ZipOutputStream zos, String fileName, File file) throws Exception {
        //目录，添加一个目录entry
        if (file.isDirectory()) {
            ZipEntry ze = new ZipEntry(fileName + "/");
            ze.setTime(System.currentTimeMillis());
            zos.putNextEntry(ze);
            zos.closeEntry();
        }
        //jar文件，需要写crc信息
        else if (fileName.endsWith(".jar")) {
            byte[] bytes = IoUtils.readFileToByte(file);
            ZipEntry ze = new ZipEntry(fileName);
            ze.setMethod(ZipEntry.STORED);
            ze.setSize(bytes.length);
            ze.setTime(System.currentTimeMillis());
            ze.setCrc(IoUtils.crc32(bytes));
            zos.putNextEntry(ze);
            zos.write(bytes);
            zos.closeEntry();
        }
        //其他文件直接写入
        else {
            ZipEntry ze = new ZipEntry(fileName);
            ze.setTime(System.currentTimeMillis());
            zos.putNextEntry(ze);
            byte[] bytes = IoUtils.readFileToByte(file);
            zos.write(bytes);
            zos.closeEntry();
        }
    }


    /**
     * 释放jar内以及子jar的所有文件
     *
     * @param jarPath   jar文件
     * @param targetDir 释放文件夹
     * @return 所有文件的完整路径，包含目录
     */
    public static List<String> unJar(String jarPath, String targetDir) {
        return unJar(jarPath, targetDir, null);
    }

    /**
     * 释放jar内以及子jar的所有文件
     *
     * @param jarPath   jar文件
     * @param targetDir 释放文件夹
     * @return 所有文件的完整路径，包含目录
     */
    public static List<String> unJar(String jarPath, String targetDir, List<String> includeFiles) {
        List<String> list = new ArrayList<>();
        File target = new File(targetDir);
        if (!target.exists()) {
            target.mkdirs();
        }

        FileInputStream fin = null;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(new File(jarPath));
            ZipEntry entry;
            File targetFile;
            //先把文件夹创建出来
            Enumeration<?> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
                    targetFile = new File(target, entry.getName());
                    if(!targetFile.exists()){
                        targetFile.mkdirs();
                    }
                } else {//有时候entries没有目录,根据文件路径创建目录
                    int lastSeparatorIndex = entry.getName().lastIndexOf(File.separator);
                    if (lastSeparatorIndex > 0) {
                        targetFile = new File(target, entry.getName().substring(0, lastSeparatorIndex));
                        if (!targetFile.exists()) {
                            targetFile.mkdirs();
                        }
                    }
                }
            }

            //再释放文件
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                targetFile = new File(target, entry.getName());

                //跳过未包含的文件
                if (includeFiles != null && includeFiles.size() > 0 && !includeFiles.contains(targetFile.getName())) {
                    continue;
                }
                byte[] bytes = IoUtils.toBytes(zipFile.getInputStream(entry));
                IoUtils.writeFile(targetFile, bytes);
                list.add(targetFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IoUtils.close(zipFile, fin);
        }
        return list;
    }

    /**
     * 在jar中根据文件名释放文件
     *
     * @param zip        压缩文件
     * @param fileName   文件名
     * @param targetFile 释放的目标文件
     * @return 释放出的文件的绝对路径
     */
    public static String releaseFileFromJar(File zip, String fileName, File targetFile) {
        byte[] bytes = getFileFromJar(zip, fileName);
        if (bytes == null) {
            return null;
        }
        IoUtils.writeFile(targetFile, bytes);
        return targetFile.getAbsolutePath();

    }

    /**
     * 在压缩文件中获取一个文件的字节
     *
     * @param zip      压缩文件
     * @param fileName 文件名
     * @return 文件的字节
     */
    public static byte[] getFileFromJar(File zip, String fileName) {
        ZipFile zipFile = null;
        try {
            if (!zip.exists()) {
                return null;
            }
            zipFile = new ZipFile(zip);
            ZipEntry zipEntry = zipFile.getEntry(fileName);
            if (zipEntry == null) {
                return null;
            }
            InputStream is = zipFile.getInputStream(zipEntry);
            return IoUtils.toBytes(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IoUtils.close(zipFile);
        }
        return null;
    }

    /**
     * 是否删除这个文件
     *
     * @param file 文件
     * @return 是否需要删除
     */
    public static boolean isDel(File file) {
        for (String f : DLE_FILES) {
            if (file.getAbsolutePath().endsWith(f)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取class运行的classes目录或所在的jar包目录
     *
     * @return 路径字符串
     */
    public static String getRootPath(String path) {
        if (path == null) {
            path = JarUtils.class.getResource("").getPath();
        }

        try {
            path = java.net.URLDecoder.decode(path, "utf-8");
        } catch (UnsupportedEncodingException e) {
        }

        if (path.startsWith("jar:") || path.startsWith("war:")) {
            path = path.substring(4);
        }
        // Spring Boot 3.2+ uses nested: URL scheme
        if (path.startsWith("nested:")) {
            path = path.substring(7);
        }
        if (path.startsWith("file:")) {
            path = path.substring(5);
        }

        // Remove trailing slash before ! for nested jar URLs like /path/to/jar/!BOOT-INF/...
        // The path after stripping prefixes might be: /E:/path/to/jar/!BOOT-INF/classes!/
        // We need to strip the trailing / before the first !
        if (path.contains("/!") ) {
            int idx = path.indexOf("/!");
            String before = path.substring(0, idx);
            String after = path.substring(idx + 1);
            // Check if before ends with .jar or .war
            if (before.endsWith(".jar") || before.endsWith(".war")) {
                path = before + "!" + after;
            }
        }

        //没解压的war包
        if (path.contains("*")) {
            return path.substring(0, path.indexOf("*"));
        }
        //war包解压后的WEB-INF
        else if (path.contains("WEB-INF")) {
            return path.substring(0, path.indexOf("WEB-INF"));
        }
        //jar
        else if (path.contains("!")) {
            return path.substring(0, path.indexOf("!"));
        }
        //普通jar/war
        else if (path.endsWith(".jar") || path.endsWith(".war")) {
            return path;
        }
        //no
        else if (path.contains("/classes/")) {
            return path.substring(0, path.indexOf("/classes/") + 9);
        }
        return null;
    }

}
