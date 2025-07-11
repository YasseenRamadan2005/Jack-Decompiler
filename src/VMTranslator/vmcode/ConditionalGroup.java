package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class ConditionalGroup implements VMinstruction {
    private PushGroup push;
    private IfGotoInstruction ifGoto;

    public ConditionalGroup(PushGroup push, IfGotoInstruction ifGoto) {
        this.push = push;
        this.ifGoto = ifGoto;
    }

    public PushGroup getPush() {
        return push;
    }

    public IfGotoInstruction getIfGoto() {
        return ifGoto;
    }

    @Override
    public List<String> decode() throws Exception {
        List<String> asm = new ArrayList<>();
        if (push instanceof BinaryPushGroup bpg1 && bpg1.getLeft() instanceof BinaryPushGroup bpg2 && bpg1.getRight().isConstant() && bpg1.getRight().getConstant() == 0 && bpg1.getOp().equals(ArithmeticInstruction.Op.EQ) && bpg2.getRight().isConstant() && bpg2.getRight().getConstant() == 0 && bpg2.getOp().equals(ArithmeticInstruction.Op.EQ)){
            asm.addAll(bpg2.setD());
            asm.addAll(List.of("@" + ifGoto.getLabel(), "D;JEQ"));
        }
        else {
            asm.addAll(push.setD());
            asm.addAll(List.of("@" + ifGoto.getLabel(), "D;JNE"));
        }
        return asm;
    }

    @Override
    public String toString() {
        return "ConditionalGroup{" + "push=" + push + ", ifGoto=" + ifGoto + '}';
    }
}
