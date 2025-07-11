import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

public class JackDecompiler {
    private final File inputDir, outputDir;
    public static HashMap<String, Boolean> FuncTable; //True if a method

    public JackDecompiler(File inputDir, File outputDir) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        FuncTable = new HashMap<>();
    }

    public void decompileAll() throws IOException {
        File[] vmFiles = inputDir.listFiles((d, n) -> n.endsWith(".vm"));
        if (vmFiles == null || vmFiles.length == 0) {
            System.err.println("No .vm files found.");
            return;
        }

        Map<String, ClassMetadata> classMap = new HashMap<>();
        Map<String, FunctionMetadata> functionMap = new HashMap<>();
        Set<String> calledFunctions = new HashSet<>();
        calledFunctions.add("Sys.init");

        Pattern pStatic = Pattern.compile("static (\\d+)");
        Pattern pField = Pattern.compile("this (\\d+)");

        for (File vmFile : vmFiles) {
            String className = vmFile.getName().replace(".vm", "");
            ClassMetadata cls = classMap.computeIfAbsent(className, ClassMetadata::new);
            List<String> lines = Files.readAllLines(vmFile.toPath());

            FunctionMetadata fn = null;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("//")) continue;

                if (line.startsWith("function ")) {
                    String[] t = line.split(" ");
                    fn = functionMap.computeIfAbsent(t[1], FunctionMetadata::new);
                    fn.numLocals = Integer.parseInt(t[2]);
                    fn.vmCode.add(line);

                    if (i + 2 < lines.size() && lines.get(i + 1).equals("push argument 0") && lines.get(i + 2).equals("pop pointer 0")) {
                        fn.isMethod = true;
                        FuncTable.put(fn.name, Boolean.TRUE);
                    } else if (i + 3 < lines.size() && lines.get(i + 1).startsWith("push constant") && lines.get(i + 2).equals("call Memory.alloc 1") && lines.get(i + 3).equals("pop pointer 0")) {
                        fn.isConstructor = true;
                        FuncTable.put(fn.name, Boolean.FALSE);
                    } else {
                        FuncTable.put(fn.name, Boolean.FALSE);
                    }
                    functionMap.put(fn.name, fn);
                    cls.functions.add(fn);
                    continue;
                }

                if (fn != null) {
                    fn.vmCode.add(line);
                    if (line.startsWith("call ")) {
                        String[] t = line.split(" ");
                        String callee = t[1];
                        int nArgs = Integer.parseInt(t[2]);
                        FunctionMetadata calleeFn = functionMap.computeIfAbsent(callee, FunctionMetadata::new);
                        calleeFn.numArgs = Math.max(calleeFn.numArgs, nArgs);
                        if (i + 1 < lines.size() && lines.get(i + 1).equals("pop temp 0")) calleeFn.isVoid = true;
                        calledFunctions.add(callee);
                    }
                }

                Matcher m;
                if ((m = pStatic.matcher(line)).find())
                    cls.staticMax = Math.max(cls.staticMax, Integer.parseInt(m.group(1)));
                if ((m = pField.matcher(line)).find())
                    cls.fieldMax = Math.max(cls.fieldMax, Integer.parseInt(m.group(1)));
            }
        }

        for (ClassMetadata cls : classMap.values()) {
            try (PrintWriter out = new PrintWriter(new File(outputDir, cls.name + ".jack"))) {
                out.println("class " + cls.name + " {");
                for (int i = 0; i <= cls.staticMax; i++)
                    out.println("    static int static_" + i + ";");
                for (int i = 0; i <= cls.fieldMax; i++)
                    out.println("    field int field_" + i + ";");

                for (FunctionMetadata fn : cls.functions) {
                    if (!calledFunctions.contains(fn.name)) continue;

                    VMToJackTranslator translator = new VMToJackTranslator();
                    String kind = fn.isConstructor ? "constructor" : fn.isMethod ? "method" : "function";
                    String ret = fn.isVoid ? "void" : "int";
                    out.print("    " + kind + " " + ret + " " + fn.name.substring(fn.name.indexOf('.') + 1) + "(");
                    if (fn.isMethod) fn.numArgs--;
                    for (int i = 0; i < fn.numArgs; i++) {
                        if (i > 0) out.print(", ");
                        out.print("int argument_" + i);
                    }
                    out.println(") {");
                    for (int i = 0; i < fn.numLocals; i++) {
                        out.println("        var int local_" + i + ";");
                    }
                    List<String> jackBody = translator.translate(fn.vmCode, cls.name, fn);
                    for (String jackLine : jackBody) {
                        out.println("        " + jackLine);
                    }
                    out.println("\t}\n");
                }
                out.println("}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class FunctionMetadata {
        final String name;
        boolean isVoid, isMethod, isConstructor;
        int numArgs, numLocals;
        final List<String> vmCode = new ArrayList<>();

        FunctionMetadata(String name) {
            this.name = name;
        }

        String returnType() {
            return isVoid ? "void" : "int";
        }
    }

    static class ClassMetadata {
        final String name;
        int staticMax = -1, fieldMax = -1;
        final List<FunctionMetadata> functions = new ArrayList<>();

        ClassMetadata(String name) {
            this.name = name;
        }
    }
}
