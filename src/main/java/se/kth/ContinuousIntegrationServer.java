package se.kth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * Skeleton of a ContinuousIntegrationServer which acts as webhook
 * See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler {
    ContinuousIntegration ci = new ContinuousIntegration(".serverbuild");
    Filesystem fileSystem = new Filesystem(".serveroutput");

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

        // for manual requests you can type http://localhost:8080/?r=https://github.com/Juliapp123/test.git&b=Fail
        if (request.getQueryString() != null) {
            buildCi(request, response);
        } else {
            showWebinterface(target, response);
        }
    }

    void buildCi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // TODO not hardcode by for example using .repository in webhook push json
        String repository = "https://github.com/Juliapp123/test.git";
        String branch = "Main";
        for (String split : request.getQueryString().split("&")) {
            String[] ab = split.split("=");
            if (ab.length < 2) {
                continue;
            }
            if (ab[0].equalsIgnoreCase("r")) {
                repository = ab[1];
            }
            if (ab[0].equalsIgnoreCase("b")) {
                branch = ab[1];
            }
        }

        Writer log = new StringWriter();

        Filesystem.BuildStatus status = ci.runContinuousIntegration(log, repository, branch, fileSystem);
        response.getWriter().write(log.toString().replace("\n", "<br>")); // nice formatting
        switch (status) {
            case SUCCESS, FAILED_TO_COMPILE, FAILED_TO_TEST -> response.setStatus(HttpServletResponse.SC_OK);
            case FAILED_SETUP -> response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    void showWebinterface(String target, HttpServletResponse response) throws IOException {
        Object directory = fileSystem.getDirectory(target);
        if (directory instanceof Filesystem.LogData) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(((Filesystem.LogData) directory).toHtml());
        } else if (directory instanceof Object[]) {
            response.setStatus(HttpServletResponse.SC_OK);
            ArrayList<File> dirs = new ArrayList<>();
            ArrayList<Filesystem.BuildData> builds = new ArrayList<>();

            for (Object obj : (Object[]) directory) {
                if (obj instanceof File) {
                    dirs.add((File) obj);
                } else if (obj instanceof Filesystem.BuildData) {
                    builds.add((Filesystem.BuildData) obj);
                }
            }

            StringBuilder buildText = new StringBuilder();

            if (!builds.isEmpty()) {
                buildText.append(Webb.buildHeader);
            }
            for (Filesystem.BuildData build : builds) {
                String statusText = switch (build.status) {
                    case SUCCESS -> "Build successful";
                    case FAILED_SETUP -> "Unable to complete job";
                    case FAILED_TO_COMPILE -> "Failed to compile";
                    case FAILED_TO_TEST -> "Failed tests";
                };
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
                Date resultdate = new Date(build.time);
                buildText.append(Webb.buildRow.formatted(build.commit, statusText, sdf.format(resultdate), fileSystem.sanitizeFilepath(new File(build.file.getParentFile(), build.commit + ".log")), build.commit));
            }

            StringBuilder buildDirs = new StringBuilder();
            if (!dirs.isEmpty()) {
                buildDirs.append(Webb.dirHeader);
            }
            for (File dir : dirs) {
                buildDirs.append(Webb.dirRow.formatted(fileSystem.sanitizeFilepath(dir), dir.getName()));
            }

            if (dirs.isEmpty() && builds.isEmpty()) {
                buildText.append(Webb.nothingFound);
            }

            String output = Webb.template.formatted(buildText.toString(), buildDirs.toString());
            response.getWriter().write(output);
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Internal server error");
        }
    }
}
