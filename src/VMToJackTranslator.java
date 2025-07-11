import VMTranslator.vmcode.VMParser;
import VMTranslator.vmcode.VMinstruction;

import java.util.List;

public class VMToJackTranslator {
    private final JackDecompiler decompiler;

    public VMToJackTranslator(JackDecompiler decompiler) {
        this.decompiler = decompiler;
    }

    public List<String> translate(List<String> vmInstructions, JackDecompiler.FunctionMetadata fn) throws Exception {
        // Step 1: Parse the VM instructions into VMinstruction list
        VMParser parser = new VMParser(vmInstructions, "Main");
        List<VMinstruction> parsed = parser.parse();
        JackCodeGenerator codeGenerator = new JackCodeGenerator(decompiler);
        // Step 3: Use JackCodeGenerator to generate Jack source code lines from the AST
        return codeGenerator.generateJackCode(parsed, fn);
    }
}