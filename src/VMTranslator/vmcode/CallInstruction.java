package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CallInstruction implements VMinstruction {
    private final String calleeFunction;
    private final int numArgs;
    private final Map<String, Integer> funcMapping;

    public CallInstruction(String calleeFunction, int numArgs, Map<String, Integer> funcMapping) {
        this.calleeFunction = calleeFunction;
        this.numArgs = numArgs;
        this.funcMapping = funcMapping;
    }

    public int getArgs() {
        return numArgs;
    }

    @Override
    public List<String> decode() {
        List<String> asm = new ArrayList<>();

        //When I jump to the pre-defined CALL subroutine, I need the return address in the D register already, the function pointer in @13, and the number of arguments plus 5 in @14

        int callCount = funcMapping.getOrDefault(VMParser.currentFunction, 0);
        String returnLabel = VMParser.currentFunction + ".ret." + callCount;

        funcMapping.put(VMParser.currentFunction, callCount + 1);

        asm.add("// call " + calleeFunction);

        asm.add("@" + (numArgs + 5));
        asm.add("D=A");
        asm.add("@14");
        asm.add("M=D"); //Deposit this ARGS + 5 for later

        asm.add("@" + calleeFunction);
        asm.add("D=A");
        asm.add("@13");
        asm.add("M=D");

        asm.add("@" + returnLabel);
        asm.add("D=A");
        asm.add("@CALL");
        asm.add("0;JMP");

        asm.add("(" + returnLabel + ")");

        return asm;
    }

    public String getFunctionName() {
        return calleeFunction;
    }

    @Override
    public String toString() {
        return "CallInstruction{" + calleeFunction + '}';
    }
}
