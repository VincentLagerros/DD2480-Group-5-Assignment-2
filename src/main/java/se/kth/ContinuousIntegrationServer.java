package se.kth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;

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
                       HttpServletResponse response) {
        response.setContentType("text/html;charset=utf-8");
        baseRequest.setHandled(true);

        // For full documentation what GitHub sends, read
        // https://docs.github.com/en/webhooks/webhook-events-and-payloads
        // We are most interested in
        // https://docs.github.com/en/webhooks/webhook-events-and-payloads#push

        try {
            String userAgent = request.getHeader("user-agent");
            if (userAgent.contains("GitHub-Hookshot")) {
                buildCi(request, response);
            } else {
                showWebinterface(target, response);
            }
        } catch (Throwable t) {
            // we do not want to crash it
            try {
                t.printStackTrace(response.getWriter());
            } catch (IOException _) {
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    void buildCi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JSONObject payload = readWebhook(request);
        JSONObject repositoryPayload = payload.getJSONObject("repository");
        String repository = repositoryPayload.getString("clone_url"); //get cloneable repo URL

        String[] repoRef = payload.getString("ref").split("/");
        String branchName = repoRef[repoRef.length - 1]; //get name of branch
        String commitId = payload.getJSONObject("head_commit").getString("id"); // get sha ID
        String ownerName = repositoryPayload.getJSONObject("owner").getString("name");
        String repoName = repositoryPayload.getString("name");
        Writer log = new StringWriter();

        // set the status of the repo to pending
        sendResponse(commitId, ownerName, repoName, Filesystem.BuildStatus.PENDING);

        Filesystem.BuildStatus status = ci.runContinuousIntegration(log, repository, ownerName, repoName, branchName, commitId, fileSystem);

        // send response to the webhook
        response.getWriter().write(log.toString());
        switch (status) {
            case SUCCESS, FAILED_TO_COMPILE, FAILED_TO_TEST -> response.setStatus(HttpServletResponse.SC_OK);
            case FAILED_SETUP -> response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            case PENDING -> throw new RuntimeException("Unreachable");
        }

        // set the status of the repo to the build status
        sendResponse(commitId, ownerName, repoName, status);
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
            builds.sort(Comparator.comparingLong(t -> t.time));

            StringBuilder buildText = new StringBuilder();

            if (!builds.isEmpty()) {
                buildText.append(Webb.buildHeader);
            }
            for (Filesystem.BuildData build : builds) {
                String statusText = switch (build.status) {
                    case SUCCESS -> "Build successful";
                    case PENDING -> "Build in progress";
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

    /**
     * Reads the payload of an HTTP message
     *
     * @param req The HTTP message to read
     * @return The payload of the HTTP message as a JSonObject if
     * payload can be read, otherwise null
     */
    static JSONObject readWebhook(HttpServletRequest req) throws IOException {
        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = req.getReader().readLine()) != null) {
            builder.append(line);
        }

        String text = builder.toString();
        return new JSONObject(text);
    }

    static void sendResponse(
            String commitId,
            String ownerName,
            String repoName,
            Filesystem.BuildStatus status
    ) {
        // Convert CI status to GitHub API state
        String state;
        String description = switch (status) {
            case SUCCESS -> {
                state = "success";
                yield "Build succeeded!";
            }
            case FAILED_TO_COMPILE -> {
                state = "failure";
                yield "Compilation failed!";
            }
            case FAILED_TO_TEST -> {
                state = "failure";
                yield "Tests failed!";
            }
            case PENDING -> {
                state = "pending";
                yield "Building in progress!";
            }
            default -> {
                state = "error";
                yield "CI setup failed!";
            }
        };
        // https://docs.github.com/en/rest/commits/statuses?apiVersion=2022-11-28#create-a-commit-status
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/statuses/%s", ownerName, repoName, commitId);

        // Construct JSON response
        JSONObject jsonResponse = new JSONObject();

        jsonResponse.put("state", state);
        jsonResponse.put("description", description);

        try {
            // Send commit status update to GitHub
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true); // Enable sending request body

            String githubToken = System.getenv("GITHUB_TOKEN");
            if (githubToken == null || githubToken.isBlank()) {
                throw new IOException("No environment variable GITHUB_TOKEN found");
            }
            connection.setRequestProperty("Authorization", "Bearer " + githubToken);

            // Write JSON data to the request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonResponse.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check GitHub API response
            int responseCode = connection.getResponseCode();
            if (responseCode != 201) { // 201 = Created (Success)
                throw new IOException("Failed to update status on GitHub, Response Code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace(); // Log errors
        }
    }
}
