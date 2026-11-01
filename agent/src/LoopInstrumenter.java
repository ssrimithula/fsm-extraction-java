import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Loop detection for Algorithm 2 (loop-aware model extraction, paper Sec 4.2).
 *
 * The paper assumes LoopStart / LoopEnd events. A bytecode agent can derive them statically: in
 * bytecode every loop is a BACKWARD jump (the target label comes earlier in the method than the jump
 * itself), so  [target label ... backward jump]  is the loop body. For every such loop we
 *   - give it a unique id (registered in FSMAgent.LOOPS, written to <trace>.loops at exit),
 *   - insert a call to FSMAgent.loopHeader(id) at the loop header   (pushes the id on a per-thread stack,
 *                                                                    unless it is already on top = next iteration),
 *   - insert FSMAgent.loopExit(id) on every path that leaves the loop (forward/backward jump out of the
 *                                                                    body, fall-through after the back edge, return).
 * FSMAgent.currentLoop() (top of the per-thread stack) is then stamped on every traced field write.
 * Because the stack is per thread and dynamic, a write made inside a called method (e.g. setState())
 * correctly gets the id of the caller's loop - the paper's "one stack per thread" extension.
 */
public class LoopInstrumenter {

    private static class Loop { int header, end, id; LabelNode headerLabel; }

    public static void instrument(String owner, MethodNode mn) {
        if (mn.instructions == null || mn.instructions.size() == 0) return;
        InsnList insns = mn.instructions;
        AbstractInsnNode[] a = insns.toArray();
        Map<AbstractInsnNode, Integer> idx = new IdentityHashMap<>();
        for (int i = 0; i < a.length; i++) idx.put(a[i], i);

        // 1. find back edges (jump to an earlier-or-same position) and merge by header
        Map<Integer, Loop> byHeader = new TreeMap<>();
        for (int j = 0; j < a.length; j++) {
            if (!(a[j] instanceof JumpInsnNode)) continue;
            int t = idx.get(((JumpInsnNode) a[j]).label);
            if (t <= j) {
                Loop l = byHeader.get(t);
                if (l == null) { l = new Loop(); l.header = t; byHeader.put(t, l); }
                l.end = Math.max(l.end, j);
            }
        }
        if (byHeader.isEmpty()) return;
        List<Loop> loops = new ArrayList<>(byHeader.values());
        for (Loop l : loops) {
            int line = -1;
            for (int i = l.header; i < a.length && i <= l.end && line < 0; i++)     // first line number in the loop
                if (a[i] instanceof LineNumberNode) line = ((LineNumberNode) a[i]).line;
            for (int i = l.header; i >= 0 && line < 0; i--)                         // else the nearest one before it
                if (a[i] instanceof LineNumberNode) line = ((LineNumberNode) a[i]).line;
            l.id = FSMAgent.registerLoop(owner + "." + mn.name + mn.desc + " (loop at line " + (line < 0 ? "?" : line) + ")");
        }

        // 2. decide what to insert where (computed on the original indices, applied afterwards)
        Map<AbstractInsnNode, List<InsnList>> after = new IdentityHashMap<>();   // insert AFTER this node
        Map<AbstractInsnNode, List<InsnList>> before = new IdentityHashMap<>();  // insert BEFORE this node

        for (Loop l : loops) {
            add(after, skipMeta(a, l.header), call("loopHeader", l.id));
            for (int j = l.header; j <= l.end; j++) {
                if (a[j] instanceof JumpInsnNode) {
                    JumpInsnNode jn = (JumpInsnNode) a[j];
                    int t = idx.get(jn.label);
                    if (t < l.header || t > l.end) add(after, skipMeta(a, t), call("loopExit", l.id));  // jump out of the body
                } else if (a[j] instanceof TableSwitchInsnNode || a[j] instanceof LookupSwitchInsnNode) {
                    List<LabelNode> ts = a[j] instanceof TableSwitchInsnNode ? ((TableSwitchInsnNode) a[j]).labels : ((LookupSwitchInsnNode) a[j]).labels;
                    LabelNode dflt = a[j] instanceof TableSwitchInsnNode ? ((TableSwitchInsnNode) a[j]).dflt : ((LookupSwitchInsnNode) a[j]).dflt;
                    List<LabelNode> all = new ArrayList<>(ts); all.add(dflt);
                    for (LabelNode ln : all) { int t = idx.get(ln); if (t < l.header || t > l.end) add(after, skipMeta(a, t), call("loopExit", l.id)); }
                } else if (isReturn(a[j].getOpcode())) {
                    add(before, a[j], call("loopExit", l.id));                                      // return from inside the loop
                }
            }
            // fall-through exit after a conditional back edge (do-while style)
            if (a[l.end].getOpcode() != Opcodes.GOTO && l.end + 1 < a.length)
                add(after, a[l.end], call("loopExit", l.id));
        }
        // 3. apply (inner loops registered later in 'loops' are applied in order; exits of inner before outer)
        for (Map.Entry<AbstractInsnNode, List<InsnList>> e : before.entrySet())
            for (InsnList il : e.getValue()) insns.insertBefore(e.getKey(), il);
        for (Map.Entry<AbstractInsnNode, List<InsnList>> e : after.entrySet()) {
            List<InsnList> lst = e.getValue();
            for (int k = lst.size() - 1; k >= 0; k--) insns.insert(e.getKey(), lst.get(k));
        }
    }

    /** From a label, move past directly following LineNumber/Frame/Label nodes: the point where real code starts. */
    private static AbstractInsnNode skipMeta(AbstractInsnNode[] a, int i) {
        int k = i;
        while (k + 1 < a.length && a[k + 1].getOpcode() == -1) k++;   // pseudo-instructions have opcode -1
        return a[k];
    }

    private static boolean isReturn(int op) { return op >= Opcodes.IRETURN && op <= Opcodes.RETURN; }

    private static void add(Map<AbstractInsnNode, List<InsnList>> m, AbstractInsnNode n, InsnList il) {
        m.computeIfAbsent(n, k -> new ArrayList<>()).add(il);
    }

    private static InsnList call(String name, int id) {
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(id));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "FSMAgent", name, "(I)V", false));
        return il;
    }
}
