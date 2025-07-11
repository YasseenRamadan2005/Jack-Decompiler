import VMTranslator.vmcode.VMParser;
import VMTranslator.vmcode.VMinstruction;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

public class JackDecompiler {
    private final File inputDir;

    private final Map<String, ClassMetadata> classMap = new HashMap<>();
    private final Map<String, FunctionMetadata> functionMap = new HashMap<>();
    private final Set<String> calledFunctions = new HashSet<>(Set.of("Sys.init"));

    private static final Pattern P_STATIC = Pattern.compile("static (\\d+)");
    private static final Pattern P_FIELD = Pattern.compile("this (\\d+)");

    public JackDecompiler(File inputDir) {
        this.inputDir = inputDir;
    }

    public void decompileAll() throws IOException {
        File[] vmFiles = inputDir.listFiles((d, n) -> n.endsWith(".vm"));
        if (vmFiles == null || vmFiles.length == 0) {
            System.err.println("No .vm files found.");
            return;
        }

        for (File vmFile : vmFiles) parseVmFile(vmFile);
        printMetadata();
    }

    public void writeJackFiles(File outputDir, VMToJackTranslator translator) {
        for (ClassMetadata cls : classMap.values()) {
            try (PrintWriter out = new PrintWriter(new File(outputDir, cls.name + ".jack"))) {
                out.println("class " + cls.name + " {");

                for (int i = 0; i <= cls.staticMax; i++) {
                    out.println("    static int static_" + i + ";");
                }
                for (int i = 0; i <= cls.fieldMax; i++) {
                    out.println("    field int field_" + i + ";");
                }

                for (FunctionMetadata fn : cls.functions) {
                    if (!calledFunctions.contains(fn.name)) continue;

                    String kind = fn.isConstructor ? "constructor" : fn.isMethod ? "method" : "function";
                    String ret = fn.isVoid ? "void" : "int";
                    out.print("    " + kind + " " + ret + " " + fn.getShortName() + "(");
                    int nArgs = fn.numArgs;
                    for (int i = 0; i < nArgs; i++) {
                        if (i > 0) out.print(", ");
                        out.print("int argument_" + i);
                    }
                    out.println(") {");

                    for (int i = 0; i < fn.numLocals; i++) {
                        out.println("        var int local_" + i + ";");
                    }

                    List<String> jackBody = translator.translate(fn.vmCode, fn);
                    for (String jackLine : jackBody) {
                        out.println("        " + jackLine);
                    }

                    out.println("    }\n");
                }

                out.println("}");
            } catch (Exception e) {
                System.err.println("Error writing " + cls.name + ".jack: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void parseVmFile(File vmFile) throws IOException {
        String className = vmFile.getName().replace(".vm", "");
        ClassMetadata cls = classMap.computeIfAbsent(className, ClassMetadata::new);
        List<String> lines = Files.readAllLines(vmFile.toPath());

        FunctionMetadata currentFn = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("function ")) {
                currentFn = handleFunctionDeclaration(lines, i);
                cls.functions.add(currentFn);
                continue;
            }

            if (currentFn != null) {
                currentFn.vmCode.add(line);
                if (line.startsWith("call ")) handleCallInstruction(lines, i);
            }

            updateClassFields(cls, line);
        }
    }

    private FunctionMetadata handleFunctionDeclaration(List<String> lines, int i) {
        String[] tokens = lines.get(i).split(" ");
        String name = tokens[1];
        int nLocals = Integer.parseInt(tokens[2]);

        FunctionMetadata fn = functionMap.computeIfAbsent(name, FunctionMetadata::new);
        fn.numLocals = nLocals;
        fn.vmCode.add(lines.get(i));

        if (i + 2 < lines.size() && lines.get(i + 1).equals("push argument 0") && lines.get(i + 2).equals("pop pointer 0")) {
            fn.isMethod = true;
        } else if (i + 3 < lines.size() && lines.get(i + 1).startsWith("push constant") && lines.get(i + 2).equals("call Memory.alloc 1") && lines.get(i + 3).equals("pop pointer 0")) {
            fn.isConstructor = true;
        }

        return fn;
    }

    private void handleCallInstruction(List<String> lines, int i) {
        String[] tokens = lines.get(i).split(" ");
        String callee = tokens[1];
        int nArgs = Integer.parseInt(tokens[2]);
        FunctionMetadata calleeFn = functionMap.computeIfAbsent(callee, FunctionMetadata::new);
        calleeFn.numArgs = Math.max(calleeFn.numArgs, nArgs);
        calleeFn.isVoid |= (i + 1 < lines.size() && lines.get(i + 1).equals("pop temp 0"));
        calledFunctions.add(callee);
    }

    private void updateClassFields(ClassMetadata cls, String line) {
        var mStatic = P_STATIC.matcher(line);
        if (mStatic.find()) cls.staticMax = Math.max(cls.staticMax, Integer.parseInt(mStatic.group(1)));

        var mField = P_FIELD.matcher(line);
        if (mField.find()) cls.fieldMax = Math.max(cls.fieldMax, Integer.parseInt(mField.group(1)));
    }

    public boolean isMethodFunction(String functionName) {
        FunctionMetadata fn = functionMap.get(functionName);
        return fn != null && fn.isMethod;
    }

    // Or if you want to check by class and method:
    public boolean isMethodFunction(String className, String functionName) {
        ClassMetadata cls = classMap.get(className);
        if (cls == null) return false;
        for (FunctionMetadata fn : cls.functions) {
            if (fn.name.equals(functionName)) {
                return fn.isMethod;
            }
        }
        return false;
    }

    private void printMetadata() {
        System.out.println("===== Decompiled Metadata =====\n");

        for (ClassMetadata cls : classMap.values()) {
            System.out.println("Class: " + cls.name);
            if (cls.staticMax != -1) {
                System.out.println("  Static Variables: static_0 to static_" + cls.staticMax);
            }
            if (cls.fieldMax != -1) {
                System.out.println("  Field Variables: field_0 to field_" + cls.fieldMax);
            }
            System.out.println("  Functions:");
            for (FunctionMetadata fn : cls.functions) {
                String kind = fn.isConstructor ? "constructor" : fn.isMethod ? "method" : "function";
                String ret = fn.isVoid ? "void" : "int";
                System.out.printf("    %s %s %s(%d args, %d locals)\n", kind, ret, fn.getShortName(), fn.numArgs, fn.numLocals);
            }
            System.out.println();
        }

        System.out.println("Called Functions:");
        for (String fn : calledFunctions) {
            System.out.println("  - " + fn);
        }

        System.out.println("\n===============================\n");
    }

    // === Accessors for use by JackEmitter ===

    public Map<String, ClassMetadata> getClassMetadata() {
        return classMap;
    }

    public Map<String, FunctionMetadata> getFunctionMetadata() {
        return functionMap;
    }

    public Set<String> getCalledFunctions() {
        return calledFunctions;
    }

    // === Metadata Classes ===


    public static class FunctionMetadata {
        public final String name;
        public boolean isVoid = false;
        public boolean isMethod = false;
        public boolean isConstructor = false;
        public int numArgs = 0;
        public int numLocals = 0;
        public final List<String> vmCode = new ArrayList<>();

        // Symbol table for function-level variables: variable name -> kind and index
        public final Map<String, Symbol> functionSymbols = new LinkedHashMap<>();

        public FunctionMetadata(String name) {
            this.name = name;
        }

        public String returnType() {
            return isVoid ? "void" : "int";
        }

        public String getShortName() {
            int dot = name.indexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : name;
        }
    }

    public static class ClassMetadata {
        public final String name;
        public int staticMax = -1;
        public int fieldMax = -1;
        public final List<FunctionMetadata> functions = new ArrayList<>();

        // Symbol table for class-level variables: variable name -> kind and index
        public final Map<String, Symbol> classSymbols = new LinkedHashMap<>();

        public ClassMetadata(String name) {
            this.name = name;
        }
    }

    // Simple symbol record for variable metadata (without type for now)
    public static class Symbol {
        public final String kind;   // e.g. "static", "field", "argument", "local"
        public final int index;

        public Symbol(String kind, int index) {
            this.kind = kind;
            this.index = index;
        }

        @Override
        public String toString() {
            return kind + "_" + index;
        }
    }
}
