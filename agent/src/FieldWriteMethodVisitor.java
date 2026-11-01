import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

/**
 * Bytecode Transformer's per-method logic (see Design & Solution, Section 6
 * "Algorithms / Methodology" - Bytecode Instrumentation pseudocode).
 *
 * Walks a method's instructions looking for PUTFIELD / PUTSTATIC on a
 * tracked field. The ORIGINAL instruction is always left untouched (the
 * program's own logic never changes) - we only insert one extra call
 * right after it, passing the class name, field name, the value that was
 * just written, and a timestamp, to FSMAgent.hook(...).
 *
 * Extends LocalVariablesSorter so we can safely allocate a fresh local
 * variable slot (via newLocal) to stash a copy of the written value while
 * we rearrange the stack for the hook call, without clashing with the
 * method's existing locals.
 */
public class FieldWriteMethodVisitor extends LocalVariablesSorter {

    private static final String HOOK_OWNER = "FSMAgent";
    private static final String HOOK_DESC =
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;J)V";

    private final String ownerClassInternalName;
    private final InstrumentationConfig config;

    public FieldWriteMethodVisitor(int api, int access, String descriptor,
                                    MethodVisitor mv, String ownerClassInternalName,
                                    InstrumentationConfig config) {
        super(api, access, descriptor, mv);
        this.ownerClassInternalName = ownerClassInternalName;
        this.config = config;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        boolean isWrite = (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC);
        // owner (not the class being rewritten) decides: a nested class that writes the tracked field of its
        // outer class directly must be seen too
        boolean tracked = isWrite && config.isFieldTracked(owner, name);

        if (!tracked) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
            return;
        }

        boolean isStatic = (opcode == Opcodes.PUTSTATIC);
        Type fieldType = Type.getType(descriptor);
        int sizeOnStack = fieldType.getSize(); // 1 for int/float/ref/etc., 2 for long/double

        // --- Step 1: duplicate the value being written, without disturbing
        //     the operands the ORIGINAL instruction still needs. ---
        if (!isStatic) {
            // Stack before: ..., objectref, value(sizeOnStack)
            if (sizeOnStack == 1) {
                mv.visitInsn(Opcodes.DUP_X1);   // ..., value, objectref, value
            } else {
                mv.visitInsn(Opcodes.DUP2_X1);  // ..., value(2-word), objectref, value(2-word)
            }
        } else {
            // Stack before: ..., value(sizeOnStack)
            if (sizeOnStack == 1) {
                mv.visitInsn(Opcodes.DUP);      // ..., value, value
            } else {
                mv.visitInsn(Opcodes.DUP2);     // ..., value(2-word), value(2-word)
            }
        }

        // --- Step 2: emit the ORIGINAL field write, completely unchanged.
        //     It consumes (objectref, value) or (value), leaving our
        //     duplicated copy sitting on top of the stack. ---
        super.visitFieldInsn(opcode, owner, name, descriptor);

        // --- Step 3: box the copy if it's a primitive, so we can pass a
        //     single Object to the hook regardless of field type. ---
        boxIfPrimitive(fieldType);

        // --- Step 4: stash the boxed copy in a fresh local so we can
        //     freely push the other hook arguments in the right order. ---
        int localIdx = newLocal(Type.getType(Object.class));
        mv.visitVarInsn(Opcodes.ASTORE, localIdx);

        mv.visitLdcInsn(owner.replace('/', '.')); // className = class that declares the tracked field
        mv.visitLdcInsn(name);                                     // fieldName
        mv.visitVarInsn(Opcodes.ALOAD, localIdx);                  // value (boxed)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System",
                "nanoTime", "()J", false);                         // timestamp
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER, "hook", HOOK_DESC, false);
    }

    /** Emits a boxing call (Integer.valueOf, etc.) if the type is a primitive. */
    private void boxIfPrimitive(Type type) {
        switch (type.getSort()) {
            case Type.INT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                        "(I)Ljava/lang/Integer;", false);
                break;
            case Type.LONG:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf",
                        "(J)Ljava/lang/Long;", false);
                break;
            case Type.FLOAT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf",
                        "(F)Ljava/lang/Float;", false);
                break;
            case Type.DOUBLE:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf",
                        "(D)Ljava/lang/Double;", false);
                break;
            case Type.BOOLEAN:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf",
                        "(Z)Ljava/lang/Boolean;", false);
                break;
            case Type.BYTE:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf",
                        "(B)Ljava/lang/Byte;", false);
                break;
            case Type.CHAR:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf",
                        "(C)Ljava/lang/Character;", false);
                break;
            case Type.SHORT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf",
                        "(S)Ljava/lang/Short;", false);
                break;
            default:
                // OBJECT or ARRAY - already a reference, nothing to box.
                break;
        }
    }
}
