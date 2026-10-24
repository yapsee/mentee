# Day 6: Order service with synchronous calls

**Goal:** place an order by orchestrating customer-service and product-service over HTTP.

**Status:** done. 11 tests pass. The full order flow works through the gateway, every error path was checked live, and the coach question was reproduced live (§4).

**Branch:** `day-06-order-service`, from `master` after Day 5 was merged (PR #4).

---

## 1. What was built

```
services/order/                              port 8070, PostgreSQL order_db
├── pom.xml   webmvc, data-jpa, postgresql, flyway, validation, openfeign,
│             restclient, eureka, config, lombok  +  testcontainers (test)
└── src/main/
    ├── resources/db/migration/V1__init_database.sql    customer_order + order_line
    └── java/com/ecom/order/
        ├── order/      Order, PaymentMethod, OrderRepository, OrderService,
        │               OrderController, OrderMapper, OrderRequest/Response
        ├── orderline/  OrderLine, OrderLineRepository, OrderLineController, OrderLineResponse
        ├── customer/   CustomerClient            ← OpenFeign (declarative)
        ├── product/    ProductClient             ← RestClient (by hand)
        │               PurchaseRequest/Response   ← our copy of product-service's contract
        ├── exception/  BusinessException, RemoteErrors, OrderNotFoundException
        ├── handler/    GlobalExceptionHandler    same ProblemDetail format
        └── config/     FeignConfig (error decoder), JpaAuditingConfig

config-server/.../order-service.yml     + application.config.product-url
config-server/.../gateway-service.yml   order-lines route merged into order-service
docs/postman/...                        + Order folder (9 requests)
```

### Endpoints

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/v1/orders` | 201 + `Location` | 400 invalid, 404 unknown customer/product, 409 out of stock, 503 a service is down |
| GET | `/api/v1/orders` | 200 | – |
| GET | `/api/v1/orders/{id}` | 200 | 404 |
| GET | `/api/v1/order-lines/order/{orderId}` | 200 | 404 |

Request body:
```json
{ "customerId": "6ad4f6…", "paymentMethod": "VISA",
  "products": [ { "productId": 1, "quantity": 2 }, { "productId": 7, "quantity": 1 } ] }
```
There's no price and no total in it. The client can't choose what it pays.

### `POST /orders`, step by step

```
OrderController ─► OrderService.createOrder()            (NOT @Transactional, see below)
   1. CustomerClient.existsById(customerId)    ── Feign ─► customer-service  GET /exists/{id}
        false → 404 "Customer not found" (product-service never called)
   2. ProductClient.purchaseProducts(lines)    ── RestClient ─► product-service POST /purchase
        stock is decremented in product_db; prices come back
        409/404 from product-service → passed to the client as-is
   3. total = Σ price × quantity                (server-side prices)
   4. orderRepository.save(order + lines)       one short local transaction (cascade)
   5. TODO Day 7: payment + Kafka OrderConfirmation
```

---

## 2. Design choices to explain

### Feign vs RestClient: the same job, two styles

| | `CustomerClient` (OpenFeign) | `ProductClient` (RestClient) |
|---|---|---|
| Code | an **interface** with `@GetMapping`. Spring writes the implementation | a class building each request by hand |
| Finding the service | `@FeignClient(name = "customer-service")`, resolved via Eureka | URL `http://product-service/...` + `LoadBalancerInterceptor`, resolved via Eureka |
| Errors | a global `ErrorDecoder` (FeignConfig) | `.onStatus(...)` in the call |
| Good for | many simple endpoints | full control (headers, streaming, special cases) |

Both hosts are **Eureka service ids**, not real hostnames. Spring Cloud LoadBalancer swaps them for a running instance, the same way `lb://` works in the gateway. Neither client goes through the gateway: internal calls stay inside the private network.

### Errors from other services keep their meaning
Our services all answer errors as `ProblemDetail`. `RemoteErrors` reads the error from product-service and re-throws it as a `BusinessException`, so the client sees:
```json
{ "status": 409, "title": "Insufficient stock",
  "shortages": ["Samsung Odyssey G9 (id 6): requested 99, available 3"],
  "instance": "/api/v1/orders" }
```
Rules:
- A **4xx** from them keeps its status, title, detail and extra fields. Their `instance` path is dropped, because it's their URL, not ours.
- A **5xx** from them, or no answer, becomes **503 Service unavailable** with no internals. Their bug isn't the client's fault, and their stack traces must not leak.

### No `@Transactional` around remote calls
A DB transaction holds a connection (and possibly locks) for its whole duration. Waiting for two HTTP calls inside it ties up a database connection for nothing, and **it still can't roll back the other services**. So only the final `save` is transactional.

### No foreign keys to customers or products
`customer_order.customer_id` and `order_line.product_id` point into **other services' databases**. The DB can't enforce them, which is why step 1 checks the customer exists, and step 2 makes product-service validate the ids.

### Other details
- **Table `customer_order`**, because `ORDER` is an SQL keyword.
- **Reference** `ORD-20261018-045D9C`: readable in emails and support calls, and the DB enforces uniqueness.
- **JPA auditing** (`@CreatedDate`, `@LastModifiedDate`) fills the dates automatically.
- **Gateway clean-up** (from last week's question): `order-lines-service` was only a route *name*. The two paths now share the single `order-service` route.

---

## 3. Boot 4 surprise caught by the context test

`OrderApplicationTests.contextLoads` failed with `No qualifying bean of type 'RestClient$Builder'`. In Boot 4 the `RestClient.Builder` auto-configuration moved into its own module, so you need **`spring-boot-starter-restclient`**. Initializr didn't add it, since we didn't select "RestClient". This is why the "useless" `contextLoads` test exists: without it, the error would have appeared only at startup.

---

## 4. The coach question, reproduced live

> *The purchase succeeded but saving the order failed. What state is the system in now?*

We broke `order_db` with a temporary trigger (`RAISE EXCEPTION` on insert), then placed an order for 2 × Sony headphones:

| | Before | After |
|---|---|---|
| Response | – | **500** |
| Sony stock (product_db) | 18 | **16** |
| Orders (order_db) | 1 | **1** |

**The stock is gone and no order exists.** Each service rolled back what *it* could. product-service had already committed its own transaction, and nothing can roll back across two databases. The trigger was removed and the stock restored by hand afterwards.

**What we did today:** the service now logs an alert with everything needed to fix it by hand:
```
ERROR … INCONSISTENCY: stock purchased but order NOT saved. customer=6ad4f6…,
lines=[PurchaseRequest[productId=10, quantity=2]]. Release this stock manually.
```

**The real fixes (discussion for the intern):**
1. **Compensation (saga):** on failure, call a `POST /products/release` that adds the stock back. But what if *that* call fails too?
2. **Reorder the steps:** save the order first as `PENDING`, then purchase, then mark it `CONFIRMED`. A failure leaves a visible `PENDING` order that a job can retry or cancel, instead of invisible lost stock.
3. **Events plus an outbox:** the Day 7–8 direction. Record "what must happen" in our own DB in the same local transaction, and let a message broker deliver it reliably.

There are no distributed transactions, only patterns for living without them.

---

## 5. Verification (live, through the gateway)

| # | Scenario | Result |
|---|---|---|
| 1 | 2 × K2 (89.99) + 1 × MX Master (99.99) | **201**, total **279.97**, `Location: http://localhost:8222/api/v1/orders/1`, stock 40→38, 50→49 |
| 2 | Order lines | `[{productId 1, qty 2}, {productId 7, qty 1}]` |
| 3 | Unknown customer | 404 `Customer not found` |
| 4 | 1 × K2 + 99 × Odyssey G9 | 409 with product-service's `shortages`; **K2 stock unchanged** |
| 5 | Unknown product | 404 from product-service, passed through |
| 6 | Empty customer id, empty products | 400 with both field errors |
| 7 | `paymentMethod: CASH` | 400 `Malformed request` |
| 8 | product-service stopped | **503** `product-service is not reachable` (immediately and 40 s later) |
| 9 | customer-service stopped | **503** `A required service is not reachable` |
| 10 | Postman, whole collection | **26 requests, 29 assertions, 0 failures** |
| 11 | `./mvnw package` | **11 tests**: 3 service, 3 web, 3 remote-error, 1 Postgres integration, 1 context |

Dev databases were reset to the seed state afterwards: no orders, original stock, no test customers.

---

## 6. Open points

- **Retry on customer `exists`:** it's a GET and safe to retry once, like the gateway does. **Never** retry `/purchase` automatically: it isn't idempotent, so a retry after a lost response would buy the stock twice. The fix for that is an *idempotency key*, which makes a good stretch topic.
- **Circuit breaker** (Resilience4j): if product-service is down, stop calling it for 30 s and fail fast instead of waiting each time. This was a stretch goal in the plan.
- The 503 for customer-service is generic ("A required service"), while product's names the service. Small polish: name the service in the Feign handler too.

---

## 7. Next

**Day 7:** payment-service (`payment_db`, 8060). Order calls it after saving, then the first **Kafka** producers: `order-topic` (OrderConfirmation) and `payment-topic` (PaymentConfirmation).
