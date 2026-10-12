# Day 3: Customer service on MongoDB

**Goal:** ship the first domain service with clean layering, validation and consistent errors. It's the template every later service copies.

**Status:** done. 9 tests pass, all 6 endpoints are verified against the real MongoDB, and the service registers in Eureka.

**Branch:** `day-03-customer-service`

---

## 1. What was built

```
services/customer/                          port 8090 (from config-server)
├── pom.xml       web(mvc), data-mongodb, validation, eureka-client, config-client, lombok
└── src/
    ├── main/java/com/ecom/customer/
    │   ├── CustomerApplication.java
    │   ├── customer/                       ← everything about the Customer feature
    │   │   ├── Customer.java               @Document("customers")
    │   │   ├── Address.java                embedded in Customer
    │   │   ├── CustomerRequest.java        record + validation   (API in)
    │   │   ├── CustomerResponse.java       record                (API out)
    │   │   ├── AddressDto.java             record + validation
    │   │   ├── CustomerMapper.java         entity ↔ DTO
    │   │   ├── CustomerRepository.java     MongoRepository + existsByEmail
    │   │   ├── CustomerService.java        business rules
    │   │   └── CustomerController.java     HTTP only
    │   ├── exception/                      CustomerNotFoundException, EmailAlreadyUsedException
    │   └── handler/GlobalExceptionHandler  one error format for the whole service
    ├── main/resources/application.yml      name + config-server import, nothing else
    └── test/
        ├── resources/application.yml       tests run without config-server / Eureka
        └── java/.../customer/
            ├── CustomerServiceTest.java    5 unit tests (Mockito)
            └── CustomerControllerTest.java 3 web-layer tests (@WebMvcTest)
docs/postman/ecom-ms.postman_collection.json   Customer folder, 9 requests with assertions
```

### Endpoints: `/api/v1/customers`

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/` | **201** + `Location` header + body | 400 validation, 409 email already used |
| PUT | `/{id}` | 200 + updated body | 400, 404, 409 |
| GET | `/` | 200 list | – |
| GET | `/{id}` | 200 | 404 |
| GET | `/exists/{id}` | 200 `true`/`false` | – (used by order-service on Day 6) |
| DELETE | `/{id}` | **204** | 404 |

### The request flow, layer by layer

```
HTTP JSON ─► CustomerController   validates (@Valid), maps HTTP ↔ Java, picks status codes
               └─► CustomerService     business rules: email must be unique, id must exist
                     ├─► CustomerMapper     CustomerRequest → Customer → CustomerResponse
                     └─► CustomerRepository Spring Data writes the "customers" collection
Exception anywhere ─► GlobalExceptionHandler ─► ProblemDetail JSON
```

Each layer has one job. The controller has no `if`s about business, and the service knows nothing about HTTP.

---

## 2. Design choices to explain to the intern

**Address is embedded, not a separate collection.** The domain model says Customer–Address is 1:1, and an address is never read without its customer. MongoDB stores it inside the customer document, so one read returns everything:
```json
{ "_id": "6ac8…", "firstname": "Ada", "email": "ada@example.com",
  "address": { "street": "Main Street", "houseNumber": "12", "zipCode": "75001" } }
```

**DTOs (records) at the API, entity inside.** The API never exposes `Customer` itself, so:
- the client can't set fields it shouldn't (like `id`)
- the DB model can change without breaking API clients
- validation rules live on the request, not on the stored data

**Errors use the RFC 9457 `ProblemDetail` standard**, built into Spring. Every error has the same shape:
```json
{ "status": 400, "title": "Validation failed", "detail": "One or more fields are invalid",
  "instance": "/api/v1/customers",
  "errors": { "firstname": "Firstname is required", "email": "Email is not valid",
              "address.houseNumber": "House number is required" } }
```
Nested fields show up as `address.houseNumber` because `@Valid` on the address cascades validation into it.

**Unique email is checked in the service** (409 Conflict). This is a check-then-write, so two simultaneous requests could both pass it. The bulletproof version is a unique index in MongoDB. It's a good stretch exercise, and it ties into Day 4's concurrency question.

**The test pyramid, small version:**
- **Unit tests** (`CustomerServiceTest`) cover business rules with a mocked repository. No Spring, so they run in 0.15 s.
- **Web slice tests** (`@WebMvcTest`) cover routing, JSON, validation and the error format, with the service mocked by `@MockitoBean`.
- **`contextLoads`** checks that the whole Spring context wires together.

---

## 3. Spring Boot 4 differences (tutorials are mostly Boot 3)

| Boot 3 tutorials say | Boot 4 (what we use) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `@MockBean` | `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) |
| `spring.data.mongodb.uri` | `spring.mongodb.uri` |
| `com.fasterxml.jackson.databind.ObjectMapper` | Jackson 3: `tools.jackson.databind.ObjectMapper` (we avoided it in tests by writing JSON as text blocks) |

---

## 4. How to run it

```bash
docker compose up -d                                     # Mongo must be running
cd services/config-server && ./mvnw spring-boot:run      # 1
cd services/discovery     && ./mvnw spring-boot:run      # 2
cd services/customer      && ./mvnw spring-boot:run      # 3
```

Then import `docs/postman/ecom-ms.postman_collection.json` into Postman. Run the **Customer** folder top to bottom with the Collection Runner. Every request has a test assertion, and *Create customer* saves the id for the following requests.

---

## 5. Verification

| Check | Result |
|---|---|
| `./mvnw package` | **9 tests, 0 failures**, BUILD SUCCESS |
| Config loaded | started on **8090** (from `customer-service.yml`) |
| POST valid | 201, `Location: …/api/v1/customers/6ac8dd7a…` |
| POST same email | 409 `Email already used` |
| POST invalid | 400 with `firstname`, `email`, `address.houseNumber` errors |
| GET / PUT / GET all | 200, update changed lastname and address |
| exists | `true` for the real id, `false` for an unknown one |
| DELETE, then GET | 204, then 404 |
| MongoDB | database `customer`, collection `customers` (in mongo-express: http://localhost:8081) |
| Eureka | `CUSTOMER-SERVICE` **UP** on 8090 at http://localhost:8761 |

---

## 6. Day 2's open question, tested live: "What if Eureka dies?"

| Step | Observed |
|---|---|
| Stop discovery | customer-service keeps answering requests (200) |
| Its log | heartbeat errors every 30 s (`Cannot execute request on any known server`). Noisy but harmless. |
| Start discovery again | customer-service **re-registers by itself** after about 45 s. No restart needed. |

Also seen: right after the service starts, the Eureka dashboard and API stay empty for up to **30 s**. That's normal. Registration is asynchronous, and the Eureka server caches its responses. Don't debug this as a bug.

---

## 7. Coach checkpoint

**Learn topics covered:** document vs relational modelling, DTO vs entity, meaningful HTTP status codes.

**Question:** *Why embed Address inside Customer instead of using a separate collection?*

**Expected answer:** it's a 1:1 relation and the address is always read together with its customer, so embedding means one read and no joins (MongoDB has no real joins). Its lifecycle also follows the customer: delete the customer and the address goes too. You'd use a separate collection if addresses were shared between customers, read on their own, or could grow without limit (e.g. a full address history).

**Follow-up for a strong intern:** *why 409 and not 400 for a duplicate email?* Because the request itself is valid. It conflicts with the current state of the server.

---

## 8. Next

**Day 4:** product-service on PostgreSQL (`product_db`, port 8050), with Flyway migrations, seed data and an atomic `/purchase` endpoint. Copy today's layout: `product/` package, records, mapper, `GlobalExceptionHandler`, test resources.
