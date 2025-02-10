package se.kth;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

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
            String[] compile = new String[]{"mvn", "compile"};
            String compileMessage = "in compilation";
            startProcess(compile, compileMessage, buildDirectory);

            readWebhook(request);

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
        } catch (Throwable t) {
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
     * Runs all tests in a specified junit test-class and returns the results of the tests
     * 
     * @param c     The test-class containing the junit tests
     * @return      The results of the tests as a Result object
     */
    static Result runTests(Class<?> c){  
       return new JUnitCore().run(c);
    }
    
    /**
     * Runs a system command inside a specified directory. 
     * 
     * @param cmd           System command
     * @param errorMessage  Message if function crashes
     * @param directory     Specified directory where the system command runs
     */
    static void startProcess(String[] cmd, String errorMessage, String directory) throws InterruptedException, IOException {
        // spawn the process for compiling and wait
        Process process = new ProcessBuilder(cmd)
            .directory(new File(directory)) // same as "cd .serverbuild cmd"
            .start();
        if (process.waitFor() != 0) {
            throw new IOException("Error: (" + process.exitValue() + ") " + errorMessage
                    + new String(process.getErrorStream().readAllBytes()));
        }
    }

    /**
     * Prints structure of the cloned repository. From StackOverflow, remove when no longer needed.  
     * 
     * @param directory     Specified directory
     */
    static void printRepo(String directory){
        File folder = new File(directory);
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

    /**
     * Reads the payload of a HTTP message
     * 
     * @param req   The HTTP message to read
     * @return  The payload of the HTTP message as a String
     */
    static String readWebhook(HttpServletRequest req){
        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = req.getReader().readLine()) != null) {
            builder.append(line);
        }

        String text = builder.toString();
    }
}
