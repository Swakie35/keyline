# Keyline

### An order service where duplicate requests and concurrent updates actually matter.

Keyline is a Java 21 HTTP backend built around two everyday distributed-systems constraints: **idempotent creates** and **optimistic concurrency**. Rather than using a database and framework for a small demo, this implementation keeps the state machine inspectable from the Java types all the way to the API surface.

## What it implements
- Create orders with an `Idempotency-Key`: identical requests replay safely; changed requests under the same key return HTTP 409.
- Explicit `CREATED → CONFIRMED → FULFILLED` transitions and a monotonic `version`.
- `If-Match` is required on state changes; stale writes return HTTP 409.
- Java 21 records, synchronized domain invariants, virtual-thread HTTP executor, consistent JSON error responses.
- Tests for replay, invalid state transitions, concurrency checks, live HTTP and health.

## Run (JDK 21, no Maven required)
```bash
./run-tests.sh
java -cp build/classes dev.swaraj.keyline.Server 8080
```
In another terminal:
```bash
curl -i -X POST 'http://127.0.0.1:8080/orders?customer=Sam&amountPence=4500' -H 'Idempotency-Key: cart-123'
# Copy the returned id, then:
curl -i -X POST 'http://127.0.0.1:8080/orders/ORDER_ID/confirm' -H 'If-Match: 1'
curl -i 'http://127.0.0.1:8080/orders/ORDER_ID'
```

## Boundary
In-memory state resets on restart; no database persistence, authentication, TLS, or multi-node consistency. A production implementation would use transactional storage, a unique idempotency-key index, replay-safe response records, proper API schemas, structured metrics and bounded resource limits. **This is an inspectable backend reference—not a deployed payment system.**

## Container build
`docker build -t keyline .` then `docker run --rm -p 8080:8080 keyline`. The local default binds to 127.0.0.1; the Docker image explicitly sets `BIND_HOST=0.0.0.0` for container networking. Do not expose an unauthenticated demo on a public port.
