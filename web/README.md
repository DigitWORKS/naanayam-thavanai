# web (Angular 21)

Owner and Super Admin web app. Agents use the mobile app (an agent login is refused here).

- Angular 21, **standalone** components, **signals**, **zoneless** (`provideZonelessChangeDetection()`, no zone.js),
  `OnPush` everywhere, router inputs via `withComponentInputBinding()`.
- HTTP: `HttpClient` + `withFetch()`, functional `authInterceptor`, `httpResource` for reads.
- Tests: Vitest (`ng test`). Style guide: 2025 file names (`login.ts`, not `login.component.ts`). Prefix `nt-`.

## Run

```bash
cd web
npm install
npm start            # ng serve on http://localhost:4200
npm test             # Vitest, single run: npx ng test --watch=false
npm run build        # production build → dist/naanayam-web
```

The Spring Boot service must be running on `localhost:8080`. `ng serve` proxies `/v1` to it
(`proxy.conf.json`), so no CORS setup is needed. In production, serve the build and route `/v1` to the service
from the same web server (nginx etc.).

If `npm install` fails with `Cannot read properties of null (reading 'edgesOut')` (an npm 10.x bug with jsdom's
optional peer), run `npm install --legacy-peer-deps` once, or update npm.

Log in with an owner (or Super Admin) mobile number and PIN.

## Structure

```
src/app/
  core/
    api.ts               API root (/v1), SKIP_AUTH context, error shape
    money.ts             Money in paise, decimal strings for the API, ₹ Indian grouping
    auth/                AuthService (signals), TokenStore, authInterceptor (refresh once on 401), guards
  layout/
    owner-shell/         side menu from the web design + router outlet
    admin-shell/         Super Admin menu
    business-summary.ts  business name, plan days, pending handovers (httpResource)
  features/
    login/               design W1
    placeholder/         stand-in for pages not built yet
  app.routes.ts          /login, /admin/** (SUPER_ADMIN), /** (OWNER); lazy-loaded
  app.config.ts          zoneless, router, http
```

Design: "Naanayam Thavanai App Design" canvas, web row (W1–W11). Tokens are CSS variables in `src/styles.scss`.

## Notes

- Session (access + refresh token) is kept in `localStorage` so a reload stays signed in. Never render API data
  as trusted HTML. Moving the refresh token to an httpOnly cookie is in the backlog.
- A refresh rejected by the server signs the user out (`/login?reason=expired`); a network failure does not.
