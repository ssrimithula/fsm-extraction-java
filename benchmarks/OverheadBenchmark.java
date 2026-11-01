/**
 * Standalone overhead benchmark for Objective O5 ("measure the runtime of
 * each test program with and without the BCI agent attached, and report
 * the slowdown factor"). TestProgramFields itself finishes in a handful
 * of writes, too fast to time meaningfully - this does a large, tight
 * loop of field writes instead, which is representative of what the real
 * Dining Philosophers / Readers-Writers benchmarks (Objective O4, planned
 * for Oct-Dec 2026) will stress once they're wired in.
 */
public class OverheadBenchmark {
    static int x = 0;
    static int y = 0;

    public static void main(String[] args) {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            x = i;
            y = -i;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("iterations=" + iterations + " elapsed_ms=" + elapsedMs
                + " final_x=" + x + " final_y=" + y);
    }
}
