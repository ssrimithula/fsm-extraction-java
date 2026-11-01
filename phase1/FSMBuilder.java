import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class FSMBuilder {
    public static void main(String[] args) {
        List<Map<String, String>> states = new ArrayList<>();
        List<String> events = new ArrayList<>();
        Set<String> transitions = new HashSet<>();
        List<String> keyVars = new ArrayList<>();
        Set<String> visitedStates = new HashSet<>();
        // Phase 2 additions (both backward compatible): --raw-ints keeps integer values as-is
        boolean rawInts = false;
        // --clean: stutter steps (state unchanged after abstraction) are dropped, and an edge (from, to, event) is drawn once, even if it was first seen as a normal
        // edge and later as a [LOOP] edge (Phase 1 drew both copies).
        boolean cleanEdges = false;
        // --pretty [--title "text"]: presentation-quality drawing (small START, short edge labels,
        // multi-line / colour-coded nodes). Same model, only the .dot styling differs.
        boolean pretty = false; String title = "Finite State Machine from Execution Trace";
        // Phase 2 addition (backward compatible): --subgraph attr=value implements the paper's
        // unary ("subgraph") abstraction: only states with attr==value, and only transitions
        // between two such states, are kept.
        String subAttr = null, subVal = null;
        for (int a = 2; a < args.length; a++) {
            if (args[a].equals("--raw-ints")) rawInts = true;
            else if (args[a].equals("--clean")) cleanEdges = true;
            else if (args[a].equals("--pretty")) pretty = true;
            else if (args[a].equals("--title") && a + 1 < args.length) title = args[++a];
            else if (args[a].equals("--subgraph") && a + 1 < args.length) {
                String[] sv = args[++a].split("=", 2);
                subAttr = sv[0]; subVal = sv[1];
            }
        }

        // Read key variables
        try {
            BufferedReader br = new BufferedReader(new FileReader(args[1]));
            String line = br.readLine();
            keyVars = Arrays.asList(line.split(","));
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Read trace file
        try {
            BufferedReader br = new BufferedReader(new FileReader(args[0]));
            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                String event = parts[0];
                String data = parts[1];

                events.add(event);

                String[] pairs = data.split(",");
                Map<String, String> state = new TreeMap<>();

                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (keyVars.contains(kv[0])) {
                        state.put(kv[0], rawInts ? kv[1] : abstractValue(kv[0], kv[1]));
                    }
                }

                states.add(state);
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (pretty) {
            writePretty(states, events, keyVars, subAttr, subVal, title);
            return;
        }

        // Generate DOT file
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("fsm.dot"));

            bw.write("digraph FSM {\n");
            bw.write("label=\"Finite State Machine from Execution Trace\";\n");
            bw.write("labelloc=\"t\";\n");
            bw.write("fontsize=18;\n");
            bw.write("node [shape=ellipse, style=filled, color=lightblue];\n");
            String startLabel = keyVars.stream()
                .map(k -> k + "=?")
                .collect(Collectors.joining(" | "));

            bw.write("\"START: " + startLabel + "\" [shape=doublecircle, color=green];\n");
            int firstIdx = 0;
            while (subAttr != null && firstIdx < states.size() - 1
                    && !subVal.equals(states.get(firstIdx).get(subAttr))) firstIdx++;
            bw.write("\"START: " + startLabel + "\" -> \"" + formatState(states.get(firstIdx)) + "\";\n");

            for (int i = 0; i < states.size() - 1; i++) {
                if (subAttr != null && (!subVal.equals(states.get(i).get(subAttr))
                        || !subVal.equals(states.get(i + 1).get(subAttr)))) continue;
                String from = formatState(states.get(i));
                String to = formatState(states.get(i + 1));
                if (cleanEdges && from.equals(to)) continue; // --clean: drop stutter steps (no visible state change)
                String event = events.get(i + 1);

                boolean isLoop = visitedStates.contains(to);
                String loopTag = isLoop ? " [LOOP]" : "";
                String transition = "\"" + from + "\" -> \"" + to + 
                    "\" [label=\"" + event + loopTag + "\", " +
                    (isLoop ? "style=dashed, color=red" : "style=solid") + "]";
                visitedStates.add(from);

                String key = cleanEdges ? "\"" + from + "\" -> \"" + to + "\" " + event : transition;
                if (!transitions.contains(key)) {
                    bw.write(transition + ";\n");
                    transitions.add(key);
                }
            }

            bw.write("}\n");
            bw.close();

            System.out.println("FSM with event labels generated!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- presentation-quality output (--pretty) -------------------------------------------------
    static String shortEvent(String e) {
        String t = e.contains(".") ? e.substring(e.lastIndexOf('.') + 1) : e;
        return t.replace("_WRITE", "");
    }

    /** Node label. Many short values (e.g. 5 philosophers E/~E) -> a coloured cell table; otherwise one line per key. */
    static String prettyLabel(Map<String, String> st) {
        boolean cells = st.size() >= 3;
        for (String v : st.values()) if (v.length() > 4) cells = false;
        if (cells) {
            StringBuilder h = new StringBuilder("<<table border=\"0\" cellborder=\"1\" cellspacing=\"0\" cellpadding=\"5\"><tr>");
            for (String k : st.keySet()) h.append("<td bgcolor=\"#e5e7eb\"><font point-size=\"10\">").append(k.replaceAll("^[A-Za-z_]*", "")).append("</font></td>");
            h.append("</tr><tr>");
            for (String v : st.values()) {
                boolean active = !(v.startsWith("~") || v.equals("C0") || v.equals("0"));
                h.append("<td bgcolor=\"").append(active ? "#fbbf24" : "#ffffff").append("\"><b>").append(v.replace("~", "&#126;")).append("</b></td>");
            }
            return h.append("</tr></table>>").toString();
        }
        StringBuilder sb = new StringBuilder("\"");
        boolean first = true;
        for (Map.Entry<String, String> e : st.entrySet()) { if (!first) sb.append("\\n"); sb.append(e.getKey()).append(" = ").append(e.getValue()); first = false; }
        return sb.append("\"").toString();
    }

    static void writePretty(List<Map<String, String>> states, List<String> events, List<String> keyVars,
                            String subAttr, String subVal, String title) {
        try (PrintWriter w = new PrintWriter("fsm.dot")) {
            w.println("digraph FSM {");
            w.println("graph [label=<" + title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "<br/><font point-size=\"12\">solid arrow = first time a state is reached, dashed red = returns to an already visited state</font>>, labelloc=t, fontsize=22, fontname=Helvetica, nodesep=0.45, ranksep=0.75, pad=0.3];");
            w.println("node [shape=box, style=\"rounded,filled\", fillcolor=\"#dbeafe\", color=\"#1e3a8a\", fontname=Helvetica, fontsize=14, margin=\"0.14,0.08\"];");
            w.println("edge [fontname=Helvetica, fontsize=12, color=\"#374151\", arrowsize=0.8];");
            w.println("START [shape=circle, label=\"START\", fillcolor=\"#86efac\", color=\"#166534\", width=0.75, fixedsize=true, fontsize=12];");
            Set<String> declared = new LinkedHashSet<>();
            for (Map<String, String> st : states) {
                if (subAttr != null && !subVal.equals(st.get(subAttr))) continue;
                String n = formatState(st);
                if (declared.add(n)) w.println("\"" + n + "\" [label=" + prettyLabel(st) + "];");
            }
            // cell-style nodes: put states with the same number of highlighted cells on the same row
            // (for Dining Philosophers: row 0 = nobody eating, row 1 = one eater, row 2 = two eaters)
            Map<Integer, List<String>> rows = new TreeMap<>();
            for (Map<String, String> st : states) {
                if (subAttr != null && !subVal.equals(st.get(subAttr))) continue;
                if (!prettyLabel(st).startsWith("<<")) continue;
                int act = 0;
                for (String v : st.values()) if (!(v.startsWith("~") || v.equals("C0") || v.equals("0"))) act++;
                List<String> l = rows.computeIfAbsent(act, k -> new ArrayList<>());
                if (!l.contains(formatState(st))) l.add(formatState(st));
            }
            for (List<String> l : rows.values()) {
                StringBuilder sb = new StringBuilder("{rank=same;");
                for (String n : l) sb.append(" \"").append(n).append("\";");
                w.println(sb.append("}"));
            }
            int first = 0;
            while (subAttr != null && first < states.size() - 1 && !subVal.equals(states.get(first).get(subAttr))) first++;
            w.println("START -> \"" + formatState(states.get(first)) + "\";");
            Set<String> visited = new HashSet<>();
            // parallel arrows between the same two states are merged into one arrow with a combined label
            Map<String, Object[]> edges = new LinkedHashMap<>();
            for (int i = 0; i < states.size() - 1; i++) {
                if (subAttr != null && (!subVal.equals(states.get(i).get(subAttr)) || !subVal.equals(states.get(i + 1).get(subAttr)))) continue;
                String from = formatState(states.get(i)), to = formatState(states.get(i + 1));
                if (from.equals(to)) continue;                         // no visible change
                boolean loop = visited.contains(to);
                visited.add(from);
                Object[] e = edges.get(from + "->" + to);
                if (e == null) { e = new Object[]{from, to, loop, new LinkedHashSet<String>()}; edges.put(from + "->" + to, e); }
                ((Set<String>) e[3]).add(shortEvent(events.get(i + 1)).replaceAll("State$", ""));
            }
            for (Object[] e : edges.values())
                w.println("\"" + e[0] + "\" -> \"" + e[1] + "\" [label=\"" + String.join(", ", (Set<String>) e[3]) + "\""
                        + ((Boolean) e[2] ? ", style=dashed, color=\"#dc2626\", fontcolor=\"#dc2626\"" : "") + "];");
            w.println("}");
            System.out.println("FSM (pretty) generated! " + declared.size() + " states");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Format state nicely
    public static String formatState(Map<String, String> state) {
        StringBuilder sb = new StringBuilder();
        List<String> sortedKeys = new ArrayList<>(state.keySet());
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            sb.append(key).append("=").append(state.get(key)).append(" | ");
        }
        return sb.toString().replaceAll(" \\| $", "");
    }
    
    public static String abstractValue(String key, String value) {
    try {
        int v = Integer.parseInt(value);
        if (v > 0) return "POSITIVE";
        if (v < 0) return "NEGATIVE";
        return "ZERO";
    } catch (Exception e) {
        return value;
    }
}
}
