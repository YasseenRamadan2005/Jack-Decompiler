// vmcode/IfGotoInstruction.java
package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class IfGotoInstruction implements VMinstruction {
    private final String label;

    public IfGotoInstruction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "IfGotoInstruction{" + "label='" + label + '}';
    }


    @Override
    public List<String> decode() {
        List<String> asm = new ArrayList<>();
        asm.add("// if-goto " + label);

        // Decrement SP and load *SP into D
        asm.add("@SP");
        asm.add("AM=M-1");
        asm.add("D=M");

        // Jump if D != 0 to the label
        asm.add("@" + label);
        asm.add("D;JNE");

        return asm;
    }
}
