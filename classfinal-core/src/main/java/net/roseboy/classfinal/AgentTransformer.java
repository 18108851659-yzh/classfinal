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

        String rawPath = domain.getCodeSource().getLocation().getPath();
        String projectPath = JarUtils.getRootPath(rawPath);
        if (StrUtils.isEmpty(projectPath)) {
            return classBuffer;
        }

        JarDecryptor decryptor = JarDecryptor.getInstance();
        decryptor.init(projectPath);

        String dotName = className.replace("/", ".").replace("\\", ".");

        if (!decryptor.isEncryptedClass(dotName)) {
            return classBuffer;
        }

        byte[] bytes = decryptor.doDecrypt(projectPath, dotName);
        if (bytes != null && bytes.length > 3 && bytes[0] == -54 && bytes[1] == -2 && bytes[2] == -70 && bytes[3] == -66) {
            return bytes;
        }
        return classBuffer;
    }
}
