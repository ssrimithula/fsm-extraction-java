import java.io.*;
import java.util.*;

/**
 * ModelAbstractor - Algorithm 3 of the base paper ("Model abstraction", Sec 5), BINARY and
 * MULTIVALUED abstraction functions applied to a trace, one function f_i per key attribute.
 * (UNARY / subgraph abstraction is FSMBuilder's --subgraph attr=value; the paper applies unary first,
 *  and since every f_i works on its own attribute the order does not change the result.)
 *
 *   binary      key:bin:OP:CONST:TRUELABEL:FALSELABEL     OP in = != < >       e.g.  state0:bin:=:EATING:E:~E
 *   multivalued key:multi:C1:C2:...:Cn                    labels  <C1, C1-C2, ..., >Cn   e.g.  f:multi:3:6
 *
 * Usage: java ModelAbstractor <in.trace> <out.trace> <spec> [<spec> ...]
 * Attributes without a spec are passed through unchanged (so FSMBuilder can still apply its default
 * POSITIVE/NEGATIVE/ZERO sign abstraction, or --raw-ints can keep them).
 */
public class ModelAbstractor {
    public static void main(String[] a) throws IOException {
        if (a.length < 3) { System.err.println("Usage: java ModelAbstractor <in> <out> <spec> [<spec>...]  (see source header)"); System.exit(1); }
        Map<String, String[]> spec = new HashMap<>();
        for (int i = 2; i < a.length; i++) { String[] p = a[i].split(":"); spec.put(p[0], p); }
        int n = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(a[0])); PrintWriter w = new PrintWriter(a[1])) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] p = ln.split(":", 2);
                if (p.length != 2) continue;
                StringBuilder out = new StringBuilder(p[0]).append(':');
                boolean first = true;
                for (String kv : p[1].split(",")) {
                    String[] x = kv.split("=", 2);
                    if (x.length != 2) continue;
                    String v = spec.containsKey(x[0]) ? apply(spec.get(x[0]), x[1]) : x[1];
                    if (!first) out.append(',');
                    out.append(x[0]).append('=').append(v); first = false;
                }
                w.println(out); n++;
            }
        }
        System.out.println("Abstracted " + n + " trace lines -> " + a[1]);
    }

    static String apply(String[] s, String v) {
        if (s[1].equals("bin")) {                       // key:bin:OP:CONST:TRUE:FALSE   (':' in labels is not allowed)
            String op = s[2], c = s[3]; boolean r;
            switch (op) {
                case "=": r = v.equals(c); break;
                case "!=": r = !v.equals(c); break;
                case "<": r = Double.parseDouble(v) < Double.parseDouble(c); break;
                case ">": r = Double.parseDouble(v) > Double.parseDouble(c); break;
                default: throw new IllegalArgumentException("op " + op);
            }
            return r ? s[4] : s[5];
        }
        if (s[1].equals("multi")) {                     // key:multi:C1:...:Cn
            double x = Double.parseDouble(v);
            int n = s.length - 2;
            double[] c = new double[n];
            for (int i = 0; i < n; i++) c[i] = Double.parseDouble(s[2 + i]);
            if (x < c[0]) return "<" + s[2];
            if (x > c[n - 1]) return ">" + s[1 + n];
            for (int i = 0; i + 1 < n; i++) if (x >= c[i] && x <= c[i + 1]) return s[2 + i] + "-" + s[3 + i];
            return s[2] + "-" + s[2];                   // n == 1 and x == C1
        }
        throw new IllegalArgumentException("unknown abstraction " + s[1]);
    }
}
