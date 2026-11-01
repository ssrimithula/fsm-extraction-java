import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trace Generator (see Design & Solution, Section 3 "System Architecture").
 *
 * Formats the events coming from the Runtime Hook into the exact same line
 * format Phase 1's FSMBuilder.java already parses:
 *
 *     EVENT:key1=val1,key2=val2,...
 *
 * Design note - solving the "partial update" problem:
 * A single field write only tells us the new value of ONE variable, but
 * FSMBuilder builds one *state* per trace line out of ALL key variables
 * present on that line. If we emitted one line per raw field write
 * (e.g. "x_WRITE:x=1" then "y_WRITE:y=0"), every state would be missing
 * whichever variable didn't just change - this is exactly the "no
 * handling of partial trace updates" limitation called out in the Phase 1
 * Result Analysis report. To avoid re-introducing that gap, this class
 * keeps a running snapshot of the latest known value of every tracked
 * field and emits, on every write, a line containing the CURRENT value of
 * every tracked field - not just the one that changed. That keeps the
 * automatically-generated trace format-compatible AND semantically
 * complete for FSMBuilder's state construction.
 */
public class TraceGenerator {

    private final Map<String, String> currentValues = new TreeMap<>(); // TreeMap => deterministic ordering
    private final AtomicLong eventCounter = new AtomicLong(0);
    private final BufferedWriter writer;

    // Every tracked field must have been written at least once before a state is reported.
    // Without this the first trace lines would be partial states (e.g. only f known, d unknown),
    // which become bogus start states in the FSM.
    private final java.util.Set<String> expectedFields;

    public TraceGenerator(String outputPath, java.util.Set<String> expectedFields) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputPath));
        this.expectedFields = expectedFields;
    }

    /**
     * Records one tracked field write and appends a formatted trace line.
     * Synchronized because instrumented programs may write tracked fields
     * from multiple threads (e.g. Dining Philosophers, Readers-Writers).
     */
    public synchronized void recordEvent(String className, String fieldName, Object value, long timestampNanos) {
        currentValues.put(fieldName, String.valueOf(value));

        if (!currentValues.keySet().containsAll(expectedFields)) return; // state not fully known yet
        long seq = eventCounter.incrementAndGet();
        // Event name deliberately does NOT include the sequence number.
        // FSMBuilder.java (kept unchanged) deduplicates transitions by
        // matching the ENTIRE edge string, including the event label. If
        // every event carried a unique counter suffix, two genuinely
        // identical transitions (same states, same field changing) would
        // never look identical to FSMBuilder, and its deduplication -
        // which is meant to collapse exactly this case - would never
        // fire. Naming events by class+field alone lets repeated real
        // transitions actually collapse, which is what Algorithm 1's
        // dedup is supposed to do in the first place.
        String eventName = className + "." + fieldName + "_WRITE";

        StringBuilder line = new StringBuilder(eventName).append(':');
        boolean first = true;
        if (FSMAgent.loopAware) {          // Algorithm 2: id of the writing thread's innermost loop
            line.append("loop=L").append(FSMAgent.currentLoop());
            first = false;
        }
        for (Map.Entry<String, String> e : currentValues.entrySet()) {
            if (!first) line.append(',');
            line.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }

        try {
            writer.write(line.toString());
            writer.newLine();
            // Flush periodically rather than on every single event: flushing
            // per-line was measured to cost ~200x overhead on a tight-loop
            // benchmark (one fsync-class syscall per field write). Buffering
            // and flushing every FLUSH_INTERVAL events keeps most of that
            // I/O cost off the hot path while still bounding how much trace
            // data could be lost if the target JVM is killed uncleanly
            // between flushes (the shutdown hook in FSMAgent.premain covers
            // the normal-exit and most abrupt-exit cases via close()).
            if (seq % FLUSH_INTERVAL == 0) writer.flush();
        } catch (IOException ioe) {
            System.err.println("[FSMAgent] Failed to write trace line: " + ioe);
        }
    }

    private static final long FLUSH_INTERVAL = 500;

    public synchronized void close() {
        if (!currentValues.keySet().containsAll(expectedFields)) {
            java.util.Set<String> missing = new java.util.TreeSet<>(expectedFields);
            missing.removeAll(currentValues.keySet());
            System.err.println("[FSMAgent] WARNING: these tracked fields were never written, so no state was "
                    + "reported: " + missing + " (give them an explicit initial assignment)");
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}
