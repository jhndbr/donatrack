# List of Issues

This document tracks bugs discovered during development, testing, or production. Each issue follows the Issue Resolution Mode in `00-sftdd-workflow.md`.

---

## How to Add an Issue

Add a new entry with **title and description only**. The AI handles all metadata.

```markdown
## [Number]. [Issue Title]
**Description**: [What went wrong — include error messages, steps to reproduce, or unexpected behavior]
```

The AI will automatically add:
- Scenarios (GWT) — the regression test: *Given [state where bug appears], When [action], Then [expected behavior]*
- Status, timestamps, root cause, resolution details
- Triage decision (separate / merge / promote to use case)
- Related use case link and test coverage

---

## Issues

<!-- Add your issues below this line -->

---

## Example Issues (delete when you start)

### 1. API returns 500 error on invalid input
**Description**: When user submits form with empty email field, API returns 500 instead of 400 with validation error

**Scenarios (GWT)**:
- Given a form with an empty email field, when the form is submitted, then the API returns 400 with a validation error

**Status**: Resolved
**Related Use Case**: Use Case #3 (Form validation)
**Timestamp**: 2026-03-05 10:00
**Started Resolution**: 2026-03-05 10:05
**Resolved**: 2026-03-05 10:30

**Root Cause**:
- Missing null check in email validation function
- Exception thrown instead of returning validation error

**Resolution**:
- Added null check before email validation
- Return 400 status with error message for empty fields
- Test coverage: `test_form_validation_empty_email`

---

### 2. Database connection timeout in production
**Description**: Application crashes after 30 seconds with "Connection timeout" error when connecting to database
**Status**: Resolved
**Related Use Case**: Use Case #1 (Database initialization)
**Timestamp**: 2026-03-05 11:00
**Started Resolution**: 2026-03-05 11:10
**Resolved**: 2026-03-05 11:45

**Root Cause**:
- Default connection timeout was 30 seconds
- Production database requires longer timeout due to network latency

**Resolution**:
- Increased connection timeout to 60 seconds
- Added retry logic with exponential backoff
- Test coverage: `test_database_connection_timeout`, `test_database_connection_retry`

---

## Tips

- **Be specific**: Include error messages, stack traces, or steps to reproduce
- **One issue at a time**: AI resolves issues sequentially
- **Link to use cases**: Mention the related use case in your description if you know it
