# Naanayam Thavanai: working rules

Read this before changing code. The full plan, decisions, progress and pending work are in
`docs/PROJECT_STATUS.md` (imported below). Update that file when you finish or change something.

@docs/PROJECT_STATUS.md

## Product
- Multi-tenant SaaS for small financiers who lend money and collect **daily or weekly** installments.
- **Tenant = financier.** Users: `SUPER_ADMIN` (DigitWorks, no tenant), `OWNER` (financier), `AGENT` (field collector).
- Name: Naanayam Thavanai (நாணயம் தவணை).
  - Mobile app ID (Play Store / App Store): `apps.digitworks.naanayamthavanai` (DigitWorks pattern `apps.digitworks.<app>`).
  - Backend Java package and Gradle group: `tn.digitworks.naanayamthavanai` (same pattern as `fieldnex`: `tn.digitworks.<app>`).

## Stack and layout
| Folder | What | Tech |
|---|---|---|
| `api-spec/openapi.yaml` | API contract, source of truth | OpenAPI 3.0.3 |
| `service/` | Backend | Spring Boot 4.1.2, Java 25, Hibernate 7, Jackson 3 (`tools.jackson.*`), Gradle Kotlin DSL |
| `db/migrations/` | Schema | MySQL 8, `utf8mb4`, Flyway (copied to `classpath:db/migration` at build) |
| `mobile/` | Agent and owner app | Flutter (not scaffolded yet) |
| `web/` | Owner and Super Admin panel | Angular (not scaffolded yet) |

Base Java package `tn.digitworks.naanayamthavanai`, one package per domain:
`common`, `security`, `tenant`, `user`, `area`, `client`, `loan` (+ `loan.schedule`, `loan.missed`),
`payment` (+ `payment.allocation`), `preclosure`, `collection`, `handover`, `report`.

## Commands
```bash
mysql -u root -p < db/create-database.sql          # one-time: DB + user naanayam/naanayam
cd service
./gradlew test                                       # unit tests, no DB needed
SPRING_PROFILES_ACTIVE=local SUPER_ADMIN_PHONE=9xxxxxxxxx SUPER_ADMIN_PIN=xxxx ./gradlew bootRun
```
API `http://localhost:8080/v1`, Swagger `http://localhost:8080/v1/swagger-ui.html`.
Env: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (required outside `local`, 32+ chars),
`SUPER_ADMIN_PHONE`, `SUPER_ADMIN_PIN`, `DOCUMENT_DIR`.

## Tenancy (never bypass)
- Shared database; **every business table has `tenant_id`**.
- The tenant comes **only** from the JWT (`CurrentUser.get().requireTenant()`). Never accept a tenant id
  from the path, query or body (only `/admin/**` takes a tenant id, and only for SUPER_ADMIN).
- Every repository query filters by `tenantId` (`findByIdAndTenantId`, etc.).
- Agents see only clients/loans in their assigned areas: use `AreaScope` (`current()`, `require()`).
  Out-of-scope records return **404, not 403**, so agents cannot probe.
- Unique keys are per tenant (e.g. client phone), except login phone (`app_user.phone`, global).

## Money and time
- Money is `DECIMAL(12,2)` in MySQL, `BigDecimal` in Java, **decimal strings** in JSON (global serializer
  in `AppConfig`). Never use `double`/`float` for money.
- Interest rates are **% per annum**.
- Business time zone is `Asia/Kolkata`. Inject `Clock` and use `LocalDate.now(clock)`; never call
  `LocalDate.now()` without it. Payment `paidOn` arrives as an offset date-time and is stored as IST.

## Business logic placement
- Core rules live in **pure classes with no Spring dependency**, each with unit tests:
  `ScheduleCalculator`, `PaymentAllocator`, `MissedInstallmentProcessor`, `PreclosureCalculator`.
  Put rule changes there first, test them, then wire them in services.
- Services own transactions and mapping. Controllers only validate input and call services.
- Money-changing operations on a loan: lock the loan (`LoanService.lockAccessible` or
  `LoanRepository.lockById*`) **and** the installments (`InstallmentRepository.lockByLoanId`) first.
- Payments are **append-only**. Corrections are `REVERSAL` rows (negative amounts and negative
  allocations). Never update or delete a payment or allocation.
- Every payment has a device-generated `clientTxnId` (UUID) for idempotent offline sync.

## Persistence rules
- Schema changes = a **new** `db/migrations/V<n>__description.sql`. Never edit an applied migration.
- `spring.jpa.hibernate.ddl-auto=validate`: entity mappings must match the migrations exactly.
  - Enums: `@Enumerated(EnumType.STRING)`, values must match the MySQL `ENUM` list.
  - `CHAR(n)` columns need `columnDefinition = "char(n)"`; the MySQL `SET` column needs its `columnDefinition`.
  - DB-managed `updated_at`: `insertable = false, updatable = false`. `created_at` is set in `@PrePersist`.
- Entities hold plain foreign-key ids (`Long clientId`), not JPA relationships.
- Native SQL (reports) must always filter by `tenant_id`.

## API rules
- OpenAPI first: change `api-spec/openapi.yaml` together with any controller change. Controllers and spec
  must match one-to-one (45 operations at the moment).
- Errors use the `Error` schema via `ApiException` / `GlobalExceptionHandler`:
  400 `VALIDATION_FAILED`, 401, 403, 404, 409 duplicates/conflicts, 422 business rules (stable `code`
  strings like `INTEREST_ABOVE_CAP`, `PAYMENT_EXCEEDS_OUTSTANDING`).
- Role checks with `@PreAuthorize` on controllers (see the roles table in `docs/PROJECT_STATUS.md`).

## Working style
- The owner prefers concise, technical communication and **incremental, targeted changes** over broad
  rewrites. Make one step at a time, keep tests green, and confirm decisions that change business rules.
- After a change: `./gradlew test`, keep the spec in sync, and update `docs/PROJECT_STATUS.md`.
