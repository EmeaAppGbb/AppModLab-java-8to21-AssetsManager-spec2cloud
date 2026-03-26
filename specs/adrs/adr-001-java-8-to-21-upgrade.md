# ADR-001: Upgrade from Java 8 to Java 21

## Status

Proposed

## Context

The project currently targets **Java 8** (`<java.version>8</java.version>` in the parent POM). Java 8 public updates ended in January 2019 (Oracle JDK). The project name (`java-8to21`) explicitly indicates Java 21 as the target.

### Modernization Assessment Findings

- **D2** (Critical): Java 8 is past end of public updates
- **D3-D5** (High): javax namespace removal requires Java 17+ (for Jakarta EE 9+)
- **P4** (Medium): Records (Java 16+), switch expressions (Java 14+), pattern matching (Java 21) would improve code quality

### Options Considered

#### Option A: Java 17 (Minimum for Spring Boot 3)

- **Pros**: Minimum version required for Spring Boot 3.x; smaller upgrade jump
- **Cons**: Only supported until Sep 2027 (Premier); misses Java 21 features (virtual threads, pattern matching for switch, record patterns, sequenced collections)

#### Option B: Java 21 LTS (Recommended)

- **Pros**: Latest LTS; supported until Sep 2031; unlocks all modern Java features; aligns with project name
- **Cons**: Larger jump from Java 8 (though all changes are incremental)

#### Option C: Java 25 LTS (Upcoming)

- **Pros**: Newest LTS, expected Sep 2025
- **Cons**: Not yet released; Spring Boot may not fully support it at launch; unnecessary risk

## Decision

**Upgrade to Java 21 LTS** (Option B).

## Rationale

1. Java 21 is the current LTS with Premier Support until Sep 2028 and Extended Support until Sep 2031.
2. The project name (`java-8to21`) explicitly targets Java 21.
3. The devcontainer already has a `jdk21/` configuration ready.
4. Java 21 unlocks: records, sealed classes, pattern matching, switch expressions, virtual threads, text blocks, and sequenced collections.
5. The codebase is small (~1200 lines of Java) — the upgrade effort is low.
6. No Java 8-specific APIs are used that were removed in later versions.

## Consequences

- **Positive**: Modern language features, better performance, long-term support, Spring Boot 3 compatibility
- **Negative**: Developers must use JDK 21+; CI/CD must provision JDK 21
- **Migration effort**: 1–2 days for language-level changes (update POM, fix any source incompatibilities)
- **Dependency**: Must complete before Spring Boot 3.x upgrade (ADR-002)

## References

- Assessment findings: D2, D3, D4, D5, P4, P5
- Oracle Java SE Support Roadmap: https://www.oracle.com/java/technologies/java-se-support-roadmap.html
