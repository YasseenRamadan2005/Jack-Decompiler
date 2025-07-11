// vmcode/LabelInstruction.java
package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class LabelInstruction implements VMinstruction {
    private final String label;

    public LabelInstruction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public List<String> decode() {
        List<String> asm = new ArrayList<>();
        asm.add("// label " + label);
        asm.add("(" + label + ")");
        return asm;
    }

    @Override
    public String toString() {
        return "LabelInstruction{" + "label='" + label + '}';
    }
}
