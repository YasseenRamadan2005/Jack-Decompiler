import VMTranslator.vmcode.VMParser;
import VMTranslator.vmcode.VMinstruction;

import java.util.List;
public class VMToJackTranslator {

    private final JackCodeGenerator codeGenerator = new JackCodeGenerator();

    public List<String> translate(List<String> vmInstructions, String ClassName, JackDecompiler.FunctionMetadata fn) throws Exception {
        // Step 1: Parse the VM instructions into VMinstruction list
        VMParser parser = new VMParser(vmInstructions, "Main");
        List<VMinstruction> parsed = parser.parse();
        // Step 3: Use JackCodeGenerator to generate Jack source code lines from the AST
        return codeGenerator.generateJackCode(parsed, ClassName, fn);
    }
}