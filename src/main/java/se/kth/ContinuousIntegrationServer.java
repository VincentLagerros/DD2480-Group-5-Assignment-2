package se.kth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONObject;

/**
 * Skeleton of a ContinuousIntegrationServer which acts as webhook
 * See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler {
    static String buildDirectory = ".serverbuild";
    static String outputDirectory = ".serveroutput";

    enum BuildStatus {
        SUCCESS,
        FAILED_SETUP,
        FAILED_TO_COMPILE,
        FAILED_TO_TEST,
    }

    // windows is a bit weird sometimes
    // https://stackoverflow.com/questions/58713148/how-to-fix-createprocess-error-2-the-system-cannot-find-the-file-specified-ev
    static String mvn = System.getProperty("os.name").toLowerCase().contains("windows") ? "mvn.cmd" : "mvn";

    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html;charset=utf-8");
        baseRequest.setHandled(true);
        // For full documentation what GitHub sends, read
        // https://docs.github.com/en/webhooks/webhook-events-and-payloads
        // We are most interested in
        // https://docs.github.com/en/webhooks/webhook-events-and-payloads#push

        // What we can also do it use target for the webserver to host content. Eg
        // target == index.html responds with html
        System.out.println(target);

        // TODO not hardcode by for example using .repository in webhook push json
        String repository = "https://github.com/Juliapp123/test.git";
        String branch = "Fail";
        Writer log = new StringWriter();

        BuildStatus status = runContinuousIntegration(log, repository, branch);
        response.getWriter().write(log.toString().replace("\n", "<br>")); // nice formatting
        switch (status) {
            case SUCCESS, FAILED_TO_COMPILE, FAILED_TO_TEST -> response.setStatus(HttpServletResponse.SC_OK);
            case FAILED_SETUP -> response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param log The logger
     * @return The commit hash of the head of the project
     */
    static String getCommitId(Writer log) throws ProcessException, IOException, InterruptedException {
        String commitId = startProcess("in git rev-parse", buildDirectory, "git", "rev-parse", "HEAD").trim();
        log.append("Commit = ").append(commitId);
        return commitId;
    }

    /**
     * @param log The logger
     * @return If the project can be compiled
     */
    static boolean compileProject(Writer log) throws IOException, InterruptedException {
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
    static boolean testProject(Writer log) throws IOException, InterruptedException {
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
     * @return status, 0 = all tests passed, 1 = unknown worker error, 2 = failed to compile, 3 = failed tests
     */
    static BuildStatus runContinuousIntegration(
            Writer log,
            String repository,
            String branch
    ) {
        String repositoryFilename = repository.replace("https://github.com/", "").replace(".git", "") + "/" + branch;
        File logFile = null;
        File statusFile = null;
        String commitId = "";

        BuildStatus status = BuildStatus.SUCCESS;
        File outputFile = new File(outputDirectory, repositoryFilename);
        Boolean _ = outputFile.mkdirs();

        try {
            cloneRepository(log, repository, branch, buildDirectory);
            commitId = getCommitId(log);

            logFile = new File(outputFile, commitId + ".log");
            statusFile = new File(outputFile, commitId + ".json");

            if (!compileProject(log)) {
                status = BuildStatus.FAILED_TO_COMPILE;
            } else if (!testProject(log)) {
                status = BuildStatus.FAILED_TO_TEST;
            }
        } catch (Throwable t) {
            status = BuildStatus.FAILED_SETUP;
            t.printStackTrace(new PrintWriter(log));
        } finally {
            // write the logfile if we have a commit id
            try {
                if (logFile != null) {
                    PrintWriter writer = new PrintWriter(logFile.getPath());
                    writer.write(log.toString());
                    writer.close();
                }
                // if we have a status file we can write to, then write what information we have
                if (statusFile != null) {
                    PrintWriter writer = new PrintWriter(statusFile.getPath());
                    JSONObject jo = new JSONObject();
                    jo.put("commit", commitId);
                    jo.put("time", System.currentTimeMillis());
                    jo.put("status", status);
                    writer.write(jo.toString(4));
                    writer.close();
                }
            } catch (Exception _) {
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
