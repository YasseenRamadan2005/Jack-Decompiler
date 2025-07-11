package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FunctionInstruction implements VMinstruction {
    private final int numLocals;
    private final Map<String, Integer> funcMapping;
    private String funcName;
    public FunctionInstruction(String functionName, int numLocals, Map<String, Integer> funcMapping) {
        funcName = functionName;
        VMParser.currentFunction = funcName;
        this.numLocals = numLocals;
        this.funcMapping = funcMapping;
    }

    @Override
    public List<String> decode() {
        VMParser.currentFunction = funcName;
        funcMapping.put(VMParser.currentFunction, 0);

        List<String> asm = new ArrayList<>();
        asm.add("// function " + VMParser.currentFunction + " with " + numLocals);
        asm.add("(" + VMParser.currentFunction + ")");

        // Push zero-initialized locals using optimized constant push
        List<PushGroup> zeroPushes = new ArrayList<>();
        for (int i = 0; i < numLocals; i++) {
            zeroPushes.add(new PushInstruction(new Address("constant", (short) 0)));
        }

        try {
            asm.addAll(PushInstruction.handleMultiplePushes(zeroPushes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate locals for function " + funcName, e);
        }

        return asm;
    }


    public String getFuncName() {
        return funcName;
    }

    @Override
    public String toString() {
        VMParser.currentFunction = funcName;
        return "FunctionInstruction{" +
                "functionName='" + VMParser.currentFunction + '\'' +
                ", numLocals=" + numLocals +
                ", funcMapping=" + funcMapping +
                '}';
    }

    public int getNumLocals() {
        return  numLocals;
    }
}
