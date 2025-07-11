package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;

public class Dereference extends PushGroup {
    //Grammar
    //  PushGroup
    //  pop pointer 1
    //  push that 0
    BinaryPushGroup base;


    public Dereference(BinaryPushGroup base) {
        this.base = base;
    }

    @Override
    public List<String> decode() throws Exception {
        List<String> list = new ArrayList<>(setD());
        list.addAll(List.of("@SP", "AM=M+1", "A=A-1", "M=D"));
        return list;
    }

    @Override
    List<String> setD() throws Exception {
        List<String> list = new ArrayList<>(base.setD());
        if (!base.isConstant()) {
            list.set(list.size() - 1, 'A' + list.get(list.size() - 1).substring(1));
        }
        else{
            list.removeLast();
        }
        list.add("D=M");
        return list;
    }


    public BinaryPushGroup getBase() {
        return base;
    }

    @Override
    boolean isConstant() {
        return false;
    }

    @Override
    short getConstant() {
        return 0;
    }

    @Override
    String toString(int i) {
        return " ".repeat(i) + "Dereference(\n" + " ".repeat(i + 4) + "base:\n" + base.toString(i + 8) + "\n" + " ".repeat(i) + ")";
    }

    @Override
    public String toString() {
        return toString(0);
    }


}
