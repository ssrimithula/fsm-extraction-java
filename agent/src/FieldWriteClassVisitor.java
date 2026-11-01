import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Bytecode Transformer (see Design & Solution, Section 4 "Module
 * Description"). Scans a class as it loads and wraps every method with a
 * FieldWriteMethodVisitor, which is where the actual instruction rewriting
 * happens.
 */
public class FieldWriteClassVisitor extends ClassVisitor {

    private final String classInternalName;
    private final InstrumentationConfig config;

    public FieldWriteClassVisitor(int api, ClassVisitor cv, String classInternalName,
                                   InstrumentationConfig config) {
        super(api, cv);
        this.classInternalName = classInternalName;
        this.config = config;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                      String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv == null) return null;
        return new FieldWriteMethodVisitor(Opcodes.ASM9, access, descriptor, mv,
                classInternalName, config);
    }
}
