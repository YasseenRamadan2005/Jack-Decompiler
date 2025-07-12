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
        if (fn.name.equals("String.appendChar")) {
            int x = 0;
        }
        switch (instr) {
            case LabelInstruction labelStart -> {
                String startLabel = labelStart.getLabel();
                // Check next instructions for while pattern
                if (start + 1 < parsed.size() && parsed.get(start + 1) instanceof ConditionalGroup cond) {
                    String exitLabel = cond.getIfGoto().getLabel();

                    // Search forward for GotoInstruction back to startLabel and LabelInstruction of exitLabel
                    int i = start + 2;
                    while (i + 1 < parsed.size()) {
                        VMinstruction instr1 = parsed.get(i);
                        VMinstruction instr2 = parsed.get(i + 1);

                        if (instr1 instanceof GotoInstruction gotoBack && gotoBack.getLabel().equals(startLabel) && instr2 instanceof LabelInstruction exitLabelInstr && exitLabelInstr.getLabel().equals(exitLabel)) {

                            // We found the while loop pattern!

                            // Generate while header with condition
                            if (cond.getPush() instanceof UnaryPushGroup up && up.getOp().equals(ArithmeticInstruction.Op.NOT)) {
                                jackLines.add(indent("while (" + generatePushGroup(up.getInner()) + ") {", indentLevel));
                            } else {
                                jackLines.add(indent("while (~(" + generatePushGroup(cond.getPush()) + ")) {", indentLevel));
                            }

                            // Generate loop body recursively
                            int bodyStart = start + 2;
                            List<String> bodyLines = new ArrayList<>();
                            while (bodyStart < i) {
                                bodyStart = generateBlock(parsed, bodyStart, bodyLines, indentLevel + 1);
                            }
                            jackLines.addAll(bodyLines);

                            jackLines.add(indent("}", indentLevel));

                            // Return index after the final exit label instruction
                            return i + 2;
                        }
                        i++;
                    }
                }
                // Default fallback, just consume this label and move on
                return start + 1;
            }

            case ConditionalGroup cg -> {
                String ifTrue = cg.getIfGoto().getLabel();
                int i = start + 1;

                if (i < parsed.size() && parsed.get(i) instanceof GotoInstruction g1) {
                    String ifFalse = g1.getLabel();

                    if (i + 1 < parsed.size() && parsed.get(i + 1) instanceof LabelInstruction l1 && l1.getLabel().equals(ifTrue)) {
                        i += 2;
                        String condition = generatePushGroup(cg.getPush());
                        jackLines.add(indent("if (" + condition + ") {", indentLevel));

                        List<String> thenBlock = new ArrayList<>();
                        while (i < parsed.size()) {
                            instr = parsed.get(i);
                            // Break on label or end-goto
                            if (instr instanceof LabelInstruction l && l.getLabel().equals(ifFalse)) break;
                            if (instr instanceof GotoInstruction g && g.getLabel().startsWith(fn.name + "$IF_END"))
                                break;
                            i = generateBlock(parsed, i, thenBlock, indentLevel + 1);
                        }
                        jackLines.addAll(thenBlock);
                        jackLines.add(indent("}", indentLevel));

                        // Optional else block
                        boolean hasElse = false;
                        String endLabel = null;

                        // Check if there's a goto after thenBlock
                        if (i < parsed.size() && parsed.get(i) instanceof GotoInstruction g2) {
                            endLabel = g2.getLabel();
                            hasElse = true;
                            i++;
                        }

                        // Expect false label next
                        if (i < parsed.size() && parsed.get(i) instanceof LabelInstruction l && l.getLabel().equals(ifFalse)) {
                            i++;
                        }

                        if (hasElse) {
                            jackLines.add(indent("else {", indentLevel));
                            List<String> elseBlock = new ArrayList<>();
                            while (i < parsed.size()) {
                                instr = parsed.get(i);
                                if (instr instanceof LabelInstruction l && l.getLabel().equals(endLabel)) break;
                                i = generateBlock(parsed, i, elseBlock, indentLevel + 1);
                            }
                            jackLines.addAll(elseBlock);
                            jackLines.add(indent("}", indentLevel));
                            if (i < parsed.size() && parsed.get(i) instanceof LabelInstruction l && l.getLabel().equals(endLabel)) {
                                i++;
                            }
                        }

                        return i;
                    }
                }

                // fallback: pure if
                String condition = generatePushGroup(cg.getPush());
                jackLines.add(indent("if (" + condition + ") {", indentLevel));
                List<String> thenBlock = new ArrayList<>();
                i = start + 1;
                while (i < parsed.size()) {
                    VMinstruction curr = parsed.get(i);
                    if (curr instanceof LabelInstruction) break;
                    i = generateBlock(parsed, i, thenBlock, indentLevel + 1);
                }
                jackLines.addAll(thenBlock);
                jackLines.add(indent("}", indentLevel));
                return i;
            }

            default -> {
                String generated = switch (instr) {
                    case PushGroup pg -> indent(generatePushGroup(pg), indentLevel);
                    case PushPopPair pair -> indent(generatePushPopPair(pair), indentLevel);
                    case PushWriter pw -> indent(generatePushWriter(pw), indentLevel);
                    case ReturnInstruction r -> indent(generateReturnInstruction(r), indentLevel);
                    case FunctionInstruction f -> "";
                    default -> "// Unhandled VM instruction: " + instr;
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
                indices.addFirst(generatePushGroup(bg.getLeft()));
                cur = bg.getRight();
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
        if (pair.getPop().getAddress().lookUpAddress().isBlank() || generatePushGroup(pair.getPush()).isBlank()) {
            return "";
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
            if (instance.equals("this")) {
                return methodName + "(" + String.join(", ", jackArgs) + ")";
            }
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
            indices.addFirst(generatePushGroup(bg.getLeft()));  // collect right as index
            dest = bg.getRight();                                 // move left toward base
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
