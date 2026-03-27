# ADR-005: Add Authentication and Authorization via Spring Security

## Status

Proposed

## Context

The security assessment (Level 3) found that the application has **zero authentication or authorization**. All endpoints — including file upload, download, and deletion — are publicly accessible. This is the most critical security finding (S1, S2) affecting OWASP categories A01 (Broken Access Control) and A07 (Identification and Authentication Failures).

### Options Considered

#### Option A: Spring Security with Form Login

- **Pros**: Simple to implement; built-in login/logout pages; session-based; works with server-rendered Thymeleaf UI
- **Cons**: Requires user management (registration, password reset); session state on server

#### Option B: Spring Security with Azure AD / Entra ID (OIDC)

- **Pros**: Leverages existing Azure identity infrastructure; SSO with organizational accounts; no password management; MFA built-in
- **Cons**: Requires Azure AD tenant; more complex setup; dependency on Azure identity services

#### Option C: Spring Security with OAuth 2.0 (Generic)

- **Pros**: Standard protocol; supports multiple identity providers; stateless with JWT
- **Cons**: More complex than form login; requires an identity provider; may be overkill for this app

## Decision

**Deferred — requires user input.** The authentication mechanism must be chosen based on the deployment context:
- **Internal/enterprise deployment** → Option B (Azure AD) recommended
- **Public-facing application** → Option A (Form Login) recommended
- **API-first / microservice** → Option C (OAuth 2.0) recommended

## Consequences

Whichever option is chosen, Spring Security will automatically provide:
- CSRF protection (Thymeleaf auto-inserts tokens)
- Security headers (CSP, X-Frame-Options, HSTS, etc.)
- Session management with secure cookie defaults
- Login/logout flows

This resolves findings S1, S2, S3, S7, and S12 from the security assessment.

## References

- Security assessment findings: S1 (no auth), S2 (no authz), S3 (no CSRF), S7 (no headers), S12 (no session config)
- Spring Security reference: https://docs.spring.io/spring-security/reference/
- Spring Cloud Azure AD: https://learn.microsoft.com/en-us/azure/developer/java/spring-framework/configure-spring-boot-starter-java-app-with-azure-active-directory
