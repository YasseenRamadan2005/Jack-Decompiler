// Main.java
import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <directory>");
            System.exit(1);
        }

        File inputDir = new File(args[0]);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            System.err.println("Error: Provided path is not a directory.");
            System.exit(1);
        }

        File outputDir = new File(inputDir, "jack_source");
        if (!outputDir.exists() && !outputDir.mkdir()) {
            System.err.println("Error: Could not create output directory 'jack_source'.");
            System.exit(1);
        }

        JackDecompiler decompiler = new JackDecompiler(inputDir, outputDir);
        decompiler.decompileAll();
    }
}