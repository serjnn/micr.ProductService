# Product Service — Production-Readiness & Perfection Roadmap (TODO)

This document provides a comprehensive, prioritized gap analysis and actionable checklist of everything required to elevate the **Product Service** from a functional proof-of-concept to an enterprise-grade, resilient, secure, observable, and fully tested production service.

---

## 🎯 Executive Summary & Priority Matrix

| Level | Focus Area | Description |
| :--- | :--- | :--- |
| 🔴 **P0: Critical** | Reliability, Bugs & Data Integrity | Immediate fixes for data loss risks, mathematical exceptions, missing DB constraints, and thread pool deadlocks. |
| 🟠 **P1: High** | Architecture, Resilience & Validation | Modernized persistence, declarative validation, RFC 7807 error handling, DLT Kafka handlers, and bulk cache optimization. |
| 🟡 **P2: Medium** | API Completeness & Security | Missing CRUD/subscription endpoints, Spring Security / OAuth2, OpenAPI specs, and structured pagination. |
| 🟢 **P3: Low / Future** | Observability, CI/CD & DevOps | ArchUnit, contract testing, mutation tests, JaCoCo quality gates, Dockerfile hardening, and KRaft migration. |

---

## 🔴 Phase 1: Critical Bug Fixes & Reliability Safeguards (P0)

- [x] **1.1. Fix Arithmetic Precision and Scale in Discount Calculation**
  - **Issue:** In [`DiscountService.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/services/DiscountService.java#L26-L42), `discount.divide(BigDecimal.valueOf(100))` does not define explicit scale and rounding mode, which can throw `ArithmeticException: Non-terminating decimal expansion` for non-terminating quotients.
  - **Action:** Enforce explicit scale and rounding: `discount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)` and encapsulate money math in a dedicated value object / domain helper.

- [x] **1.2. Eliminate Kafka Auto-Commit Data Loss Risk**
  - **Issue:** [`application.yaml`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/resources/application.yaml#L9-L10) enables `enable-auto-commit: true` with `auto-commit-interval: 100`. Offsets are committed periodically regardless of processing success, leading to silent message loss on pod restarts/crashes.
  - **Action:** Set `enable-auto-commit: false` and configure `AckMode.RECORD` or `AckMode.MANUAL_IMMEDIATE` in [`KafkaConsumerConfiguration.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/kafka/consumer/KafkaConsumerConfiguration.java).

- [x] **1.3. Implement Kafka Dead Letter Topic (DLT) & Error Recovery**
  - **Issue:** Consumer listener lacks a `DefaultErrorHandler` with backoff and DLT publisher. A poisoned message (malformed JSON or processing failure) causes infinite retries or halts consumption.
  - **Action:** Configure `DefaultErrorHandler` with exponential backoff (e.g. 3 retries, initial interval 1s, multiplier 2.0) and route unrecoverable failures to a dedicated `discountChangesTopic.DLT`.

- [x] **1.4. Fix Missing Database Indexes and Constraints**
  - **Issue:** In [`V1__init_schema.sql`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/resources/db/migration/V1__init_schema.sql):
    - `subscribers` table lacks an index on `product_id` (causes full table scans when fetching subscribers on discount updates).
    - `subscribers` table lacks a composite unique constraint on `(product_id, client_id)`, allowing duplicate subscriptions and redundant notifications.
    - `product` table lacks an index on `category`.
    - Type mismatch: `product.id` uses 32-bit `SERIAL` while `subscribers.product_id` is `BIGINT`.
  - **Action:** Add a Flyway migration `V2__add_indexes_and_constraints.sql`:
    - Add `UNIQUE (product_id, client_id)` on `subscribers`.
    - Add `CREATE INDEX idx_subscribers_product_id ON subscribers (product_id)`.
    - Add `CREATE INDEX idx_product_category ON product (category)`.
    - Migrate primary keys to `BIGINT GENERATED ALWAYS AS IDENTITY`.

- [x] **1.5. Configure Thread Pool Rejection Policy & Backpressure**
  - **Issue:** In [`AsyncConfig.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/config/AsyncConfig.java#L16-L25), `ThreadPoolTaskExecutor` uses the default `AbortPolicy`. Under heavy discount update bursts, notifications will fail with `RejectedExecutionException`.
  - **Action:** Configure `executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())` and implement graceful shutdown with `executor.setWaitForTasksToCompleteOnShutdown(true)`.

- [x] **1.6. Align Server Port Configuration**
  - **Issue:** [`application.yaml`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/resources/application.yaml#L64) defines default port `7022` while [`README.md`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/README.md#L28-L86) mentions `7002`.
  - **Action:** Standardize on a single port across configuration, documentation, and Docker configs.

---

## 🟠 Phase 2: Architecture & Data Access Modernization (P1)

- [x] **2.1. Migrate and Enhance Repository Abstractions**
  - **Action:** Add `update`, `deleteById`, `existsById`, `searchByNameOrDescription`, `findProductIdsByClientId`, and `existsByProductIdAndClientId` helper methods on repositories with idempotent operations.

- [x] **2.2. Implement Explicit Transaction Management**
  - **Issue:** Services and repositories currently have no `@Transactional` or `@Transactional(readOnly = true)` boundary annotations.
  - **Action:** Add `@Transactional` across mutating service workflows (e.g. `ProductService.add`, `SubscriptionService.subscribe`) and `@Transactional(readOnly = true)` for read methods.

- [x] **2.3. Introduce Domain Exception Hierarchy & Business Codes**
  - **Issue:** Direct usage of `ResponseStatusException(HttpStatus.NOT_FOUND)` in controller and lack of domain-specific exceptions.
  - **Action:** Create domain exceptions:
    - `ProductNotFoundException`
    - `DuplicateSubscriptionException`
    - `SubscriptionNotFoundException`
    - `InvalidDiscountException`
    - `ProductServiceException`

- [ ] **2.4. Audit Fields and Entity Lifecycle**
  - **Issue:** No entity auditing or tracking of when records were created or modified.
  - **Action:** Add `created_at`, `updated_at`, and `@Version` (optimistic locking) to domain entities and tables.

---

## 🟠 Phase 3: Validation & Error Handling (RFC 7807) (P1)

- [x] **3.1. Add Bean Validation to DTOs and Controllers**
  - **Issue:** `spring-boot-starter-validation` is absent in `pom.xml`. Endpoints in [`ProductController.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/controller/ProductController.java) accept unvalidated payloads.
  - **Action:**
    - Add `spring-boot-starter-validation` dependency.
    - Annotate [`Product`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/models/Product.java) record: `@NotBlank` name, `@Positive` price, `@NotNull` category, `@Size(max = 1000)` description.
    - Annotate [`IdsRequest`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/dtos/IdsRequest.java): `@NotEmpty List<@NotNull @Positive Long> ids`.
    - Add `@Valid` to `@RequestBody` arguments in [`ProductController.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/controller/ProductController.java).
    - Add `@Positive` validation to path variables (`id`, `productId`, `clientId`).

- [x] **3.2. Implement Global Exception Handler (`@RestControllerAdvice`)**
  - **Issue:** Unhandled exceptions return generic Spring Boot whitelabel or default stack traces.
  - **Action:** Implement `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler`:
    - Handle `MethodArgumentNotValidException` with field-by-field error details.
    - Handle domain exceptions (`ProductNotFoundException` -> 404, `DuplicateSubscriptionException` -> 409, `SubscriptionNotFoundException` -> 404).
    - Adopt RFC 7807 standard (`org.springframework.http.ProblemDetail`) supported natively in Spring Boot 3.

---

## 🟠 Phase 4: Caching & Distributed Resilience (P1)

- [x] **4.1. Resolve N+1 External & Cache Lookups on Batch Queries**
  - **Action:** Enriched batch lookups and validated discount mapping flow.

- [x] **4.2. Prevent Cache Stampede / Dogpiling**
  - **Issue:** [`DiscountCacheManager.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/redis/DiscountCacheManager.java#L32) uses `@Cacheable(value = "discounts", key = "#productId")` without synchronization. On high-traffic cache expiry, numerous concurrent requests will simultaneously hit the downstream discount service.
  - **Action:** Enable `sync = true` on `@Cacheable(value = "discounts", key = "#productId", sync = true)`.

- [x] **4.3. Implement Redis Cache Error Handler (Fault-Tolerant Cache)**
  - **Issue:** If Redis goes down, `@Cacheable` methods throw exceptions and fail the entire HTTP request.
  - **Action:** Configure a custom `CacheErrorHandler` (extending `SimpleCacheErrorHandler`) that logs cache read/write failures as warnings and falls back directly to the downstream source without interrupting request flow.

- [x] **4.4. Configure HTTP Client Timeouts & Connection Pooling**
  - **Issue:** [`RestClient.Builder`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/redis/RedisConfig.java#L47-L50) uses default unbounded connection/read timeouts.
  - **Action:** Configure `SimpleClientHttpRequestFactory` with explicit connection timeout (3s) and read timeout (5s).

- [x] **4.5. Separate Cache TTL per Cache Namespace**
  - **Action:** Configure cache-specific expiration in [`RedisConfig.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/redis/RedisConfig.java) (30 minutes for discounts, 1 hour default).

---

## 🟠 Phase 5: Messaging & Event-Driven Architecture (P1)

- [x] **5.1. Standardize Package Naming Conventions**
  - **Issue:** Packages `kafkaConsumer` and `kafkaProducer` use camelCase against standard Java conventions.
  - **Action:** Rename to `com.serjnn.ProductService.kafka.consumer` and `com.serjnn.ProductService.kafka.producer`.

- [x] **5.2. Define Kafka Partitioning Key Strategy**
  - **Issue:** In [`KafkaSender.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/kafka/producer/KafkaSender.java#L20), `kafkaTemplate.send(topicName, discountNotification)` sends records without a key.
  - **Action:** Pass `String.valueOf(discountNotification.productId())` as the Kafka message key to preserve partition ordering.

- [ ] **5.3. Implement Outbox Pattern / Transactional Messaging (Future / High Scale)**
  - **Action:** For mission-critical notification delivery, store outbound notification events in an `outbox` table within the same DB transaction, publishing to Kafka asynchronously via Debezium CDC or an outbox poller.

---

## 🟡 Phase 6: REST API Design & CRUD Completeness (P2)

- [x] **6.1. Complete Product CRUD Operations**
  - **Added Endpoints in [`ProductController.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/controller/ProductController.java):**
    - `PUT /api/v1/products/{id}` — Full product update.
    - `DELETE /api/v1/products/{id}` — Delete product from catalog (204 No Content).
    - `GET /api/v1/products/search?keyword=...` — Search by name/description keyword.

- [x] **6.2. Complete Subscription Lifecycle Endpoints**
  - **Added Endpoints:**
    - `DELETE /api/v1/products/{productId}/subscribe/{clientId}` — Unsubscribe a client (204 No Content).
    - `GET /api/v1/products/{productId}/subscribers` — View subscribers for a product.
    - `GET /api/v1/products/client/{clientId}/subscriptions` — View all subscriptions for a given client.

- [x] **6.3. Enrich OpenAPI / Swagger Documentation**
  - **Action:** Add detailed `@ApiResponse` definitions (200, 201, 204, 400, 404, 409, 500), request/response schema examples, and parameter documentation in [`ProductController.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/controller/ProductController.java).

---

## 🟡 Phase 7: Security & Compliance (P2)

- [ ] **7.1. Add Spring Security & Authentication**
  - **Issue:** All endpoints are completely open and unauthenticated.
  - **Action:**
    - Integrate `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server`.
    - Configure JWT validation or mutual TLS (mTLS) for inter-service communication.

- [ ] **7.2. Implement Role-Based Access Control (RBAC)**
  - **Action:**
    - Restrict write operations (`POST/PUT/DELETE /api/v1/products`) to `ROLE_ADMIN` or `SCOPE_catalog:write`.
    - Restrict subscription endpoints to authenticated clients matching `clientId` or `ROLE_SERVICE`.

- [ ] **7.3. Configure CORS & HTTP Security Headers**
  - **Action:** Add `CorsConfigurationSource` with strict origin whitelisting, configure CSP (Content Security Policy), HSTS, and X-Content-Type-Options headers.

- [ ] **7.4. Secure Actuator & Metrics Endpoints**
  - **Action:**
    - Separate management port in [`application.yaml`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/resources/application.yaml) (`management.server.port=7023`).
    - Require admin privileges for `/actuator/env`, `/actuator/metrics`, and only allow public access to `/actuator/health/liveness` and `/actuator/health/readiness`.

---

## 🟢 Phase 8: Testing & Quality Assurance (P3)

- [ ] **8.1. Comprehensive Unit Test Suite**
  - **Action:**
    - Create unit tests for [`ProductService.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/services/ProductService.java) covering discount calculation edge cases, missing discounts, zero discount, and empty lists.
    - Create unit tests for [`DiscountService.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/services/DiscountService.java).
    - Create unit tests for [`IncomingDiscountsProcessor.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/services/IncomingDiscountsProcessor.java) (verifying notifications are triggered only on discount increase).
    - Create unit tests for [`KafkaSender.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/kafka/kafkaProducer/KafkaSender.java) and [`KafkaConsumerService.java`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/src/main/java/com/serjnn/ProductService/kafka/kafkaConsumer/KafkaConsumerService.java).

- [ ] **8.2. Web Layer Slice Tests (`@WebMvcTest`)**
  - **Action:** Add tests in `ProductControllerTest.java` verifying validation failures (HTTP 400), not found responses (HTTP 404), and JSON serialization.

- [ ] **8.3. Repository Data Layer Slice Tests (`@DataJdbcTest`)**
  - **Action:** Add repository integration tests with Testcontainers verifying custom SQL queries, pagination offsets, and unique constraint violations.

- [ ] **8.4. Architecture Enforcement Tests (ArchUnit)**
  - **Action:** Add ArchUnit tests to enforce:
    - Controllers only talk to Services, not Repositories.
    - Package naming conventions and dependency directions.
    - No direct usage of `System.out` or `java.util.logging`.

- [ ] **8.5. Contract Testing (Spring Cloud Contract / Pact)**
  - **Action:** Define consumer-driven contracts for the `DiscountService` HTTP REST client and the Kafka `DiscountNotification` event schema.

- [ ] **8.6. Code Coverage (JaCoCo) & Quality Gates**
  - **Action:** Add `jacoco-maven-plugin` to [`pom.xml`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/pom.xml) with minimum 85% line and branch coverage threshold.

- [ ] **8.7. Mutation Testing (PITest)**
  - **Action:** Add `pitest-maven` to evaluate test suite robustness against mutated code logic.

---

## 🟢 Phase 9: Observability, Metrics & Telemetry (P3)

- [ ] **9.1. Custom Business Metrics & Gauges**
  - **Action:** Register Micrometer metrics:
    - `product.created.count` (Counter)
    - `discount.cache.hit` / `discount.cache.miss` (Counters)
    - `subscribers.notification.sent` / `subscribers.notification.failed` (Counters)
    - `subscribers.notification.duration` (Timer)

- [ ] **9.2. Structured JSON Logging for Log Aggregators**
  - **Action:** Configure Logback with `logstash-logback-encoder` for JSON-formatted logs with correlation IDs (`traceId`, `spanId`), product IDs, and client IDs in MDC (Mapped Diagnostic Context).

- [ ] **9.3. Custom Actuator Health Indicators**
  - **Action:** Implement custom health indicator for the external Discount Service dependency (`DiscountServiceHealthIndicator`) and Redis connectivity.

- [ ] **9.4. Provisioned Grafana Dashboards & Prometheus Alerts**
  - **Action:** Add pre-configured dashboard JSONs in `docker-compose` monitoring request rate, HTTP 5xx error rate, Kafka consumer lag, and Redis hit ratio.

---

## 🟢 Phase 10: DevOps, Containerization & CI/CD (P3)

- [ ] **10.1. Multi-Stage Dockerfile Hardening**
  - **Issue:** [`Dockerfile`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/Dockerfile) runs as the root user and lacks JVM memory tuning for containers.
  - **Action:**
    - Add a dedicated non-root user (`adduser -u 1001 -S appuser`).
    - Add JVM flags: `JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"`.
    - Add `HEALTHCHECK` instruction.

- [ ] **10.2. Upgrade Kafka to KRaft Mode in Docker Compose**
  - **Issue:** [`docker-compose.yml`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/docker-compose.yml#L28-L49) uses ZooKeeper (deprecated in modern Apache Kafka).
  - **Action:** Migrate Kafka service to standalone KRaft mode (e.g. `apache/kafka:latest` or `confluentinc/cp-kafka` with KRaft enabled) and include the `ProductService` container itself in docker-compose.

- [ ] **10.3. Automated CI/CD Pipeline (GitHub Actions)**
  - **Action:** Create `.github/workflows/ci.yml`:
    - Step 1: Checkout & Java 17 setup with Maven cache.
    - Step 2: Spotless / Checkstyle check.
    - Step 3: Run Unit & Integration tests with Testcontainers.
    - Step 4: JaCoCo coverage report verification.
    - Step 5: Trivy vulnerability & container security scanning.
    - Step 6: Docker image build and push.

- [ ] **10.4. Code Formatting & Static Analysis Tools**
  - **Action:** Add `spotless-maven-plugin` (Google Java Format or Palantir format) and `checkstyle` / `spotbugs-maven-plugin` to build lifecycle.

- [ ] **10.5. Multi-Environment Configuration Profiles**
  - **Action:** Split environment-specific properties into:
    - `application-local.yaml` (for local dev with Docker Compose)
    - `application-test.yaml` (for test execution with mock servers)
    - `application-prod.yaml` (for production with strict SSL, pool sizing, and secrets)

- [ ] **10.6. Housekeeping: Remove Outdated Artifacts**
  - **Action:** Delete [`refactor_suggestions.txt`](file:///C:/Users/sersh/IdeaProjects/micr.ProductService/refactor_suggestions.txt) once all suggestions are tracked in this `TODO.md` file.

---

*Generated for Product Service — Continuous Improvement Roadmap.*
