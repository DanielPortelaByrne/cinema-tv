## Jira agent workflow (Claude Code and Codex)

Jira project key: `CIN`.

## Picking up work
1. Using the Atlassian MCP, find a `CIN` story in status **To Do** with the label `agent-ready` and no assignee. Skip anything labelled `blocked-on-daniel`.
2. Assign it, move it to **In Progress**, and comment the plan.
3. Branch: `CIN-123-short-slug`. Never commit to `main`.
4. Make the change, run the repo's tests/build, and open a PR titled `CIN-123: summary`, with the Jira link in the body. Use the repo's PR template.
5. Move the story to **In Review** and comment the PR URL. Do not merge; Daniel reviews.

## Rules
- One story per PR. Do not expand scope; file a new story instead.
- Anything touching money movement, credentials or health data: stop and comment on the story.
- Stories are not labelled `agent-ready` until Daniel triages them.
