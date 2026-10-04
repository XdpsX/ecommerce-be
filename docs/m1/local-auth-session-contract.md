# LOCAL Authentication Session Contract

This contract applies to local email/password authentication. Google OAuth remains on its existing redirect and
access-token flow and does not create a refresh session or refresh cookie.

## Register and login

`POST /auth/register` and `POST /auth/login` return the existing JSON shape:

```json
{
  "accessToken": "<short-lived JWT>"
}
```

They also set a host-only `HttpOnly` refresh cookie. The default cookie name is `refresh_session`, its path is `/auth`,
its `SameSite` value is `Strict`, and `Secure` is enabled outside the development profile. The cookie value is opaque
and is never returned in JSON, persisted directly, logged, or included in an error.

LOCAL access tokens last 15 minutes by default. A successful registration or login creates an independent 30-day
absolute refresh session; logging in again does not replace earlier sessions.

## Refresh

`POST /auth/refresh` requires the configured `X-Session-Request: 1` header and a credentialed request carrying the
refresh cookie. On success it returns the same access-token body and rotates the cookie credential exactly once. The
cookie `Max-Age` is calculated from the original session expiry, so rotation does not extend the 30-day lifetime.

Malformed, unknown, expired, revoked, reused, and secret-mismatch credentials all return `401` with
`INVALID_REFRESH_CREDENTIAL`. The response does not disclose which state occurred. Reuse of a superseded credential
revokes the affected session after the row-locked transaction completes.

## Logout

`POST /auth/logout` requires the same guard header. It attempts to revoke only the session represented by the current
cookie, returns `204 No Content`, and always expires the refresh cookie. Logout does not blacklist already-issued access
tokens; they remain valid until their normal short expiry.

## Browser and deployment requirements

Frontend calls to refresh and logout must be credentialed and include the guard header. Credentialed CORS remains
restricted to `app.cors.allowed-origins`. The guard header is a browser CSRF boundary, not an authentication mechanism
for non-browser clients.

The normal deployment is same-site and uses `SameSite=Strict`. A genuinely cross-site frontend must configure
`SameSite=None` and `Secure=true` over HTTPS. Configure the issuer, lifetimes, cookie attributes, and guard header
through `app.auth-session.*`.
