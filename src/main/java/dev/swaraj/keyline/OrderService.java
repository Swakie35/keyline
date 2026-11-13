package dev.swaraj.keyline;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Small concurrency-safe order lifecycle with optimistic version checks. */
public final class OrderService {
    public enum Status { CREATED, CONFIRMED, FULFILLED }
    public record Order(String id, String customer, long amountPence, Status status, long version) {}
    private record IdempotentCreate(String fingerprint, String orderId) {}
    private final Map<String, Order> orders = new HashMap<>();
    private final Map<String, IdempotentCreate> idempotency = new HashMap<>();

    public synchronized Order create(String customer, long amountPence, String key) {
        if (customer == null || customer.isBlank() || customer.length() > 120) throw new IllegalArgumentException("customer is required (max 120 characters)");
        if (amountPence <= 0 || amountPence > 100_000_000) throw new IllegalArgumentException("amountPence must be between 1 and 100000000");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key header required");
        String fingerprint = customer + "\u0000" + amountPence;
        IdempotentCreate seen = idempotency.get(key);
        if (seen != null) {
            if (!Objects.equals(seen.fingerprint(), fingerprint)) throw new Conflict("Idempotency key used with different order data");
            return orders.get(seen.orderId());
        }
        Order order = new Order(UUID.randomUUID().toString(), customer, amountPence, Status.CREATED, 1);
        orders.put(order.id(), order);
        idempotency.put(key, new IdempotentCreate(fingerprint, order.id()));
        return order;
    }

    public synchronized Order get(String id) {
        Order order = orders.get(id);
        if (order == null) throw new NotFound("Order not found");
        return order;
    }

    public synchronized Order transition(String id, long expectedVersion, Status next) {
        Order old = get(id);
        if (old.version() != expectedVersion) throw new Conflict("Version mismatch; reload before updating");
        if (!((old.status() == Status.CREATED && next == Status.CONFIRMED) ||
              (old.status() == Status.CONFIRMED && next == Status.FULFILLED)))
            throw new Conflict("Illegal order transition");
        Order updated = new Order(old.id(), old.customer(), old.amountPence(), next, old.version() + 1);
        orders.put(id, updated);
        return updated;
    }

    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
