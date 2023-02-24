package com.github.ryarnyah;

import org.apache.commons.codec.binary.Hex;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JavacardJCDK {
    private String name;
    private String path;
    private JavacardVersion version;

    public JavacardJCDK() {
    }

    public JavacardJCDK(String path) {
        this.path = path;
        this.version = detectJcdkVersion();
        this.name = version.name();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public JavacardVersion getVersion() {
        return version;
    }

    public void setVersion(JavacardVersion version) {
        this.version = version;
    }

    public JavacardJCDK dup() {
        JavacardJCDK javacardJCDK = new JavacardJCDK();
        javacardJCDK.setVersion(this.getVersion());
        javacardJCDK.setName(this.getName());
        javacardJCDK.setPath(this.getPath());
        return javacardJCDK;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavacardJCDK that = (JavacardJCDK) o;
        return name.equals(that.name) && path.equals(that.path) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path, version);
    }

    public enum JavacardVersion {
        NONE, V211, V212, V221, V222, V301, V303, V304, V305U1, V305U2, V305U3, V305U4, V310B43, V310R;

        public boolean isVerifySupported() {
            return (this == V305U3) ||
                    (this == V305U4) ||
                    (this == V310B43) ||
                    (this == V310R);
        }

        public boolean isV3() {
            return (this == V305U1) ||
                    (this == V305U2) ||
                    (this == V305U3) ||
                    (this == V305U4) ||
                    (this == V310B43) ||
                    (this == V310R);
        }
    }

    private static final Map<String, JavacardVersion> KNOWN_VERSION_HASH = new HashMap<>() {
        {
            put("b14c6000c9e9fda6bf41060fd8881be5b3a22ff6", JavacardVersion.V303);
            put("b31e646fdc371448fdaa61957422f02dd1e0a98f", JavacardVersion.V304);
            put("d61e092c3377b48479d731e9064e1c6e800812da", JavacardVersion.V305U1);
            put("fd247497d22fa5a6a5776b9ba672a0ef8005e1e5", JavacardVersion.V305U2);
            put("b363c6a021126120de0288b37d9525be3245e45e", JavacardVersion.V305U3);
            put("a1ae71ecd2ef36ba6747bdcc30d14d2ccdbb33a3", JavacardVersion.V305U4);
            put("9179a7e64b577995c254fd0ed61526e324d266f5", JavacardVersion.V310B43);
            put("2fb329ccb8950ecad3ccad79cde13a073a2ad4ed", JavacardVersion.V310R);
            put("3451f71907fc06b69552f4157facea92224f0045", JavacardVersion.V221);
            put("928f4bf6abd462abb7acadac0d172b14359d4964", JavacardVersion.V222);
        }
    };

    private JavacardVersion detectJcdkVersion() {
        JavacardVersion version = JavacardVersion.NONE;
        if (this.path != null) {
            if (Files.exists(Paths.get(this.path, "lib", "tools.jar"))) {
                try (FileInputStream inputStream = new FileInputStream(Paths.get(this.path, "lib", "tools.jar").toFile())) {
                    byte[] data = inputStream.readAllBytes();
                    String toolsJarSum = sha1sum(data);
                    if (KNOWN_VERSION_HASH.containsKey(toolsJarSum)) {
                        version = KNOWN_VERSION_HASH.get(toolsJarSum);
                    }
                } catch (IOException e) {
                    // No error
                }
            } else if (Files.exists(Paths.get(this.path, "lib", "api21.jar"))) {
                version = JavacardVersion.V212;
            } else if (Files.exists(Paths.get(this.path, "bin", "api.jar"))) {
                version = JavacardVersion.V211;
            } else if (Files.exists(Paths.get(this.path, "lib", "converter.jar"))) {
                try (FileInputStream inputStream = new FileInputStream(Paths.get(this.path, "lib", "api.jar").toFile())) {
                    byte[] data = inputStream.readAllBytes();
                    String toolsJarSum = sha1sum(data);
                    if (KNOWN_VERSION_HASH.containsKey(toolsJarSum)) {
                        version = KNOWN_VERSION_HASH.get(toolsJarSum);
                    }
                } catch (IOException e) {
                    // No error
                }
            }
        }
        return version;
    }

    private static String sha1sum(byte[] data) {
        String sha1;
        try {
            MessageDigest msdDigest = MessageDigest.getInstance("SHA-1");
            msdDigest.update(data);
            sha1 = new String(Hex.encodeHex(msdDigest.digest()));
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
        return sha1;
    }

    public String getExportDir() {
        if (version == JavacardVersion.V212) {
            return Paths.get(path, "api21_export_files").toString();
        }
        return Paths.get(path, "api_export_files").toString();
    }

    public List<Path> getToolJars() {
        List<Path> jars = new ArrayList<>();
        if (version == JavacardVersion.V211) {
            // We don't support verification with 2.1.X, so only converter
            jars.add(Paths.get(path, "bin", "converter.jar"));
        } else if (version.isV3()) {
            jars.add(Paths.get(path, "lib", "tools.jar"));
        } else {
            jars.add(Paths.get(path, "lib", "converter.jar"));
            jars.add(Paths.get(path, "lib", "offcardverifier.jar"));
        }
        return jars;
    }
}
