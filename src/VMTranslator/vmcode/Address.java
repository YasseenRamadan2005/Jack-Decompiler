package VMTranslator.vmcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Address {
    private final String segment;
    private final short index;

    public Address(String segment, short index) {
        this.segment = segment;
        this.index = index;
    }

    private String getShortSegmentName() {

        return switch (segment) {
            case "local" -> "LCL";
            case "argument" -> "ARG";
            case "this" -> "THIS";
            case "that" -> "THAT";
            default -> segment;
        };
    }


    //Sets either the M register to the value or the D register to the address
    //targetReg is either A or D
    //Overwrites the D reg if the segment is a pointer type and index is large
    public List<String> resolveAddressTo(String targetReg) {
        List<String> asm = new ArrayList<>();

        switch (segment) {
            case "constant" -> asm.add("@" + index);

            case "static" -> asm.add("@" + VMParser.moduleName + "." + index);

            case "temp", "pointer" -> asm.add("@" + (segment.equals("temp") ? 5 + index : 3 + index));

            case "local", "argument", "this", "that" -> {
                asm.add("@" + getShortSegmentName());
                if (index == 0) {
                    asm.add(targetReg + "=M");
                } else if (index < 4) {
                    asm.add("A=M+1");
                    for (int i = 1; i < index; i++) {
                        asm.add("A=A+1");
                    }
                    if (targetReg.equals("D")) asm.add("D=A");
                } else {
                    asm.add("D=M");
                    asm.add("@" + index);
                    asm.add(targetReg + "=D+A");
                }
            }
            default -> throw new IllegalArgumentException("Unknown segment: " + segment);
        }
        return asm;
    }

    //Sets the D reg to the value
    public List<String> setDreg() {
        List<String> asm = new ArrayList<>();

        if (segment.equals("constant")) {
            if (index == 0 || index == 1 || index == -1) {
                asm.add("D=" + index);
            } else {
                if (index < 0) {
                    asm.add("@" + (-index));
                    asm.add("D=A");
                    asm.add("D=-D");
                } else {
                    asm.add("@" + index);
                    asm.add("D=A");
                }
            }
        } else {
            asm.addAll(resolveAddressTo("A"));
            asm.add("D=M");
        }

        return asm;
    }

    public String lookUpAddress() {
        if (segment.equals("pointer") && index == (short) 0) {
            return "this";
        }
        return switch (segment) {
            case "constant" -> String.valueOf(index);
            case "static", "argument", "local" -> segment + "_" + index;
            case "this" -> "field_" + index;
            default -> ""; // fallback
        };
    }

    public boolean isTrivial() {
        return switch (segment) {
            case "local", "argument", "this", "that" -> index < 4;
            default -> true;
        };
    }

    public boolean isReachable(Address a) {
        // Pointer-based segments
        Set<String> pointerSegments = Set.of("local", "argument", "this", "that");

        // Check if both are in a pointer segment
        if (pointerSegments.contains(this.segment) && this.segment.equals(a.segment)) {
            return Math.abs(this.index - a.index) < 4;
        }

        return true;
    }


    public boolean isConstant() {
        return segment.equals("constant");
    }

    public short getIndex() {
        return index;
    }

    public String getSegment() {
        return segment;
    }

    @Override
    public String toString() {
        if ("static".equals(segment) && VMParser.moduleName != null) {
            return VMParser.moduleName + "." + index;
        }
        return segment + " " + index;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Address other)) return false;
        return segment.equals(other.segment) && index == other.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(segment, index);
    }
}
