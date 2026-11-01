import java.io.*;
import java.util.*;

/**
 * LoopAwareFSMBuilder - Algorithm 2 of the base paper ("Improved model extraction", Sec 4.2).
 *
 * Difference to Algorithm 1 (FSMBuilder): every state is  <loop id, x1..xm>.  The loop id (the
 * innermost loop the writing thread was in, stamped by the agent on every trace line as loop=Ln) is a
 * HIDDEN first component: two states with the same key values but different loop ids are DIFFERENT
 * nodes, so a value that is assigned before a loop and again inside it no longer creates a
 * cycle that is not in the source code. Inside one loop the same states repeat, so the real loop
 * closes properly (Fig 5(B), 6(B,C)).
 *
 * Also as in Sec 4.3, every edge is labelled with the number of times it was taken.
 *
 * Usage: java LoopAwareFSMBuilder <trace> <keys> <out.dot> [--raw-ints] [--show-loops]
 */
public class LoopAwareFSMBuilder {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) { System.err.println("Usage: java LoopAwareFSMBuilder <trace> <keys> <out.dot> [--raw-ints] [--show-loops]"); System.exit(1); }
        boolean raw = Arrays.asList(args).contains("--raw-ints"), show = Arrays.asList(args).contains("--show-loops");
        List<String> keys = Arrays.asList(new BufferedReader(new FileReader(args[1])).readLine().trim().split(","));

        Map<String, Integer> nodeId = new LinkedHashMap<>();          // full state (loop + values) -> node number
        Map<Integer, String> label = new HashMap<>();
        Map<String, Integer> edgeCount = new LinkedHashMap<>();
        String title = "Algorithm 2 (loop-aware) model";
        for (int k = 3; k < args.length - 1; k++) if (args[k].equals("--title")) title = args[k + 1].replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        String startKey = "L0|START";
        nodeId.put(startKey, 0);
        int cur = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(args[0]))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] p = ln.split(":", 2);
                if (p.length != 2) continue;
                String loop = "L0"; TreeMap<String, String> vals = new TreeMap<>();
                for (String kv : p[1].split(",")) {
                    String[] x = kv.split("=", 2);
                    if (x.length != 2) continue;
                    if (x[0].equals("loop")) loop = x[1];
                    else if (keys.contains(x[0])) vals.put(x[0], raw ? x[1] : FSMBuilder.abstractValue(x[0], x[1]));
                }
                String vtxt = vals.toString().replace("{", "").replace("}", "").replace(", ", " | ");
                String full = loop + "|" + vtxt;
                Integer id = nodeId.get(full);
                if (id == null) { id = nodeId.size(); nodeId.put(full, id); }
                label.put(id, vtxt + (show ? "\\n[" + loop + "]" : ""));
                String e = cur + "->" + id;
                edgeCount.merge(e, 1, Integer::sum);
                cur = id;
            }
        }
        // same visual style as FSMBuilder --pretty; node colour = which loop the state belongs to
        String[] palette = {"#dbeafe", "#fde68a", "#bbf7d0", "#fbcfe8", "#ddd6fe", "#fed7aa", "#a5f3fc", "#e9d5ff"};
        Map<String, String> loopColour = new LinkedHashMap<>();
        for (String full : nodeId.keySet()) {
            String lp = full.substring(0, full.indexOf('|'));
            if (!lp.equals("L0") && !loopColour.containsKey(lp)) loopColour.put(lp, palette[loopColour.size() % palette.length]);
        }
        try (PrintWriter w = new PrintWriter(args[2])) {
            StringBuilder legend = new StringBuilder("white = outside every loop");
            for (Map.Entry<String, String> e : loopColour.entrySet()) legend.append(" &#183; ").append(e.getKey()).append(" = loop ").append(e.getKey().substring(1));
            w.println("digraph FSM {");
            w.println("graph [label=<" + title + "<br/><font point-size=\"12\">colour = which loop the state belongs to (hidden loop id of Algorithm 2); xN = times the arrow was taken; dashed red = back to an earlier state</font>>, labelloc=t, fontsize=22, fontname=Helvetica, nodesep=0.45, ranksep=0.6, pad=0.3];");
            w.println("node [shape=box, style=\"rounded,filled\", color=\"#1e3a8a\", fontname=Helvetica, fontsize=14, margin=\"0.14,0.08\"];");
            w.println("edge [fontname=Helvetica, fontsize=12, color=\"#374151\", arrowsize=0.8];");
            w.println("n0 [shape=circle, label=\"START\", fillcolor=\"#86efac\", color=\"#166534\", width=0.75, fixedsize=true, fontsize=12];");
            for (Map.Entry<String, Integer> n : nodeId.entrySet()) {
                if (n.getValue() == 0) continue;
                String lp = n.getKey().substring(0, n.getKey().indexOf('|'));
                String fill = lp.equals("L0") ? "#ffffff" : loopColour.get(lp);
                w.println("n" + n.getValue() + " [label=\"" + label.get(n.getValue()).replace(" | ", "\\n").replace("=", " = ") + "\", fillcolor=\"" + fill + "\"];");
            }
            for (Map.Entry<String, Integer> e : edgeCount.entrySet()) {
                String[] ft = e.getKey().split("->");
                int f = Integer.parseInt(ft[0]), t = Integer.parseInt(ft[1]);
                boolean back = t <= f && f != 0;
                w.println("n" + f + " -> n" + t + " [label=\"x" + e.getValue() + "\"" + (back ? ", style=dashed, color=\"#dc2626\", fontcolor=\"#dc2626\"" : "") + "];");
            }
            w.println("}");
        }
        System.out.println("Loop-aware FSM: " + (nodeId.size() - 1) + " states, " + edgeCount.size() + " edges -> " + args[2]);
    }
}
