// vmcode/GotoInstruction.java
package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class GotoInstruction implements VMinstruction {
    private final String label;

    public GotoInstruction(String label) {
        this.label = label;
    }

    @Override
    public List<String> decode() {
        List<String> asm = new ArrayList<>();
        asm.add("// goto " + label);
        asm.add("@" + label);
        asm.add("0;JMP");
        return asm;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "GotoInstruction{" + "label='" + label + '}';
    }
}
