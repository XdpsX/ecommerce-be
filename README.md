# Ecommerce Backend

Spring Boot backend for a personal E-commerce learning project.

## Run locally

Requirements:

- Java 25
- Docker with Docker Compose

Create the local environment file:

```powershell
Copy-Item .env.example .env
```

Update the placeholder credentials in `.env` when the related integrations are needed. The file is ignored by Git.

Start MySQL only:

```powershell
docker compose up -d mysql
```

The initialization script creates the `ecommerce` database the first time MySQL starts with an empty volume.

Run the application with the `dev` profile configured in `.env`:

```powershell
.\mvnw.cmd spring-boot:run
```

The API runs at `http://localhost:8080` by default. Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

Async Cloudinary eager processing posts signed notifications to `POST /webhooks/cloudinary/eager`. For end-to-end
local testing, set `CLOUDINARY_EAGER_NOTIFICATION_URL` in `.env` to a public HTTPS tunnel forwarding to that path.
The default two-hour signature timestamp tolerance can be changed with
`CLOUDINARY_WEBHOOK_TIMESTAMP_TOLERANCE`. Unit tests use mocked Cloudinary responses and signed webhook fixtures;
no tunnel is required for the normal test suite.

To build and run both MySQL and the application in Docker:

```powershell
docker compose -f compose.local.yml up -d --build
```

The application container connects to MySQL through the Compose service name `mysql`.

## Bootstrap the first local admin

Public registration always creates a `USER`. The dev-only startup runner can promote a registered local account without embedding an admin password in the repository:

1. Register through `POST /auth/register` with the intended name, email, and password. Use an email you control. Registration creates a `LOCAL` `USER`; local email identities are trimmed and lowercased.
2. In the ignored `.env` file, set `APP_BOOTSTRAP_ADMIN_ENABLED=true` and `APP_BOOTSTRAP_ADMIN_EMAIL` to that account's canonical email. Restart the application with the `dev` profile. The runner only acts when both the `dev` profile and explicit opt-in are active. If the account does not exist yet, it logs that no account was found and makes no change; register it and restart. It skips accounts that are not `LOCAL`.
3. Set `APP_BOOTSTRAP_ADMIN_ENABLED=false` (or remove the setting) after promotion so future starts do not reapply the bootstrap action.
4. Sign in again with `POST /auth/login` to get a fresh access token. Existing tokens retain the role claim they were issued with.
5. Send the new token as `Authorization: Bearer <access-token>` to `GET /users/me` and confirm the response has `"role":"ADMIN"`. Then call `GET /admin/brands`; a `200` response confirms access (the list may be empty).

For a manual database alternative, connect to the local `ecommerce` MySQL database as an operator authorized to change user roles. First confirm the target row is the intended `LOCAL` account with role `USER`:

```sql
SELECT id, email, auth_provider, role
FROM users
WHERE email = 'admin@example.com';
```

Replace `admin@example.com` below with the registered email in canonical form. Continue only if the query returns exactly one row and its provider and role are `LOCAL` and `USER`. In the same database session, run the guarded update and check that exactly one row was updated before committing; otherwise roll back and investigate.

```sql
START TRANSACTION;

UPDATE users
SET role = 'ADMIN'
WHERE email = 'admin@example.com'
  AND auth_provider = 'LOCAL'
  AND role = 'USER';

SELECT ROW_COUNT() AS updated_rows;
```

Use the same canonical email in both statements. In that session, run `COMMIT;` only when `updated_rows` is exactly `1`; otherwise run `ROLLBACK;`. The `users.role` column stores `ADMIN`/`USER` as text, and the local provider is stored as `LOCAL`.

If registration reports that the email already exists, inspect that exact row before proceeding. Promote it only if it is the intended `LOCAL` `USER` account. If the row is missing, belongs to `GOOGLE`, is already in another role, or the email is not the intended identity, stop and resolve the account/email with an authorized operator; do not change a different row or create a duplicate. Never use a token issued before promotion to verify admin access.

## Verify changes

```powershell
.\mvnw.cmd spotless:check verify
```

Use `spotless:apply` explicitly when source formatting is needed:

```powershell
.\mvnw.cmd spotless:apply
```

Stop the local database without deleting its data:

```powershell
docker compose -f compose.local.yml down
```
