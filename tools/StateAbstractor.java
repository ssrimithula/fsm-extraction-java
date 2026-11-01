import java.io.*;
import java.util.*;

/**
 * StateAbstractor.java - a basic implementation of Algorithm 3 (Model
 * Abstraction) from the reference paper, which Phase 1's Result Analysis
 * report explicitly listed as future work ("group states by equivalence
 * classes to produce more compact FSMs for large programs").
 *
 * PROBLEM THIS SOLVES: FSMBuilder builds one state per DISTINCT combination
 * of key-variable values. For Dining Philosophers tracked as 5 individual
 * fields (state0..state4), that's up to 3^5 = 243 possible joint states -
 * across a short run, almost every snapshot is unique, so FSMBuilder's
 * deduplication/loop-detection essentially never fires, and the resulting
 * diagram is a long chain of near-unique states rather than a compact FSM.
 *
 * ABSTRACTION APPLIED: instead of tracking WHICH philosopher is in which
 * state, this collapses each snapshot down to a SYSTEM-LEVEL count: how
 * many philosophers are THINKING, how many HUNGRY, how many EATING right
 * now. Two very different concrete situations (e.g. "P0 eating, others
 * thinking" vs "P3 eating, others thinking") now map to the exact same
 * abstract state ("1 eating, 0 hungry, 4 thinking") - this is a genuine
 * equivalence-class grouping, not just a relabeling.
 *
 * IMPORTANT DESIGN NOTE - why counts are prefixed with 'C':
 * FSMBuilder.java (kept completely UNCHANGED) auto-abstracts any bare
 * integer value into POSITIVE/NEGATIVE/ZERO. Counts are always >= 0, so a
 * bare count like "3" would collapse to the same "POSITIVE" as a count of
 * "1" - destroying the exact abstraction we just built. Prefixing with a
 * non-numeric character ("C3") makes Integer.parseInt fail inside
 * FSMBuilder's abstractValue(), so it falls through to returning the raw
 * string unchanged - preserving the exact count as a distinct state label,
 * with zero changes to FSMBuilder itself.
 *
 * Usage:
 *   java StateAbstractor <inputTrace> <outputTrace> <rawKeys> <possibleValues>
 * Example:
 *   java StateAbstractor trace_dp.txt trace_dp_abstracted.txt \
 *        state0,state1,state2,state3,state4 THINKING,HUNGRY,EATING
 */
public class StateAbstractor {

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("Usage: java StateAbstractor <inputTrace> <outputTrace> <rawKeys> <possibleValues>");
            System.exit(1);
        }
        String inputPath = args[0];
        String outputPath = args[1];
        List<String> rawKeys = Arrays.asList(args[2].split(","));
        List<String> possibleValues = Arrays.asList(args[3].split(","));

        try (BufferedReader br = new BufferedReader(new FileReader(inputPath));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {

            // Running snapshot of the latest known value of every raw key -
            // same "keep a running snapshot" idea TraceGenerator uses, since
            // this trace also only reports ONE changed field per line.
            Map<String, String> current = new HashMap<>();
            for (String k : rawKeys) current.put(k, null);

            String line;
            long lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                String[] parts = line.split(":", 2);
                if (parts.length != 2) continue;
                String event = parts[0];

                for (String pair : parts[1].split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2 && rawKeys.contains(kv[0])) {
                        current.put(kv[0], kv[1]);
                    }
                }

                // Don't emit a line until every raw key has been seen at
                // least once (avoids counting "unknown" as a real value).
                boolean allKnown = true;
                for (String k : rawKeys) {
                    if (current.get(k) == null) { allKnown = false; break; }
                }
                if (!allKnown) continue;

                Map<String, Integer> counts = new LinkedHashMap<>();
                for (String v : possibleValues) counts.put(v, 0);
                for (String k : rawKeys) {
                    String v = current.get(k);
                    counts.merge(v, 1, Integer::sum);
                }

                StringBuilder out = new StringBuilder(event).append(':');
                boolean first = true;
                for (String v : possibleValues) {
                    if (!first) out.append(',');
                    // lowercase field name + 'C' prefix, e.g. "eating=C1"
                    out.append(v.toLowerCase()).append("=C").append(counts.get(v));
                    first = false;
                }
                bw.write(out.toString());
                bw.newLine();
            }
            System.out.println("Abstracted " + lineNum + " raw trace lines -> " + outputPath);
        }
    }
}
