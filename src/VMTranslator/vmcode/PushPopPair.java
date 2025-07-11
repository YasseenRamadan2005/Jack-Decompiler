package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class PushPopPair implements VMinstruction {
    //Grammar:
    //  PushGroup
    //  PopInstruction

    private final PushGroup push;
    private final PopInstruction pop;

    public PushPopPair(PushGroup push, PopInstruction pop) {
        this.push = push;
        this.pop = pop;
    }

    public PushGroup getPush() {
        return push;
    }

    public PopInstruction getPop() {
        return pop;
    }

    @Override
    public List<String> decode() throws Exception {
        List<String> asm = new ArrayList<>();
        Address dest = pop.getAddress();

        //If the push is a CallGroup and the pop is to temp 0, then we can disregard the return value by just decrementing the stack
        if (push instanceof CallGroup && dest.equals(new Address("temp", (short) 0))){
            asm.addAll(push.decode());
            asm.addAll(List.of("@SP", "M=M-1"));
            return asm;
        }

        //Handle the constant case here
        if (push.isConstant() && Math.abs(push.getConstant()) <= 1){
            asm.addAll(pop.getAddress().resolveAddressTo("A"));
            asm.add("M=" + push.getConstant());
            return asm;
        }

        //Handle any increment or decrement case here
        if (push instanceof BinaryPushGroup bpg) {
            if (bpg.getLeft() instanceof PushInstruction || bpg.getRight() instanceof PushInstruction) {
                PushInstruction pi = bpg.getLeft() instanceof PushInstruction
                        ? (PushInstruction) bpg.getLeft()
                        : (PushInstruction) bpg.getRight();
                PushGroup other = bpg.getLeft() instanceof PushInstruction
                        ? bpg.getRight()
                        : bpg.getLeft();

                if (pi.getAddress().equals(dest) && other.isConstant()) {
                    int val = other.getConstant();
                    ArithmeticInstruction.Op op = bpg.getOp();

                    // Only optimize add/sub by ±1
                    if (Math.abs(val) == 1) {
                        List<String> resolve = dest.resolveAddressTo("A");

                        if (op == ArithmeticInstruction.Op.ADD && val == 1) {
                            asm.addAll(resolve);
                            asm.add("M=M+1");
                            return asm;
                        }
                        if (op == ArithmeticInstruction.Op.SUB && val == 1) {
                            asm.addAll(resolve);
                            asm.add("M=M-1");
                            return asm;
                        }
                        // For SUB and val == -1, it's equivalent to ADD 1
                        if (op == ArithmeticInstruction.Op.SUB && val == -1) {
                            asm.addAll(resolve);
                            asm.add("M=M+1");
                            return asm;
                        }
                        // For ADD and val == -1, it's equivalent to SUB 1
                        if (op == ArithmeticInstruction.Op.ADD && val == -1) {
                            asm.addAll(resolve);
                            asm.add("M=M-1");
                            return asm;
                        }
                    }
                }
            }
        }

        if (push instanceof BinaryPushGroup bpg) {
            if (bpg.getLeft() instanceof PushInstruction left && bpg.getRight() instanceof PushInstruction right) {
                Address leftAddr = left.getAddress();
                Address rightAddr = right.getAddress();
                ArithmeticInstruction.Op op = bpg.getOp();

                if (dest.isTrivial()) {
                    String opAsm = switch (op) {
                        case ADD -> "D+M";
                        case SUB -> "D-M";
                        case AND -> "D&M";
                        case OR  -> "D|M";
                        default  -> null;
                    };

                    if (opAsm != null) {
                        if (leftAddr.equals(dest)) {
                            asm.addAll(right.setD());
                            asm.addAll(dest.resolveAddressTo("A"));
                            asm.add("M=" + opAsm);
                            return asm;
                        } else if (rightAddr.equals(dest)) {
                            // Commutative ops only: ADD, AND, OR
                            if (op == ArithmeticInstruction.Op.ADD ||
                                    op == ArithmeticInstruction.Op.AND ||
                                    op == ArithmeticInstruction.Op.OR) {
                                asm.addAll(left.setD());
                                asm.addAll(dest.resolveAddressTo("A"));
                                asm.add("M=" + opAsm);
                                return asm;
                            }
                        }
                    }
                }
            }
        }


        if (dest.isTrivial()) {
            asm.addAll(push.setD());
            asm.addAll(dest.resolveAddressTo("A"));
            asm.add("M=D");
        } else {
            asm.addAll(push.decode());
            asm.addAll(pop.decode());
        }
        return asm;
    }


    public Address getPopAddress(){
        return pop.getAddress();
    }
    @Override
    public String toString() {
        return toStringHelper(0);
    }

    private String toStringHelper(int indent) {
        StringBuilder sb = new StringBuilder();
        String pad = "  ".repeat(indent);
        sb.append(pad).append("PushPopPair {\n");
        sb.append(pad).append("  push: ").append(push).append("\n");


        sb.append(pad).append("  pop:  ").append(pop).append("\n");
        sb.append(pad).append("}\n");

        return sb.toString();
    }
}
