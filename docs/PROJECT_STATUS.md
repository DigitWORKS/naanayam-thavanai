# Project status: plan, decisions, progress, pending

Last updated: 2026-11-14

## 1. Plan

### Build order
| # | Step | Status |
|---|---|---|
| 1 | API spec + DB schema | ✅ Done (spec 44 operations, migrations V1–V8) |
| 2 | Backend core logic (schedule, allocation, missed days, pre-closure) | ✅ Done, unit tested |
| 3 | Backend: entities, repositories, services, controllers | ✅ Done |
| 4 | First run on local MySQL | ✅ Done 2026-11-14: Flyway V1–V6 applied, Hibernate validation passed, Super Admin created |
| 4b | Smoke-test API flow end to end, integration tests | ⏳ Next |
| 5 | Flutter mobile MVP: login, today's due list, record payment, client + loan entry, offline sync | ⏳ Pending |
| 6 | Angular web MVP: login, dashboard, clients/loans, areas/agents, settings, Super Admin tenants | ⏳ Pending |
| 7 | Reports export, SMS receipts, deployment, backups | ⏳ Pending |

Mobile comes first among the frontends because daily collection is the core activity. Both frontends use
API clients generated from `api-spec/openapi.yaml`:
- Flutter: `openapi-generator-cli generate -g dart-dio -i api-spec/openapi.yaml -o mobile/packages/api_client`
- Angular: `openapi-generator-cli generate -g typescript-angular -i api-spec/openapi.yaml -o web/src/app/core/api`

### Roles
| Action | Super Admin | Owner | Agent |
|---|---|---|---|
| Tenants (financiers), run jobs | ✅ | | |
| Settings, areas, agents, reports | | ✅ | read settings/areas |
| Clients | | ✅ | own areas only |
| Create loan | | ✅ | preview only |
| Record payment, due list | | ✅ | own areas only |
| Reverse payment, pre-close, mark default | | ✅ | pre-closure quote only |
| Cash handover | | confirm / dispute | submit |

## 2. Decisions

### Compliance (confirmed with the owner / CA)
- Most financiers hold a money-lending licence: stored as `tenant.licence_no`. DigitWorks provides software
  only; each financier is responsible for their licence and lending practices (terms of service).
- Maximum legal interest rate is **configurable per tenant** (`tenant_settings.max_interest_rate`, % p.a.);
  loans above it are rejected.
- Interest method FLAT / REDUCING: tenant chooses which are allowed; chosen per loan.
- Penalties: configurable (fixed or %, grace days, cap per loan, on/off).
- Record retention: **8 years** (CA confirmed). Default in `tenant_settings.record_retention_years`.
- **No GST**; only income tax → financial-year interest income report (April–March).
- Default handling: **legal action only**, no coercive collection. Loan status `DEFAULTED` + `legal_note`.
- Personal data (DPDP): store only what is needed. **Aadhaar: last 4 digits only.** Client consent captured
  at client creation (`consent_given_at`, `consent_version`). Documents stored privately (see pending).

### Business rules
- Interest collection **per loan**: `UPFRONT_DEDUCTED` (FLAT only) or `ADDED_TO_REPAYMENT`.
- Interest rate entered as **% per annum** for now. *Owner wants to rework this later* to also allow entry
  per tenure (e.g. "20% for 100 days") with conversion to p.a. for the cap check.
- Daily schedule counts **every calendar day** (Sundays and holidays included). First installment is due one
  period after the start date. Installments rounded up to the whole rupee; the last one takes the remainder.
- **No renewals or top-ups.** Loan terms are fixed after creation.
- A client can have **2 active loans** at once (configurable: `max_active_loans_per_client`).
- Agents work from day one; **an agent can have multiple areas** (`user_area`). Area name is one field
  (Tamil or English; pick one script per tenant to avoid duplicates).
- Payment modes: **cash and UPI**. UPI needs the **UPI transaction ID (UTR)**: 12 digits (spaces ignored),
  unique per tenant among unreversed payments (a reversed payment's UTR can be recorded again). Errors:
  `UPI_REF_REQUIRED`, `INVALID_UPI_REF` (422), `DUPLICATE_UPI_REF` (409). Cash payments store no UTR.
- **Custom amount payments**: any amount, allocated in this order:
  1. outstanding penalties and installments due today or earlier, oldest first (tenant setting
     `allocation_order`: `PENALTY_FIRST` / `INSTALLMENT_FIRST`);
  2. the rest is an advance (`advance_allocation`): `NEXT_INSTALLMENTS` (skip coming days) or
     `LAST_INSTALLMENTS` (finish earlier; cancels carried-forward extensions first).
  Paying more than the total outstanding is rejected (`PAYMENT_EXCEEDS_OUTSTANDING`); use pre-closure.
- **Missed payment extends the tenure** (nightly job, 00:15 IST): an unpaid or part-paid installment due
  before today becomes `MISSED`; its shortfall moves to a new extension installment after the last due date
  (same interest/principal ratio). The `MISSED` row keeps only its penalty. One penalty per missed
  installment, after the grace days, only if the extension is still unpaid; never for days before penalties
  were turned on (`penalty_effective_from`).
- **Pre-closure**: settlement = outstanding principal + outstanding interest + pending penalty − rebate +
  charge. Rebate (`UNEARNED_INTEREST`) = interest of installments due after the closing date. Charge: none,
  fixed, or % of (principal + interest − rebate). Two steps: quote, then confirm (409 `QUOTE_OUTDATED` if the
  amount changed). Upfront-deducted loans get no rebate (their installments carry no interest): owner chose
  "skip for now", no special rule.
- Cash handover: agent submits the day's cash; expected amount = agent's CASH payments that day (including
  reversals); owner confirms or disputes.

### Technical
- Multi-tenant: **shared MySQL database with `tenant_id`** on every business table (not a DB per tenant).
- Auth: our own HS256 JWTs (Spring Security resource server). Phone + 4–6 digit PIN (BCrypt).
  Access token 12 h, refresh token 30 days (stateless JWT with `typ=refresh`). First Super Admin is created
  from `SUPER_ADMIN_PHONE` / `SUPER_ADMIN_PIN` on startup.
- **Self-registration** (decided 2026-11-16): financiers register themselves from the login screen
  ("Run a finance business? Register"). The owner enters business name, licence no. (optional), maximum
  interest rate, name, mobile, and **sets their own PIN** (current PIN login). **Mobile OTP verification will
  replace/strengthen this later** and is required before the public sign-up goes live. Super Admin keeps
  `POST /admin/tenants` for manual onboarding.
- **Plans are an ENUM** (decided 2026-11-16): `TRIAL`, `MONTHLY`, `YEARLY`. `plan_expiry` is derived from the
  plan: TRIAL = registration + 30 days, MONTHLY = +1 month, YEARLY = +1 year (renewal extends from the current
  expiry, or from today if already expired). Prices to be decided. Login is blocked after expiry (existing check).
- Loan number: `LN000001` per tenant (count + 1 under a tenant row lock).
- Document storage: `DocumentStorage` interface, `LocalDocumentStorage` for now (JPG/PNG/WEBP/PDF, 5 MB),
  downloaded through an authenticated endpoint.
- Repo conventions: `service/` (not `backend/`), monorepo with `api-spec`, `service`, `mobile`, `web`,
  `db/migrations`.
- Identifiers: mobile app ID `apps.digitworks.naanayamthavanai`; backend Java package and Gradle group
  `tn.digitworks.naanayamthavanai` (renamed from `apps.digitworks...` on 2026-11-14 to match `fieldnex`).

## 3. Implemented

### Database (`db/`)
- `create-database.sql`: creates database `naanayam` (utf8mb4) and user `naanayam`.
- `V1__init.sql`: tenant, tenant_settings, app_user, area, user_area, client, client_document, loan,
  installment, payment, payment_allocation, cash_handover.
- `V2`: allocation/advance and pre-closure settings, `PRECLOSED` / `SETTLED` statuses, `payment.purpose`,
  `loan_preclosure`.
- `V3`: installment principal/interest components. `V4`: `installment.penalty_paid`.
- `V5`: `tenant_settings.penalty_effective_from`. `V6`: `payment_allocation.interest_part`.
- `V7`: index `payment (tenant_id, upi_ref_no)` for the duplicate UTR check (not unique, see decisions).
- `V8`: `tenant.plan` (TRIAL/MONTHLY/YEARLY) and `tenant.terms_accepted_at`.

### Backend (`service/`)
- Pure logic + tests (62 unit tests, all passing):
  `ScheduleCalculator` (11), `PaymentAllocator` (14), `MissedInstallmentProcessor` (15), `PreclosureCalculator` (7),
  `UpiRefCheck` (5, Mockito), `Plan` (7), `RateLimiter` (3).
- `UpiRefCheck`: UTR validation + duplicate check for payments and pre-closure; locks the tenant row first
  (lock order: tenant → loan → installments).
- Entities and repositories for all 13 tables.
- Services and controllers for every spec operation (44): auth (incl. self-registration), admin tenants, settings, users, areas,
  clients + documents, loans (preview, create, list, detail, installments, ledger, default), collections due
  list, payments (record, bulk sync, reverse), pre-closure (quote, confirm), cash handover, reports
  (dashboard, collections by day/agent/area, FY interest income JSON), nightly missed-installment job +
  `POST /admin/jobs/missed-installments`.
- Security: JWT filter chain, `@PreAuthorize` per role, `CurrentUser`, `AreaScope`, JSON 401/403 bodies.
- Verification so far: compiled against the real Spring Boot 4.1.2 / Hibernate 7.4 jars; unit tests run on
  JUnit 6; entity columns cross-checked against V1–V6; spec ↔ controller operations match 1:1.
  Started locally on MySQL 8.0.35 (IntelliJ run config, profile `local`, working directory `service/`).

## 4. Pending and known gaps

### Next (backend)
- [x] Run `db/create-database.sql`, start the service (works).
- [ ] Smoke-test the first-run flow (README) end to end.
- [ ] Integration tests with Testcontainers MySQL, including a **tenant isolation test** (tenant A cannot read
      tenant B) and agent area scoping.
- [ ] CORS configuration for the Angular web app (not configured yet).
- [ ] Login protection: rate limit / lockout after repeated wrong PINs.

### Backend gaps
- [ ] Interest income report export: XLSX / PDF (JSON only now; other formats return `FORMAT_NOT_SUPPORTED`).
- [ ] Move documents to private, encrypted S3 storage before production.
- [ ] Nightly job is single-instance: add ShedLock before running more than one server.
- [ ] Suspended tenant / deactivated user keeps access until the access token expires (≤ 12 h); refresh
      tokens cannot be revoked (no logout list). Consider a per-request check or token versioning.
- [ ] Interest rate per-tenure input (owner's planned rework), converted to p.a. for the cap check.
- [ ] REDUCING loans: a carried-forward shortfall does not earn extra interest for the delay.
- [ ] Offline payment synced after the nightly job marked that day `MISSED`: money goes by the advance rule
      instead of to that day (acceptable for now; revisit with real usage).
- [ ] Importing existing paper loans: start date may be up to 365 days back, but past installments will be
      marked missed by the next nightly job. Needs an import mode that records past payments first.
- [ ] Pre-closure payments cannot be reversed.
- [ ] Cash handover reconciles CASH only; no UPI reconciliation.
- [ ] Reports are owner-only; no agent dashboard. Collections report has no target when grouped by agent.
- [ ] Penalty is one-time per missed installment (no daily accrual). Confirm this matches financiers' practice.
- [ ] Audit log for settings changes and reversals (who/when), beyond payment remarks.
- [x] Plans + self-registration backend (2026-11-16):
  - V8: `tenant.plan` ENUM + `tenant.terms_accepted_at`; existing tenants without expiry get created + 30 days.
  - `Plan` enum holds the expiry rule (`PlanTest`); `Tenant.applyPlan` sets plan, expiry and status.
  - `TenantOnboarding` creates tenant + settings + owner for both Super Admin and self-registration.
  - Public `POST /auth/register` → 201 with tokens (owner signed in). **Only TRIAL** self-serve for now
    (`PLAN_NEEDS_PAYMENT` otherwise) so nobody gets a paid period without paying. Rate limit: 5 per hour per
    client IP, in memory (`RateLimiter`, single server; behind a proxy, enable forwarded headers first).
  - Super Admin: `plan` on `POST /admin/tenants` and `PATCH /admin/tenants/{id}` (renew keeps unused days).
- [ ] Before public sign-up goes live: mobile OTP on register, and forwarded-header config so the rate limit
      sees real client IPs.
- [ ] Mobile OTP for registration and login (replaces PIN-only); also lets owners reset their own PIN.
- [ ] Billing / payment collection for MONTHLY and YEARLY plans (prices TBD).
- [ ] SMS / WhatsApp receipts and reminders (India requires DLT template registration).

### Frontends
- [x] Mobile UI design, 13 screens (design canvas in the owner's Claude account, "Naanayam Thavanai App
      Design"): login (with Register link), agent today list, record payment (cash / UPI with UTR field), loan
      detail, owner dashboard, new loan, clients list, new client, register your finance (self-registration with
      plan choice), client detail, cash handover (agent → owner), pre-closure quote, owner settings (plan, interest
      cap/methods, max loans, penalty, payment allocation, pre-closure, retention). Brand: deep green `#0E6B4F`,
      gold coin mark, Inter + Space Grotesk + Noto Sans Tamil.
- [ ] Remaining designs: Tamil-language version; web (Angular) owner and Super Admin screens.
- [x] API gaps found while designing: `409 CLIENT_EXISTS` now returns `details` {clientId, name, areaId,
      areaName, isActive, accessible} (`accessible` false for an agent outside that area). Loan creation is
      owner-only (`POST /loans` → 403 for agents); apps hide "Save & give loan" when `user.role` is AGENT.
      (A simultaneous duplicate create still falls back to the generic `409 DUPLICATE` from the unique key.)
- [x] Flutter `mobile/` slice 1, built and running on the Android emulator against the local service
      (Android Studio; platform folders from `flutter create`, see `mobile/README.md`): Riverpod + Dio + secure storage; login (SUPER_ADMIN refused, web only), token
      refresh-once interceptor (offline keeps the session), agent Today list (summary, area chips, pull to
      refresh), record payment (cash / UPI + 12-digit UTR, quick amounts, idempotent `clientTxnId`, form locks
      after an unknown outcome). Money as integer paise. Owners currently land on the same Today list.
- [x] Swagger UI "Authorize" (bearer JWT) via `common/OpenApiConfig.java`.
- [~] Flutter slice 6 written (to verify): Settings (owner, More → Settings; design board 13) — plan card (plan,
      expiry, days left; Upgrade explains that DigitWorks updates plans until billing exists), interest cap + allowed
      methods, active loans per client stepper, late penalty (on/off, fixed ₹ / % of missed amount, grace days,
      optional cap per loan, "applies from" note), payment order + where extra money goes, pre-closure (on/off,
      rebate future interest, no charge / fixed / %), record retention read-only; unsaved-changes guard; saving
      refreshes loan rules used by New loan / pre-closure. Backend: GET /reports/dashboard adds plan, planExpiry.
- [~] Flutter slice 5 written (to verify): Pre-close quote (design board 12) from Loan detail — settlement amount,
      principal / interest left, unpaid penalty, rebate for future-day interest, pre-closure charge with its label
      from settings (2% / ₹x); owner picks Cash / UPI (+UTR), confirms, POST /preclose with an idempotent
      clientTxnId (form locks on unknown outcome); 409 QUOTE_OUTDATED shows the new amount and reloads the quote.
      Agents see the quote read-only. No backend change.
- [~] Flutter slice 4 written (owner testing in progress): Loans tab (owner) — list by status (Active / Closed / Pre-closed /
      Defaulted) with client, progress, "₹x behind", end date; New loan (client picker or pre-selected, principal,
      rate checked against the cap from GET /settings, Daily / Weekly + tenure, Flat / Reducing limited to allowed
      methods, added to dues / deduct upfront, start date ±30 days, live POST /loans/preview card, confirm dialog);
      Loan detail (outstanding, N of M paid, progress, behind / penalty banner, terms grid, ends with +extensions,
      Schedule with Today / Due / Overdue / Missed / Extension and older paid rows collapsed, Payments ledger
      with reversals, Collect via today's due list). Entry points: dashboard "+", client detail "New loan" +
      loan cards, New client "Save & give loan" (owner). Backend: LoanSummary now has clientId, clientName,
      areaName, totalPayable, endDate (names batched per call). Pre-close quote button is a placeholder.
- [x] Flutter slice 3, verified: Clients tab for owners and agents — list with search, filter chips
      (All / Behind / No active loan) and area chips, outstanding + "₹x behind" per client, Load more paging;
      New client (area from owner's active areas or agent's assigned areas, Aadhaar last 4 only, guarantor,
      bilingual consent, 409 CLIENT_EXISTS shown inline with "Open client"); Client detail (call / WhatsApp,
      active loans on track / behind, details, document count, Collect payment via today's due list, loan picker
      for 2 loans). Backend: `GET /clients` rows now carry activeLoans / outstanding / overdueAmount (batched per
      page) and a `filter` param (ALL / BEHIND / NO_ACTIVE_LOAN; BEHIND matches LoanBalances.arrears).
      Deferred: client photos / document upload (needs image_picker + platform permissions), edit client,
      "Save & give loan" and loan progress bar (both done in slice 4).
- [x] Flutter slice 2, verified on the emulator: login matched to design (+91 field, PIN boxes,
      Register link, footer), register screen (free trial), role-based bottom navigation (owner Home / Today /
      Clients / Loans / More, with Reports + Settings under More since owners also collect and manage clients;
      agent Today / Clients / Handover / More; unbuilt tabs show a placeholder; log out moved to More; owner
      Today list filters by all active areas), owner dashboard (collected vs target, cash / UPI, outstanding, behind
      schedule, by-area progress, pending handovers with Confirm / Review → accept or dispute).
      Backend: `businessName` added to `GET /reports/dashboard`. Tamil / English switch waits for Tamil strings.
- [ ] Flutter next (owner first): reports, client photos; then agent handover, offline queue (drift) + `/payments/sync`; bundled fonts (Inter,
      Space Grotesk, Noto Sans Tamil) instead of google_fonts runtime fetch; Tamil + English strings;
      generated API client; release INTERNET permission.
- [ ] Angular `web/`: scaffold, generated client, owner screens, Super Admin tenant screens.

### Legal, product, infra
- [ ] Draft privacy policy and terms of service (consent wording, "software provider only" clause);
      lawyer review; privacy policy URL is required for the Play Store.
- [ ] Deployment, daily MySQL backups (`mysqldump` + binary logs), CI.
- [ ] `git init` and first commit (not done yet).
