# ADR-002: Upgrade from Spring Boot 2.7 to Spring Boot 3.x

## Status

Proposed

## Context

The project uses **Spring Boot 2.7.18** as the parent POM. Spring Boot 2.7 reached OSS end-of-life on **June 30, 2023** and no longer receives community security patches. The latest Spring Boot version is 3.5.12.

### Modernization Assessment Findings

- **D1** (Critical): Spring Boot 2.7.18 is EOL
- **D3** (High): javax.persistence → jakarta.persistence (required by Spring Boot 3)
- **D4** (High): javax.servlet → jakarta.servlet (required by Spring Boot 3)
- **D5** (High): javax.annotation.PostConstruct → jakarta.annotation
- **P1** (High): WebMvcConfigurerAdapter deprecated since Spring 5.0
- **P2** (High): HandlerInterceptorAdapter deprecated since Spring 5.3

### Options Considered

#### Option A: Spring Boot 3.4.x (Stable, Well-Documented)

- **Pros**: Mature migration guides; known issues documented; stable in production
- **Cons**: Will eventually reach EOL (but has years of support remaining)

#### Option B: Spring Boot 3.5.x (Latest)

- **Pros**: Latest features and bug fixes; longest remaining support window
- **Cons**: Newest major release; migration guides may be less comprehensive for 3.5-specific changes

#### Option C: Stay on Spring Boot 2.7 with Commercial Support

- **Pros**: No migration effort; commercial support available until Jun 2029
- **Cons**: Paid support; blocks adoption of modern Java features; growing dependency risk; no community patches

## Decision

**Upgrade to Spring Boot 3.4.x** (Option A).

## Rationale

1. Spring Boot 3.4.x is stable with well-documented migration paths.
2. The javax → jakarta namespace migration is the primary breaking change, which is mechanical and well-tooled.
3. Official migration guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide
4. OpenRewrite and Spring Boot Migrator provide automated migration recipes.
5. The codebase is small — manual migration is feasible alongside automated tooling.
6. The deprecated adapters (WebMvcConfigurerAdapter, HandlerInterceptorAdapter) are removed in Spring 6 (used by SB3), forcing the fix.

## Migration Checklist

1. ✅ Upgrade Java to 21 (ADR-001 — prerequisite)
2. Replace all `javax.persistence` → `jakarta.persistence`
3. Replace all `javax.servlet` → `jakarta.servlet`
4. Replace all `javax.annotation` → `jakarta.annotation`
5. Replace `WebMvcConfigurerAdapter` → implement `WebMvcConfigurer`
6. Replace `HandlerInterceptorAdapter` → implement `HandlerInterceptor`
7. Update `spring-boot-starter-parent` version to 3.4.x
8. Update `hibernate.dialect` property (auto-detected in SB3, can be removed)
9. Run full test suite and verify
10. Update devcontainer to use JDK 21

## Consequences

- **Positive**: Security patches, modern Jakarta EE, performance improvements, GraalVM native image support
- **Negative**: Breaking change for javax → jakarta imports; all dependencies must support Jakarta namespace
- **Migration effort**: 1–2 weeks (including testing and validation)
- **Dependency**: Requires Java 21 upgrade (ADR-001) to be completed first

## References

- Assessment findings: D1, D3, D4, D5, P1, P2
- Spring Boot 3.0 Migration Guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide
- OpenRewrite Spring Boot 3 recipes: https://docs.openrewrite.org/recipes/java/spring/boot3
