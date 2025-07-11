package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PushInstruction extends PushGroup {
    private final Address address;


    public PushInstruction(Address address) {
        this.address = address;
    }

    private static String indent(int n) {
        return " ".repeat(n);
    }

    @Override
    public List<String> decode() {
        if (isConstant()) {
            if (Math.abs(getConstant()) <= 1) {
                return new ArrayList<>(List.of("@SP", "AM=M+1", "A=A-1", "M=" + getConstant()));
            }
        }
        List<String> asm = new ArrayList<>();
        asm.addAll(setD()); // Handles constant optimization internally
        asm.addAll(List.of("@SP", "AM=M+1", "A=A-1", "M=D"));
        return asm;
    }

    @Override
    List<String> setD() {
        if (isConstant()) {
            short c = getConstant();
            if (c == 0 || c == 1 || c == -1) {
                return List.of("D=" + c);
            }
            if (c < 0) {
                return List.of("@" + (-c), "D=-A");
            }
            return List.of("@" + c, "D=A");
        }
        return address.setDreg();
    }

    public static List<String> handleMultiplePushes(List<PushGroup> pushes) throws Exception {
        if (pushes.isEmpty()) {
            return new ArrayList<>();
        }
        if (pushes.size() == 1) {
            return pushes.getFirst().decode(); // Case 1
        }
        if (allTrivialConstants(pushes)) {
            return encodeTrivialConstants(pushes); // Case 2
        }
        if (allSameNonTrivialPush(pushes)) {
            return encodeRepeatedNonTrivialPush(pushes); // Case 3
        }

        return encodeGroupedPushes(pushes); // Case 4
    }

    private static boolean allTrivialConstants(List<PushGroup> pushes) {
        return pushes.stream().allMatch(p -> p.isConstant() && Math.abs(p.getConstant()) <= 1);
    }

    private static List<String> encodeTrivialConstants(List<PushGroup> pushes) {
        int n = pushes.size();
        if (n == 0){
            return new ArrayList<>();
        }
        if (n == 1) {
            return new ArrayList<>(List.of("@SP", "AM=M+1", "A=A-1", "M=" + pushes.getFirst().getConstant()));
        }
        if (n == 2) {
            return new ArrayList<>(List.of("@SP", "M=M+1", "AM=M+1", "A=A-1", "M=" + pushes.getLast().getConstant(), "A=A-1", "M=" + pushes.getFirst().getConstant()));
        }
        List<String> asm = new ArrayList<>(List.of("@" + n, "D=A", "@SP", "AM=D+M", "A=A-1"));

        for (int i = n - 1; i >= 0; i--) {
            asm.add("M=" + pushes.get(i).getConstant());
            if (i != 0) asm.add("A=A-1");
        }
        return asm;
    }

    private static boolean allSameNonTrivialPush(List<PushGroup> pushes) {
        return pushes.stream().allMatch(p -> p instanceof PushInstruction pi && pi.equals(pushes.getFirst()));
    }

    private static List<String> encodeRepeatedNonTrivialPush(List<PushGroup> pushes) throws Exception {
        List<String> asm = new ArrayList<>();
        int n = pushes.size();
        asm.addAll(List.of("@" + n, "D=A", "@SP", "M=D+M"));
        asm.addAll(((PushInstruction) pushes.getFirst()).setD());
        asm.addAll(List.of("@SP", "A=M-1"));
        for (int i = 0; i < n; i++) {
            asm.add("M=D");
            if (i != n - 1) asm.add("A=A-1");
        }
        return asm;
    }

    private static List<String> encodeGroupedPushes(List<PushGroup> pushes) throws Exception {
        List<String> asm = new ArrayList<>();
        int i = 0;
        while (i < pushes.size()) {
            int j = i;

            // Collect left trivial constants
            List<PushGroup> left = new ArrayList<>();
            while (j < pushes.size() && isTrivialConstant(pushes.get(j))) {
                left.add(pushes.get(j));
                j++;
            }

            // Collect middle non-trivial pushes
            List<PushGroup> middle = new ArrayList<>();
            while (j < pushes.size() && isNonTrivialPush(pushes.get(j))) {
                middle.add(pushes.get(j));
                j++;
            }

            // Collect right trivial constants
            List<PushGroup> right = new ArrayList<>();
            while (j < pushes.size() && isTrivialConstant(pushes.get(j))) {
                right.add(pushes.get(j));
                j++;
            }

            int totalSize = left.size() + middle.size() + right.size();

            if (middle.isEmpty()) {
                // Only trivial constants
                asm.addAll(encodeTrivialConstants(pushes.subList(i, i + totalSize)));
                i += totalSize;
                continue;
            }

            if (allSameNonTrivialPush(middle)) {
                // Increment SP by total size
                asm.add("@" + totalSize);
                asm.add("D=A");
                asm.add("@SP");
                asm.add("M=D+M");
                asm.addAll(((PushInstruction) middle.getFirst()).setD());
                asm.add("@SP");
                asm.add("A=M-1");
                // Write right trivial constants downward
                for (int k = right.size() - 1; k >= 0; k--) {
                    asm.add("M=" + right.get(k).getConstant());
                    asm.add("A=A-1");
                }
                // Write middle repeated pushes downward
                for (int k = 0; k < middle.size(); k++) {
                    asm.add("M=D");
                    asm.add("A=A-1");
                }
                if (!left.isEmpty()) {
                    for (int k = left.size() - 1; k >= 0; k--) {
                        asm.add("M=" + left.get(k).getConstant());
                        if (k != 0) asm.add("A=A-1");
                    }
                }
                i += totalSize;
            } else {
                // Middle is NOT all same PushInstruction
                // Decode left trivial constants + first middle push normally
                asm.addAll(encodeTrivialConstants(left));
                asm.addAll(middle.get(0).decode());

                // Decode all middle except last normally
                for (int idx = 1; idx < middle.size() - 1; idx++) {
                    asm.addAll(middle.get(idx).decode());
                }

                // Decode last middle + right trivial constants together
                if (!middle.isEmpty()) {
                    List<PushGroup> lastPlusRight = new ArrayList<>();
                    lastPlusRight.add(middle.getLast());
                    lastPlusRight.addAll(right);
                    asm.addAll(encodeTrivialConstantsPlusOnePush(lastPlusRight));
                } else {
                    // No middle pushes? Just encode right constants
                    asm.addAll(encodeTrivialConstants(right));
                }

                i += totalSize;
            }
        }
        return asm;
    }

    // Helper: encode list of trivial constants + 1 non-trivial PushGroup at start
    // Example: last middle + right trivial constants together
    private static List<String> encodeTrivialConstantsPlusOnePush(List<PushGroup> pushes) throws Exception {
        List<String> asm = new ArrayList<>();
        if (pushes.isEmpty()) return asm;
        if (pushes.size() == 1){
            return pushes.getFirst().decode();
        }
        // The first element is the non-trivial push
        PushGroup firstPush = pushes.getFirst();
        if (!(firstPush instanceof PushInstruction) || firstPush.isConstant()) {
            // fallback: decode all normally if first isn't a proper push
            for (PushGroup pg : pushes) asm.addAll(pg.decode());
            return asm;
        }

        int n = pushes.size();

        asm.addAll(List.of("@" + n, "D=A", "@SP", "M=D+M"));
        asm.addAll(((PushInstruction) firstPush).setD());
        asm.addAll(List.of("@SP", "A=M-1", "M=D"));

        // write the rest trivial constants downward
        for (int k = pushes.size() - 1; k >= 1; k--) {
            asm.add("A=A-1");
            asm.add("M=" + pushes.get(k).getConstant());
        }

        return asm;
    }


    private static boolean isTrivialConstant(PushGroup p) {
        return p.isConstant() && Math.abs(p.getConstant()) <= 1;
    }

    private static boolean isNonTrivialPush(PushGroup p) {
        return !(p instanceof PushInstruction pi) || !(pi.isConstant() && Math.abs(p.getConstant()) <= 1);
    }


    @Override
    public short getConstant() {
        return address.getIndex();
    }

    public Address getAddress() {
        return address;
    }

    public boolean isConstant() {
        return address.isConstant();
    }

    @Override
    public String toString() {
        return toString(0);
    }

    public String toString(int indent) {
        return indent(indent) + "PushInstruction(\"" + address.getSegment() + " " + address.getIndex() + "\")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PushInstruction other)) return false;
        return Objects.equals(getAddress(), other.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAddress());
    }
}

