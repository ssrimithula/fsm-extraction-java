import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Field Filter (see Design & Solution, Section 4 "Module Description").
 *
 * Reads the instrumentation config file and tells the Bytecode Transformer
 * exactly which classes/fields are worth tracking, so the trace isn't
 * flooded with writes to fields nobody asked for.
 *
 * Config file format (one line per class):
 *     ClassName:field1,field2,...
 *
 * Blank lines and lines starting with '#' are ignored. ClassName is the
 * class's simple/internal name (e.g. "TestProgram" for a default-package
 * class, or "com/example/Foo" / "com.example.Foo" for a packaged class -
 * both separators are accepted).
 */
public class InstrumentationConfig {

    // internal (slash-form) class name -> set of tracked field names
    private final Map<String, Set<String>> trackedFields = new HashMap<>();

    public static InstrumentationConfig load(String path) {
        InstrumentationConfig config = new InstrumentationConfig();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(":", 2);
                if (parts.length != 2) continue;

                String className = parts[0].trim().replace('.', '/');
                Set<String> fields = new HashSet<>();
                for (String f : parts[1].split(",")) {
                    String field = f.trim();
                    if (!field.isEmpty()) fields.add(field);
                }
                if (!fields.isEmpty()) {
                    config.trackedFields.put(className, fields);
                }
            }
        } catch (Exception e) {
            System.err.println("[FSMAgent] Failed to read instrumentation config '" + path + "': " + e);
        }
        return config;
    }

    /** True if this class (internal/slash-form name) has at least one tracked field. */
    public boolean isClassTracked(String internalClassName) {
        if (trackedFields.containsKey(internalClassName)) return true;
        // nested/inner classes (Outer$Inner, Outer$1 ...) belong to the tracked class: their code is
        // what usually calls the setters and contains the loops
        int d = internalClassName.indexOf('$');
        return d > 0 && trackedFields.containsKey(internalClassName.substring(0, d));
    }

    /** True if the given field on the given class should be instrumented. */
    public boolean isFieldTracked(String internalClassName, String fieldName) {
        Set<String> fields = trackedFields.get(internalClassName);
        return fields != null && fields.contains(fieldName);
    }

    /** Union of all tracked field names (used to know when a full state is available). */
    public Set<String> allFields() {
        Set<String> all = new HashSet<>();
        for (Set<String> s : trackedFields.values()) all.addAll(s);
        return all;
    }

    public Set<String> classes() {
        return trackedFields.keySet();
    }
}
