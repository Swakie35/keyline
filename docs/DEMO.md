# Reproducible local walkthrough

This walkthrough does not require production data or cloud deployment. Run the commands at this repository root.

## Prerequisites

Java JDK 21; the repository uses the standard library and a Bash test runner.

## Setup and test

```bash
bash run-tests.sh
```

## Demonstrate the main path

```bash
bash run-tests.sh
```

## What to look for

- Observe the console or generated report; **do not substitute copied/synthetic screenshots for actual runtime output**.
- For algorithms, inspect tests around boundary conditions and compare behavior with the README.
- For Java demos, the test runner exercises the HTTP service or core worker behavior; the README includes manual server commands.
- Open `docs/ARCHITECTURE.md` to explain design trade-offs and limits.

## Interpretation

Data is process-local and disappears on restart. No authentication, durable storage, TLS termination or multi-node consistency. Bind locally when experimenting.

If a command fails, capture the exact error and runtime version rather than claiming the repository is verified.
