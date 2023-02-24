package com.github.ryarnyah;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.Arg;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringJoiner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class JavacardConverter {

    private JavacardConverter() {
    }

    public static void convertCAP(
            String javaExecutable,
            int timeoutInSeconds,
            MavenProject project,
            StreamConsumer out,
            StreamConsumer err,
            JavacardJCDK jcdk,
            JavacardApplet applet,
            boolean debug,
            Log log) throws Exception {
        Commandline commandline = new Commandline();
        commandline.addSystemEnvironment();

        List<String> sysArgs = new ArrayList<>();
        List<String> appArgs = new ArrayList<>();

        sysArgs.add(javaExecutable);

        StringJoiner classPathJoiner = new StringJoiner(File.pathSeparator);
        for (Path jar : jcdk.getToolJars()) {
            classPathJoiner.add(jar.toString());
        }
        sysArgs.add("-cp '" + classPathJoiner + "'");

        String className;
        if (jcdk.getVersion().isV3()) {
            className = "com.sun.javacard.converter.Main";
            commandline.addEnvironment("jc.home", jcdk.getPath());
            sysArgs.add("-Djc.home=" + jcdk.getPath());
        } else {
            className = "com.sun.javacard.converter.Converter";
        }
        Path appletOutputPath = Paths.get(project.getBuild().getDirectory(), "javacard-compile");
        // output path
        appArgs.add("-d '" + appletOutputPath + "'");

        // classes for conversion
        appArgs.add("-classdir '" + Paths.get(project.getBuild().getOutputDirectory()) + "'");

        // construct export path
        StringJoiner expStringBuilder = new StringJoiner(File.pathSeparator);

        // Add targetSDK export files
        expStringBuilder.add(jcdk.getExportDir());
        List<File> exps = new ArrayList<>();
        try {
            // imports
            List<Artifact> libraries = getConverterDependencies(project);
            for (Artifact library : libraries) {
                File path = library.getFile();
                Path tmp = Files.createTempDirectory(library.getGroupId() + "-" + library.getArtifactId());
                extractExps(path, tmp.toFile());
                exps.add(tmp.toFile());
            }
            for (File imp : exps) {
                expStringBuilder.add(imp.toString());
            }
            if (jcdk.getVersion().isVerifySupported()) {
                appArgs.add("-verify");
            } else {
                appArgs.add("-noverify");
            }
            appArgs.add("-useproxyclass");
            appArgs.add("-exportpath '" + expStringBuilder + "'");

            // always be a little verbose
            appArgs.add("-verbose");
            appArgs.add("-nobanner");

            if (debug) {
                appArgs.add("-debug");
            }

            // define applets
            appArgs.add("-applet " + hexAID(Hex.decodeHex(applet.getAppletAID())) + " "
                    + applet.getPackageName() + "." + applet.getAppletClass());

            // package properties
            VersionInformation versionInformation = new VersionInformation(project.getVersion());
            appArgs.add(applet.getPackageName() + " " + hexAID(Hex.decodeHex(applet.getPackageAID())) + " " +
                    versionInformation.getMajor() + "." + versionInformation.getMinor());

            for (String sysArg : sysArgs) {
                Arg arg = commandline.createArg();
                arg.setLine(sysArg);
            }
            Arg mainClass = commandline.createArg();
            mainClass.setValue(className);

            for (String appArg : appArgs) {
                Arg arg = commandline.createArg();
                arg.setLine(appArg);
            }

            log.debug("Execute " + commandline);
            int exitValue = CommandLineUtils.executeCommandLine(
                    commandline,
                    out,
                    err,
                    timeoutInSeconds
            );

            if (exitValue != 0) {
                throw new MojoFailureException("Unable to convert cap");
            }
            // Move converted files to output directory
            Path outputPath = Paths.get(project.getBuild().getDirectory());
            Path appletFilesPath = Paths.get(appletOutputPath.toString(), applet.getPackageName().replace('.', '/'), "javacard");
            File[] appletFiles = appletFilesPath.toFile().listFiles();
            if (appletFiles == null) {
                throw new MojoFailureException("Unable to access files in " + appletFilesPath);
            }
            for (File appletFile : appletFiles) {
                String fileName = FilenameUtils.getBaseName(appletFile.getName());
                String ext = FilenameUtils.getExtension(appletFile.getName());
                if (applet.getOutputName() != null) {
                    fileName = applet.getOutputName();
                }
                Path outPath = Paths.get(outputPath.toString(), fileName + "." + ext);
                log.info("Copying " + appletFile + " to " + outPath);
                Files.copy(appletFile.toPath(), outPath, REPLACE_EXISTING);
            }
        } finally {
            // Delete temporary folders / files
            for (File exp : exps) {
                Files.walkFileTree(exp.toPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file,
                                                     @SuppressWarnings("unused") BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e)
                            throws IOException {
                        if (e == null) {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                        // directory iteration failed
                        throw e;
                    }
                });
            }
        }
    }

    public static List<Artifact> getConverterDependencies(MavenProject project) {
        List<Artifact> deps = new ArrayList<>();
        for (Artifact artifact : project.getDependencyArtifacts()) {
            if (!"test".equals(artifact.getScope())) {
                deps.add(artifact);
            }
        }
        return deps;
    }

    private static String hexAID(byte[] aid) {
        StringBuilder hexAID = new StringBuilder();
        for (byte b : aid) {
            hexAID.append(String.format("0x%02X", b));
            hexAID.append(":");
        }
        String hex = hexAID.toString();
        // Cut off the final colon
        return hex.substring(0, hex.length() - 1);
    }

    private static List<File> extractExps(File in, File out) throws IOException {
        List<File> exps = new ArrayList<>();
        try (JarFile jarfile = new JarFile(in)) {
            Enumeration<JarEntry> entries = jarfile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().toLowerCase().endsWith(".exp")) {
                    File f = new File(out, entry.getName());
                    if (!f.exists()) {
                        if (!f.getParentFile().mkdirs())
                            throw new IOException("Failed to create folder: " + f.getParentFile());
                        f = new File(out, entry.getName());
                    }
                    try (InputStream is = jarfile.getInputStream(entry);
                         FileOutputStream fo = new java.io.FileOutputStream(f)) {
                        byte[] buf = new byte[1024];
                        while (true) {
                            int r = is.read(buf);
                            if (r == -1) {
                                break;
                            }
                            fo.write(buf, 0, r);
                        }
                    }
                    exps.add(f);
                }
            }
        }
        return exps;
    }
}
