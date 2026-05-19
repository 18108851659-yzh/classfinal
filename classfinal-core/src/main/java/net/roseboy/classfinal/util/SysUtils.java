package net.roseboy.classfinal.util;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SysUtils {

    public static String runCmd(String cmd, int line) {
        Process process;
        Scanner sc = null;
        StringBuffer sb = new StringBuffer();
        try {
            process = Runtime.getRuntime().exec(cmd);
            process.getOutputStream().close();
            sc = new Scanner(process.getInputStream());
            int i = 0;
            while (sc.hasNextLine()) {
                i++;
                String str = sc.nextLine();
                if (line <= 0) {
                    sb.append(str).append("\r\n");
                } else if (i == line) {
                    return str.trim();
                }
            }
            sc.close();
        } catch (Exception e) {
        } finally {
            IoUtils.close(sc);
        }
        return sb.toString();
    }

    public static String runCmd(String cmd, String substr) {
        Process process;
        Scanner sc = null;
        try {
            process = Runtime.getRuntime().exec(cmd);
            process.getOutputStream().close();
            sc = new Scanner(process.getInputStream());
            while (sc.hasNextLine()) {
                String str = sc.nextLine();
                if (str != null && str.contains(substr)) {
                    return str.trim();
                }
            }
            sc.close();
        } catch (Exception e) {
        } finally {
            IoUtils.close(sc);
        }
        return null;
    }

    public static List<String> getMacList() {
        ArrayList<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        try {
            java.util.Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface iface = en.nextElement();
                List<InterfaceAddress> addrs = iface.getInterfaceAddresses();
                for (InterfaceAddress addr : addrs) {
                    InetAddress ip = addr.getAddress();
                    if (ip.isLinkLocalAddress()) {
                        continue;
                    }
                    NetworkInterface network = NetworkInterface.getByInetAddress(ip);
                    if (network == null) {
                        continue;
                    }
                    byte[] mac = network.getHardwareAddress();
                    if (mac == null) {
                        continue;
                    }
                    sb.delete(0, sb.length());
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    if (!list.contains(sb.toString())) {
                        list.add(sb.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static String getCPUSerialNumber() {
        String sysName = System.getProperty("os.name");
        if (sysName.contains("Windows")) {
            String str = runCmd("wmic cpu get ProcessorId", 2);
            return str;
        } else if (sysName.contains("Linux")) {
            String str = runCmd("dmidecode |grep -A16 \"Processor Information$\"", "ID");
            if (str != null) {
                return str.substring(str.indexOf(":")).trim();
            }
        } else if (sysName.contains("Mac")) {
            String str = runCmd("system_profiler SPHardwareDataType", "Serial Number");
            if (str != null) {
                return str.substring(str.indexOf(":") + 1).trim();
            }
        }
        return "";
    }

    public static String getHardDiskSerialNumber() {
        String sysName = System.getProperty("os.name");
        if (sysName.contains("Windows")) {
            String str = runCmd("wmic path win32_physicalmedia get serialnumber", 2);
            return str;
        } else if (sysName.contains("Linux")) {
            String str = runCmd("dmidecode |grep -A16 \"System Information$\"", "Serial Number");
            if (str != null) {
                return str.substring(str.indexOf(":")).trim();
            }
        } else if (sysName.contains("Mac")) {
            String str = runCmd("system_profiler SPStorageDataType", "Volume UUID");
            if (str != null) {
                return str.substring(str.indexOf(":") + 1).trim();
            }
        }
        return "";
    }
}
