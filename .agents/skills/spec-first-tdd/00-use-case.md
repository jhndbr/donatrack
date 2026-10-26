# Project: [Your Project Name]

## Project Context

**Description**: [Brief description of what your application does. Example: A CLI tool that processes user data and generates reports.]

**Tech Stack**:
- **Language**: [e.g., Python 3.11+, JavaScript ES6+, Java 17+]
- **Framework**: [e.g., Click (CLI), Express.js (API), Spring Boot]
- **Testing**: [e.g., pytest, Jest, JUnit]
- **Database**: [e.g., PostgreSQL, MongoDB, DynamoDB, or "JSON file" for simple storage]
- **Dependencies**: [e.g., Pillow, axios, lombok]
- **Deployment**: [e.g., Docker, AWS Lambda, Kubernetes]

**Telemetry**:
- **Telemetry**: [e.g., OpenTelemetry / Native SDK (Datadog, X-Ray, etc.) / None]
- **Test Telemetry Harness**: [e.g., InMemorySpanExporter / Mock / N/A]

**Architecture**:
- [e.g., CLI-based application]
- [e.g., RESTful API with microservices]
- [e.g., Event-driven architecture with message queues]
- [e.g., Serverless functions with API Gateway]

**Development Environment**:
- **OS**: [e.g., macOS, Linux, Windows]
- **Runtime**: [e.g., Python 3.11+, Node.js 18+, JDK 17+]
- **Package Manager**: [e.g., pip, npm, maven]
- **Test Command**: [e.g., `PYTHONPATH=. pytest`, `npm test`, `mvn test`]

**Session Settings**:
- **Learning Mode**: OFF (toggle ON to add `# AI:` teaching comments to all code and tests)
- **Observability**: OFF (toggle ON to include telemetry contracts — traces, metrics, events — in BDD scenarios; requires `00-observability-conventions.md`)
- **Collaboration Mode**: Human-in-the-loop (default) or Autopilot

---

## Use Cases

### 1. [First Use Case Title]
**Description**: [Brief description of what this use case should accomplish. Example: User should be able to authenticate with email and password.]

<!-- AI adds all metadata below. Do not edit manually. -->
<!--
**Scenarios (GWT)**:
- Given [context], When [action], Then [expected outcome]
- Given [context], When [action], Then [expected outcome]

**Status**: Pending
**Priority**: [High / Medium / Low]
**Last Phase Completed**: None
**Current Phase**: None
**Test Coverage**: 0 tests
**Comprehension**: Gates passed: 0/3 | Last gap: None (Learning Mode only)
**Last Updated**: YYYY-MM-DD HH:MM
-->

---

## Tips

- **Keep use cases atomic**: Each use case should be small enough to fit in a single function or class
- **One at a time**: Process use cases sequentially, not in parallel
- **Spec-first**: Always start with the SPEC phase — decompose the use case into Given/When/Then scenarios and get them approved before writing any test
- **Observability-first**: If the project needs telemetry, set up observability infrastructure as the first use case before feature use cases include telemetry contracts
- **Human-in-the-loop**: Review and approve each phase before moving to the next
- **Abort / Skip**: Tell AI to "Skip to Refactor" or "Abandon this use case" at any point
