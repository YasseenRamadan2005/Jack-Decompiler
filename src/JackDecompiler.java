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
        inferVariableTypes();  // <-- Added pass to infer types
        printMetadata();
    }

    public void writeJackFiles(File outputDir, VMToJackTranslator translator) {
        for (ClassMetadata cls : classMap.values()) {
            try (PrintWriter out = new PrintWriter(new File(outputDir, cls.name + ".jack"))) {
                out.println("class " + cls.name + " {");

                for (int i = 0; i <= cls.staticMax; i++) {
                    String varName = "static_" + i;
                    Symbol sym = cls.classSymbols.get(varName);
                    String type = (sym != null && sym.type != null) ? sym.type : "int";
                    out.println("    static " + type + " " + varName + ";");
                }

                for (int i = 0; i <= cls.fieldMax; i++) {
                    String varName = "field_" + i;
                    Symbol sym = cls.classSymbols.get(varName);
                    String type = (sym != null && sym.type != null) ? sym.type : "int";
                    out.println("    field " + type + " " + varName + ";");
                }

                for (FunctionMetadata fn : cls.functions) {
                    if (!calledFunctions.contains(fn.name)) continue;

                    String kind = fn.isConstructor ? "constructor" : fn.isMethod ? "method" : "function";
                    String ret = fn.isConstructor ? cls.name : (fn.isVoid ? "void" : "int");

                    out.print("    " + kind + " " + ret + " " + fn.getShortName() + "(");

                    int nArgs = fn.numArgs;
                    if (fn.isMethod) {
                        nArgs--;
                    }
                    for (int i = 0; i < nArgs; i++) {
                        if (i > 0) out.print(", ");
                        String argName = "argument_" + i;
                        Symbol sym = fn.functionSymbols.get("argument_" + i);
                        String type = (sym != null && sym.type != null) ? sym.type : "int";
                        out.print(type + " " + argName);
                    }

                    out.println(") {");

                    for (int i = 0; i < fn.numLocals; i++) {
                        String localName = "local_" + i;
                        Symbol sym = fn.functionSymbols.get("local_" + i);
                        String type = (sym != null && sym.type != null) ? sym.type : "int";
                        out.println("        var " + type + " " + localName + ";");
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

    private void inferVariableTypes() {
        for (ClassMetadata cls : classMap.values()) {
            for (FunctionMetadata fn : cls.functions) {
                List<String> lines = fn.vmCode;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!line.startsWith("call ")) continue;

                    String[] parts = line.split(" ");
                    if (parts.length < 3) continue;

                    String callee = parts[1];
                    int nArgs;
                    try {
                        nArgs = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    FunctionMetadata target = functionMap.get(callee);
                    if (target == null || !target.isMethod) continue;

                    int receiverLineIndex = i - nArgs;
                    if (receiverLineIndex < 0) continue;

                    String receiverLine = lines.get(receiverLineIndex).trim();
                    if (!receiverLine.startsWith("push ")) continue;

                    String[] segParts = receiverLine.split(" ");
                    if (segParts.length != 3) continue;

                    String segment = segParts[1];
                    int index;
                    try {
                        index = Integer.parseInt(segParts[2]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    String type = callee.split("\\.")[0];

                    if (segment.equals("static") || segment.equals("local") || segment.equals("argument")) {
                        String varKey = segment + "_" + index;
                        Symbol sym = fn.functionSymbols.computeIfAbsent(varKey, k -> new Symbol(segment, index));
                        sym.type = type;
                    } else if (segment.equals("this")) {
                        String varKey = "field_" + index;
                        Symbol sym = cls.classSymbols.computeIfAbsent(varKey, k -> new Symbol("field", index));
                        sym.type = type;
                    }
                }
            }
        }
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

                if (!fn.functionSymbols.isEmpty()) {
                    for (Map.Entry<String, Symbol> entry : fn.functionSymbols.entrySet()) {
                        System.out.printf("      - %s → %s\n", entry.getKey(), entry.getValue());
                    }
                }
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

        public final Map<String, Symbol> classSymbols = new LinkedHashMap<>();

        public ClassMetadata(String name) {
            this.name = name;
        }
    }

    public static class Symbol {
        public final String kind;
        public final int index;
        public String type;

        public Symbol(String kind, int index, String type) {
            this.kind = kind;
            this.index = index;
            this.type = type;
        }

        public Symbol(String kind, int index) {
            this(kind, index, null);
        }

        @Override
        public String toString() {
            return kind + "_" + index + (type != null ? " : " + type : "");
        }
    }
}

