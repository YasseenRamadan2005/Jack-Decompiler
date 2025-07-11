package VMTranslator;

import VMTranslator.vmcode.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//Basic: 47 lines
public class VMTranslator {
    private final File[] vmFiles;
    private final File outputFile;

    public VMTranslator(File[] vmFiles, File outputFile) {
        this.vmFiles = vmFiles;
        this.outputFile = outputFile;
    }

    public void translate() throws Exception {
        int machineLine = 0;
        List<String> bootstrapCode  = new ArrayList<>(List.of("//Set 256 to be the start of the stack", "@256", "D=A", "@SP", "M=D",

                "//Set up the comparison ops subroutines", "@SKIP", "0;JMP",

                "// ------------------------------------------------------------", "//  Shared code for gt, lt, eq", "//  Expectations on entry:", "//Stack  – return address", "//D  – (left – right)", "// ------------------------------------------------------------",

                "// want  (left  > right)  ⇔ (D > 0)", "(DO_GT)", "@RETURN_TRUE", "D;JGT", "@RETURN_FALSE", "0;JMP",

                "// want  (left == right)  ⇔ (D == 0)", "(DO_EQ)", "@RETURN_TRUE", "D;JEQ", "@RETURN_FALSE", "0;JMP",

                "// want  (left  < right)  ⇔ (D < 0)", "(DO_LT)", "@RETURN_TRUE", "D;JLT", "@RETURN_FALSE", "0;JMP",

                "// ---- set boolean in D --------------------------------------", "(RETURN_TRUE)", "D=-1", "@WRITE_BACK", "0;JMP",

                "(RETURN_FALSE)", "D=0", "@WRITE_BACK", "0;JMP",

                "// ---- collapse stack and return -----------------------------", "(WRITE_BACK)", "@SP", "AM=M-1", "A=M", "0;JMP",

                "(SKIP)",

                "//Set up calling and returning from functions", "@SKIPo", "0;JMP",

                "(CALL)", "@SP", "AM=M+1", "A=A-1", "M=D", "@LCL", "D=M", "@SP", "AM=M+1", "A=A-1", "M=D", "@ARG", "D=M", "@SP", "AM=M+1", "A=A-1", "M=D", "@THIS", "D=M", "@SP", "AM=M+1", "A=A-1", "M=D", "@THAT", "D=M", "@SP", "AM=M+1", "A=A-1", "M=D", "@14", "D=M", "@SP", "D=M-D", "@ARG", "M=D", "@SP", "D=M", "@LCL", "M=D", "@13", "A=M", "0;JMP",

                "(RETURN)", "@LCL", "D=M", "@14", "M=D", "@5", "A=D-A", "D=M", "@15", "M=D", "@SP", "AM=M-1", "D=M", "@ARG", "A=M", "M=D", "@ARG", "D=M", "@SP", "M=D+1", "@14", "A=M-1", "D=M", "@THAT", "M=D", "@14", "A=M-1", "A=A-1", "D=M", "@THIS", "M=D", "@14", "A=M-1", "A=A-1", "A=A-1", "D=M", "@ARG", "M=D", "@14", "A=M-1", "A=A-1", "A=A-1", "A=A-1", "D=M", "@LCL", "M=D", "@15", "A=M", "0;JMP",

                "(SKIPo)"));
        List<String> allAssemblyLines = new ArrayList<>();

        VMParser.currentFunction = "global";
        CallInstruction c = new CallInstruction("Sys.init", 0, new HashMap<>());
        bootstrapCode.addAll(c.decode());
        // Add bootstrap with line number annotations
        for (String line : bootstrapCode) {
            if (isRealInstruction(line)) {
                allAssemblyLines.add(line + " // " + machineLine++);
            } else {
                allAssemblyLines.add(line);
            }
        }
        // Process each .vm file
        for (File vmFile : vmFiles) {
            List<String> lines = Files.readAllLines(vmFile.toPath());
            VMParser parser = new VMParser(lines, getModuleName(vmFile));
            List<VMinstruction> instructions = parser.parse();

            for (VMinstruction inst : instructions) {
                // Add the VM comment
                String comment = "//" + inst.toString().replaceAll("(?m)^", "//");
                allAssemblyLines.add(comment);

                List<String> assembly = inst.decode();
                if (assembly != null) {
                    for (String line : assembly) {
                        if (isRealInstruction(line)) {
                            allAssemblyLines.add(line + " // " + machineLine++);
                        } else {
                            allAssemblyLines.add(line);
                        }
                    }
                }
                allAssemblyLines.add(""); // blank line between instructions
            }

            //System.out.println(vmFile.getName() + "  " + instructions.size());
        }

        Files.write(outputFile.toPath(), allAssemblyLines);
    }

    private String getModuleName(File file) {
        String name = file.getName();
        return name.substring(0, name.lastIndexOf('.'));
    }

    private boolean isRealInstruction(String line) {
        line = line.trim();
        return !line.isEmpty() && !line.startsWith("//") && !line.startsWith("(");

    }

}
