# E-commerce Microservices

A Spring Boot microservices e-commerce platform in a single monorepo, built over a 2-week intern track.

- Architecture: [`docs/diagrams/architecture.png`](docs/diagrams/architecture.png)
- Domain model: [`docs/diagrams/domain-model.png`](docs/diagrams/domain-model.png)
- Daily plan: [`docs/plan/intern-plan.html`](docs/plan/intern-plan.html)
- Daily reports: [`docs/days/`](docs/days/)

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| Docker Desktop | 24+ | Runs all infrastructure (no local Postgres/Mongo/Kafka needed) |
| JDK | 21 | Spring Boot 3 services |
| Maven | 3.9+ | Builds (each service will also ship a Maven wrapper) |
| Node.js + Angular CLI | 20 LTS / 18+ | Frontend |
| Git, Postman, IntelliJ | latest | Daily work |

## Repository layout

```
e-commerce-ms/
├── docker-compose.yml        # infrastructure (services added on Day 10)
├── .env.example              # copy to .env
├── infra/
│   ├── postgres/init.sql     # creates product_db, order_db, payment_db
│   └── pgadmin/servers.json  # pre-registers the postgres server in pgAdmin
├── services/
│   ├── config-server/  discovery/  gateway/
│   ├── customer/  product/  order/  payment/  notification/
├── frontend/                 # Angular app (Day 9)
└── docs/
```

## Start the infrastructure

```bash
cp .env.example .env          # first time only
docker compose up -d
docker compose ps             # wait until postgres, mongodb and kafka show (healthy)
```

Stop with `docker compose down`. Add `-v` to also wipe all data.

## Local URLs

| Component | URL / address | Credentials |
|---|---|---|
| PostgreSQL | `localhost:5433` (see `POSTGRES_PORT`) | `ecom` / `ecom` |
| pgAdmin | http://localhost:5050 | `admin@ecom.com` / `admin` |
| MongoDB | `localhost:27017` | `ecom` / `ecom` |
| mongo-express | http://localhost:8081 | `admin` / `admin` |
| Kafka | `localhost:9092` (containers: `kafka:29092`) | – |
| Zipkin | http://localhost:9411 | – |
| MailDev UI / SMTP | http://localhost:1080 / `localhost:1025` | – |

## Service ports

| Service | Port | Storage |
|---|---|---|
| config-server | 8888 | Git / classpath |
| discovery (Eureka) | 8761 | – |
| gateway | 8222 | – |
| customer | 8090 | MongoDB |
| product | 8050 | PostgreSQL `product_db` |
| order | 8070 | PostgreSQL `order_db` |
| payment | 8060 | PostgreSQL `payment_db` |
| notification | 8040 | MongoDB |
| frontend | 4200 | – |

Start order once services exist: **config-server → discovery → everything else**.
