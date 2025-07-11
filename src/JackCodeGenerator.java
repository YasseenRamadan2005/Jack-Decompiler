import java.util.ArrayList;
import java.util.List;

import VMTranslator.vmcode.*;

public class JackCodeGenerator {
    private String ClassName;
    private JackDecompiler.FunctionMetadata fn;

    public List<String> generateJackCode(List<VMinstruction> parsed, String className, JackDecompiler.FunctionMetadata fn) {
        this.ClassName = className;
        this.fn = fn;
        List<String> jackLines = new ArrayList<>();
        int i = 1;
        if (fn.isConstructor || fn.isMethod) {
            parsed.remove(1);
        }
        int indentLevel = 0;
        while (i < parsed.size()) {
            i = generateBlock(parsed, i, jackLines, indentLevel);
        }

        return jackLines;
    }


    private int generateBlock(List<VMinstruction> parsed, int start, List<String> jackLines, int indentLevel) {
        VMinstruction instr = parsed.get(start);
        switch (instr) {
            case LabelInstruction labelStart when (start + 1 < parsed.size()) && parsed.get(start + 1) instanceof ConditionalGroup cond -> {
                String endLabel = cond.getIfGoto().getLabel();
                if (cond.getPush() instanceof UnaryPushGroup up && up.getOp().equals(ArithmeticInstruction.Op.NOT)) {
                    jackLines.add(indent("while (" + generatePushGroup(up.getInner()) + ") {", indentLevel));
                } else {
                    jackLines.add(indent("while (~(" + generatePushGroup(cond.getPush()) + ")) {", indentLevel));
                }

                int i = start + 2;
                List<String> body = new ArrayList<>();
                while (i + 1 < parsed.size() && !(parsed.get(i) instanceof GotoInstruction g && g.getLabel().equals(labelStart.getLabel()) && parsed.get(i + 1) instanceof LabelInstruction l && l.getLabel().equals(endLabel))) {
                    i = generateBlock(parsed, i, body, indentLevel + 1);
                }
                jackLines.addAll(body);
                jackLines.add(indent("}", indentLevel));
                return i + 2;

            }
            case ConditionalGroup cg -> {
                String falseLabel = cg.getIfGoto().getLabel();

                // Detect condition expression and emit "if"
                if (cg.getPush() instanceof UnaryPushGroup up && up.getOp().equals(ArithmeticInstruction.Op.NOT)) {
                    jackLines.add(indent("if (" + generatePushGroup(up.getInner()) + ") {", indentLevel));
                } else {
                    jackLines.add(indent("if (~(" + generatePushGroup(cg.getPush()) + ")) {", indentLevel));
                }

                int i = start + 1;
                List<String> thenBlock = new ArrayList<>();

                // Parse then block: stop at either
                //   a) goto instruction that jumps to end label (if-else)
                //   b) label instruction matching falseLabel (if no else)
                while (i < parsed.size()) {
                    VMinstruction current = parsed.get(i);

                    // Stop if goto jumps to end label or label matches falseLabel
                    if (current instanceof GotoInstruction) {
                        // This goto should jump over else block (to end label)
                        break;
                    } else if (current instanceof LabelInstruction l && l.getLabel().equals(falseLabel)) {
                        // Label marking start of else block or end of if block without else
                        break;
                    }
                    i = generateBlock(parsed, i, thenBlock, indentLevel + 1);
                }

                jackLines.addAll(thenBlock);
                jackLines.add(indent("}", indentLevel));

                // If next instruction is a goto jumping to end label, then there's an else block
                if (i < parsed.size() && parsed.get(i) instanceof GotoInstruction g) {
                    String endLabel = g.getLabel();
                    i++; // consume goto

                    // Next instruction should be the label marking start of else block
                    if (i < parsed.size() && parsed.get(i) instanceof LabelInstruction elseLabelInstr && elseLabelInstr.getLabel().equals(falseLabel)) {
                        i++; // consume else label

                        List<String> elseBlock = new ArrayList<>();
                        // Parse else block until the end label
                        while (i < parsed.size()) {
                            VMinstruction current = parsed.get(i);
                            if (current instanceof LabelInstruction l && l.getLabel().equals(endLabel)) {
                                break;
                            }
                            i = generateBlock(parsed, i, elseBlock, indentLevel + 1);
                        }
                        jackLines.add(indent("else {", indentLevel));
                        jackLines.addAll(elseBlock);
                        jackLines.add(indent("}", indentLevel));
                        if (i < parsed.size() && parsed.get(i) instanceof LabelInstruction endLabelInstr && endLabelInstr.getLabel().equals(endLabel)) {
                            i++; // consume end label
                        }
                    }
                    return i;
                } else if (i < parsed.size() && parsed.get(i) instanceof LabelInstruction l && l.getLabel().equals(falseLabel)) {
                    // No else block, just consume the falseLabel
                    i++;
                    return i;
                }

                return i;

            }

            case LabelInstruction l -> {
                return start + 1;
            }

            default -> {
                String generated = switch (instr) {
                    case PushGroup pg -> indent(generatePushGroup(pg), indentLevel);
                    case PushPopPair pair -> indent(generatePushPopPair(pair), indentLevel);
                    case PushWriter pw -> indent(generatePushWriter(pw), indentLevel);
                    case ReturnInstruction r -> indent(generateReturnInstruction(r), indentLevel);
                    default -> "// Unhandled VM instruction: " + instr.getClass().getSimpleName();
                };
                jackLines.add(generated);
                return start + 1;
            }

        }
    }

    private String generatePushGroup(PushGroup pg) {
        if (pg instanceof CallGroup cg) {
            return generateCallGroup(cg);
        } else if (pg instanceof Dereference dr) {
            List<String> indices = new ArrayList<>();
            PushGroup current = dr.getBase();

            // Inline unwrap logic: collect right-hand sides of nested ADDs as indices
            while (current instanceof BinaryPushGroup bg && bg.getOp().equals(ArithmeticInstruction.Op.ADD)) {
                indices.addFirst(generatePushGroup(bg.getRight())); // index
                current = bg.getLeft();                             // step toward base
            }

            StringBuilder sb = new StringBuilder(generatePushGroup(current)); // base
            for (String index : indices) {
                sb.append("[").append(index).append("]");
            }

            return sb.toString();
        } else if (pg instanceof UnaryPushGroup ug) {
            String operator = switch (ug.getOp()) {
                case ArithmeticInstruction.Op.NEG -> "-";
                case ArithmeticInstruction.Op.NOT -> "~";
                default -> throw new IllegalStateException("Unhandled op: " + ug.getOp());
            };
            if (ug.getInner() instanceof PushInstruction){
                return " (" + operator + generatePushGroup(ug.getInner()) + ")";
            }
            return " (" + operator + " ( " + generatePushGroup(ug.getInner()) + " ))";
        } else if (pg instanceof BinaryPushGroup bg) {
            String operator = switch (bg.getOp()) {
                case ArithmeticInstruction.Op.ADD -> "+";
                case ArithmeticInstruction.Op.SUB -> "-";
                case ArithmeticInstruction.Op.AND -> "&";
                case ArithmeticInstruction.Op.OR -> "|";
                case ArithmeticInstruction.Op.GT -> ">";
                case ArithmeticInstruction.Op.EQ -> "=";
                case ArithmeticInstruction.Op.LT -> "<";
                default -> throw new IllegalStateException("Unhandled op: " + bg.getOp());
            };
            if (bg.getLeft() instanceof PushInstruction pl && bg.getRight() instanceof PushInstruction pr){
                return generatePushGroup(pl) + " " + operator + " " + generatePushGroup(pr);
            }
            return "( " + generatePushGroup(bg.getLeft()) + " " + operator + " " + generatePushGroup(bg.getRight()) + ")";
        } else if (pg instanceof PushInstruction pi) {
            return pi.getAddress().lookUpAddress();
        } else {
            return pg.toString();
        }
    }

    private String generateCallGroup(CallGroup cg) {
        if (isAppendCharChain(cg)) {
            return "\"" + extractStringFromAppendChain(cg) + "\"";
        }

        StringBuilder sb = new StringBuilder();
        List<PushGroup> theArguments = cg.getPushes();

        if (JackDecompiler.FuncTable.get(cg.getFunctionName())) {
            sb.append(generatePushGroup(theArguments.getFirst())).append(".").append(cg.getFunctionName().substring(cg.getFunctionName().indexOf('.') + 1)).append("(");
            theArguments.removeFirst();
            List<String> args = theArguments.stream().map(this::generatePushGroup).toList();
            sb.append(String.join(", ", args)).append(")");
        } else {
            sb.append(cg.getFunctionName()).append("(");
            List<String> args = theArguments.stream().map(this::generatePushGroup).toList();
            sb.append(String.join(", ", args)).append(")");
        }

        return sb.toString();
    }


    private boolean isAppendCharChain(CallGroup cg) {
        // Must be at least one appendChar
        if (!cg.getFunctionName().equals("String.appendChar")) return false;

        while (cg.getFunctionName().equals("String.appendChar")) {
            List<PushGroup> args = cg.getPushes();
            if (args.size() != 2) return false;

            PushGroup base = args.get(0);
            PushGroup charArg = args.get(1);

            // Ensure charArg is push constant
            if (!(charArg instanceof PushInstruction cp) || !cp.isConstant()) {
                return false;
            }

            if (base instanceof CallGroup nested) {
                cg = nested;
            } else {
                return false;
            }
        }

        // Now we're at the base call. Must be String.new with one push constant argument
        return cg.getFunctionName().equals("String.new") && cg.getPushes().size() == 1 && cg.getPushes().getFirst() instanceof PushInstruction pi && pi.isConstant();
    }


    private String extractStringFromAppendChain(CallGroup cg) {
        StringBuilder sb = new StringBuilder();

        while (cg.getFunctionName().equals("String.appendChar")) {
            List<PushGroup> args = cg.getPushes();
            PushGroup charArg = args.get(1);

            if (charArg instanceof PushInstruction push) {
                int code = push.getConstant();
                sb.insert(0, (char) code);
            }

            // move to previous CallGroup
            cg = (CallGroup) args.get(0);
        }

        // done: hit String.new (can skip size check; already validated)
        return sb.toString();
    }


    private String generatePushPopPair(PushPopPair pair) {
        if (pair.getPush() instanceof CallGroup cg && pair.getPop().getAddress().equals(new Address("temp", (short) 0))) {
            return "do " + generateCallGroup(cg) + ";";
        }
        return "let " + pair.getPop().getAddress().lookUpAddress() + " = " + generatePushGroup(pair.getPush()) + ";";
    }

    private String generateReturnInstruction(ReturnInstruction retInstr) {
        if (fn != null && fn.returnType().equals("void")) {
            return "return;";
        }
        return "return " + generatePushGroup(retInstr.getPg()) + ";";
    }

    private String generatePushWriter(PushWriter pw) {
        // Handle dest (e.g. arr[2][3])
        PushGroup dest = pw.getDest();
        List<String> indices = new ArrayList<>();

        // Unwrap nested BinaryPushGroup with ADD ops to collect indices
        while (dest instanceof BinaryPushGroup bg && bg.getOp().equals(ArithmeticInstruction.Op.ADD)) {
            indices.addFirst(generatePushGroup(bg.getRight()));  // collect right as index
            dest = bg.getLeft();                                 // move left toward base
        }

        String base = generatePushGroup(dest); // now base is not ADD anymore
        StringBuilder lhs = new StringBuilder(base);
        for (String index : indices) {
            lhs.append("[").append(index).append("]");
        }

        // Handle source (e.g. arr[3])
        String rhs = generatePushGroup(pw.getSource());

        return "let " + lhs + " = " + rhs + ";";
    }

    private String indent(String line, int level) {
        return "\t".repeat(level) + line;
    }

}
