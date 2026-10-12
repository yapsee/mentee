# Day 4: Product service on PostgreSQL

**Goal:** build a relational service with versioned migrations and a safe stock purchase.

**Status:** done. 13 tests pass, including real PostgreSQL tests via Testcontainers. Endpoints are verified against `product_db`, and the service is UP in Eureka.

**Branch:** `day-04-product-service`, branched from `day-03-customer-service` because Day 3's PR isn't merged yet. **Merge Day 3 first.**

---

## 1. What was built

```
services/product/                                port 8050, PostgreSQL product_db
├── pom.xml   webmvc, data-jpa, postgresql, flyway, validation, eureka, config, lombok
│             test: testcontainers-postgresql, spring-boot-testcontainers
└── src/
    ├── main/resources/
    │   ├── application.yml                      name + config-server import
    │   └── db/migration/
    │       ├── V1__init_database.sql            tables, constraints, index
    │       └── V2__insert_data.sql              4 categories, 12 products
    ├── main/java/com/ecom/product/
    │   ├── category/  Category, CategoryRepository
    │   ├── product/   Product, ProductRepository, ProductService, ProductController,
    │   │              ProductMapper, ProductRequest/Response,
    │   │              ProductPurchaseRequest/Response
    │   ├── exception/ ProductNotFound, CategoryNotFound, InsufficientStock, InvalidPurchase
    │   └── handler/GlobalExceptionHandler       same ProblemDetail format as customer
    └── test/java/com/ecom/product/
        ├── TestcontainersConfiguration.java     throwaway Postgres for tests
        ├── ProductApplicationTests.java         context loads + Flyway runs
        └── product/
            ├── ProductServiceTest.java          5 unit tests (Mockito)
            ├── ProductControllerTest.java       4 web-layer tests
            └── ProductPurchaseIntegrationTest   3 tests on real Postgres, incl. concurrency
```

### Endpoints: `/api/v1/products`

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/` | 201 + `Location` | 400 validation, 404 unknown category |
| GET | `/` | 200, list with category name | – |
| GET | `/{id}` | 200 | 404 |
| POST | `/purchase` | 200, lines with **server-side prices** | 400 invalid/duplicate lines, 404 unknown ids, **409 not enough stock** |

`/purchase` body: `[{"productId": 1, "quantity": 2}, {"productId": 7, "quantity": 1}]`. Order-service calls it on Day 6.

---

## 2. Key concepts and design choices

### Flyway owns the schema
- `ddl-auto: validate` (set on Day 2 in `product-service.yml`) means Hibernate only **checks** that entities match the tables. It never changes the database.
- Flyway applies `V1`, then `V2`, once, and records them in `flyway_schema_history`:
  ```
  1 | init database | t
  2 | insert data   | t
  ```
- **Rule: never edit a migration that has already run.** Flyway checksums each file and refuses to start if one changed. To change the schema, add `V3__add_product_image.sql`.
- The database guards the data too: `CHECK (available_quantity >= 0)`, `CHECK (price > 0)`, foreign key to `category`, `UNIQUE` category name. Even a bug in Java can't store negative stock.

### Money is `BigDecimal` / `NUMERIC(12,2)`, never `double`
In floating point, `0.1 + 0.2 = 0.30000000000000004`. Prices must be exact.

### Purchase is all-or-nothing (`@Transactional`)
1. Reject duplicate product ids (400).
2. Load **all** requested products in **one** query, locked.
3. Unknown ids → 404, listing every missing id.
4. Check **every** line, then report all shortages together (409).
5. Only if everything passes, decrement each stock. There's no `save()`: Hibernate saves the changed entities when the transaction commits.

Any exception → rollback → no stock changes. Verified live: buying `1 × product 1` + `99 × product 6` returned 409, and product 1's stock was **not** decremented.

**Why the response includes the price:** order-service will total the order with prices from product-service, never prices sent by the browser, so a client can't buy a monitor for €1.

### N+1 queries, and how we avoided them
`Product → Category` is `LAZY`. Without care, `findAll()` runs 1 query for the products plus **1 query per product** for its category. `@EntityGraph(attributePaths = "category")` on `findAll`/`findById` loads both in **one JOIN**. Ask the intern to turn on `spring.jpa.show-sql: true` and count the queries with and without it.

### Status codes: 409 for out of stock (the plan said 400)
Following Day 3's lesson, a request for 99 monitors is well-formed, so it's not a 400. It conflicts with the current stock, which makes it a **409**. A real 400 is reserved for malformed input (quantity 0, empty list, duplicate lines). I updated the plan page to match.

---

## 3. The coach question, proven with a test

> *Two customers buy the last unit at the same moment. What happens?*

`ProductPurchaseIntegrationTest.concurrentBuyersOfTheLastUnits_neverOversell` runs **10 threads**, each buying 1 unit of a product with **3** in stock, against real PostgreSQL:

| Version | Successful purchases | Final stock |
|---|---|---|
| **Without** a lock (we removed it to check) | **10** ❌ oversold by 7 | – |
| **With** `@Lock(PESSIMISTIC_WRITE)` (committed code) | **3** ✅ | **0** |

**Why it breaks without the lock:** each transaction reads `stock = 3`, each decides "3 ≥ 1, OK", and each writes its own result. The checks and writes interleave, which is a classic *lost update*.

**The fix in the code:** `SELECT … FOR UPDATE` (`findAllByIdForUpdate`). The second buyer waits until the first commits, then reads the new stock. The ids are locked in **sorted order**, so two purchases of overlapping products can't deadlock.

**The alternative the plan mentioned, `@Version` (optimistic locking):** nobody waits. The second writer gets an `OptimisticLockException` and must retry. It's better when conflicts are rare. For stock on popular items conflicts are common, so the pessimistic lock is simpler here. Good discussion: *which would you pick for editing a product's description?* (optimistic, because conflicts are rare.)

---

## 4. Testcontainers: tests with a real database

`./mvnw package` starts a **throwaway** `postgres:16-alpine` container, runs Flyway on it, runs the tests, then destroys it. The compose database is never touched.
- **Requirement:** Docker must be running to build product-service.
- `@ServiceConnection` wires the datasource to the container automatically, with no URL or password in test config.
- Mocks can't catch locking, SQL or migration bugs. The concurrency test only means something on a real database.

Boot 4 / Testcontainers 2 note: the class is `org.testcontainers.postgresql.PostgreSQLContainer` (older tutorials use `org.testcontainers.containers.PostgreSQLContainer`), and the artifacts are `testcontainers-postgresql` and `testcontainers-junit-jupiter`.

---

## 5. How to run it

```bash
docker compose up -d
cd services/config-server && ./mvnw spring-boot:run
cd services/discovery     && ./mvnw spring-boot:run
cd services/product       && ./mvnw spring-boot:run
```

Postman: re-import `docs/postman/ecom-ms.postman_collection.json` and run the **Product** folder top to bottom (8 requests with assertions).

pgAdmin (http://localhost:5050 → *ecom-postgres* → `product_db`) shows the tables and `flyway_schema_history`.

---

## 6. Verification

| Check | Result |
|---|---|
| `./mvnw package` | **13 tests, 0 failures** (5 unit, 4 web, 3 integration, 1 context) |
| Flyway on `product_db` | V1 + V2 applied, 12 products seeded |
| GET all / by id | 200 with `categoryName`; unknown id 404 |
| POST product | 201; unknown category 404 |
| Purchase 2 × K2 + 1 × MX Master | 200, stock 40 → 38 and 50 → 49 |
| Purchase 1 × K2 + 99 × Odyssey G9 | 409 with `shortages`; **K2 stock unchanged** |
| Purchase unknown id | 404 |
| Eureka | `PRODUCT-SERVICE` UP |

Test data created during the checks was removed afterwards, and the seed stock was restored.

---

## 7. Next

**Day 5:** API gateway on 8222 routing `/api/v1/customers/**` and `/api/v1/products/**` through Eureka (`lb://`), CORS for Angular, Postman switched to the gateway, and the week-1 demo and code review. Also re-run the "Eureka down" test through the gateway to see the 503 from the last discussion.
