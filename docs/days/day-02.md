# Day 2: Config Server and Eureka discovery

**Goal:** centralise configuration and give services a registry so they can find each other by name.

**Status:** done. Both servers build, their tests pass, and they run together.

---

## 1. What was built

```
services/
├── config-server/                         Spring Cloud Config Server, port 8888
│   └── src/main/resources/
│       ├── application.yml                port + "native" profile
│       └── configurations/                ← config for EVERY service lives here
│           ├── application.yml            shared: Eureka URL, actuator, tracing
│           ├── discovery.yml              8761, registry settings
│           ├── gateway-service.yml        8222
│           ├── customer-service.yml       8090 + MongoDB "customer"
│           ├── product-service.yml        8050 + Postgres product_db
│           ├── order-service.yml          8070 + Postgres order_db
│           ├── payment-service.yml        8060 + Postgres payment_db
│           └── notification-service.yml   8040 + MongoDB "notification" + MailDev SMTP
└── discovery/                             Netflix Eureka Server, port 8761
    ├── src/main/resources/application.yml name + config-server address, nothing else
    └── src/test/resources/application.yml tests run without config-server
```

**Stack generated with Spring Initializr:** Spring Boot **4.1.1**, Spring Cloud **2025.1.3**, Java **21**, Maven wrapper included in each project.

| Project | Dependencies | Key code |
|---|---|---|
| config-server | `spring-cloud-config-server` | `@EnableConfigServer` |
| discovery | `spring-cloud-starter-netflix-eureka-server`, `spring-cloud-starter-config` | `@EnableEurekaServer` |

### How configuration flows

```
discovery starts
  └─ reads its own application.yml: name=discovery, import=configserver:http://localhost:8888
       └─ GET http://localhost:8888/discovery/default
            └─ config-server merges configurations/application.yml + configurations/discovery.yml
                 └─ discovery gets server.port=8761 and its Eureka settings
```

A service's file overrides the shared `application.yml`. Every future service follows the same pattern: its own `application.yml` holds only `spring.application.name` and `spring.config.import`.

### Design choices to explain

- **`native` profile.** The config server reads files from its own classpath instead of a Git repo. It's simple for learning. The bonus task below moves it to Git, as in the architecture diagram.
- **Placeholders with defaults.** For example, `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/product_db`. Locally the defaults apply. On Day 10, Docker sets `DB_HOST=postgres` and `DB_PORT=5432` without touching any file. The config server sends placeholders unresolved, and each client resolves them.
- **`ddl-auto: validate`** is already set for the Postgres services. Flyway will own the schema (Day 4).
- **Tracing sampling** is already set to `1.0` in the shared file, ready for Zipkin on Day 10.
- **Spring Boot 4 rename:** the MongoDB URI property is now `spring.mongodb.uri` (it was `spring.data.mongodb.uri` in Boot 3). Online tutorials still use the old name, so watch for it on Day 3.

---

## 2. How to run it

Infrastructure from Day 1 isn't needed for today, but it does no harm if it's running.

```bash
# terminal 1
cd services/config-server && ./mvnw spring-boot:run
# terminal 2, once terminal 1 says "Started"
cd services/discovery && ./mvnw spring-boot:run
```

In IntelliJ, open each project's `pom.xml` as a Maven project (right-click → *Add as Maven Project*) and run the two `*Application` classes from the **Services** tool window.

---

## 3. Verification

| Check | Command / URL | Result |
|---|---|---|
| Both build and tests pass | `./mvnw package` in each project | BUILD SUCCESS |
| Config served | http://localhost:8888/customer-service/default | JSON with `customer-service.yml` and the shared `application.yml` |
| Discovery got its config | discovery log: `Located environment: name=discovery` then `Tomcat started on port 8761` | OK |
| Eureka dashboard | http://localhost:8761 | 200, no instances yet (expected until Day 3) |

---

## 4. The coach question, tested live

> *What happens to a running service if config server dies? If Eureka dies?*

We tried it rather than guessing:

| Scenario | What happened |
|---|---|
| Config server stops while discovery is **running** | Discovery keeps working. Config is read at **startup** only. |
| Discovery **restarts** while config server is down, with `optional:configserver:` | ⚠️ It started on **port 8080** with default settings and logged only a WARN. It's broken but looks healthy. |
| Same, with `configserver:` (no `optional:`) | ✅ It refuses to start: `ConfigClientFailFastException: Could not locate PropertySource and the resource is not optional`. |

**Decision:** we removed `optional:`. A service with the wrong config is worse than a service that doesn't start. **Rule for every service from Day 3: never use `optional:` for the config server import.** On Day 10, Docker healthchecks plus `depends_on` guarantee config-server starts first.

**If Eureka dies:** running services keep calling each other, because every client keeps a **local cache** of the registry. New instances can't register, and the cache slowly goes stale. We'll see it live on Day 3 when customer-service registers.

---

## 5. Machine setup done today

- Installed **Temurin JDK 21.0.12** to `~/Library/Java/JavaVirtualMachines/` (no admin rights needed). The system default is still Corretto 17, so either:
  - set the **Project SDK to 21** in IntelliJ (File → Project Structure), or
  - in the terminal: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`
- Maven no longer matters, because every project uses its own `./mvnw`.

---

## 6. Problems hit and fixes

| Problem | Cause | Fix |
|---|---|---|
| Discovery test printed registration stack traces | The test context tried to register with Eureka and to reach config-server | `src/test/resources/application.yml` disables both for tests |
| Discovery silently started on 8080 | `optional:` config import | Removed `optional:` (see §4) |

The log line `Failed to set up a Bean Validation provider` during tests is INFO only and harmless, because these two servers have no validation.

---

## 7. Not done / next

- **Bonus (optional for the intern):** move `configurations/` to a Git repository and switch the config server from `native` to `git` (`spring.cloud.config.server.git.uri`). Then a config change becomes a commit, with history and review.
- **Day 3:** customer-service on MongoDB, the first service that registers in Eureka.
