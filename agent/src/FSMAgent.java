import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * FSMAgent.java - Phase 2 deliverable.
 *
 * The Agent Loader + Runtime Hook described in the Design & Solution
 * document (Section 3, System Architecture / Section 4, Module
 * Description). Attached to a target JVM with:
 *
 *     java -javaagent:FSMAgent.jar=instrumentation.txt,trace_auto.txt -cp ... TargetProgram
 *
 * Agent args are "<configFile>,<outputTraceFile>":
 *   - configFile:      Field Filter config (see InstrumentationConfig)
 *   - outputTraceFile: where the auto-generated trace is written, in the
 *                       exact format Phase 1's FSMBuilder.java expects.
 *
 * No source code of the target program is read, modified, or recompiled -
 * only the bytecode the JVM loads at runtime, and only for classes listed
 * in the config file.
 */
public class FSMAgent {

    private static TraceGenerator traceGenerator;

    // ---- Algorithm 2 support: loop ids and one loop stack per thread --------------------------------
    static volatile boolean loopAware = false;
    static final java.util.Map<Integer, String> LOOPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_LOOP = new java.util.concurrent.atomic.AtomicInteger(1);
    private static final ThreadLocal<java.util.ArrayDeque<Integer>> LOOP_STACK =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    static int registerLoop(String description) {
        int id = NEXT_LOOP.getAndIncrement();
        LOOPS.put(id, description);
        return id;
    }
    /** Called by instrumented code at a loop header: entering the loop, or just another iteration. */
    public static void loopHeader(int id) {
        java.util.ArrayDeque<Integer> s = LOOP_STACK.get();
        if (s.isEmpty() || s.peek() != id) s.push(id);
    }
    /** Called by instrumented code on every path that leaves the loop; no-op if the loop is not on top. */
    public static void loopExit(int id) {
        java.util.ArrayDeque<Integer> s = LOOP_STACK.get();
        if (!s.isEmpty() && s.peek() == id) s.pop();
    }
    /** Id of the innermost loop the calling thread is currently in (0 = not inside any loop). */
    static int currentLoop() {
        java.util.ArrayDeque<Integer> s = LOOP_STACK.get();
        return s.isEmpty() ? 0 : s.peek();
    }

    /**
     * JVM entry point for a -javaagent, called before the target program's
     * own main() runs (java.lang.instrument.Premain-Class contract).
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            System.err.println("[FSMAgent] Usage: -javaagent:FSMAgent.jar=<configFile>,<outputTraceFile>[,loops]");
            return;
        }

        String[] args = agentArgs.split(",");
        if (args.length < 2) {
            System.err.println("[FSMAgent] Expected 2 args: <configFile>,<outputTraceFile>. Got: " + agentArgs);
            return;
        }

        String configPath = args[0].trim();
        String outputPath = args[1].trim();
        loopAware = args.length > 2 && args[2].trim().equals("loops");   // optional 3rd arg: Algorithm 2 mode

        InstrumentationConfig config = InstrumentationConfig.load(configPath);
        traceGenerator = new TraceGenerator(outputPath, config.allFields());

        System.err.println("[FSMAgent] Loaded. Tracking classes: " + config.classes()
                + " -> writing trace to " + outputPath);

        // Flush the trace file on JVM shutdown even if the target program
        // doesn't reach a "natural" end (e.g. is killed, or never calls
        // System.exit explicitly).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (traceGenerator != null) traceGenerator.close();
            if (loopAware) {
                try (java.io.PrintWriter pw = new java.io.PrintWriter(outputPath + ".loops")) {
                    new java.util.TreeMap<>(LOOPS).forEach((k, v) -> pw.println("L" + k + "=" + v));
                } catch (Exception ignored) { }
            }
        }));

        inst.addTransformer(new FSMClassTransformer(config));
    }

    /**
     * Runtime Hook (see Design & Solution Section 4). Compiled into the
     * agent and invoked by the instrumented bytecode itself, once per
     * tracked field write. Never called directly by target-program source
     * code - only by the INVOKESTATIC instructions FieldWriteMethodVisitor
     * inserts.
     */
    public static void hook(String className, String fieldName, Object value, long timestampNanos) {
        if (traceGenerator != null) {
            traceGenerator.recordEvent(className, fieldName, value, timestampNanos);
        }
    }

    /** ClassFileTransformer: the actual "Bytecode Transformer" block from Figure 1. */
    static class FSMClassTransformer implements ClassFileTransformer {

        private final InstrumentationConfig config;

        FSMClassTransformer(InstrumentationConfig config) {
            this.config = config;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                 ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            // className here is already slash-form (e.g. "com/example/Foo").
            if (className == null || !config.isClassTracked(className)) {
                return null; // null = "leave this class exactly as-is"; keeps overhead near zero
            }                // for every class we don't care about (Design Consideration: Scalability).

            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                FieldWriteClassVisitor visitor = new FieldWriteClassVisitor(Opcodes.ASM9, writer, className, config);
                if (loopAware) {
                    // Algorithm 2: first add loop-start/loop-end calls (tree API), then the field-write hooks
                    org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode(Opcodes.ASM9);
                    reader.accept(cn, ClassReader.EXPAND_FRAMES);
                    for (org.objectweb.asm.tree.MethodNode mn : cn.methods) LoopInstrumenter.instrument(className, mn);
                    cn.accept(visitor);
                } else {
                    reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                }
                return writer.toByteArray();
            } catch (Throwable t) {
                // Never let instrumentation failures crash the target program -
                // fail safe by returning the original, unmodified bytecode.
                System.err.println("[FSMAgent] Failed to instrument " + className + ": " + t);
                return null;
            }
        }
    }
}
