# Naanayam Thavanai (நாணயம் தவணை)

Multi-tenant lending and daily/weekly collection app for financiers. DigitWORKS.

App ID: `apps.digitworks.naanayamthavanai`

| Folder | What | Tech |
|---|---|---|
| `api-spec/` | API contract (source of truth) | OpenAPI 3.0.3 |
| `service/` | Backend | Spring Boot 4, Java 25, Gradle |
| `mobile/` | Agent and owner app | Flutter |
| `web/` | Owner and Super Admin panel | Angular |
| `db/migrations/` | Schema | MySQL 8, Flyway |

## Backend

```bash
# 1. One-time: create the database and user (asks for the MySQL root password)
mysql -u root -p < db/create-database.sql

# 2. Unit tests (no DB needed)
cd service
./gradlew test

# 3. Run locally. Flyway creates the tables; the first Super Admin is created from the env vars.
SPRING_PROFILES_ACTIVE=local \
SUPER_ADMIN_PHONE=9xxxxxxxxx SUPER_ADMIN_PIN=xxxx \
./gradlew bootRun
```

- API: `http://localhost:8080/v1` · Swagger UI: `http://localhost:8080/v1/swagger-ui.html`
- Env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (required outside the `local` profile, 32+ chars),
  `SUPER_ADMIN_PHONE`, `SUPER_ADMIN_PIN`, `DOCUMENT_DIR`.

### First run

1. `POST /auth/login` as the Super Admin → access token.
2. `POST /admin/tenants` → creates a financier, its owner login (same phone) and default settings.
3. Log in as the owner → `PUT /settings`, `POST /areas`, `POST /users` (agents), `PUT /users/{id}/areas`.
4. `POST /clients` → `POST /loans/preview` → `POST /loans`.
5. Agents: `GET /collections/due` → `POST /payments`.
6. `POST /admin/jobs/missed-installments` runs the nightly job on demand (normally 00:15 IST).

### Roles

| Action | Super Admin | Owner | Agent |
|---|---|---|---|
| Tenants, run jobs | ✅ | | |
| Settings, areas, agents, reports | | ✅ | read settings/areas |
| Clients | | ✅ | own areas |
| Create loan | | ✅ | preview only |
| Record payment, due list | | ✅ | own areas |
| Reverse payment, pre-close, mark default | | ✅ | quote only |
| Cash handover | | confirm | submit |

## Conventions

- `tenant_id` comes only from the JWT, never from request path, query or body.
- Money is `DECIMAL(12,2)` in the DB, `BigDecimal` in Java, decimal strings in the API.
- Business timezone is `Asia/Kolkata`.
- Payments are append-only; corrections are `REVERSAL` entries.
- Interest rates are % per annum.
