# Project status: plan, decisions, progress, pending

Last updated: 2026-11-14

## 1. Plan

### Build order
| # | Step | Status |
|---|---|---|
| 1 | API spec + DB schema | ✅ Done (spec 54 operations, migrations V1–V10) |
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
  ("Run a lending business? Register"). The owner enters business name, licence no. (optional), maximum
  interest rate, name, mobile, and **sets their own PIN** (current PIN login). **Mobile OTP verification will
  replace/strengthen this later** and is required before the public sign-up goes live. Super Admin keeps
  `POST /admin/tenants` for manual onboarding.
- **Plans are an ENUM** (decided 2026-11-16): `TRIAL`, `MONTHLY`, `YEARLY`. `plan_expiry` is derived from the
  plan: TRIAL = registration + 30 days, MONTHLY = +1 month, YEARLY = +1 year (renewal extends from the current
  expiry, or from today if already expired). Prices to be decided. Login is blocked after expiry (existing check).
- Loan number: `LN000001` per tenant (count + 1 under a tenant row lock).
- Document storage: `DocumentStorage` interface, `LocalDocumentStorage` for now (JPG/PNG/WEBP/PDF, 5 MB),
  downloaded through an authenticated endpoint.
- Android build targets **Java 25** (2026-11-18): `compileOptions` + Kotlin `jvmTarget` 25 and `jvmToolchain(25)`
  in `mobile/android/app/build.gradle.kts`, so javac/kotlinc use a local JDK 25 even though
  `~/.gradle/gradle.properties` pins the Gradle daemon to JDK 17. Android runtime APIs are still limited by `minSdk`.
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
- `V9`: `cash_handover.owner_remarks` — the owner's note on confirm / dispute; `remarks` stays the agent's note.
- `V10`: `app_user.token_version` — bumped on deactivation and on a new PIN; tokens carry it (claim `tv`, absent = 0).

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
- [x] Web (Angular) design, 11 screens + shared side menu (same canvas, second row): login; owner dashboard (KPIs,
      today by agent / area, handovers, most behind); loans table (status tabs, filters, paging); loan detail
      (summary, terms, payments with Reverse, schedule table, Mark defaulted); clients (list + detail panel with
      documents); cash handovers (tabs, review panel); agents & areas (agent table, add agent, areas); settings;
      Super Admin financiers (list + plan / expiry / licence / suspend panel); reports: collections (period presets,
      group by day / agent / area, bar chart vs target with hover, table) and income for tax (FY, interest /
      penalty / pre-closure by month, received basis). Agents stay on mobile.
- [ ] API gaps found in the web design: GET /loans needs q, areaId, frequency, behind-only filter, sort by amount
      behind, CSV export; areas need client count + assigned agents; FY interest-earned total for the dashboard;
      installment should say which extension a missed day moved to (#59 → #101); admin tenant list needs search
      and "expiring in 7 days"; Super Admin owner-PIN reset (support); an agent's cash payments for one day
      (handover "See list"); interest-income export for the CA (Excel / CSV; the API only returns JSON).
- [x] Angular `web/` set up (Angular 21.2, standalone, signals, zoneless via provideZonelessChangeDetection, OnPush,
      Vitest, 2025 file names, prefix `nt`): core (API root `/v1` with dev proxy to :8080, Money in paise, AuthService
      on signals, functional interceptor with shared refresh-on-401, guards: owner → owner shell, Super Admin →
      admin shell, agents refused), owner shell with the design's side menu (business name / plan days / pending
      handovers via httpResource), admin shell, login (design W1), placeholder pages for every menu item. Dev and
      production builds pass (initial 268 kB, 75 kB transfer); 10 Vitest tests pass.
- [~] Web slice 1 written (to verify): owner Dashboard (design board "Web owner dashboard") — date + greeting,
      search (goes to Clients with ?q), + New loan; cards: collected today vs target with cash / UPI, outstanding
      (active loans, clients), behind schedule (loans, penalty due), interest earned this FY; Today by agent
      (areas, collected, target, cash in hand = cash less a confirmed handover, handover state; owner row "You");
      Today by area (bars, sorted by target); Cash handovers to confirm (one-click Confirm when it matches, Review
      dialog to confirm or dispute when it differs; refreshes the menu badge); Most behind (top 5, days since the
      oldest unpaid due). Side menu and dashboard share one GET /reports/dashboard. Checked against a mock API at
      1440 and 900 px. Backend: /reports/dashboard adds activeClients, penaltyDue, fy, fyInterest; new
      GET /reports/agents-today and GET /reports/most-behind (owner); spec now 47 operations.
      Owner's note is kept apart from the agent's (V9 owner_remarks; API ownerRemarks on handovers; mobile
      handover card shows "Owner: …").
- [~] Web slice 2 written (to verify): Loans list (board "Web loans list") — tabs with counts (Active / Closed /
      Pre-closed / Defaulted), search (loan no., client name, phone digits; 300 ms debounce), Area and Frequency
      filters, Behind only (Active tab), paid bar "58 of 102 days", status tag (₹x behind / On track / …),
      pages; all filters in the URL query (?status=&q=&area=&frequency=&behind=true&page=) so Back, reload and
      the dashboard's "All N loans" link work; Export CSV downloads the current tab + filters (all pages).
      Loan detail (board "Web loan detail") — header with client, phone, area, start; Outstanding with paid bar,
      behind + penalty; facts (principal, interest, total, paid, installment, ends with +extensions, interest
      collected, given to client); legal note / closed date; Payments newest first (5, then "All N"), reversed
      ones struck through with the reason; Schedule from 4 days back + last 3 rows ("⋯ N more ⋯", Show all),
      today highlighted, "Missed → moved to #101" / "Extension (from #92)". Actions (active loans): Collect
      payment (amount chips: installment / behind + today / all; cash or UPI with 12-digit UTR; one clientTxnId
      per dialog so a retry is not a second payment), Pre-close quote (breakdown, cash / UPI, refreshes the quote
      on QUOTE_OUTDATED), Mark defaulted (legal note), Reverse payment (reason; active and closed loans,
      not pre-closure payments). Shared: nt-dialog (native <dialog>), apiErrorMessage, list styles.
      Fixed: Esc did not close the dashboard's handover dialog (a template listener returning false cancels
      the event).
      Backend: GET /loans adds frequency, q, behind; GET /loans/counts; GET /loans/export (owner, CSV, UTF-8
      BOM, formula-safe cells, ≤10,000 rows; LoanCsvTest); LoanSummary + clientPhone, startDate, paidCount,
      installmentCount; loan + clientPhone, areaName, paidCount, installmentCount; installment + extendedFrom;
      ledger + remarks. Spec 49 operations.
- [~] Web slice 3 written (to verify): Clients (board "Web clients") — list + selected client side by side;
      /clients/:clientId selects (one route via a URL matcher, so the list is not rebuilt); search (300 ms),
      All / Behind / No active loan, Area, pages, all in the URL query and kept when picking another client.
      Rows: name + phone, area, active loans, outstanding, tag (₹x behind / No active loan / Inactive).
      Panel: initials, area, client since; Call (tel:) and WhatsApp (wa.me); active loans (tag, paid bar,
      installment · left · ends) linking to the loan; "n of max active loans · limit reached" (GET /settings);
      + New loan link (/loans/new?clientId=, page still a placeholder); details (alt mobile, address, Aadhaar
      masked, guarantor, consent date + version); documents list with View (fetched with the token, opened
      as a blob). New client / Edit dialog: area, name, mobile (new only), alt mobile, address, Aadhaar last 4
      (new only), guarantor, consent (same EN + Tamil text and version v1 as mobile) / Active toggle (edit);
      duplicate mobile shows "<name> (<area>) already has this number · Open client". Shared nt-pager and
      shared/format (formatPhone, pageWindow) now used by Loans too.
      Not yet on web: document upload (with the client photos item).
      Backend: client detail + consentVersion, createdAt; PATCH /clients accepts "" to clear alt / guarantor
      mobile (was a 400).
- [~] Web slice 4 written (to verify): New loan (/loans/new, ?clientId= preselects; no web board, the
      mobile screen laid out wide) — client search + card (phone, area, "n active loans of max"; blocks an
      inactive client or one at the limit), amount, rate with the max from Settings, method (disallowed one
      greyed), interest collected, daily / weekly, number of days / weeks, start date (±30 days, first due one
      period later); live preview (POST /loans/preview, 400 ms debounce, stale preview dimmed): give the
      client, interest, total, installment, count, last due, schedule (first 5 + last 2, Show all); confirm
      dialog, then opens the new loan. Fixed across all web pages: resource value() throws in the error state
      (Angular 21), so not-found / error views crashed instead of showing — now safeValue() everywhere
      (core/resource.ts, with a spec). CLAUDE.md: stack rows for mobile / web, Web conventions section.
- [~] Web slice 5 written (to verify): Cash handovers (board "Web cash handovers") — tabs To confirm (with
      the menu count) / Confirmed / Disputed, Agent, date range (To confirm: all dates; others default to
      this month), all in the URL (?status=&agent=&from=&to=&h=); rows date, agent, cash collected, handed,
      Matches / ₹x short / extra; month totals (cash received = confirmed handed, short, disputed). Side panel:
      agent · areas, cash collected (n payments), handed, short / extra, agent's note, owner's note, the cash
      payments of that day (time, client · loan linking to the loan, reversals in red, total; warns if entries
      changed after the handover), remarks, Accept ₹x / Dispute (needs a note). Refreshes list, totals and
      menu badge. Backend: GET /cash-handovers/{id}/payments (owner; agent only own) — the agent's CASH
      entries of that day, the same rows as expectedAmount. Spec 50 operations.
- [~] Web slice 6 written (to verify): Agents & areas (board "Web agents & areas") — agents table (name, phone,
      last login, area pills, collected this month from the collections report by agent, Active / Inactive)
      with Areas (checkbox dialog, PUT /users/{id}/areas), New PIN (entered twice), Deactivate, Reactivate;
      areas grid (active clients, agents; inactive ones dimmed) with Add area and Rename; Add agent form
      (name, +91 mobile, first PIN, areas — at least one).
      Security fix (backend): deactivating a user or setting a new PIN now signs them out everywhere. Before,
      a deactivated agent's access token kept working for up to 12 h and a PIN reset left the old phone
      signed in for up to 30 days (refresh token). V10 token_version, claim "tv" in both tokens, checked on
      refresh and on every request (TokenState: one primary-key lookup per request; TokenStateTest). Tokens
      issued before V10 count as version 0, so deploying signs nobody out.
      Backend: GET /areas/overview (owner) — every area with active client count and active agents. Spec 51.
      Known (mobile): payments queued offline stay on the phone after sign-out and upload under whoever logs
      in next on that phone; should be tied to the user who recorded them.
- [~] Web slice 7 written (to verify): Settings (board "Web settings") — plan card (plan, ends, days left;
      business, owner, licence, records kept; Upgrade disabled until payments exist); Interest & loans (cap,
      allowed methods, active loans per client stepper); Late penalty (on/off, fixed ₹ / %, value, grace days,
      max per loan, "applies to days missed from …"); How payments are applied (clear first, extra money
      goes to); Pre-closure (allowed, no interest for future days, no charge / fixed / %); Save with
      "Unsaved changes" + Discard, leave-page and close-tab warnings. Your PIN: current + new twice; other
      devices signed out, this browser keeps going (new tokens stored).
      Backend: GET /settings/tenant (owner's business, licence, plan); POST /auth/change-pin (any role;
      wrong PIN = 422 WRONG_PIN, not 401; signs out other devices via token version; returns new tokens).
      Security fix: login had no attempt limit (a 4-digit PIN is 10,000 guesses) — now 10 per mobile number
      per 15 min, change-PIN 5 per user per 15 min, 429 TOO_MANY_ATTEMPTS (in memory, per server instance).
      Spec 53 operations. Mobile can use change-pin later (its Settings has no PIN change yet).
- [~] Web slice 8 written (to verify): Reports (boards "Web reports" / "Web income for tax"), tabs Collections
      | Income for tax. Collections: Today / This week (Monday start) / This month / Custom (≤ 1 year), group by
      Day / Agent / Area, all in the URL; totals (target, collected % of target, cash, UPI, payments); day chart
      (target bar behind, collected in front, today in gold, hover / focus tooltip; up to 62 days, every day
      shown, zero days included), or bars per agent / area with "of target · %"; table with % of target (below
      80% in amber); Export CSV. Income for tax: FY picker (this and 4 previous), "counted when received" note,
      totals (income, interest, penalty, pre-closure charges), month rows with bars, future months "—", FY total;
      Download for CA (CSV). CSVs built in the browser (shared/csv.ts: BOM, CRLF, formula-safe; spec).
      Backend fix (collections report): target now skips defaulted loans like the dashboard (Today's target on
      Reports matched the dashboard only when no defaulted loan had dues); payment count excludes reversals;
      by-agent target = dues in the agent's assigned areas (was always null).
      Today's collections is not on the web (decided): the owner sees today on the Dashboard; collecting is
      done in the mobile app. Removed from the web menu and routes.
- [~] Web slice 9 written (to verify): Super Admin › Financiers (board "Web admin financiers") — cards (all,
      on trial, expiring in 7 days, suspended; click to filter), search (business, owner, phone digits), chips
      All / Trial / Active / Suspended / Expiring in 7 days, all in the URL (?q=&show=&page=&t=); rows with
      since, owner + phone, plan, expiry (amber within 7 days, red when ended), status (Expired shown when
      the plan has ended). Side panel: self-registered + terms time or "Added by Super Admin", plan Trial /
      Monthly / Yearly with the resulting expiry (same rule as Plan.renewedExpiry, unused days kept; spec),
      expiry override, licence, Save; Suspend (confirm) / Lift suspension. Add financier dialog (business,
      owner, mobile, first PIN, max interest, licence, plan with its end date).
      Backend: GET /admin/tenants adds q and expiringSoon (soonest first); GET /admin/tenants/summary; PATCH
      status: SUSPENDED suspends, any other value lifts a suspension and the status follows the plan (sending
      ACTIVE for a trial no longer made it ACTIVE).
      Security fix: suspending a financier, or its plan ending, only blocked new logins and refreshes — access
      tokens kept working up to 12 h. The per-request token check now also requires the financier to be usable
      (same rule as Tenant.isUsable; TokenStateTest). Spec 54 operations.
      The web app has no placeholder pages left (features/placeholder can be deleted). Files renamed: features/admin/businesses.*, business-panel.*, business-form.ts.
- [x] Naming: the UI says "business" instead of "financier" (Super Admin › Businesses at /admin/businesses;
      /admin/financiers redirects; web login hint; mobile "Ask the business owner to reset it"; server
      messages). Code / API keep "tenant". Rule in CLAUDE.md › Words.
      Files renamed too (web features/admin: businesses.*, business-panel.*, business-form.ts). "finance" as a
      noun also replaced: mobile register ("Register your business", "A business with this number…"), login
      ("Run a lending business?"), web login. Client consent notice now "I allow this business to store…";
      version kept at v1 (decided: wording change only).
- [ ] Web: all pages in the design are built; next is verifying slices 1–9 on the real backend.
- [ ] Tamil version: no separate design; done in code (Flutter ARB / Angular i18n) after the web app and the
      remaining functionality are complete. Noto Sans Tamil is already in both designs.
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
- [~] Flutter slice 8 written (to verify): offline payment queue. A payment that gets no response is saved on the
      phone (sqflite, table pending_payment, keyed by clientTxnId and the collecting user) and sent with
      POST /payments/sync on app start, resume, every minute while waiting, after saving, pull to refresh and
      "Sync now". CREATED / DUPLICATE → removed; FAILED DUPLICATE (concurrent write) → retried; other FAILED →
      "Needs attention" with Retry / Discard; a 400 batch is re-sent one by one. Today list shows a banner and
      "₹x saved on phone" per loan; Handover is blocked until saved payments are sent; log out warns (queue is
      kept per user and sent on next login). Decision logic unit tested (test/sync_outcome_test.dart).
      Chose sqflite over drift (no build_runner step). No backend change.
- [~] Flutter slice 7 written (to verify): agent Cash handover tab (design board 11) — cash collected today (count,
      last time), UPI note, amount handed (prefilled, matches / short / extra), note required when it differs,
      confirm dialog, once per day (409 HANDOVER_EXISTS → shows today's handover), today's status (waiting /
      confirmed / disputed) and earlier days (last 14). Backend: GET /cash-handovers/preview (agent) — cash and
      UPI totals, cash payment count, last cash time, existing handover; spec now 45 operations.
      Deferred: "See list" of today's cash payments; handing over a previous unsubmitted day.
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
- [ ] Flutter next: to be decided with the owner. Deferred by owner: client photos / document upload, reports; then agent handover, offline queue (drift) + `/payments/sync`; bundled fonts (Inter,
      Space Grotesk, Noto Sans Tamil) instead of google_fonts runtime fetch; Tamil + English strings;
      generated API client; release INTERNET permission.
- [ ] Angular `web/`: scaffold, generated client, owner screens, Super Admin tenant screens.

### Legal, product, infra
- [ ] Draft privacy policy and terms of service (consent wording, "software provider only" clause);
      lawyer review; privacy policy URL is required for the Play Store.
- [ ] Deployment, daily MySQL backups (`mysqldump` + binary logs), CI.
- [ ] `git init` and first commit (not done yet).
