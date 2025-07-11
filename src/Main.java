// Main.java

import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <directory>");
            System.exit(1);
        }

        File inputDir = new File(args[0]);
        if (!inputDir.isDirectory()) {
            System.err.println("Not a directory.");
            System.exit(1);
        }

        File outputDir = new File(inputDir, "jack_source");
        outputDir.mkdir();

        JackDecompiler decompiler = new JackDecompiler(inputDir);
        decompiler.decompileAll();

        VMToJackTranslator translator = new VMToJackTranslator(decompiler);
        decompiler.writeJackFiles(outputDir, translator);
    }
}
