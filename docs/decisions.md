# Design notes — keyline

**Why this exists.** An HTTP order lifecycle API with optimistic concurrency, validation and idempotency.

The default implementation is intentionally self-contained so reviewers can run it without private services, paid accounts or unpublished data. The README names its limitations; this project is an engineering reference, not a claim about production deployment.
