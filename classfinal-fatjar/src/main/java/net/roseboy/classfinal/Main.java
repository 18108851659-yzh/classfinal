package net.roseboy.classfinal;


import net.roseboy.classfinal.util.*;

import java.io.File;
import java.util.List;
import java.util.Scanner;


public class Main {
    public static void main(String[] args) {
        Const.pringInfo();
        Scanner scanner = new Scanner(System.in);

        CmdLineOption cmd = new CmdLineOption();
        cmd.addOption("packages", true, "加密的包名(可为空,多个用\",\"分割)");
        cmd.addOption("exclude", true, "排除的类名(可为空,多个用\",\"分割)");
        cmd.addOption("file", true, "加密的jar/war路径");
        cmd.addOption("libjars", true, "jar/war lib下的jar(可为空,多个用\",\"分割)");
        cmd.addOption("classpath", true, "依赖jar包目录(可为空,多个用\",\"分割)");
        cmd.addOption("cfgfiles", true, "需要加密的配置文件(可为空,多个用\",\"分割)");
        cmd.addOption("Y", false, "无需确认");
        cmd.addOption("debug", false, "调试模式");
        cmd.parse(args);

        String path=null,libjars,packages,excludeClass,classpath,cfgfiles;

        if (args == null || args.length == 0) {
            while (StrUtils.isEmpty(path)) {
                Log.print("请输入需要加密的jar/war路径:");
                path = scanner.nextLine();
            }

            Log.print("请输入jar/war包lib下要加密jar文件名(多个用\",\"分割):");
            libjars = scanner.nextLine();

            Log.print("请输入需要加密的包名(可为空,多个用\",\"分割):");
            packages = scanner.nextLine();

            Log.print("请输入需要排除的类名(可为空,多个用\",\"分割):");
            excludeClass = scanner.nextLine();

            Log.print("请输入依赖jar包目录(可为空,多个用\",\"分割):");
            classpath = scanner.nextLine();

            Log.print("请输入要加密的配置文件名(可为空,多个用\",\"分割):");
            cfgfiles = scanner.nextLine();
        }else{
            path = cmd.getOptionValue("file", "");
            libjars = cmd.getOptionValue("libjars", "");
            packages = cmd.getOptionValue("packages", "");
            excludeClass = cmd.getOptionValue("exclude", "");
            classpath = cmd.getOptionValue("classpath", "");
            cfgfiles = cmd.getOptionValue("cfgfiles", "");
        }

        Log.println();
        Log.println("加密信息如下:");
        Log.println("-------------------------");
        Log.println("1. jar/war路径:      " + path);
        Log.println("2. lib下的jar:       " + libjars);
        Log.println("3. 包名前缀:          " + packages);
        Log.println("4. 排除的类名:        " + excludeClass);
        Log.println("5. 加密配置文件:      " + cfgfiles);
        Log.println("6. ClassPath:       " + classpath);
        Log.println("-------------------------");
        Log.println();

        String yes;
        if (cmd.hasOption("Y")) {
            yes = "Y";
        } else {
            Log.println("确定执行吗？(Y/n)");
            yes = scanner.nextLine();
            while (!"n".equals(yes) && !"Y".equals(yes)) {
                Log.println("Yes or No ？[Y/n]");
                yes = scanner.nextLine();
            }
        }
        IoUtils.close(scanner);

        if (!"Y".equals(yes)) {
            Log.println("已取消！");
            return;
        }
        Log.println("处理中...");
        List<String> includeJarList = StrUtils.toList(libjars);
        List<String> packageList = StrUtils.toList(packages);
        List<String> excludeClassList = StrUtils.toList(excludeClass);
        List<String> classPathList = StrUtils.toList(classpath);
        List<String> cfgFileList = StrUtils.toList(cfgfiles);
        includeJarList.add("-");

        JarEncryptor encryptor = new JarEncryptor(path);
        encryptor.setPackages(packageList);
        encryptor.setIncludeJars(includeJarList);
        encryptor.setExcludeClass(excludeClassList);
        encryptor.setClassPath(classPathList);
        encryptor.setCfgfiles(cfgFileList);
        try {
            String result = encryptor.doEncryptJar();
            Log.println("加密完成！");
            Log.println("==>" + result);
        } catch (Exception e) {
            Log.println("ERROR: " + e.getMessage());
        }
    }
}
