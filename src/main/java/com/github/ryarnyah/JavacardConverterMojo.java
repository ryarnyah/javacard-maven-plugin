package com.github.ryarnyah;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.github.ryarnyah.ReflectionUtils.invokeMethodWithArray;
import static com.github.ryarnyah.ReflectionUtils.tryGetMethod;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;


@Mojo(
        name = "convert",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class JavacardConverterMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;
    @Parameter(property = "maven.javacard.runtimeId", defaultValue = "java")
    private String runtimeId;
    @Parameter(property = "maven.javacard.timeoutInSeconds", defaultValue = "60")
    private int timeoutInSeconds;
    @Parameter(property = "maven.javacard.debug", defaultValue = "false")
    private boolean debug;
    @Parameter(property = "maven.javacard.jcdk")
    private String jcdkPath;
    @Parameter(property = "jvm")
    private String jvm;
    @Parameter(property = "applets")
    private List<JavacardApplet> applets;
    @Component
    private ToolchainManager toolchainManager;
    @Parameter
    private Map<String, String> jdkToolchain;

    /**
     * A plexus-util StreamConsumer to redirect messages to plugin log
     */
    private final StreamConsumer out = line -> getLog().info(line);

    /**
     * A plexus-util StreamConsumer to redirect errors to plugin log
     */
    private final StreamConsumer err = line -> getLog().error(line);

    public void execute()
            throws MojoExecutionException, MojoFailureException {
        String javaPath = getEffectiveJvm(getToolchain());
        getLog().debug("Got java Path: " + javaPath);

        if (StringUtils.isEmpty(jcdkPath) || !Paths.get(jcdkPath).toFile().exists()) {
            throw new MojoFailureException("JCDK Path is invalid");
        }
        JavacardJCDK jcdk = new JavacardJCDK(jcdkPath);

        for (JavacardApplet applet : applets) {
            if (StringUtils.isEmpty(applet.getAppletAID())) {
                throw new MojoFailureException("AppletAID is mandatory for applet " + applet);
            }
            if (StringUtils.isEmpty(applet.getPackageAID())) {
                throw new MojoFailureException("PackageAID is mandatory for applet " + applet);
            }
            if (StringUtils.isEmpty(applet.getAppletClass())) {
                throw new MojoFailureException("AppletClass is mandatory for applet " + applet);
            }
            if (StringUtils.isEmpty(applet.getPackageName())) {
                throw new MojoFailureException("PackageName is mandatory for applet " + applet);
            }
            try {
                JavacardConverter.convertCAP(
                        javaPath,
                        timeoutInSeconds,
                        project,
                        out,
                        err,
                        jcdk,
                        applet,
                        debug,
                        getLog()
                );
            } catch (Exception e) {
                throw new MojoExecutionException("Unable to convert cap", e);
            }
        }
    }

    private String getEffectiveJvm(Toolchain toolchain) throws MojoFailureException {
        if (isNotEmpty(getJvm())) {
            File pathToJava = new File(getJvm()).getAbsoluteFile();
            if (!pathToJava.getPath().startsWith("java")) {
                throw new MojoFailureException("Given path does not end with java executor \""
                        + pathToJava.getPath() + "\".");
            }
            return pathToJava.getAbsolutePath();
        }

        if (toolchain != null) {
            String jvmToUse = toolchain.findTool("java");
            if (isNotEmpty(jvmToUse)) {
                return jvmToUse;
            }
        }

        // use the same JVM as the one used to run Maven (the "java.home" one)
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    private static <T extends ToolchainManager> Toolchain getToolchainMaven33x(Class<T> toolchainManagerType,
                                                                               T toolchainManager,
                                                                               MavenSession session,
                                                                               Map<String, String> toolchainArgs)
            throws MojoFailureException {
        Method getToolchainsMethod =
                tryGetMethod(toolchainManagerType, "getToolchains", MavenSession.class, String.class, Map.class);
        if (getToolchainsMethod != null) {
            //noinspection unchecked
            List<Toolchain> tcs = invokeMethodWithArray(toolchainManager,
                    getToolchainsMethod, session, "jdk", toolchainArgs);
            if (tcs.isEmpty()) {
                throw new MojoFailureException(
                        "Requested toolchain specification did not match any configured toolchain: " + toolchainArgs);
            }
            return tcs.get(0);
        }
        return null;
    }

    //TODO remove the part with ToolchainManager lookup once we depend on
    //3.0.9 (have it as prerequisite). Define as regular component field then.
    private Toolchain getToolchain() throws MojoFailureException {
        Toolchain tc = null;

        if (getJdkToolchain() != null) {
            tc = getToolchainMaven33x(ToolchainManager.class, getToolchainManager(), getSession(), getJdkToolchain());
        }

        if (tc == null) {
            tc = getToolchainManager().getToolchainFromBuildContext("jdk", getSession());
        }

        return tc;
    }

    public ToolchainManager getToolchainManager() {
        return toolchainManager;
    }

    public MavenProject getProject() {
        return project;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public MavenSession getSession() {
        return session;
    }

    public void setSession(MavenSession session) {
        this.session = session;
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }

    public int getTimeoutInSeconds() {
        return timeoutInSeconds;
    }

    public void setTimeoutInSeconds(int timeoutInSeconds) {
        this.timeoutInSeconds = timeoutInSeconds;
    }

    public String getJcdkPath() {
        return jcdkPath;
    }

    public void setJcdkPath(String jcdkPath) {
        this.jcdkPath = jcdkPath;
    }

    public String getJvm() {
        return jvm;
    }

    public void setJvm(String jvm) {
        this.jvm = jvm;
    }

    public List<JavacardApplet> getApplets() {
        return applets;
    }

    public void setApplets(List<JavacardApplet> applets) {
        this.applets = applets;
    }

    public void setToolchainManager(ToolchainManager toolchainManager) {
        this.toolchainManager = toolchainManager;
    }

    public Map<String, String> getJdkToolchain() {
        return jdkToolchain;
    }

    public void setJdkToolchain(Map<String, String> jdkToolchain) {
        this.jdkToolchain = jdkToolchain;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
