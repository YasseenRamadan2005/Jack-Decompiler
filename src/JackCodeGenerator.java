import VMTranslator.vmcode.*;

import java.util.ArrayList;
import java.util.List;

public class JackCodeGenerator {
    private JackDecompiler.FunctionMetadata fn;
    private JackDecompiler decompiler;  // reference to query metadata

    public JackCodeGenerator(JackDecompiler decompiler) {
        this.decompiler = decompiler;
    }

    public List<String> generateJackCode(List<VMinstruction> parsed, JackDecompiler.FunctionMetadata fn) throws Exception {
        this.fn = fn;
        List<VMinstruction> cleaned = preprocess(parsed);
        List<String> jackLines = new ArrayList<>();
        int i = 0;
        int indentLevel = 0;

        while (i < cleaned.size()) {
            i = generateBlock(cleaned, i, jackLines, indentLevel);
        }

        return jackLines;
    }

    // Strip compiler-generated instructions like:
    // push argument 0 / pop pointer 0 (method)
    // push constant n / call Memory.alloc 1 / pop pointer 0 (constructor)
    private List<VMinstruction> preprocess(List<VMinstruction> parsed) {
        List<VMinstruction> result = new ArrayList<>(parsed);
        if (fn.isMethod || fn.isConstructor) {
            result.remove(1);
            result.remove(0);
        }
        return result;
    }

    private int generateBlock(List<VMinstruction> parsed, int start, List<String> jackLines, int indentLevel) throws Exception {
        VMinstruction instr = parsed.get(start);

        switch (instr) {
            case LabelInstruction labelStart when (start + 1 < parsed.size()) && parsed.get(start + 1) instanceof ConditionalGroup cond -> {
                // While loop detection pattern:
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
                int i = start + 1;

                // Detect the *other compiler* if-else pattern:
                // if-goto ELSE_LABEL
                // goto THEN_LABEL
                // label ELSE_LABEL
                // ... else block ...
                // label THEN_LABEL
                // ... then block ...
                if (i < parsed.size() && parsed.get(i) instanceof GotoInstruction thenGoto) {
                    String thenLabel = thenGoto.getLabel();

                    if (i + 1 < parsed.size() && parsed.get(i + 1) instanceof LabelInstruction elseLabelInstr && elseLabelInstr.getLabel().equals(falseLabel)) {

                        // Generate if condition without redundant negation
                        String conditionStr;
                        if (cg.getPush() instanceof UnaryPushGroup up && up.getOp().equals(ArithmeticInstruction.Op.NOT)) {
                            conditionStr = generatePushGroup(up.getInner());
                        } else {
                            conditionStr = "~(" + generatePushGroup(cg.getPush()) + ")";
                        }
                        jackLines.add(indent("if (" + conditionStr + ") {", indentLevel));

                        i += 2; // consume goto THEN_LABEL and label ELSE_LABEL

                        // Parse else block until label THEN_LABEL
                        List<String> elseBlock = new ArrayList<>();
                        while (i < parsed.size()) {
                            VMinstruction current = parsed.get(i);
                            if (current instanceof LabelInstruction l && l.getLabel().equals(thenLabel)) {
                                break;
                            }
                            i = generateBlock(parsed, i, elseBlock, indentLevel + 1);
                        }

                        // Consume label THEN_LABEL
                        if (i < parsed.size() && parsed.get(i) instanceof LabelInstruction thenLabelInstr && thenLabelInstr.getLabel().equals(thenLabel)) {
                            i++;
                        }

                        // Parse then block until next label or end of list
                        List<String> thenBlock = new ArrayList<>();
                        while (i < parsed.size()) {
                            VMinstruction current = parsed.get(i);
                            if (current instanceof LabelInstruction) break;
                            i = generateBlock(parsed, i, thenBlock, indentLevel + 1);
                        }

                        // Emit then block inside the if braces first
                        jackLines.addAll(thenBlock);
                        jackLines.add(indent("} else {", indentLevel));
                        jackLines.addAll(elseBlock);
                        jackLines.add(indent("}", indentLevel));

                        return i;
                    }
                }


                // FALLBACK: original if-else detection pattern
                jackLines.add(indent("if (~(" + generatePushGroup(cg.getPush()) + ")) {", indentLevel));

                int j = start + 1;
                List<String> thenBlock = new ArrayList<>();

                while (j < parsed.size()) {
                    VMinstruction current = parsed.get(j);
                    if (current instanceof GotoInstruction || (current instanceof LabelInstruction l && l.getLabel().equals(falseLabel))) {
                        break;
                    }
                    j = generateBlock(parsed, j, thenBlock, indentLevel + 1);
                }

                jackLines.addAll(thenBlock);
                jackLines.add(indent("}", indentLevel));

                if (j < parsed.size() && parsed.get(j) instanceof GotoInstruction g) {
                    String endLabel = g.getLabel();
                    j++;

                    if (j < parsed.size() && parsed.get(j) instanceof LabelInstruction elseLabelInstr && elseLabelInstr.getLabel().equals(falseLabel)) {
                        j++;

                        List<String> elseBlock = new ArrayList<>();
                        while (j < parsed.size()) {
                            VMinstruction current = parsed.get(j);
                            if (current instanceof LabelInstruction l && l.getLabel().equals(endLabel)) {
                                break;
                            }
                            j = generateBlock(parsed, j, elseBlock, indentLevel + 1);
                        }

                        jackLines.add(indent("else {", indentLevel));
                        jackLines.addAll(elseBlock);
                        jackLines.add(indent("}", indentLevel));

                        if (j < parsed.size() && parsed.get(j) instanceof LabelInstruction endLabelInstr && endLabelInstr.getLabel().equals(endLabel)) {
                            j++;
                        }
                    } else if (j < parsed.size() && parsed.get(j) instanceof LabelInstruction l && l.getLabel().equals(falseLabel)) {
                        j++;
                    }

                    return j;
                } else if (j < parsed.size() && parsed.get(j) instanceof LabelInstruction l && l.getLabel().equals(falseLabel)) {
                    j++;
                    return j;
                }

                return j;
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
                    case FunctionInstruction f -> "";
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
            PushGroup cur = dr.getBase();
            List<String> indices = new ArrayList<>();
            while (cur instanceof BinaryPushGroup bg && bg.getOp() == ArithmeticInstruction.Op.ADD) {
                indices.addFirst(generatePushGroup(bg.getRight()));
                cur = bg.getLeft();
            }
            StringBuilder sb = new StringBuilder(generatePushGroup(cur));
            for (String idx : indices) sb.append('[').append(idx).append(']');
            return sb.toString();
        } else if (pg instanceof UnaryPushGroup ug) {
            String operator = switch (ug.getOp()) {
                case ArithmeticInstruction.Op.NEG -> "-";
                case ArithmeticInstruction.Op.NOT -> "~";
                default -> throw new IllegalStateException("Unhandled op: " + ug.getOp());
            };
            return operator + "(" + generatePushGroup(ug.getInner()) + ")";
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
            if (bg.getLeft() instanceof PushInstruction pl && bg.getRight() instanceof PushInstruction pr) {
                return generatePushGroup(pl) + " " + operator + " " + generatePushGroup(pr);
            }
            return "( " + generatePushGroup(bg.getLeft()) + " " + operator + " " + generatePushGroup(bg.getRight()) + ")";
        } else if (pg instanceof PushInstruction pi) {
            return pi.getAddress().lookUpAddress();
        } else {
            return pg.toString();
        }
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

    private String generateCallGroup(CallGroup cg) {
        if (isAppendCharChain(cg)) {
            return "\"" + extractStringFromAppendChain(cg) + "\"";
        }

        String functionName = cg.getFunctionName();  // e.g. ClassName.methodName
        List<PushGroup> args = cg.getPushes();
        List<String> jackArgs = new ArrayList<>();

        boolean isMethod = decompiler.isMethodFunction(functionName);

        if (isMethod && !args.isEmpty()) {
            // First argument is instance
            String instance = generatePushGroup(args.getFirst());
            for (int i = 1; i < args.size(); i++) {
                jackArgs.add(generatePushGroup(args.get(i)));
            }
            String methodName = functionName.substring(functionName.indexOf('.') + 1);
            return instance + "." + methodName + "(" + String.join(", ", jackArgs) + ")";
        } else {
            for (PushGroup arg : args) {
                jackArgs.add(generatePushGroup(arg));
            }
            return functionName + "(" + String.join(", ", jackArgs) + ")";
        }
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
