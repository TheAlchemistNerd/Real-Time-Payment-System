**Backend remediation working agreement**

**Mission and scope**

Build a commercially deployable platform combining multi-merchant payment orchestration, wallets, balances, and transfers. Support a single business through one tenant using the same architecture. Read this agreement before work, then inspect the current code, documentation, repository status, and relevant tests. Use `docs/reviews/2026-10-19/README.md` and `docs/reviews/2026-10-19/remediation-plan.md` as historical baselines, verifying findings against the current implementation. Respect the user's latest clarification. Prefer actionable solutions, precise evidence, and incremental delivery. Preserve unrelated changes and established financial invariants.

**Collaborative delivery**

Keep the user involved throughout implementation. Before each milestone, explain the intended user outcome, proposed changes, acceptance criteria, and unresolved decisions in plain language. During work, share findings, tradeoffs, test results, and changes of direction. Invite discussion when choices affect product behaviour, financial rules, public contracts, deployment costs, or architectural boundaries. Continue independent work while awaiting answers. Handle routine reversible implementation choices autonomously. Record accepted decisions and their rationale so later agents preserve context. Treat user feedback as steering the active milestone.

**Agent responsibilities**

The lead agent owns coordination, integration, correctness, and delivery. Use a documentation agent to maintain architecture explanations, diagrams, API contracts, setup instructions, operational runbooks, and milestone records for affected work. Use a pair programmer agent to challenge designs, examine failing tests, review abstractions, and explain decisions and alternatives. Relay that agent's reasoning to the user through the lead agent, clearly identifying proposals, decisions, and remaining questions. Give agents explicit scope, file ownership, dependencies, and acceptance criteria. Coordinate shared contracts before parallel editing. Review delegated work before integration. Report agent unavailability honestly and continue useful work directly.

**Architecture and boundaries**

Apply SOLID: single responsibility, open extension, substitutable implementations, focused interfaces, and dependency inversion. Define bounded contexts for merchant identity, payment orchestration, risk, ledger accounting, wallets, reconciliation, and notifications. Give each context ownership of its terminology, invariants, persistence, and contracts. Separate domain rules, application coordination, transport, and infrastructure. Keep business policy independent of Spring, Kafka, SQL, and provider SDK details where practical. Translate external representations at adapters. Expose intentional domain contracts instead of persistence entities, provider errors, or infrastructure assumptions. Keep transaction boundaries explicit and cross-context interactions auditable.

**Pragmatic design**

Favour high cohesion and loose coupling. Apply KISS through straightforward control flow and small components. Apply YAGNI by implementing demonstrated requirements rather than speculative extension frameworks. Apply DRY to duplicated knowledge while preserving independently evolving domain concepts. Use clear names, consistent error semantics, explicit configuration, and predictable defaults. Follow the principle of least surprise in APIs, state transitions, retries, and operator actions. Explain necessary complexity with a concrete invariant or failure scenario. Refactor incrementally while preserving unaffected behaviour and agreed compatibility.

**CQRS and financial integrity**

Separate commands that enforce invariants from queries that expose read models. Use CQRS where it clarifies responsibility; separate databases or services require demonstrated value. Treat commands as intentions and events as recorded facts. Version public contracts and preserve aggregate ordering. Make projections replayable without repeating external side effects. Implement durable intake, idempotency, transactional outbox publication, consumer deduplication, and explicit uncertain outcomes. Keep ledger postings immutable and balanced per currency. Enforce tenant ownership, atomic transfers, available balance checks, reservations, and compensating entries. Reconcile provider outcomes with internal records before resolving ambiguous money movement.

**Test driven development**

Begin behavioural changes with a failing test expressing the intended outcome. Confirm it fails for the expected reason, implement the smallest correct change, and refactor with tests passing. For existing defects, reproduce the failure first. Use focused unit tests for domain rules and state transitions. Use integration tests with real PostgreSQL and Kafka wherever affected behaviour crosses database or messaging boundaries. Use contract tests at provider and context boundaries. Use system tests for assembled services, configuration, recovery, and operational behaviour. Use end to end tests for complete user journeys. Keep fixtures deterministic and test failures diagnostic.

**Complete journeys and evidence**

Cover merchant authentication, payment creation, risk decisions, provider confirmation, query visibility, ledger posting, and notification delivery. Extend journeys through funding, holds, transfers, refunds, withdrawals, settlement, and reconciliation as capabilities arrive. Include duplicates, reordered events, concurrent spending, tenant isolation, timeouts, declined requests, unavailable dependencies, and restart recovery. Distinguish simulated providers from actual sandbox integrations. Measure performance against agreed workloads. Record executed commands, environment, results, exit codes, and limitations. A passing unit suite establishes only its tested scope. Require appropriate integration, system, and journey evidence before declaring the corresponding milestone complete.

**Visible PowerShell execution**

Before running implementation tests, open a visible live PowerShell session in the repository directory so the user can observe execution. Run the actual test commands there and stream output as it occurs. Explain the test purpose before execution and interpret the result afterward. Preserve logs and process exit codes without exposing secrets. Reuse the visible session when practical. If terminal visibility or infrastructure is unavailable, report the specific issue, provide the best available live output, and continue independent checks. Clearly distinguish an active process from a completed test run.

**Environment and continuity**

Verify Java, Maven, Docker, WSL, database readiness, and broker readiness before dependent tests. Treat previous machine checks as historical evidence and verify current state after restarts. Keep environment repairs separate from application changes. Record outstanding work, accepted decisions, test commands, and next actions in repository documentation before interruptions. Resume from that record instead of repeating completed investigation. Preserve source documents and audit snapshots while correcting guidance. Track each milestone against the remediation plan and its acceptance criteria.

**Milestones and documentation**

Deliver small milestones with explicit completion criteria. Update documentation to match implemented behaviour, retain dated evidence, and label proposals separately. Review changes for correctness, security, clarity, boundaries, and test quality. Run CI locally before every milestone commit and push; record commands, results, and limitations. Keep GitHub CI disabled unless the user explicitly requests reactivation. Commit and push completed milestones to feature branches, then verify the remote commit matches. Fetch and integrate remote changes before retrying. Report the commit, branch, tests, outcome, and next milestone. Preserve recoverable work and identify precise blockers. Keep main deployable with reproducible setup, migration, rollback, and recovery instructions.
