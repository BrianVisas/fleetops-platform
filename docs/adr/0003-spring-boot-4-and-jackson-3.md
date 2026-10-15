# ADR-0003: Stay on Spring Boot 4, and what that costs

- Status: accepted
- Date: 2026-10-15

## Context

Spring Initializr generated `fleet-api` on Spring Boot 4.1.1 (Spring Framework 7).
Almost all tutorials, Stack Overflow answers and generated examples still assume
Boot 3, and three differences cost real time during package 2:

1. Test slice annotations moved. `@WebMvcTest` is now
   `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, not
   `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`.
2. Test slices live in their own starters. `@WebMvcTest` is only on the classpath
   with `spring-boot-starter-webmvc-test` (test scope) declared; `spring-boot-starter-test`
   alone is not enough.
3. Jackson 3 is the default. Its packages are `tools.jackson.*`, so
   `com.fasterxml.jackson.databind.ObjectMapper` does not resolve unless Jackson 2 is
   added back explicitly.

## Options considered

1. **Pin to Spring Boot 3.5.x.** Every example found online would apply directly.
   But the 3.5 line is past its free support window, so the dependency scanning in
   Phase 2 would flag the framework itself, and the project would start life on an
   unsupported version.
2. **Stay on 4.1.1.** Current and supported. Cost: external examples need
   translating, and the official API docs become the first reference rather than
   blog posts.

## Decision

Stay on Spring Boot 4.1.1. Declare `spring-boot-starter-webmvc-test` for the web
test slice. Avoid Jackson's API in test code altogether: request bodies in MockMvc
tests are written as text blocks instead of being serialised, which removes the
Jackson 2/3 question from tests entirely.

## Consequences

- Import paths come from the Boot 4 API docs, not from examples found by search.
- Any later need for Jackson in application code uses `tools.jackson.*`; adding
  Jackson 2 back is a conscious decision, not a reflex.
- Test bodies as text blocks are more verbose but show the exact payload a client
  sends, which makes the API tests in Phase 3 easier to keep consistent.
- Each new test slice (`@DataJpaTest` and friends) may need its own starter; check
  before assuming `spring-boot-starter-test` covers it.
