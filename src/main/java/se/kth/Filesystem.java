package se.kth;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This handles the virtual file system used for build history
 */
public class Filesystem {
    String outputDirectory;

    Filesystem(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    enum BuildStatus {
        SUCCESS,
        PENDING,
        FAILED_SETUP,
        FAILED_TO_COMPILE,
        FAILED_TO_TEST,
    }

    /**
     * .log files, used to store the build log
     */
    public static class LogData {
        String log;
        File file;

        LogData(File file) throws IOException {
            this.log = readFileToEnd(file);
            this.file = file;
        }

        LogData(File file, String log) {
            this.log = log;
            this.file = file;
        }

        void writeToFile() throws FileNotFoundException {
            try (PrintWriter writer = new PrintWriter(this.file.getPath())) {
                writer.write(this.log);
            }
        }

        @Override
        public String toString() {
            return this.log;
        }

        String toHtml() {
            return this.log.replace("\n", "<br>");
        }
    }

    /**
     * .json files, used to store the metadata about each build
     */
    public static class BuildData {
        BuildStatus status;
        String commit;
        Long time;
        File file;

        BuildData(File file, BuildStatus status, String commit) {
            this.file = file;
            this.time = System.currentTimeMillis();
            this.commit = commit;
            this.status = status;
        }

        BuildData(File file) throws IOException {
            JSONObject json = new JSONObject(readFileToEnd(file));
            this.file = file;
            this.time = json.getLong("time");
            this.commit = json.getString("commit");
            this.status = json.getEnum(BuildStatus.class, "status");
        }

        JSONObject toJson() {
            JSONObject jo = new JSONObject();
            jo.put("commit", commit);
            jo.put("time", System.currentTimeMillis());
            jo.put("status", status);
            return jo;
        }

        void writeToFile() throws FileNotFoundException {
            try (PrintWriter writer = new PrintWriter(this.file.getPath())) {
                writer.write(toJson().toString(4));
            }
        }
    }

    /**
     * Save the current metadata to storage
     */
    public void saveToFile(
            String ownerName,
            String repoName,
            String branchName,
            String commitId,
            String log,
            BuildStatus status
    ) throws IOException {
        File ownerFile = new File(outputDirectory, ownerName);
        File repoFile = new File(ownerFile, repoName);
        File branchFile = new File(repoFile, branchName);

        Boolean _ = branchFile.mkdirs();

        BuildData dataFile = new BuildData(new File(branchFile, commitId + ".json"), status, commitId);
        LogData logFile = new LogData(new File(branchFile, commitId + ".log"), log);

        dataFile.writeToFile();
        logFile.writeToFile();
    }

    /**
     * @param file Input file path
     * @return A virtual file path without the outputDirectory prefix
     */
    public String sanitizeFilepath(File file) {
        Path root = new File(outputDirectory).toPath();
        Path path = file.toPath();
        assert path.startsWith(root);
        return path.subpath(root.getNameCount(), path.getNameCount()).toString();
    }

    /**
     * Given the target directory, shows the content
     *
     * @param target eg name/repo/branch/commit.log
     * @return If the file is a Log, then returns a LogData, otherwise returns an Array of type BuildData or File, where
     * BuildData is a single build and File is a Directory.
     */
    public Object getDirectory(String target) throws IOException {
        File root = new File(outputDirectory);
        for (String subdirectory : target.split("/")) {
            root = new File(root, subdirectory);
        }
        if (!root.exists()) {
            throw new FileNotFoundException();
        } else if (root.isFile()) {
            if (!FilenameUtils.getExtension(root.getName()).equalsIgnoreCase("log")) {
                // Cant read non log files, as that is a security issue
                throw new FileNotFoundException();
            }
            return new LogData(root);
        }
        try (Stream<Path> stream = Files.list(root.toPath())) {
            return stream.map(Path::toFile).filter(x -> x.isDirectory() || (x.isFile() && FilenameUtils.getExtension(x.getName()).equalsIgnoreCase("json"))).map(item -> {
                try {
                    return item.isDirectory() ? item : new BuildData(item);
                } catch (Exception e) {
                    // Json errors
                    e.printStackTrace();
                    return null;
                }
            }).filter(Objects::nonNull).toArray();
        }
    }

    /***
     * Helper function to read an entire file as a string
     * @param file The input file that should be read
     * @return The content of the file
     */
    public static String readFileToEnd(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line = reader.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = reader.readLine();
            }
            return sb.toString();
        }
    }
}
