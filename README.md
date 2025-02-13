# DD2480-Group-5-Assignment-2


# Dependencies
1. junit
2. jetty-all
3. servlet-api
4. ommons-io
5. json


# How to use the server
1. Install ngrok via https://ngrok.com/downloads
2. Run via `ngrok http http://localhost:8080`
3. Copy and paste the token forwarding url of form `https://a-b-c-d-e.ngrok-free.app` to in /settings/hooks
4. Change the `Content type` to application/json in the new webhook
5. Generate a new GITHUB_TOKEN in https://github.com/settings/tokens/new and check `repo:status`
6. Run with your preferred IDE with the environment variable GITHUB_TOKEN set

You can then try it out at http://localhost:8080/ after running for an interactive web user interface.


# How to start and test the server
1. Run mvn clean compile
2. Run mvn test


# Implementation of the compilation of the remote repo
Uses ProcessBuilder() to run commands in the remote repo (mvn compile), this means that it relies on system programs instead of libraries. Crerated a method startProcess() that handels this.

We test this by cloning a valid and an invalid repository and confirming that cloneRepository() either throws an error or not.  



# Implementation of test of the remote repo and test
Uses ProcessBuilder() to run commands in the remote repo (mvn clean test). this means that it relies on system programs instead of libraries. Crerated a method startProcess() that handels this.

We test this by cloning a valid and an invalid repository and confirming the boolean result from the testProject() method. 


# Implementation of notifications to the remote repo 
1. Set status of the response depending on the boolean answer from cloning, compiling, and testing the remote repository.
2. Format the API URL with information from the webhook.
3. Send POST with all information to GitHub API to set the commit status.

We test this by sending valid and invalid github information, such as SHA (commitId), repo, ownerName. If valid the sendResponse() true, otherwise false.  


# Contributions
We worked a lot with pair programming and in groups, the work division was equal but the commit history may not accurately reflect this.
- Erik: Worked with testing the remote repository, testing the server, and reading information from webhooks.
- Julia:  Worked with a compilation of the remote repository and sent a response to Git Hub.
- Rasmuss: Worked with compilation of the remote repository and sending responses to Git Hub.
- Vincent: Did the Init commit, cloning of the remote repository, and the web UI.
- Remarkable contribution: Cross-platform, works on Mac and Windows. We are also proud that we implemented pending status for notifications.
