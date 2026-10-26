# Day 7: Payment service and Kafka producers

**Goal:** complete the order flow with a payment, then publish both confirmations asynchronously.

**Status:** done. payment-service has 9 tests (including real Postgres + real Kafka via Testcontainers) and order-service has 13. Both events reach Kafka, the coach question was demonstrated live, and a real bug was found and fixed (§5).

**Branch:** `day-07-payment-kafka`, from `master` after Day 6 was merged (PR #5).

---

## 1. What was built

```
services/payment/                              port 8060, PostgreSQL payment_db, Kafka producer
├── db/migration/V1__init_database.sql         payment (order_reference UNIQUE)
└── java/com/ecom/payment/
    ├── payment/       Payment, PaymentStatus, PaymentMethod, PaymentService (idempotent),
    │                  PaymentController, PaymentRequest/Response, CustomerDto, PaymentMapper
    ├── notification/  PaymentConfirmation (event), PaymentNotificationProducer
    ├── config/        KafkaTopicConfig (payment-topic), JpaAuditingConfig
    └── handler/, exception/

services/order/ (changed)
├── customer/CustomerClient     existsById → findById (we need name + email now)
├── payment/PaymentClient       NEW, OpenFeign → payment-service
├── kafka/                      NEW: OrderConfirmation (event), OrderProducer, KafkaTopicConfig (order-topic)
└── order/OrderService          + steps 5 (pay) and 6 (publish)

config-server: order-service.yml + payment-service.yml   Kafka producer settings
docs/postman: + Payment folder (4 requests)
```

### The full order flow now

```
POST /api/v1/orders  (gateway → order-service)
 1. customer-service   GET  /customers/{id}            Feign       404 → "Customer not found"
 2. product-service    POST /products/purchase         RestClient  stock taken, prices back
 3. total = Σ price × qty
 4. order_db           save order + lines
 5. payment-service    POST /payments                  Feign       payment saved
        └─► Kafka  payment-topic  PaymentConfirmation   (published by payment-service)
 6. Kafka  order-topic  OrderConfirmation               (published by order-service)
 → 201
```
Steps 1–5 are **synchronous**: the client waits, and every step can make the order fail. Step 6 and the payment event are **asynchronous**: nobody waits for the emails.

### What actually landed on Kafka (one real order)

```
payment-topic  partition 1  key ORD-20261024-94A935  __TypeId__: paymentConfirmation
{"orderReference":"ORD-20261024-94A935","amount":1278.98,"paymentMethod":"PAYPAL",
 "customerFirstname":"Ada","customerLastname":"Lovelace","customerEmail":"ada.day7@example.com"}

order-topic    partition 1  key ORD-20261024-94A935  __TypeId__: orderConfirmation
{"orderReference":"ORD-20261024-94A935","totalAmount":1278.98,"paymentMethod":"PAYPAL",
 "customer":{"id":"6adc…","firstname":"Ada","lastname":"Lovelace","email":"ada.day7@example.com"},
 "products":[{"productId":4,"name":"Dell U2723QE",…,"price":579.00,"quantity":1},
             {"productId":10,"name":"Sony WH-1000XM5",…,"price":349.99,"quantity":2}]}
```

---

## 2. Kafka concepts, as seen in this code

| Concept | Here |
|---|---|
| **Topic** | `order-topic`, `payment-topic`, created at startup by `NewTopic` beans (`KafkaTopicConfig`) |
| **Partition** | 3 per topic. The key decides the partition, so both events of an order landed on **partition 1** |
| **Key** | the order reference: all events of one order stay **in order** on one partition |
| **Value** | JSON, by `JacksonJsonSerializer` (Spring Kafka 4 / Jackson 3; tutorials say `JsonSerializer`, now deprecated) |
| **`__TypeId__` header** | `orderConfirmation`, a **token**, not a Java class name (`spring.json.type.mapping`). The consumer maps it to its own class, so no Java code is shared between services |
| **Contract** | the JSON fields. Rule: add fields freely, **never rename or remove** one, because consumers break |
| **Retention** | 7 days (`retention.ms=604800000`). Messages stay even after they're read |

**The event carries everything the email needs** (customer, products, prices). Notification-service will never call order, customer or product services. If it had to, it would be coupled to all of them being up.

---

## 3. Idempotent payments

`payment.order_reference` is **UNIQUE**, and `PaymentService.createPayment`:
- returns the **existing** payment with **200** (not 201) when the order was already paid, and publishes **nothing** new
- handles the race (two simultaneous requests for one order): the DB constraint lets one insert win, and the loser gets the winner's payment instead of a 500

Verified live: re-sending the payment for `ORD-20261024-94A935` returned the same `PAY-20261024-26CEB4`, with 1 row in the DB and 1 Kafka message (also covered by `PaymentKafkaIntegrationTest`).

**Why it matters:** order-service's log now says "Retry the payment for this order" on failure, and that retry is safe. This is the Day 5 lesson (only retry what's idempotent), applied by *making* a POST idempotent.

---

## 4. The coach question, demonstrated live

> *Notification is down for an hour. What happens to the messages, and what happens when it comes back?*

notification-service doesn't exist yet (Day 8), so a console consumer in group `notification-standin` played its role:

| Step | Result |
|---|---|
| "Notification" starts, reads from the beginning | 7 messages read, **lag 0** |
| "Notification" is down; 2 orders placed | both **201**: ordering doesn't depend on notification at all |
| While down | `kafka-consumer-groups --describe` shows **lag = 2** |
| "Notification" restarts (no `--from-beginning`) | receives **exactly the 2 missed** orders, then **lag 0** |

**Answer:** nothing is lost. Kafka keeps messages for 7 days whether they're read or not, and stores each **consumer group's offset** (how far it has read). A restarted consumer resumes from its offset. The producer never even knew the consumer was down. That's the whole point of async messaging.

**Gotcha found during the demo:** a *brand-new* consumer group starts at the **end** of the topic by default (`auto.offset.reset=latest`), so it sees nothing old. Notification-service on Day 8 must decide: `earliest` (process history) or `latest` (only new events). The topics currently contain test orders from today, so `earliest` would email them.

---

## 5. Bug found and fixed: "Kafka down" made a successful payment look failed

**Test:** stop the Kafka container and place an order.

**Before the fix:** after 5 s the client got **503 "payment-service is not reachable"**, but:

| | State |
|---|---|
| product_db | stock taken |
| order_db | order saved |
| payment_db | payment **SUCCEEDED** |
| order log | `INCONSISTENCY: … payment FAILED` ← **false** |

The customer would think the order failed and might order again, **paying twice**.

**Cause:** when the broker is unreachable, `kafkaTemplate.send()` doesn't return a failed future. It **throws** after `max.block.ms` (5 s). The exception escaped `PaymentService`, so payment-service answered 500, which order-service turned into 503.

**Fix:** both producers catch the exception and log `FAILED to publish … no email will be sent`. A lost notification must never hide a completed payment. Regression tests: `PaymentNotificationProducerTest`, `OrderProducerTest`.

**After the fix:** with Kafka down, the order returns **201** (in 11 s: two 5 s waits), the payment is SUCCEEDED, and both lost events are logged. With Kafka back, the next order publishes normally.

### What's still not solved: the dual write
We write to the DB **and** to Kafka, and nothing makes both happen or neither. Today a Kafka outage means **lost emails**, logged but not retried. The production answer is the **transactional outbox**: save the event in an `outbox` table *in the same DB transaction* as the payment, and let a background job (or Debezium) publish it once Kafka is back. That's a great stretch topic. We kept the simple version for the bootcamp.

---

## 6. Verification

| Check | Result |
|---|---|
| `./mvnw package` payment | **9 tests**: 3 service, 3 web, 1 producer, 1 Postgres+Kafka integration, 1 context |
| `./mvnw package` order | **13 tests** (+1 service test: failed payment publishes nothing, +1 producer test) |
| Topics | `order-topic`, `payment-topic`, 3 partitions each, auto-created |
| Order of 1 × Dell + 2 × Sony | 201, total **1278.98**, payment SUCCEEDED, 1 message per topic with the same key and partition |
| Pay the same order again | **200**, same payment, no new row, no new message |
| Invalid payment | 400 with `amount` and `customer.email` |
| Kafka down (after fix) | order 201, payment SUCCEEDED, `FAILED to publish` logged |
| Postman, whole collection | **30 requests, 35 assertions, 0 failures** |

Dev databases were reset afterwards: no orders, no payments, seed stock, no test customers. The Kafka topics still hold today's test events (see §4, gotcha).

---

## 7. Next

**Day 8:** notification-service (MongoDB, port 8040). It consumes both topics with `@KafkaListener` (groups `orderGroup` and `paymentGroup`), maps the `__TypeId__` tokens to its own classes, stores a `Notification` document, and sends HTML emails with Thymeleaf to MailDev (http://localhost:1080).
