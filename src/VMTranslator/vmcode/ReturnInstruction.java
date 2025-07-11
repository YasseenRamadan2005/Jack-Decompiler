// vmcode/ReturnInstruction.java
package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class ReturnInstruction implements VMinstruction {
    private PushGroup pg;
    public ReturnInstruction(PushGroup pg) {
        this.pg = pg;
    }

    public void setPg(PushGroup pg) {
        this.pg = pg;
    }

    public PushGroup getPg() {
        return pg;
    }

    @Override
    public List<String> decode() throws Exception {
        List<String> asm = new ArrayList<>(pg.decode());
        asm.addAll(List.of("@RETURN", "0;JMP"));
        return asm;
    }

    @Override
    public String toString() {
        return "ReturnInstruction{}";
    }
}
