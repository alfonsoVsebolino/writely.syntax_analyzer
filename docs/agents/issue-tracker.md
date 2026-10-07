# Issue tracker: GitHub Issues

Issues for this repo live on GitHub Issues, driven by the `gh` CLI.

## Reading issues

- List open issues: `gh issue list --limit 50`
- View an issue with full details: `gh issue view <number>`
- View raw JSON representation: `gh issue view <number> --json number,title,body,labels,state`

## Status & Labels

- `ready-for-agent`: Ticket is unblocked, fully specified, and ready for agent implementation.
- `needs-attention`: Ticket has ambiguities, requires human guidance, or lacks scope details.
- `need-to-refine`: Ticket lacks concrete target specs, acceptance criteria, or passing test scenarios required before it can be accepted and satisfied.
- `blocked`: Blocked by one or more predecessor issues.
- `in-progress`: Currently being worked on.

## Semantic Blocking Protocol

- Blocking relationships between tickets are declared in issue descriptions or tracking documents.
- An issue cannot be marked `ready-for-agent` until all predecessor issues are closed/completed.
- Any conflict between `ready-for-agent` and `needs-attention` must be surfaced to the user before modification.

## Writing issues

- Create an issue: `gh issue create --title "<title>" --body "<body>" --label "<label>"`
- Add labels: `gh issue edit <number> --add-label "<label>"`
- Close an issue: `gh issue close <number> --comment "<closing comment>"`
