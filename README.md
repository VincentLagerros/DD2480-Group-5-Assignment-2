# DD2480-Group-5-Assignment-2

1. Install ngrok via https://ngrok.com/downloads
2. Run via `ngrok http http://localhost:8080`
3. Copy and paste the token forwarding url of form `https://a-b-c-d-e.ngrok-free.app` to in /settings/hooks
4. Change the `Content type` to application/json in the new webhook
5. Generate a new GITHUB_TOKEN in https://github.com/settings/tokens/new and check `repo:status`
6. Run with your preferred IDE with the environment variable GITHUB_TOKEN set

You can then try it out at http://localhost:8080/ after running for an interactive web user interface.