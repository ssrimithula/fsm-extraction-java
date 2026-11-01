import java.io.*;
import java.util.*;

/**
 * TraceChecker - checks the base paper's safety properties directly on the RAW trace the agent
 * produced (before any abstraction), i.e. on exactly the states the extracted FSM is built from.
 *
 *   dp        : no two ADJACENT philosophers are EATING at the same time          (paper Sec 6.1, Fig 8)
 *   rw        : G[(r>0 -> w=0) && (w>0 -> r=0) && (w=0 || w=1)]                    (paper Sec 6.1, Fig 9)
 *   elevator  : G[(d=up & d'=up -> f<f') && (d=down & d'=down -> f>f') && (d!=d' -> f=f')]  (Fig 10)
 *
 * Usage: java TraceChecker <dp|rw|elevator> <rawTrace>
 * Exit code 0 = property holds on every state/transition of the trace, 1 = violated.
 */
public class TraceChecker {
    public static void main(String[] a) throws IOException {
        if (a.length != 2) { System.err.println("Usage: java TraceChecker <dp|rw|elevator> <rawTrace>"); System.exit(2); }
        String prop = a[0];
        List<Map<String, String>> st = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(a[1]))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] p = ln.split(":", 2);
                if (p.length != 2) continue;
                Map<String, String> m = new HashMap<>();
                for (String kv : p[1].split(",")) { String[] x = kv.split("=", 2); if (x.length == 2) m.put(x[0], x[1]); }
                st.add(m);
            }
        }
        int viol = 0, shown = 0;
        for (int i = 0; i < st.size(); i++) {
            String bad = null;
            Map<String, String> s = st.get(i);
            switch (prop) {
                case "dp": {
                    for (int k = 0; k < 5; k++)
                        if ("EATING".equals(s.get("state" + k)) && "EATING".equals(s.get("state" + ((k + 1) % 5))))
                            bad = "philosophers " + k + " and " + ((k + 1) % 5) + " both EATING";
                    break; }
                case "rw": {
                    int r = 0, w = 0;
                    for (String k : s.keySet()) {
                        if (k.startsWith("reader") && "READING".equals(s.get(k))) r++;
                        if (k.startsWith("writer") && "WRITING".equals(s.get(k))) w++;
                    }
                    if (r > 0 && w > 0) bad = "r=" + r + " and w=" + w + " at once";
                    else if (w > 1) bad = "w=" + w + " (two writers)";
                    break; }
                case "elevator": {
                    if (i == 0) break;
                    Map<String, String> p = st.get(i - 1);
                    String d = p.get("d"), d2 = s.get("d"); int f = Integer.parseInt(p.get("f")), f2 = Integer.parseInt(s.get("f"));
                    if (d.equals("up") && d2.equals("up") && !(f < f2)) bad = "up->up but floor " + f + " -> " + f2;
                    if (d.equals("down") && d2.equals("down") && !(f > f2)) bad = "down->down but floor " + f + " -> " + f2;
                    if (!d.equals(d2) && f != f2) bad = "direction changed " + d + "->" + d2 + " but floor " + f + " -> " + f2;
                    break; }
                default: System.err.println("unknown property " + prop); System.exit(2);
            }
            if (bad != null) { viol++; if (shown++ < 3) System.out.println("  VIOLATION at line " + (i + 1) + ": " + bad); }
        }
        System.out.println("property[" + prop + "]: " + st.size() + " states checked, " + viol + " violations -> " + (viol == 0 ? "HOLDS" : "VIOLATED"));
        System.exit(viol == 0 ? 0 : 1);
    }
}
