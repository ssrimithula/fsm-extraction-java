import java.io.*;
import java.util.*;

/**
 * PredicateAbstractor.java - implements the base paper's BINARY abstraction
 * (Section 5, Algorithm 3; demonstrated on Dining Philosophers in Figure 8),
 * as distinct from StateAbstractor.java's count-based abstraction.
 *
 * KEY DIFFERENCE FROM StateAbstractor: this keeps each key's POSITION/IDENTITY
 * distinct - it does NOT merge "philosopher 0 eating" with "philosopher 2
 * eating" the way a count would. Each attribute xi is independently mapped
 * through a predicate f_i(xi), exactly as the paper describes: "an attribute
 * xi is subject to a test f_i(xi), which returns true or false depending on
 * the value of xi in each state."
 *
 * WHY POSITION MATTERS HERE (and didn't for Readers-Writers): the Dining
 * Philosophers safety property is about ADJACENCY - "no two ADJACENT
 * philosophers eating." A count like eating=C2 cannot distinguish two
 * adjacent eaters (a real violation) from two safely non-adjacent ones -
 * counting would destroy exactly the information the property needs.
 * Readers-Writers has no such adjacency concept (all readers/writers are
 * interchangeable), which is why counting was the right abstraction there.
 *
 * Usage:
 *   java PredicateAbstractor <inputTrace> <outputTrace> <keys> <targetValue> <trueLabel> <falseLabel>
 * Example (matches the paper's Figure 8 exactly - predicate xi = E):
 *   java PredicateAbstractor trace_dp.txt trace_dp_binary.txt \
 *        state0,state1,state2,state3,state4 EATING E ~E
 */
public class PredicateAbstractor {

    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            System.err.println("Usage: java PredicateAbstractor <inputTrace> <outputTrace> <keys> <targetValue> <trueLabel> <falseLabel>");
            System.exit(1);
        }
        String inputPath = args[0];
        String outputPath = args[1];
        List<String> keys = Arrays.asList(args[2].split(","));
        String targetValue = args[3];
        String trueLabel = args[4];
        String falseLabel = args[5];

        try (BufferedReader br = new BufferedReader(new FileReader(inputPath));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {

            // Running snapshot, same idea as StateAbstractor/TraceGenerator -
            // needed because each raw trace line only reports one changed field.
            Map<String, String> current = new HashMap<>();
            for (String k : keys) current.put(k, null);

            String line;
            long lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                String[] parts = line.split(":", 2);
                if (parts.length != 2) continue;
                String event = parts[0];

                for (String pair : parts[1].split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2 && keys.contains(kv[0])) {
                        current.put(kv[0], kv[1]);
                    }
                }

                boolean allKnown = true;
                for (String k : keys) {
                    if (current.get(k) == null) { allKnown = false; break; }
                }
                if (!allKnown) continue;

                StringBuilder out = new StringBuilder(event).append(':');
                boolean first = true;
                for (String k : keys) {
                    if (!first) out.append(',');
                    String rawValue = current.get(k);
                    String abstracted = rawValue.equals(targetValue) ? trueLabel : falseLabel;
                    out.append(k).append('=').append(abstracted);
                    first = false;
                }
                bw.write(out.toString());
                bw.newLine();
            }
            System.out.println("Binary-abstracted " + lineNum + " raw trace lines -> " + outputPath);
        }
    }
}
