import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Helpers: run a JVM on the compiled project (build/classes, agent/FSMAgent.jar) and capture its output. */
class TestSupport {
    static final Path ROOT = Paths.get(System.getProperty("project.root", System.getProperty("user.dir"))).toAbsolutePath();
    static final Path CLASSES = ROOT.resolve("build/classes");
    static final Path JAR = ROOT.resolve("agent/FSMAgent.jar");

    static String run(Path workDir, String stdin, String... javaArgs) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(System.getProperty("java.home") + "/bin/java"));
        cmd.addAll(Arrays.asList(javaArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile()).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        if (stdin != null) { p.getOutputStream().write(stdin.getBytes()); }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); throw new RuntimeException("timeout"); }
        return out;
    }
    /** Run a main class with the agent attached; returns program output. Trace goes to traceFile. */
    static String runWithAgent(Path workDir, String config, Path traceFile, boolean loops, String stdin, String mainClass, String... args) throws Exception {
        List<String> a = new ArrayList<>();
        a.add("-javaagent:" + JAR + "=" + ROOT.resolve("configs").resolve(config) + "," + traceFile + (loops ? ",loops" : ""));
        a.add("-cp"); a.add(CLASSES.toString()); a.add(mainClass); a.addAll(Arrays.asList(args));
        return run(workDir, stdin, a.toArray(new String[0]));
    }
    static String runClass(Path workDir, String mainClass, String... args) throws Exception {
        List<String> a = new ArrayList<>(Arrays.asList("-cp", CLASSES.toString(), mainClass)); a.addAll(Arrays.asList(args));
        return run(workDir, null, a.toArray(new String[0]));
    }
    static List<String> lines(Path f) throws IOException { return Files.readAllLines(f); }
}
