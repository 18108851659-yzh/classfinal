package net.roseboy.classfinal;

import net.roseboy.classfinal.util.JarUtils;
import net.roseboy.classfinal.util.StrUtils;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class AgentTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain domain, byte[] classBuffer) {
        if (className == null || domain == null || loader == null) {
            return classBuffer;
        }

        String projectPath = domain.getCodeSource().getLocation().getPath();
        projectPath = JarUtils.getRootPath(projectPath);
        if (StrUtils.isEmpty(projectPath)) {
            return classBuffer;
        }

        JarDecryptor decryptor = JarDecryptor.getInstance();
        decryptor.init(projectPath);

        className = className.replace("/", ".").replace("\\", ".");

        if (!decryptor.isEncryptedClass(className)) {
            return classBuffer;
        }

        byte[] bytes = decryptor.doDecrypt(projectPath, className);
        if (bytes != null && bytes[0] == -54 && bytes[1] == -2 && bytes[2] == -70 && bytes[3] == -66) {
            return bytes;
        }
        return classBuffer;
    }
}
