package com.tuvistavie.bigcode.astgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.tuvistavie.bigcode.astgen.util.FileFinder;
import com.tuvistavie.bigcode.astgen.visitors.JsonVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class contains static methods to transform files in their
 * JSON AST representation
 */
public class AstGenerator {
    private static Logger logger = LoggerFactory.getLogger(AstGenerator.class);
    private static ObjectMapper mapper = new ObjectMapper();

    private AstGenerator() {}

    public static class Options {
        private int maxNodes;
        private int minNodes;

        public Options() {
            this(0, 100000);
        }
        public Options(int minNodes, int maxNodes) {
            this.minNodes = minNodes;
            this.maxNodes = maxNodes;
        }
    }

    /**
     * Returns a list containing all the nodes of the AST contained by the program in {@code filepath}
     *
     * @param filepath the path of the file to parse
     * @return the list of all the nodes in the AST
     * @throws IOException if the file does not exist
     */
    public static List<Map<String, Object>> parseFile(Path filepath) throws IOException {
        CompilationUnit cu = JavaParser.parse(filepath);
        JsonVisitor visitor = new JsonVisitor();
        List<Map<String, Object>> astNodes = new ArrayList<>();
        cu.accept(visitor, astNodes);
        return astNodes;
    }

    /**
     * @see AstGenerator#processAllFiles(Path, Path, Options)
     */
    public static void processAllFiles(Path pattern, Path output) throws IOException {
        processAllFiles(pattern, output, new Options());

    }
    /**
     * Processes all the files matched by {@code pattern}, generate the JSON AST and
     * output the result in the {@code output}
     *
     * @param pattern the pattern to search for files
     * @param output  the path where to save result
     * @param options options to parse files
     *
     * @throws IOException if the {@code output} does not point to an existing directory
     */
    public static void processAllFiles(Path pattern, Path output, Options options) throws IOException {
        FileFinder.Result filesResult = FileFinder.findFiles(pattern);

        Set<Path> files = filesResult.getFiles();
        int totalCount = files.size();
        logger.info("starting to process " + totalCount + " files");

        Path root = Paths.get("").toAbsolutePath();

        String jsonOutput = output.toString() + ".json";
        String filesOutput = output.toString() + ".txt";
        String failedOutput = output.toString() + "_failed.txt";

        final Object writeLock = new Object();

        try(PrintWriter jsonWriter = new PrintWriter(jsonOutput, "UTF-8");
            PrintWriter astWriter = new PrintWriter(filesOutput, "UTF-8");
            PrintWriter failedWriter = new PrintWriter(failedOutput, "UTF-8")) {

            AtomicInteger counter = new AtomicInteger(0);

            filesResult.getFiles().parallelStream().forEach(file -> {
                Path relativePath = root.relativize(file.toAbsolutePath());
                try {
                    List<Map<String, Object>> parsed = parseFile(file);

                    if (parsed.size() < options.minNodes) {
                        throw new RuntimeException("too few nodes");
                    }
                    if (parsed.size() > options.maxNodes) {
                        throw new RuntimeException("too many nodes");
                    }

                    String jsonAST = mapper.writeValueAsString(parsed);

                    synchronized(writeLock) {
                        jsonWriter.println(jsonAST);
                        astWriter.println(relativePath);
                    }

                    int currentCount = counter.getAndIncrement();
                    if (currentCount % 1000 == 0) {
                        logger.info("progress: " + currentCount + "/" + totalCount);
                    }
                } catch (Exception e) {
                    logger.debug("failed to parse " + file + ": " + e.getMessage());
                    failedWriter.println(relativePath + "\t" + e.getMessage());
                }
            });
        }
    }
}
