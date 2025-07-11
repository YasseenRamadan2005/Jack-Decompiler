package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

/**
 * A primitive arithmetic / logical VM command.
 */
public final class ArithmeticInstruction implements VMinstruction {

    public static int counter;
    private final Op op;


    public ArithmeticInstruction(Op op) {
        this.op = op;
        counter = 0;
    }

    @Override
    public List<String> decode() {
        return new ArrayList<>(op.emit(true));
    }

    public boolean isUnary() {
        return op.unary;
    }

    public boolean isBinary() {
        return !op.unary;
    }

    public Op getOp() {
        return op;
    }

    @Override
    public String toString() {
        return "ArithmeticInstruction{" + op + '}';
    }

    /* ────────────────────────────────  Op  ──────────────────────────────── */
    public enum Op {
        ADD("D+M", false) {}, SUB("M-D", false) {}, AND("D&M", false) {}, OR("D|M", false) {}, NEG("-D", true) {
            int apply(int v) {
                return -v;
            }
        }, NOT("!D", true) {
            int apply(int v) {
                return ~v;
            }
        }, LT("lt", false) {}, EQ("eq", false) {}, GT("gt", false) {};
        /* ---------- data ---------- */
        private final String rhs;
        private final boolean unary;

        /* ---------- ctor ---------- */
        Op(String rhs, boolean unary) {
            this.rhs = rhs;
            this.unary = unary;
        }

        /* ---------- public API ---------- */
        public boolean isUnary() {
            return unary;
        }

        public boolean isBinary() {
            return !unary;
        }

        public boolean isCompare() {
            return this == LT || this == EQ || this == GT;
        }

        /// constant folding: apply this op to v (only meaningful for unary ops)
        int apply(int v) {
            return v;
        }                 // default (binary ops do nothing)

        /**
         * assembly text when the result is in <code>D</code>
         */
        public String onD() {
            return "D=" + rhs;
        }

        /**
         * assembly text when the result is in <code>M</code>
         */
        public String onM() {
            return "M=" + rhs;
        }

        /**
         * assembly template for the whole stack sequence
         */
        List<String> emit(boolean alone) {
            //if alone, the final result on the stack, else in the D register.
            if (unary) {
                if (alone) {
                    return List.of("@SP", "A=M-1", onM().replace('D', 'M'));
                }
                return List.of("@SP", "A=M-1", onM());
            }
            if (isCompare()) {
                //Make the subtraction and the label
                List<String> ls = new ArrayList<>(List.of("@COMPARE_" + VMParser.currentFunction + "_" + rhs + counter, "D=A", "@13", "M=D", "@SP", "AM=M-1", "D=M", "A=A-1", "D=M-D"));
                switch (rhs) {
                    case "lt":
                        ls.addAll(List.of("@DO_LT", "0;JMP"));
                        break;
                    case "eq":
                        ls.addAll(List.of("@DO_EQ", "0;JMP"));
                        break;
                    case "gt":
                        ls.addAll(List.of("@DO_GT", "0;JMP"));
                        break;
                }
                ls.add("(COMPARE_" + VMParser.currentFunction + "_" + rhs + counter + ")");
                counter++;
                if (alone) {
                    ls.addAll(List.of("@SP", "A=M-1", "M=D"));
                }
                return ls;
            }
            List<String> ls = new ArrayList<>(List.of("@SP", "AM=M-1", "D=M", "A=A-1"));
            if (alone) {
                ls.add("M=" + rhs);
            }
            else{
                ls.add("D=" + rhs);
            }
            return ls;
        }
    }
}
