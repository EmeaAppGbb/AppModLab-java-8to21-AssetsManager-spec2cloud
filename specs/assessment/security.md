# Security Assessment

## Summary

- **Assessment depth**: Level 3 (Deep Analysis) — auto-escalated: Level 1 found 2 critical + 5 high → Level 2; Level 2 found auth/authz architectural gap → Level 3
- **Total findings**: 15
- **Critical**: 2 | **High**: 5 | **Medium**: 5 | **Low**: 3
- **OWASP categories affected**: A01, A02, A03, A04, A05, A07, A08
- **Categories clear**: A06 (components up-to-date), A09 (logging present), A10 (no SSRF vectors)

## Findings

### Critical

| # | OWASP | Finding | Location | Remediation | Effort |
|---|-------|---------|----------|-------------|--------|
| S1 | A01, A07 | **No authentication framework** — `spring-boot-starter-security` is not on the classpath. Zero authentication mechanism. All file operations (upload, view, download, delete) are anonymous. | `web/pom.xml` (absent dependency), all controllers | Add `spring-boot-starter-security`. Create `SecurityConfig` with `SecurityFilterChain` bean. Implement form login or OAuth 2.0. | 1 week |
| S2 | A01 | **No authorization enforcement** — No `@PreAuthorize`, `@Secured`, or `@RolesAllowed` annotations. No role-based access control. Any unauthenticated user can delete any file. | `StorageController.java` (all 6 endpoints) | Add method-level security annotations after S1. Define roles (e.g., VIEWER, EDITOR, ADMIN). Restrict delete to EDITOR+. | Days (after S1) |

### High

| # | OWASP | Finding | Location | Remediation | Effort |
|---|-------|---------|----------|-------------|--------|
| S3 | A01 | **No CSRF protection** — No Spring Security means no CSRF tokens on POST forms. Upload, delete forms are vulnerable to cross-site request forgery. | `upload.html:7`, `list.html:21`, `view.html:24` | Enabled automatically by Spring Security (S1). Thymeleaf auto-inserts CSRF tokens when Security is on classpath. | Automatic with S1 |
| S4 | A04 | **No server-side file type validation** — Upload accepts any file if client-side `accept="image/*"` is bypassed. No MIME type check, no magic-byte validation, no extension allowlist. | `CloudStorageService.uploadObject()`, `LocalFileStorageService.uploadObject()` | Add `validateUploadedFile()`: check content type against allowlist (`image/jpeg`, `image/png`, `image/gif`, `image/webp`), validate file magic bytes. | Hours |
| S5 | A01 | **Incomplete path traversal protection** — `validateKey()` checks for `..`, `/`, `\` as literal strings but doesn't handle URL-encoded bypass (`%2e%2e`), double encoding, or canonical path verification. | `StorageController.java:123-130` | Use `Path.normalize()` and verify resolved path stays within the allowed base directory. Spring's `UriUtils.decode()` before validation. | Hours |
| S6 | A03 | **XSS via exception messages** — Exception messages (e.g., `IOException.getMessage()`) are passed to Thymeleaf flash attributes and rendered in the UI. If message contains user input or HTML, XSS is possible. Thymeleaf `th:text` does HTML-escape, which mitigates most vectors, but defense-in-depth requires sanitized messages. | `StorageController.java:59,82,118`, `layout.html:32-38` | Replace `e.getMessage()` with generic user-friendly messages. Log the full exception server-side. | Hours |
| S7 | A05 | **No security headers** — Missing: `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security`, `Referrer-Policy`. Application is vulnerable to clickjacking, MIME-sniffing, and lacks HSTS. | `WebMvcConfig.java` (no header config) | Add via Spring Security's `headers()` DSL (automatic with S1), or add a custom `HandlerInterceptor` that sets all headers. | Hours |

### Medium

| # | OWASP | Finding | Location | Remediation | Effort |
|---|-------|---------|----------|-------------|--------|
| S8 | A08 | **Jackson deserialization without type restrictions** — `Jackson2JsonMessageConverter` uses default ObjectMapper with no trusted-type configuration. While `ImageProcessingMessage` is a simple record (low risk), the converter could deserialize unexpected types from the RabbitMQ queue. | `RabbitConfig.java:24-27` (both modules) | Configure `DefaultClassMapper` with explicit allowed types: `converter.setClassMapper(classMapper)` where `classMapper.setTrustedPackages("com.microsoft.migration.assets.common.model")`. | Hours |
| S9 | A05 | **Exception details exposed to users** — Stack trace content in `e.getMessage()` returned to the UI. May reveal: internal file paths, Azure SDK error details, database connection info. | `StorageController.java:59,82,118,132` | Return generic messages ("Upload failed", "File not found"). Log exceptions at ERROR level for operators. | Hours |
| S10 | A05 | **Swagger UI publicly accessible** — `/swagger-ui.html` and `/v3/api-docs` are unauthenticated. Reveals complete API structure, parameter types, and endpoint paths to potential attackers. | `web/application.properties:27-28` | Disable in production profile: `springdoc.swagger-ui.enabled=false`. Or restrict access to ADMIN role via Spring Security. | Minutes |
| S11 | A02 | **Default development credentials in config fallbacks** — Azurite storage key, `guest:guest` for RabbitMQ, and `postgres:postgres` for DB are used as `${ENV_VAR:default}` fallbacks. If env vars are not set in production, these insecure defaults activate. | `application.properties` (both modules) | Remove default values for sensitive properties. Use `${ENV_VAR}` without fallback so the app fails to start if credentials are missing. Or use Spring profile-specific configs (`application-prod.properties` without defaults). | Hours |
| S12 | A05 | **No session management configuration** — Default Tomcat session settings: no explicit timeout, no `HttpOnly`/`Secure`/`SameSite` cookie attributes. | Application config (absent) | Add: `server.servlet.session.timeout=15m`, `server.servlet.session.cookie.http-only=true`, `server.servlet.session.cookie.secure=true`, `server.servlet.session.cookie.same-site=strict`. | Minutes |

### Low

| # | OWASP | Finding | Location | Remediation | Effort |
|---|-------|---------|----------|-------------|--------|
| S13 | A05 | **File operation logging may expose sensitive data** — Request URIs and error messages logged at INFO level. Logs may contain storage keys that are user-identifiable. | `WebMvcConfig.FileOperationLoggingInterceptor` | Mask or truncate storage keys in log output. Ensure log files are access-controlled. | Hours |
| S14 | A05 | **HTTP used for Azurite connection** — Default connection string uses `http://` (not HTTPS) for Azurite dev emulator. If this default accidentally runs in production, storage traffic is unencrypted. | `application.properties:4` (both modules) | Use HTTPS endpoints for production Azure Storage. The Azurite default is only safe for local development. | Minutes |
| S15 | A05 | **DevTools on classpath** — `spring-boot-devtools` is a runtime dependency in the web module. While it auto-disables in packaged JARs, it should be excluded from production builds. | `web/pom.xml` | Already `<optional>true</optional>` and Spring Boot disables it in production JARs. Low risk but verify in deployment. | None |

## OWASP Top 10 Coverage

| OWASP ID | Category | Findings | Status |
|----------|----------|----------|--------|
| A01 | Broken Access Control | S1, S2, S3, S5 | 🔴 Critical gap — no auth, no CSRF |
| A02 | Cryptographic Failures | S11, S14 | 🟡 Default dev credentials in fallbacks |
| A03 | Injection | S6 | 🟡 XSS risk via exception messages (mitigated by Thymeleaf escaping) |
| A04 | Insecure Design | S4 | 🟠 No server-side file type validation |
| A05 | Security Misconfiguration | S7, S9, S10, S12, S13, S15 | 🟠 No security headers, session config, Swagger exposed |
| A06 | Vulnerable and Outdated Components | — | ✅ Clear — Spring Boot 3.4.4, Java 21, all deps current |
| A07 | Identification and Authentication Failures | S1 | 🔴 No authentication mechanism |
| A08 | Software and Data Integrity Failures | S8 | 🟡 Jackson type restrictions needed |
| A09 | Security Logging and Monitoring Failures | — | ✅ Clear — SLF4J logging present, file ops intercepted |
| A10 | Server-Side Request Forgery | — | ✅ Clear — no user-controlled URL requests |

## Remediation Roadmap

### Phase 1: Critical — No Deployment Without These

```
S1 (Add Spring Security) ──→ S2 (Authorization annotations)
                          ──→ S3 (CSRF — automatic with S1)
                          ──→ S7 (Security headers — automatic with S1)
                          ──→ S10 (Swagger restricted to ADMIN)
                          ──→ S12 (Session config)
```

**ADR required**: Authentication mechanism choice (form login, OAuth 2.0/OIDC, Azure AD). See ADR-005.

### Phase 2: High — Fix Before Production

```
S4 (Server-side file validation) — independent
S5 (Path traversal hardening)    — independent
S6 (Sanitize error messages)     — independent
S9 (Generic error responses)     — pairs with S6
S11 (Remove credential defaults) — independent
```

### Phase 3: Medium — Harden

```
S8 (Jackson trusted types) — independent
S13 (Log sanitization)     — independent
S14 (HTTPS for storage)    — deployment config
```

## Decision Points

| Decision | Impact | Recommendation |
|----------|--------|----------------|
| **Authentication mechanism** | Determines S1 implementation (form login vs OAuth vs Azure AD) | ADR-005 needed. For internal apps: Azure AD/OIDC. For public apps: form login + registration. |
| **Authorization model** | Determines S2 implementation (RBAC vs ABAC) | RBAC is sufficient for this application (VIEWER, EDITOR, ADMIN roles). |
| **Swagger access policy** | S10 — disable vs restrict | Restrict to ADMIN role in production; disable entirely if no API consumers. |

## Positive Findings

| Area | Status | Evidence |
|------|--------|----------|
| SQL Injection | ✅ Secure | Spring Data JPA with derived queries — parameterized automatically |
| Command Injection | ✅ Secure | No `Runtime.exec()`, `ProcessBuilder`, or shell execution |
| SSRF | ✅ Secure | No user-controlled URL requests. Only Azure SDK + RabbitMQ client calls |
| Dependencies | ✅ Current | Spring Boot 3.4.4, Java 21, Azure SDK 12.29.0 — all up-to-date |
| Secrets in repo | ✅ Clean | No `.env`, `.pem`, `.key`, `.p12`, `.jks` files committed |
| UUID generation | ✅ Secure | `UUID.randomUUID()` (cryptographically secure) for storage keys |
| JPA DDL mode | ✅ Secure | `ddl-auto=validate` with Flyway migrations — no auto schema changes |
| SQL logging | ✅ Secure | `show-sql=false` — no query leakage in logs |
