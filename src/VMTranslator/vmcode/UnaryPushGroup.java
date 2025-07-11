package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UnaryPushGroup extends PushGroup {

    private PushGroup inner;
    private ArithmeticInstruction.Op op;

    public UnaryPushGroup(PushGroup inner, ArithmeticInstruction.Op op) {
        this.inner = inner;
        this.op = op;
    }

    @Override
    public List<String> decode() throws Exception {
        List<String> asm = new ArrayList<>();
        if (isConstant()) {
            short constant = getConstant();
            if (Math.abs(constant) <= 1) {
                return new ArrayList<>(List.of("@SP", "AM=M+1", "A=A-1", "M=" + constant));
            }
            else{
                asm.addAll(setD());
                asm.addAll(List.of("@SP", "AM=M+1", "A=A-1", "M=D"));
                return asm;
            }
        }
        List<String> code = new ArrayList<>(inner.decode());
        code.addAll(op.emit(false));
        return code;
    }

    @Override
    List<String> setD() throws Exception {
        // Constant folding
        if (isConstant()) {
            PushInstruction p = new PushInstruction(new Address("constant", getConstant()));
            return p.setD();
        }

        // Optimize wrapped push
        if (isWrappedPush()) {
            if (inner instanceof PushInstruction pi) {
                List<String> asm = new ArrayList<>(pi.getAddress().resolveAddressTo("A"));
                asm.add("D=" + (op == ArithmeticInstruction.Op.NEG ? "-" : "!") + "M");
                return asm;
            }
        }

        // General case
        List<String> asm = new ArrayList<>(inner.setD());
        asm.add(op.onD());
        return asm;
    }

    /**
     * Detects whether this is a wrapped PushInstruction or nested UnaryPushGroup wrapping a PushInstruction
     */
    boolean isWrappedPush() {
        return inner instanceof PushInstruction || (inner instanceof UnaryPushGroup u && u.isWrappedPush());
    }

    @Override
    public boolean isConstant() {
        return inner.isConstant();
    }

    @Override
    public short getConstant() {
        if (op.equals(ArithmeticInstruction.Op.NOT)){
            return (short) ~(inner.getConstant());
        }
        if (op.equals(ArithmeticInstruction.Op.NEG)){
            return (short) -(inner.getConstant());
        }
        throw new IllegalStateException();
    }


    public PushGroup getInner() {
        return inner;
    }

    public ArithmeticInstruction.Op getOp() {
        return op;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int ind) {
        return " ".repeat(ind) + "UnaryPushGroup(" + inner.toString(ind + 4) +  ",\n"  + op + ')';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UnaryPushGroup other)) return false;
        return Objects.equals(inner, other.inner) && op == other.op;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inner, op);
    }

}
