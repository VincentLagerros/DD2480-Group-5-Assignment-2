package se.kth;

import org.apache.commons.io.FileUtils;

import java.io.*;

/**
 * This is the main class for running the compiler or cloning the repository.
 */
public class ContinuousIntegration {
    String buildDirectory;

    ContinuousIntegration(String buildDirectory) {
        this.buildDirectory = buildDirectory;
    }

    // windows is a bit weird sometimes
    // https://stackoverflow.com/questions/58713148/how-to-fix-createprocess-error-2-the-system-cannot-find-the-file-specified-ev
    static String mvn = System.getProperty("os.name").toLowerCase().contains("windows") ? "mvn.cmd" : "mvn";

    /**
     * @param log The logger
     * @return The commit hash of the head of the project
     */
    String getCommitId(Writer log) throws ProcessException, IOException, InterruptedException {
        String commitId = startProcess("in git rev-parse", buildDirectory, "git", "rev-parse", "HEAD").trim();
        log.append("Commit = ").append(commitId);
        return commitId;
    }

    /**
     * @param log The logger
     * @return If the project can be compiled
     */
    boolean compileProject(Writer log) throws IOException, InterruptedException {
        log.append("\n==== Starting mvn compile ====\n");
        try {
            log.append(startProcess("in compilation", buildDirectory, mvn, "compile"));
            return true;
        } catch (ProcessException e) {
            return false;
        }
    }

    /**
     * @param log The logger
     * @return If the test was successful
     */
    boolean testProject(Writer log) throws IOException, InterruptedException {
        log.append("\n==== Running tests ====\n");
        try {
            log.append(startProcess("tests could not start", buildDirectory, mvn, "test"));
            return true;
        } catch (ProcessException e) {
            log.append(e.stdout);
            log.append("\n==== Test failed ====\n");
            log.append(e.stderr);
            return false;
        }
    }

    /**
     * @param log        Logger responsible
     * @param repository https repository GitHub url
     * @param branch     the specified repository branch url
     * @return status
     */
    Filesystem.BuildStatus runContinuousIntegration(
            Writer log,
            String repository,
            String branch,
            String commitId,
            String ownerName,
            String repoName,
            Filesystem filesystem
    ) {
        Filesystem.BuildStatus status = Filesystem.BuildStatus.SUCCESS;
        //String commitId = "";

        try {
            cloneRepository(log, repository, branch, buildDirectory);
            //commitId = getCommitId(log);

            if (!compileProject(log)) {
                status = Filesystem.BuildStatus.FAILED_TO_COMPILE;
            } else if (!testProject(log)) {
                status = Filesystem.BuildStatus.FAILED_TO_TEST;
            }
        } catch (Throwable t) {
            status = Filesystem.BuildStatus.FAILED_SETUP;
            t.printStackTrace(new PrintWriter(log));
        } finally {
            // write the logfile if we have a commit id
            try {
                filesystem.saveToFile(repository, branch, commitId, log.toString(), status);
            } catch (IOException e) {
                e.printStackTrace(new PrintWriter(log));
                // no error should happen
                e.printStackTrace();
            }
        }
        return status;
    }


    /**
     * Clones a repository into the cwd/directory
     *
     * @param url       The https url of the git repo
     * @param branch    The branch of the git repo, e.g. Main
     * @param directory The output folder directory, e.g. build
     */
    static void cloneRepository(
            Writer log,
            String url,
            String branch,
            String directory
    ) throws InterruptedException, IOException, ProcessException {
        try {
            // just delete the file directory in case it exists for a clean git clone, this
            // is easier than git pull
            FileUtils.deleteDirectory(new File(directory));
        } catch (Throwable t) {
            // ignore if we can even delete it or not
        }

        // spawn the process for git cloning and wait, this is easier than importing a
        // lib
        log.append("\n==== Starting cloning repository ====\n");
        log.write(startProcess("in git clone due to: ", null, "git", "clone", url, "-b", branch, directory));
    }

    /**
     * Runs a system command inside a specified directory.
     *
     * @param cmd          System command
     * @param errorMessage Message if function crashes
     * @param directory    Specified directory where the system command runs
     * @return returns the stdout log
     */
    static String startProcess(String errorMessage, String directory, String... cmd) throws InterruptedException, IOException, ProcessException {
        // spawn the process for compiling and wait
        ProcessBuilder builder = new ProcessBuilder(cmd);
        if (directory != null) {
            builder.directory(new File(directory));
        }

        // this is needed because waitFor may get stuck if the stdout is not processed
        Process process = builder.start();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder stdout = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stdout.append(line);
            stdout.append('\n');
        }
        reader.close();

        if (process.waitFor() != 0) {
            throw new ProcessException(
                    stdout.toString(),
                    new String(process.getErrorStream().readAllBytes()),
                    process.exitValue(),
                    errorMessage);
        }
        return stdout.toString();
    }
}
