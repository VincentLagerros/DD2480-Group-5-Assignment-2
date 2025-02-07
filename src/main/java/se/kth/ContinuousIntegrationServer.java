package se.kth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * Skeleton of a ContinuousIntegrationServer which acts as webhook
 * See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler {
    static String buildDirectory = ".serverbuild";

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

        try {
            // 1st clone your repository

            // TODO not hardcode by for example using .repository in webhook push json
            String repository = "git@github.com:Juliapp123/test.git";
            String branch = "main";
            //String file = ".serverbuild/Main.java";

            cloneRepository(repository, branch, buildDirectory);
            printRepo(buildDirectory);
           
            // 2nd compile the code
            compileRepository();

            response.getWriter().println("CI job done");
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Throwable t) {
            // in case of exception, just print it to both stderr and response
            t.printStackTrace(System.err);
            t.printStackTrace(response.getWriter());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Clones a repository into the cwd/directory
     *
     * @param url       The https url of the git repo
     * @param branch    The branch of the git repo, e.g. Main
     * @param directory The output folder directory, e.g. build
     */
    static void cloneRepository(String url, String branch, String directory) throws InterruptedException, IOException {
        try {
            // just delete the file directory in case it exists for a clean git clone, this
            // is easier than git pull
            FileUtils.deleteDirectory(new File(directory));
        } catch (Throwable _) {
            // ignore if we can even delete it or not
        }

        // spawn the process for git cloning and wait, this is easier than importing a
        // lib
        Process process = new ProcessBuilder("git", "clone", url, "-b", branch, directory).start();
        if (process.waitFor() != 0) {
            throw new IOException("Bad exitcode (" + process.exitValue() + ") in git clone due to: "
                    + new String(process.getErrorStream().readAllBytes()));
        }
    }

    /**
     * Compiles the cloned repository
     *
     * @param file      the cloned file to compile
     */
    static void compileRepository() throws InterruptedException, IOException {
        // spawn the process for compiling (and running?) and wait
        Process process = new ProcessBuilder("mvn", "compile")
            .directory(new File(buildDirectory)) // same as  "cd .serverbuild mvn compile" have to go to the correct dir
            .start();
        if (process.waitFor() != 0) {
            throw new IOException("Error: (" + process.exitValue() + ") in complilation"
                    + new String(process.getErrorStream().readAllBytes()));
        }
    }

    static void printRepo(String buildDirectory){
        File folder = new File(buildDirectory);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    System.out.println("File " + listOfFiles[i].getName());
                } else if (listOfFiles[i].isDirectory()) {
                    System.out.println("Directory " + listOfFiles[i].getName());
                }
            }
        }

    }
    

}
