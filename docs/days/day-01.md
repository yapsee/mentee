# Day 1: Monorepo and infrastructure

**Goal:** understand the target system and get every piece of infrastructure running with one command.

**Status:** done. All 7 containers are up and verified.

---

## 1. What was built

### Monorepo layout

```
e-commerce ms/
├── .env / .env.example       # credentials and ports (.env is git-ignored)
├── .gitignore
├── README.md                 # how to start, URLs, ports
├── docker-compose.yml        # the infrastructure
├── infra/
│   ├── postgres/init.sql     # creates the 3 relational databases
│   └── pgadmin/servers.json  # pgAdmin opens already connected to postgres
├── services/                 # one folder per microservice (empty for now)
│   ├── config-server/  discovery/  gateway/
│   └── customer/  product/  order/  payment/  notification/
├── frontend/                 # Angular app, Day 9
└── docs/
    ├── diagrams/architecture.png, domain-model.png
    ├── plan/intern-plan.html
    └── days/day-01.md        # this file
```

Empty folders hold a `.gitkeep` so Git tracks them.

**Why one repository?** All services change together during the bootcamp. A single clone, a single compose file and one PR per feature are easier to follow. Each service is still its own Maven project with its own `pom.xml`, so it can be built and deployed on its own, which is what makes it a microservice.

### Infrastructure (`docker-compose.yml`)

| Container | Image | Purpose in the architecture | Host port |
|---|---|---|---|
| `ecom-postgres` | postgres:16-alpine | Databases for **product**, **order** and **payment** | 5433 |
| `ecom-pgadmin` | dpage/pgadmin4:8 | Web UI for Postgres | 5050 |
| `ecom-mongodb` | mongo:7 | Databases for **customer** and **notification** | 27017 |
| `ecom-mongo-express` | mongo-express:1.0.2 | Web UI for Mongo | 8081 |
| `ecom-kafka` | apache/kafka:3.8.0 | Message broker for the order/payment confirmations | 9092 |
| `ecom-zipkin` | openzipkin/zipkin:3 | Distributed tracing UI | 9411 |
| `ecom-maildev` | maildev/maildev:2.1.0 | Fake SMTP server that catches notification emails | 1080 (UI), 1025 (SMTP) |

Design choices to explain to the intern:

- **One Postgres container, three databases** (`product_db`, `order_db`, `payment_db`). That's database-per-service without running three servers on a laptop. Services must never read another service's database.
- **Kafka in KRaft mode.** Kafka 3.x manages itself without Zookeeper, so there's one container instead of two.
- **Two Kafka listeners.** `localhost:9092` is for apps running in the IDE. `kafka:29092` is for apps running inside Docker (Day 10). A client must connect to the address the broker *advertises*, which is the most common Kafka-in-Docker pitfall.
- **Healthchecks.** Postgres, Mongo and Kafka report `(healthy)` only once they accept connections. pgAdmin and mongo-express wait for them with `depends_on: condition: service_healthy`.
- **Named volumes.** Data survives `docker compose down`. Only `down -v` wipes it.
- **`${VAR:-default}`** in compose. The file works even without `.env`, and anyone can override a port or password locally.

---

## 2. How to run it

```bash
cd "e-commerce ms"
cp .env.example .env      # first time only
docker compose up -d
docker compose ps
```

> **Do I need Postgres or MongoDB installed locally?** No. Everything runs in Docker, so Docker Desktop is the only requirement for the infrastructure. If a local Postgres is already running, it doesn't conflict, because the container is published on port **5433**. Change `POSTGRES_PORT` in `.env` if needed.

---

## 3. Verification (done on Day 1)

| Check | Command / URL | Result |
|---|---|---|
| All containers running | `docker compose ps` | 7/7 up; postgres, mongodb, kafka **healthy** |
| 3 databases exist | `docker exec ecom-postgres psql -U ecom -d postgres -c "\l"` | `product_db`, `order_db`, `payment_db` |
| Mongo accepts auth | `docker exec ecom-mongodb mongosh -u ecom -p ecom --eval "db.adminCommand('ping')"` | `ok: 1` |
| Kafka works | create, list and delete a `smoke-test` topic with `kafka-topics.sh` | OK |
| pgAdmin | http://localhost:5050 | 200 |
| mongo-express | http://localhost:8081 (admin/admin) | 200 |
| Zipkin | http://localhost:9411 | 200 |
| MailDev | http://localhost:1080 | 200 |

Kafka smoke test the intern should repeat:

```bash
docker exec ecom-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic smoke-test
docker exec -it ecom-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic smoke-test
# type a few lines, Ctrl+C, then:
docker exec ecom-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic smoke-test --from-beginning --timeout-ms 5000
```

---

## 4. Problems hit and fixes

| Problem | Cause | Fix |
|---|---|---|
| Port 5432 already in use | A local PostgreSQL runs on this Mac | Published the container on **5433** (`POSTGRES_PORT` in `.env`) |
| pgAdmin kept restarting | pgAdmin 8 rejects `.local` email domains (`admin@ecom.local`) | Changed the login to `admin@ecom.com` |
| `mv` failed on the screenshot files | macOS puts a narrow no-break space before "PM" in screenshot names | Used a glob (`Screenshot*4.20.45*.png`) |

Good debugging lessons for the intern: read `docker logs <container>` first, and check ports with `lsof -iTCP:<port> -sTCP:LISTEN`.

---

## 5. Gaps on this machine before Day 2

- **JDK is 17, the plan targets 21.** Spring Boot 3 runs on 17, but install JDK 21 (e.g. `brew install --cask temurin@21`) to match the plan.
- **Maven is 3.6.3.** Fine for now. Each service will include the Maven wrapper (`./mvnw`) so the version is pinned per project.
- **Git:** not initialised yet. Run `git init` and make the first commit as part of the intern's Day 1.

---

## 6. Coach checkpoint

**Learn topics covered:** when microservices help and when they hurt; database per service; images vs containers vs volumes vs networks.

**Question for the intern:**
> Why does each service own its database? What breaks if Order reads the product table directly?

Expected answer: coupling. A schema change in Product would break Order, the two can't be deployed or scaled on their own, and Product loses control of its own data rules (e.g. stock checks). Order must ask Product through its API.

**Next (Day 2):** Config Server (8888) and Eureka discovery (8761).
