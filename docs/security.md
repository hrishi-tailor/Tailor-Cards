# Security

How Tailor Cards protects the store, its admins and its customers: each control, the threat it addresses, and how to operate it. The repository is public, so nothing here relies on the code being secret.

## Before deploying this

**Set `TOTP_ENCRYPTION_KEY` on the backend first.** With two-factor login on (the default), the app refuses to start without it, the same way it refuses without `ADMIN_PASSWORD`. Render keeps the previous version running if a deploy fails to start.

```bash
openssl rand -base64 32
```

Then, on first sign-in after the deploy, the admin is asked to set up an authenticator app.

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `TOTP_ENCRYPTION_KEY` | none (required) | AES-256 key for TOTP secrets at rest. Keep it with your other secrets; changing it invalidates every enrolment |
| `ADMIN_2FA_ENABLED` | `true` | Set `false` only to recover access in an emergency |
| `TRUSTED_PROXY_HOPS` | `1` | Proxies in front of the backend that add to `X-Forwarded-For` (Render: 1) |

## 1. Secrets in git history

**Threat.** The repository is public, so anything ever committed (even if later removed) is readable by anyone.

**Check.** A [gitleaks](https://github.com/gitleaks/gitleaks) scan of all 106 commits on every branch, plus pattern searches for providers gitleaks has no rule for (Anthropic, RapidAPI, Supabase JWTs, Resend, Stripe live and webhook secrets, AWS, Turnstile).

**Result.**
- **No API keys** were found. gitleaks reported 4 Stripe matches, all the placeholder `sk_test_placeholder`.
- **Hard-coded admin passwords** were found: two different `ADMIN_PASSWORD` defaults in `application.yaml` between Sep 15 (`1e74840`, `a7795a9`) and Oct 6 (`2bae216`). They are still in the history.
- **Action:** rotate `ADMIN_PASSWORD` on Render, and anywhere the same password was reused.

Removing them from history (git filter-repo or BFG, then a force-push) is optional once rotated. It wasn't done here.

Re-run the scan any time:

```bash
gitleaks git --log-opts="--all" --redact -v .
```

## 2. Two-factor login for admins

**Threat.** A stolen, guessed or reused admin password gives full control: prices, offers, listings and customer data.

**Control.** After the password, every request made as ADMIN needs a second-factor session, which is only issued for a valid code. The flow:
- **Codes:** RFC 6238 TOTP, the standard used by Google Authenticator, 1Password and Authy: 6 digits, 30-second steps, ±1 step of clock drift. A code can't be used twice: the last accepted step is stored.
- **Setup:** happens on the login page. A QR code (otpauth URI) and the key to type in, confirmed with a first code.
- **Backup codes:** 10 single-use codes, shown once, for a lost phone.
- **Enforcement:** a filter runs after the password check on every request. Any ADMIN request without a live `X-Admin-Session` (12 hours) gets `401 two_factor_required`. Only `/api/auth/2fa/*` (setup and sign-in) and `/api/auth/verify` are exempt. Customer endpoints and the read-only DEMO role are not affected.

**Data at rest.**
- TOTP secrets are encrypted with AES-256-GCM under `TOTP_ENCRYPTION_KEY`, so a database leak alone doesn't yield working second factors.
- Backup codes are stored as bcrypt hashes and session tokens as SHA-256 hashes.

**Code:** `security/twofactor/*`. Migration V10.

**Recovery.**
- **Lost phone:** sign in with a backup code, then replace the backup codes from a new authenticator: `POST /api/auth/2fa/backup-codes`.
- **No codes left:** remove the enrolment directly in the database. The next sign-in starts setup again:

```sql
DELETE FROM admin_sessions WHERE username = 'admin';
DELETE FROM admin_backup_codes WHERE username = 'admin';
DELETE FROM admin_two_factor WHERE username = 'admin';
```

## 3. Login rate limiting and lockout

**Threat.** Guessing passwords or codes, either many guesses against one account or spraying across accounts.

**Control.**
- **Failures counted:** a wrong password or a wrong second-factor code.
- **Account lockout:** 5 failures within 15 minutes lock the account for 15 minutes.
- **IP block:** 20 failures from one IP, against any usernames, block that IP for 15 minutes.
- **Blocked before checking:** blocked requests get `429` with `Retry-After` before the password is checked, so guessing can't continue during a lockout, even with the right password and code.
- **What clears it:** only a complete sign-in (password and second factor). Knowing the password can't reset the count while guessing codes.

**Client IP.** It's the `X-Forwarded-For` entry added by the hosting proxy (counted from the right, `TRUSTED_PROXY_HOPS`). The left end is whatever the client sent and is ignored, so a spoofed header doesn't get around the block.

**Settings and code:** `app.security.login.*`, `security/login/*`.

**Trade-off.** Someone who knows the admin username can lock it out for 15 minutes. That's the usual cost of account lockout, kept short on purpose.

## 4. Security headers

**Threats.**
- Cross-site scripting: injected scripts running in the store or admin pages.
- Clickjacking: the site framed by another site.
- MIME sniffing.
- Leaking URLs through the Referer header.
- Downgrade to HTTP.
- Pages using browser features they don't need.

**API (Spring Security, every response including errors).**
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`. A JSON API never needs to run or be framed. Swagger UI gets its own policy allowing only its own scripts.
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`. This is sent on every response, because the app sits behind Render's TLS proxy and sees plain HTTP.
- `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cross-Origin-Opener-Policy: same-origin`.
- `Permissions-Policy` with every feature off.

**Frontend.** The same headers are in `frontend/vercel.json` and in the static site in `render.yaml`. A test fails if the two ever differ.

| Directive | Allows | Why |
| :--- | :--- | :--- |
| `script-src` | the site, `challenges.cloudflare.com` | No inline or eval'd scripts. The theme script moved from `index.html` to `/theme-init.js` |
| `style-src` | the site, Google Fonts, `'unsafe-inline'` | Inline styles only; scripts are never inline |
| `img-src` | the site, `data:`, `blob:`, any `https:` | Admins can use images from any host; images can't run code |
| `connect-src` | the site, the Render API, Turnstile | Change this if the API moves |
| `frame-src` | Turnstile | Its challenge iframe |
| `object-src`, `frame-ancestors` | none | |
| `form-action` | the site, Stripe Checkout | |

`upgrade-insecure-requests` is set as well.

Other frontend headers:
- `Permissions-Policy` allows only the camera, for the card scanner, on this site.
- `Referrer-Policy: strict-origin-when-cross-origin`.

Stripe uses hosted Checkout, reached by a full-page redirect, which CSP doesn't restrict. If Stripe.js is ever added, allow `https://js.stripe.com` in `script-src` and `frame-src`.

**Checked.** A production build was served with these exact headers. Home, shop (live products and images from the Render API), sell and admin pages loaded with no CSP violations.

## 5. Audit log of admin actions

**Threats.**
- Unnoticed or deniable changes to prices, offers or listings, whether by a compromised admin account or a mistake.
- Someone with access covering their tracks.

**What it records.** Every admin change, with who (account and role), what (action, record type and id), when, from where (IP and request), and the record as JSON before and after:
- **Prices and listings:** product create, update and delete; card linking; photos and official images; categories; price overrides; snapshot syncs.
- **Offers:** buylist decisions (accept, counter, reject); trade requests (approve, counter, decline); daily-limit resets.
- **Pricing rules:** buy rules, trade parameters, liquidity tags.
- **Account security:** two-factor enrolment and backup-code changes.

**Append-only at three levels.**
- **Entity:** `@Immutable`, with no setters.
- **Repository:** can only add and read; it has no update or delete methods.
- **Database:** on PostgreSQL, a trigger (Java migration V11.1) rejects `UPDATE`, `DELETE` and `TRUNCATE` on `admin_audit_log`. This is tested against a real PostgreSQL.

**Read it.** `GET /api/admin/audit-log` (ADMIN only), newest first. Filter with `?entityType=PRODUCT&entityId=42` or `?action=BUY_RULE_UPDATE`.

**Limit.** The database owner can still drop the trigger. For tamper evidence beyond the database, export the log on a schedule to storage the app can't write to.

## 6. Upload validation

**Threat.** Uploading something other than a photo: an HTML page or script served from our domain (stored XSS, phishing, malware hosting), or a "pixel bomb" that expands to gigabytes when decoded. There's also a privacy leak: photos carry the customer's GPS location and camera details.

**Before.** The public buylist upload, which needs no login, trusted the declared Content-Type and file name. So `card.jpg` containing HTML was accepted and served.

**Now.** Every image goes through one check, whatever the client claims:
1. **Size:** at most 10 MB; above that gets `413`.
2. **Type by content:** JPEG, PNG or WebP, recognised by magic bytes. The declared type and file name are ignored, so SVG, HTML, PDF, GIF and scripts are refused.
3. **Dimensions:** read from the header before anything decodes the pixels. At most 50 megapixels and 20,000 px on a side.
4. **Metadata stripped:** EXIF including GPS, XMP, comments and appended data are removed. The orientation is kept so photos stay upright.
5. **Stored name and type** come from the detected format, never from the upload.

**Coverage.** The public buylist upload, buylist chat and card scanner photos, product photos and the listing generator all go through `ListingImageSanitizer`. The storage service only ever stores sanitised bytes.

## 7. Error responses

**Threat.** Error messages leaking internals (SQL statements, class names, file paths) that help an attacker map the system.

**Control.** Unexpected errors return a generic message, with the details logged server-side. Spring's own client errors (wrong method, unknown path, unsupported media type) keep their real `4xx` status instead of becoming a `500`.

## 8. Dependency and code scanning (GitHub Actions)

| Workflow | What it catches | When |
| :--- | :--- | :--- |
| **Dependabot** (`.github/dependabot.yml`) | Outdated or vulnerable Maven, npm, Actions and Docker dependencies | Weekly grouped PRs; security updates as soon as an advisory is published (turn on "Dependabot security updates" in the repo settings) |
| **CodeQL** (`codeql.yml`) | Injection, XSS, path traversal, unsafe deserialisation, workflow injection (security-extended queries) in Java, TypeScript and the workflows | Pushes and PRs to `main`, weekly |
| **ZAP baseline** (`zap-baseline.yml`) | Missing or weak security headers, cookie flags, information leaks, reflected XSS and vulnerable JS libraries, on the running app | Pushes and PRs to `main`, weekly |

**How the ZAP job runs.** It starts the real app in CI: the backend with PostgreSQL, and the production frontend build served with the `vercel.json` headers by `frontend/scripts/serve-dist.mjs`. It never scans production. Rules marked `FAIL` in `.zap/rules.tsv` fail the job; accepted findings are reported as warnings with the reason.

**Local run, before committing.**
- **Frontend:** 0 failures, 62 checks passed.
- **API:** 0 failures, 62 passed.
- **It found a real problem:** Swagger UI bundled DOMPurify 3.1.6, which has known XSS CVEs. The Swagger UI webjar is now pinned to 5.33.2 (DOMPurify 3.4.16).

## Known limitations

- **Admin password in the browser.** The admin password is kept in browser storage as an HTTP Basic header, as before. An XSS bug could read it. The strict CSP makes XSS much harder, and the password alone no longer grants access. Moving to an HttpOnly, SameSite session cookie would remove the stored password entirely.
- **Lockout counts reset on restart.** They're held in memory and assume a single backend instance. Move them to the database (or Redis) before running several instances. The same applies to phone-link tokens.
- **Spoofable IP in the buylist's own limits.** The buylist chat's per-IP limits (`buylistchat/identity/ClientIp`) still take the left-most `X-Forwarded-For` entry, which a client can set. Switch them to `ClientAddress` with `TRUSTED_PROXY_HOPS`.
- **Open CORS.** CORS allows any origin. That's acceptable while authentication is a header rather than a cookie, because browsers don't attach it cross-site on their own. Tighten it to the store's origins if cookies are introduced.
