# Day 5: API gateway and week-1 review

**Goal:** put a single public entry point in front of the private network, then review the week's work end to end.

**Status:** done. The gateway routes all services through Eureka, CORS is ready for Angular, the full Postman collection passes through the gateway (17 requests, 18 assertions, 0 failures), and the code review findings are below.

**Branch:** `day-05-gateway`, branched from `day-04-product-service` (merge Day 4 first).

---

## 1. What was built

```
services/gateway/                       Spring Cloud Gateway (reactive, Netty), port 8222
├── pom.xml   gateway-server-webflux, eureka-client, config-client, actuator
└── src/main/resources/application.yml  name + config-server import

services/config-server/.../configurations/
├── gateway-service.yml                 ← routes, CORS, retry, trusted proxies
└── application.yml                     + server.forward-headers-strategy (all services)

docs/postman/ecom-ms.postman_collection.json   now calls {{gatewayUrl}} only
```

There's no Java code beyond the generated main class. A gateway is configuration.

### Routes

| Path | Goes to | Status today |
|---|---|---|
| `/api/v1/customers/**` | `lb://CUSTOMER-SERVICE` | ✅ |
| `/api/v1/products/**` | `lb://PRODUCT-SERVICE` | ✅ |
| `/api/v1/orders/**`, `/api/v1/order-lines/**` | `lb://ORDER-SERVICE` | 503 until Day 6 |
| `/api/v1/payments/**` | `lb://PAYMENT-SERVICE` | 503 until Day 7 |
| anything else | – | 404 |

`lb://NAME` means: ask Eureka for the instances registered as `NAME` and load-balance between them. No host or port appears anywhere in the gateway config.

**Debug tip:** http://localhost:8222/actuator/gateway/routes lists the active routes. Check it first when a route returns 404.

### CORS
Only `http://localhost:4200` (Angular, Day 9) may call the gateway from a browser:

| Preflight from | Result |
|---|---|
| `http://localhost:4200` | 200, `Access-Control-Allow-Origin: http://localhost:4200` |
| `http://evil.example` | **403** |

`Location` is in `exposed-headers` so the Angular app can read it after a POST. CORS is configured **only on the gateway**. The services never see the browser.

---

## 2. Two problems found and fixed today

### Bug: `Location` header leaked the private service address
After `POST /api/v1/customers` through the gateway, the response said `Location: http://localhost:8090/...`. That's the private port, not the gateway's.

- **Cause, part 1:** services build URLs from the request they receive, and they receive it from the gateway, not from the client.
- **Fix, part 1:** `server.forward-headers-strategy: framework` in the shared `application.yml`, so every service builds URLs from the `X-Forwarded-*` headers.
- **Cause, part 2:** it still didn't work. Since a 2025 security hardening, Spring Cloud Gateway **doesn't send `X-Forwarded-*` at all** unless `trusted-proxies` is configured. We found it in the gateway's configuration metadata. Older tutorials don't mention it.
- **Fix, part 2:** `trusted-proxies` set to local and private-network addresses only.
- **Result:** `Location: http://localhost:8222/api/v1/customers/…` ✅. A client sending a fake `X-Forwarded-Host: evil.example` still gets the gateway URL. The Postman collection now asserts it.

### A crashed instance caused errors for about 30 s, so we added retries
See §3, last test.

---

## 3. The coach question, tested live

> *Start a second product-service on another port. What does the gateway do with the traffic?*

```bash
cd services/product && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8051
```
A command-line port overrides config-server because of `override-system-properties: false` (Day 2).

| Test | Result |
|---|---|
| Eureka | 2 × `PRODUCT-SERVICE` UP (8050 and 8051) |
| 20 GETs via gateway | **10 → 8050, 10 → 8051** (round-robin) |
| Kill 8051, then 10 GETs immediately (no retry) | `200 500 200 500 …`: **half fail** |
| Same, 45 s later | all 200 |
| Kill 8051, 10 GETs, **with the Retry filter** | **all 200** |
| POSTs right after the kill, with Retry | still alternate 400/500: POSTs are **not** retried, on purpose |

**Why half fail:** the gateway keeps its own copy of the Eureka registry and refreshes it every 30 s. For that window it still sends every second request to the dead instance.

**Why only GET is retried:** GET is *idempotent*, meaning doing it twice is harmless. Retrying a `POST /orders` whose first attempt actually succeeded but whose response got lost would **create the order twice**. That's a key idea for Day 6–8.

---

## 4. "What if Eureka is down?" (follow-up from Day 3)

| Situation | Result |
|---|---|
| Eureka down, gateway already running | **200**: the gateway uses its cached registry |
| Eureka down, **gateway restarted** | **503 Service Unavailable**: empty cache, it can't resolve `PRODUCT-SERVICE` |
| Same moment, product called directly on 8050 | 200: the service itself is fine |
| Eureka back | gateway routes again after about 60 s, with no restart |

**Takeaway for production:** run Eureka with **several instances**. It's a single point of failure for anything that starts while it's down.

---

## 5. Week-1 code review

Findings from reviewing Days 1–5 together. ✅ means fixed during the week.

| # | Finding | Severity | Status |
|---|---|---|---|
| 1 | `Location` header exposed private service ports | Bug | ✅ fixed today (§2) |
| 2 | `optional:configserver:` made services start silently on port 8080 | Bug | ✅ fixed Day 2 |
| 3 | Overselling under concurrent purchases | Bug | ✅ fixed Day 4 (pessimistic lock + test) |
| 4 | Customer unique email is check-then-insert, so two parallel requests can both pass | Minor | Open. Exercise: add a unique index on `email` and map `DuplicateKeyException` to 409. |
| 5 | Services are still reachable directly (8090, 8050), bypassing the gateway | Design | Open. Fixed on Day 10: in docker-compose, only the gateway publishes a port. |
| 6 | Gateway errors (503, 500) use Spring's default JSON, not our `ProblemDetail` format | Minor | Open. Stretch: a gateway error handler. |
| 7 | `GlobalExceptionHandler` is duplicated in each service | Accepted | Intentional. A shared library would couple service releases together. Duplication between services is cheaper than coupling. |
| 8 | Any config change needs a config-server rebuild + restart (`classpath:` location) | Dev friction | Open. Option: switch `search-locations` to a `file:` path, or do the Git-backed bonus from Day 2. |
| 9 | Naming, packages and error format are consistent across customer and product | ✅ Good | – |
| 10 | Each service has unit, web-slice and (product) real-DB tests | ✅ Good | – |

---

## 6. How to run it

```bash
docker compose up -d
cd services/config-server && ./mvnw spring-boot:run   # 1
cd services/discovery     && ./mvnw spring-boot:run   # 2
cd services/customer      && ./mvnw spring-boot:run   # 3
cd services/product       && ./mvnw spring-boot:run   # 4
cd services/gateway       && ./mvnw spring-boot:run   # 5
```

Wait about 30 s after the services start, until they show in Eureka, before calling the gateway. Otherwise you get a 503.

Postman: re-import the collection and run everything. It only uses `{{gatewayUrl}}` now. Command-line alternative:
```bash
npx newman run docs/postman/ecom-ms.postman_collection.json
```

---

## 7. Week-1 demo script (15 min, from a cold start)

1. `docker compose up -d`, then `docker compose ps`: all healthy.
2. Start config-server, then show `curl localhost:8888/customer-service/default`.
3. Start discovery, customer, product and gateway, then show the Eureka dashboard with 3 services.
4. Run the Postman collection via the gateway: all green.
5. In pgAdmin, show the stock dropping after a purchase. In mongo-express, show the customer document with its embedded address.
6. Explain one decision the intern is proud of, and one bug they fixed.

---

## 8. Next

**Day 6:** order-service (`order_db`, port 8070). It calls customer-service (`/exists/{id}`) with OpenFeign and product-service (`/purchase`) with RestClient. The gateway route already exists, so `/api/v1/orders` will go from 503 to 201.
