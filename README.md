# Revo English

Personal website of Эмир, an English tutor for Russian-speaking adults: a landing page, two learning paths
(General English and IT English), short interactive exercises and pricing. Visitors contact the tutor via Telegram.
Students sign in to a private portal; their accounts are created by the teacher.

**Stack:** Java 25 · Spring Boot 4.1 · Gradle (wrapper included) · Thymeleaf · Spring Security · Spring Data JPA ·
PostgreSQL with Flyway migrations · plain CSS and vanilla JavaScript. No third-party scripts or fonts.

| Route              | Purpose                                                |
|--------------------|--------------------------------------------------------|
| `/`                | Landing page with the «Звучит ли это естественно?» quiz |
| `/english`         | General English path with a 4-question exercise        |
| `/it-english`      | IT English path with an interactive work scenario      |
| `/robots.txt`      | Generated; depends on `SITE_BASE_URL`                  |
| `/sitemap.xml`     | Generated once `SITE_BASE_URL` is set (404 before)     |
| `/actuator/health` | Health check (plus `/liveness` and `/readiness`)       |
| `/login`           | Sign-in for students and the teacher (no self-registration) |
| `/dashboard`       | Student dashboard — **students only**                  |
| `/account/password`| Change own password — any signed-in user               |
| `/admin/students`  | Create, enable/disable students, reset passwords — **administrators only** |
| `/admin/students/{id}/subscriptions` | Subscriptions, payments and lesson credits of one student — **administrators only** |

---

## Run locally

Requires **JDK 25** (the Gradle toolchain is set to Java 25) and a **PostgreSQL** database. The easiest local
database is the included `compose.yaml` (needs Docker):

```bash
export SPRING_DATASOURCE_PASSWORD=<choose-a-local-password>   # used by both the database and the app
docker compose up -d                                          # PostgreSQL 17 (Debian bookworm image) on 127.0.0.1:5432
```

```bash
./gradlew bootRun            # http://localhost:8080
./gradlew build              # compile + all tests; produces build/libs/RevoEnglishWebsite-0.0.1-SNAPSHOT.jar
```

On Windows use `gradlew.bat` (PowerShell: `$env:SPRING_DATASOURCE_PASSWORD = '…'`). If `java` is not on the PATH, set
`JAVA_HOME` to a JDK 25 installation first. In IntelliJ, add `SPRING_DATASOURCE_PASSWORD` to the run configuration's
environment variables. Tests do **not** need PostgreSQL — they use an in-memory H2 database.

---

## Configuration

All settings have safe defaults; production values are passed as environment variables.

| Environment variable     | Default             | Description |
|--------------------------|---------------------|-------------|
| `SITE_TELEGRAM_USERNAME` | `RevoWanDerLuciuse` | Telegram username used by every contact button (with or without `@`). Validated at startup. |
| `SITE_BASE_URL`          | *(empty)*           | **Public origin of the site**, e.g. `https://your-domain.example` — no trailing path needed. While empty, the deployment is treated as non-production: no canonical URLs or `og:url`, no sitemap, `robots.txt` disallows everything and pages carry `noindex`. **Set this only on the real production deployment.** |
| `SITE_ANALYTICS_DEBUG`   | `false`             | `true` logs analytics events to the browser console (for checking an integration). Keep `false` in production. |
| `SPRING_PROFILES_ACTIVE` | *(none)*            | Set to `prod` in production (see below). |
| `PORT`                   | `8080`              | HTTP port the app listens on. |
| `SPRING_DATASOURCE_URL`  | `jdbc:postgresql://localhost:5432/revoenglish` | JDBC URL of the PostgreSQL database. |
| `SPRING_DATASOURCE_USERNAME` | `revoenglish`   | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | *(none — required)* | Database password. **Secret.** |
| `ADMIN_BOOTSTRAP_LOGIN`  | *(empty)*           | One-time creation of the first administrator (see *Student portal*). |
| `ADMIN_BOOTSTRAP_PASSWORD` | *(empty)*         | Password for that administrator, ≥ 10 characters. **Secret — remove after the first start.** |
| `ADMIN_BOOTSTRAP_NAME`   | `Администратор`     | Display name for that administrator. |
| `PORTAL_TIME_ZONE`       | `Asia/Ho_Chi_Minh`  | Time zone of subscription start/end dates and the daily expiry job (Vietnam, UTC+7). Use `Europe/Moscow` for Moscow time. |

The same settings can be given as Spring properties (`site.telegram-username`, `site.base-url`,
`site.analytics-debug`, `server.port`) in `application.yaml` or on the command line.

Secrets: only `SPRING_DATASOURCE_PASSWORD` (permanently) and `ADMIN_BOOTSTRAP_PASSWORD` (once). Nothing secret is
committed; there is no default administrator and no default database password.

### Production profile (`prod`)

`application-prod.yaml` adds:

- `server.forward-headers-strategy: native` — the app trusts `X-Forwarded-*` headers from proxies on private/internal
  network addresses, so it knows requests arrived over HTTPS (needed for HSTS and correct URLs).
- Graceful shutdown (in-flight requests finish during a redeploy).

Always run production behind a reverse proxy or platform load balancer that **terminates HTTPS**.

---

## Deployment

The project builds into one self-contained executable jar. Any host that can run Java 25 works.

1. **Build** (locally or in CI):
   ```bash
   ./gradlew clean build
   ```
   Output: `build/libs/RevoEnglishWebsite-0.0.1-SNAPSHOT.jar`. GitHub Actions (`.github/workflows/build.yml`) runs
   the same build with all tests on every push to `master` and on every pull request — deploy only a green build.
2. **Copy** the jar to the server and **run** it with Java 25:
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   export SITE_BASE_URL=https://your-domain.example      # <-- PLACEHOLDER: your real domain
   export SITE_TELEGRAM_USERNAME=RevoWanDerLuciuse
   export SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/<db-name>   # <-- PLACEHOLDERS
   export SPRING_DATASOURCE_USERNAME=<db-user>
   export SPRING_DATASOURCE_PASSWORD=<db-password>
   java -XX:MaxRAMPercentage=75 -jar RevoEnglishWebsite-0.0.1-SNAPSHOT.jar
   ```
   Keep it running with your platform's process manager (systemd service, container platform, PaaS, …).
   On start, Flyway creates/updates the tables automatically. Back up the database regularly — it holds the accounts.
3. **Put HTTPS in front**: point your domain at a reverse proxy / load balancer that terminates TLS and forwards
   to the app's port, passing `X-Forwarded-For` and `X-Forwarded-Proto`. The login limit counts failed attempts per
   visitor address taken from `X-Forwarded-For`; without it every visitor would share the proxy's address.
   Example for nginx (adapt to your setup):
   ```nginx
   location / {
       proxy_pass         http://127.0.0.1:8080;
       proxy_set_header   Host              $host;
       proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
       proxy_set_header   X-Forwarded-Proto $scheme;
   }
   ```
4. **Health checks**: configure the platform to probe
   - `GET /actuator/health/liveness` → restart the app if it fails,
   - `GET /actuator/health/readiness` → send traffic only when it returns 200.

   Responses contain only `{"status":"UP"}`-style data. No other actuator endpoints are exposed over HTTP.
5. **Verify after deploy** (see checklist below).

### Container image (optional)

A `Dockerfile` is included for hosts that deploy containers (verified: the image builds, starts against PostgreSQL 17,
reports healthy and runs as a non-root user). It sets `SPRING_PROFILES_ACTIVE=prod`; pass the same environment
variables as above, at least the database ones:
```bash
docker build -t revo-english .
docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... -e SITE_BASE_URL=... revo-english
```
Note that the image build skips the tests (`bootJar`); rely on the CI build for those.

---

## Pre-launch checklist

- [ ] Decide the production domain and set `SITE_BASE_URL` to it (e.g. `https://your-domain.example`).
- [ ] Confirm `SITE_TELEGRAM_USERNAME` (default `RevoWanDerLuciuse`) opens the right chat: click a Telegram button.
- [ ] Set `SPRING_PROFILES_ACTIVE=prod` and serve the site only over HTTPS (the session cookie is HTTPS-only in `prod`).
- [ ] Create a PostgreSQL database and set `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`; set up backups.
- [ ] Create the first administrator with `ADMIN_BOOTSTRAP_*`, sign in once, then remove `ADMIN_BOOTSTRAP_PASSWORD`.
- [ ] Configure the health probes listed above.
- [ ] After deploy, open `https://<domain>/robots.txt` (must list the sitemap) and `https://<domain>/sitemap.xml`.
- [ ] View page source on `/`, `/english`, `/it-english`: `<link rel="canonical">` must use the real domain.
- [ ] Check a link preview (e.g. paste a URL into Telegram) — it shows the title, description and photo.
- [ ] Optionally submit the sitemap in Google Search Console / Yandex Webmaster.
- [ ] If you want analytics, connect a provider (see below) and update the CSP accordingly.
- [ ] Any non-production deployment (staging, preview) must keep `SITE_BASE_URL` empty so it is not indexed.

---

## Student portal

Private area for students. **There is no self-registration**: only an administrator (the teacher) creates accounts.

### First administrator (one time)

1. Start the app once with these environment variables (choose a strong password, ≥ 10 characters):
   ```bash
   export ADMIN_BOOTSTRAP_LOGIN=<your-login>          # e.g. emir or an e-mail address
   export ADMIN_BOOTSTRAP_PASSWORD=<strong-password>
   export ADMIN_BOOTSTRAP_NAME=Эмир                   # optional
   ```
2. The log says *"Administrator account created from ADMIN_BOOTSTRAP_* variables"* (the password is never logged).
3. **Remove `ADMIN_BOOTSTRAP_PASSWORD`** (and the other two) from the environment and restart.

If the login already exists, nothing is changed — restarting never resets the administrator's password, and a
student account is never turned into an administrator. To create another administrator later, use a new login.

### Creating a student account

1. Sign in at `/login` → **Ученики** (`/admin/students`).
2. Enter the student's name and a login (username or e-mail; latin letters, digits, `. _ - + @`).
3. Choose **«Сгенерировать временный пароль»** (recommended) or set an initial password yourself.
4. The next page shows the sign-in address, login and the temporary password **once**, plus a ready-made message —
   copy it and send it to the student personally (e.g. in Telegram). The password is not stored in plaintext anywhere.
5. At first sign-in the student must choose their own password before the dashboard opens.

On the same page you can **disable / enable** access (takes effect immediately, even for an open session) and issue a
**new temporary password** if a student forgets theirs.

### How it is secured

- Server-side authorization by role: `/admin/**` administrators only, `/dashboard/**` students only,
  `/account/**` any signed-in user; anonymous visitors are redirected to `/login`. Public pages stay public.
- Passwords are hashed with BCrypt (`{bcrypt}…` via Spring's delegating encoder); 10 characters to 72 bytes.
- Same message for unknown login, wrong password and disabled account. CSRF tokens on every form (login and logout too).
- Password guessing is limited: after 10 failed sign-ins from one address within 15 minutes, that address cannot try
  again until the 15 minutes have passed (`LoginThrottle`; counted per address, so nobody can lock the teacher out from
  elsewhere). The counters are kept in memory and reset on restart.
- Session cookie: HttpOnly, SameSite=Lax, HTTPS-only in `prod`; the session id changes at sign-in; 60-minute timeout.
- No account ids for "my" data in URLs — the dashboard uses the signed-in account only. The role is never read from a
  request; the student form has no role field.
- Accounts store only: name, login, password hash, role, active flag, "must change password" flag, creation time.

### Database

PostgreSQL, schema managed by **Flyway** (`src/main/resources/db/migration`, first migration
`V1__create_user_account.sql`). Flyway was added because the project had no migration tool and the schema will grow in
later steps; Hibernate only validates the schema (`ddl-auto: validate`). The app runs in UTC so that every host can
connect (the PostgreSQL driver sends the JVM time zone, and some Windows zone names are rejected by PostgreSQL).

## Subscriptions and lesson credits

Managed by the teacher on `/admin/students` → **Абонементы**. **The portal is an information tool, not a payment
system**: payment, cancellations and refunds are handled by the teacher directly; the portal only records what happened
and shows the student and the teacher the current state.

### Model

| Table | Holds |
|-------|-------|
| *(none)* | **Plan definitions** live in `PricingCatalog` (the same list the public pricing section shows): Start 4 × 60 min 6 000 ₽, Regular 8 × 60 min 11 200 ₽, Intensive 12 × 60 min 15 600 ₽. |
| `subscription` | One student's subscription: a **snapshot** of the plan at assignment (name, lessons, minutes, price in kopecks, `RUB`), status, start/end dates, complimentary reason, who created/activated/closed it and when. A later price change never alters existing subscriptions. |
| `subscription_payment` | The teacher's «Оплата получена» confirmation: plan price in kopecks, `RUB`, date and who confirmed. Never shown to students. |
| `lesson_credit_transaction` | Append-only ledger: `GRANT` (+lessons on activation), `CONSUME` (−1), `RESTORE` (+1, points at the consumption it reverses), `ADJUSTMENT` (±N, reason and administrator required). |

The **balance is never stored**: it is the sum of the subscription's ledger entries. Every ledger write first locks
the subscription row (`SELECT … FOR UPDATE`), checks the balance, then inserts — all in one transaction.
Money is stored as `BIGINT` kopecks with an explicit currency column; there is no floating point anywhere.
Schema: Flyway migration `V2__create_subscriptions_and_lesson_credits.sql` (checks and unique constraints mirror the rules).

### Life cycle

1. **Assign** a plan with start and end date → status **Ожидает оплаты**, no lessons yet.
2. When the student has paid you, click **«Оплата получена»** → **Активен**, the plan's lessons are granted and the
   confirmation (date, who) is kept in the history. Double clicks do nothing extra.
3. **Expires** the day after the end date: a daily job (00:05 in `PORTAL_TIME_ZONE`) marks it **Истёк**; until then it
   is already treated as expired everywhere. **«Отменить абонемент»** (optional comment) → **Отменён**.

**Complimentary** subscription: tick «Бесплатно», give a reason → price 0, active immediately, lessons granted.
Only one subscription can be in force per student; a new one can be activated once the current one has ended or
been cancelled.

### Lesson credits

- **Consume**: «Отметить проведённое занятие» (until the calendar exists) takes 1 lesson from the subscription active on
  the lesson date. `LessonCreditService.consumeLesson(student, lessonRef, date, …)` is the entry point the calendar
  will use with its own lesson reference; each reference can be charged only once.
- **Restore**: «Вернуть» next to a consumption (optional comment). Each consumption can be restored once.
- **Adjust**: ±1…50 lessons with a reason, only on the active subscription, never below zero.
- **Duplicates**: every form carries a one-time key; resubmitting the same form records nothing new (enforced by unique
  constraints in the database as well).
- **Authorization**: the URLs are administrator-only, and the services re-check the administrator role and that the
  subscription belongs to the student in the URL. Students only see their own plan on `/dashboard` (no prices).

### Handled by the teacher outside the portal

Payment itself, refunds, and which cancellations give a lesson back are the teacher's decisions; the portal records
the outcome («Оплата получена», «Отменить абонемент», «Вернуть», «Корректировка»). Unused lessons stay on an expired
subscription (visible in the history); carry them over with a positive adjustment if you decide to.
Default period: one calendar month (start → same day next month − 1); dates in `PORTAL_TIME_ZONE` (`Asia/Ho_Chi_Minh`).

## Pricing

Monthly plans of individual, online, 60-minute lessons are shown in the `#pricing` section on `/`, `/english` and
`/it-english` (shared fragment `templates/fragments/pricing.html`, styles in `static/css/pricing.css`).

| Plan      | Lessons / month | Per lesson (= per hour) | Per month | Savings vs. 1 500 ₽ per lesson |
|-----------|-----------------|-------------------------|-----------|--------------------------------|
| Start     | 4               | 1 500 ₽                 | 6 000 ₽   | —                              |
| Regular ★ | 8               | 1 400 ₽                 | 11 200 ₽  | 800 ₽                          |
| Intensive | 12              | 1 300 ₽                 | 15 600 ₽  | 2 400 ₽                        |

- **To change prices**, edit `PricingCatalog.PLANS` (lessons per month and price per lesson only). Monthly price,
  savings, hourly rate and the Telegram messages are calculated from those values in `PricingPlan`;
  `PricingPlanTests` pins the current figures, so update it together with the prices.
- Each «Выбрать тариф» button opens Telegram with a pre-filled draft, e.g.
  «Здравствуйте! Меня интересует тариф Regular — 8 занятий в месяц.» On `/english` and `/it-english` the path is part
  of the same message: «Здравствуйте! Меня интересует IT English, тариф Regular — 8 занятий в месяц.» The visitor edits
  and sends the message themselves.
- On the path pages, the main buttons (hero, exercise result, closing section) lead to `#pricing` instead of opening a
  separate chat, so a visitor ends up sending one message with both path and plan. A small «Написать без выбора тарифа»
  link and the header button still open a plain path message for questions. Homepage path cards also link to
  `/english#pricing` and `/it-english#pricing`.
- There is no payment processing: payment and scheduling are arranged in Telegram.
- **Open decision:** rules for unused lessons, cancellations, rescheduling and refunds are not defined. The site
  only says they are agreed before payment (see the `DECISION NEEDED` comment in `pricing.html`).

## SEO and social sharing

- Each page has its own `<title>`, meta description, Open Graph and Twitter Card (`summary`) tags (fragment `seo` in
  `templates/fragments/layout.html`).
- Absolute URLs (canonical, `og:url`, `og:image` = the tutor's photo `/images/emir.jpg`) are rendered only when
  `SITE_BASE_URL` is set, because they must contain the real domain.
- Error pages are always `noindex`; `/error` and `/actuator/` are disallowed in `robots.txt`.
- The sitemap lists `/`, `/english`, `/it-english` (`SeoController.PUBLIC_PATHS` — add new public pages there).

---

## Analytics

No analytics service is connected. `static/js/analytics.js` records these events and queues them until an
adapter is registered:

| Event               | Properties                         | When |
|---------------------|------------------------------------|------|
| `path_view`         | `page`, `path`                     | `/english` or `/it-english` opened |
| `exercise_start`    | `page`, `exercise`                 | first answer in an exercise |
| `exercise_complete` | `page`, `exercise`, `score`, `total` | result screen, or answering the IT scenario |
| `telegram_click`    | `page`, `cta` (button location, e.g. `pricing-regular`) | a click on any Telegram link |

A `telegram_click` only means the button was clicked — not that a message was sent or a student signed up.
Events never contain personal data (no names, message texts, Telegram drafts or IP addresses).

**Connecting a provider later** — create one small adapter module and load it on every page
(e.g. add `<script type="module" th:src="@{/js/analytics-adapter.js}"></script>` to the `common-head` fragment):

```js
// static/js/analytics-adapter.js
import { setAdapter } from '/js/analytics.js';
setAdapter(event => provider.track(event.name, event.props));   // replace with your provider's API
```

Prefer a privacy-friendly, cookie-less provider; a cookie-based one may require a consent banner. The site sends a
strict `Content-Security-Policy` (only same-origin scripts and connections), so add the provider's script and
endpoint domains to `script-src` / `connect-src` in `SecurityConfig.CONTENT_SECURITY_POLICY`.
Set `SITE_ANALYTICS_DEBUG=true` temporarily to see events in the browser console.

---

## Security and reliability notes

- The marketing pages are public; the student portal is protected by role (see *Student portal*). CSRF protection
  is enabled for every form.
- Response headers: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options: DENY`,
  `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`, and HSTS on HTTPS requests.
- Error pages are branded, in Russian, and never include stack traces, exception names or messages.
- Configuration values are validated at startup (`SiteProperties`); an invalid Telegram username or base URL stops
  the app with a clear message. Missing `SITE_BASE_URL` is logged as a warning.

## Performance notes

- Fonts (Cormorant Garamond, Manrope) are self-hosted variable WOFF2 files, Latin and Cyrillic subsets only
  (licences in `static/fonts/OFL-*.txt`).
- CSS, fonts, images and the favicon get content-hashed URLs and are cached for a year. JavaScript modules are
  revalidated on each visit (cheap `304` responses), because they import each other by relative path.
- HTML, CSS, JS, SVG and XML responses are gzip-compressed. The tutor photo (`static/images/emir.jpg`) is a
  720 × 960 JPEG of about 110 KB and is lazy-loaded. When replacing it, keep the 3:4 format and the file name:
  the page, the link previews (`og:image`) and the tests all expect `/images/emir.jpg`.

## Tests

`./gradlew test` covers page rendering, links and anchors, contextual Telegram drafts, exercise data, SEO tags,
robots/sitemap in both modes, security headers, caching, compression, error pages, CSRF and the health endpoint —
plus the student portal: access control per role, login/logout, forced password change, deactivation, account
creation, duplicate logins, password hashing, the failed-login limit, administrator bootstrap and that no password
appears in the logs —
and subscriptions: plan allocation and pricing, activation/expiry, balance calculation, consume/restore, adjustments,
duplicate submissions (including two at the same moment), rollback of a failed activation, ownership checks and
student access.
Tests use an in-memory H2 database in PostgreSQL mode (`src/test/resources/config/application.yaml`).
