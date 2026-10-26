# Testing strategy (Module 3)

The project: `resource-service`, `song-service`, `resource-processor`, an API gateway and Eureka. Services talk over
synchronous HTTP (OpenFeign through Eureka) and over messaging (RabbitMQ). Files live in S3 (LocalStack), locations
and metadata in PostgreSQL.

## 1. Goal and principles

- **Fast feedback first.** Most tests run in seconds, without Docker. The slow tests are few and each one proves
  something that a faster test cannot.
- **Test at the borders.** The risky places are the borders: database, storage, broker and the other services.
  A test at a border uses the real thing (a container) or the real contract.
- **A test belongs to the cheapest level that can catch the defect.** If a unit test catches it, no E2E test is added.
- **Services stay independent.** A service is tested without its neighbours (component and contract tests). Only the
  E2E tests need the whole stack.

## 2. Test levels

| Level       | What is checked                                         | What is replaced                        | Tools                                               | Docker               | Tests | Time     | Command                                               |
|-------------|---------------------------------------------------------|-----------------------------------------|-----------------------------------------------------|----------------------|-------|----------|-------------------------------------------------------|
| Unit        | Logic of one class: order of steps, compensation, CSV   | Everything around it (mocks)            | JUnit 5, Mockito, AssertJ                           | no                   | 59    | ~10 s    | `gradlew test`                                        |
| Integration | One layer with its real infrastructure                  | The other layers and neighbour services | JUnit 5, Spring Boot Test, Testcontainers, WireMock | yes (4 of 5 classes) | 29    | ~1.5 min | `gradlew integrationTest`                             |
| Component   | One service as a black box, business scenarios          | Neighbour services (WireMock)           | Cucumber, WireMock, Testcontainers (RabbitMQ)       | yes                  | 5     | ~2 min   | `gradlew componentTest`                               |
| Contract    | The agreement between two services (HTTP and messaging) | The other side of the contract          | Pact (consumer-driven)                              | no                   | 14    | ~1.5 min | `gradlew contractTests`                               |
| End-to-end  | The whole stack through the public API                  | Nothing                                 | Cucumber, REST-assured, Awaitility                  | stack must run       | 7     | ~30 s    | `04_run_e2e.cmd` (stack: `01_start_services_wsl.cmd`) |

Total: **114 tests**. Unit tests of the minimum scope are in `resource-processor` and `resource-service`.
`song-service` is covered by contract tests only (unit and integration tests of `song-service` are not written).

## 3. Why this mix

The mix is a pyramid, not 100% of one kind.

- **Not 100% unit tests.** They would not notice a wrong SQL type, a wrong S3 setting, a queue declared with other
  parameters, or a service that changed its JSON. These are exactly the defects of a distributed system.
- **Not 100% E2E tests.** They are slow, fragile and hard to diagnose. In Module 2 the processing is asynchronous
  (queue, retry), so every E2E scenario must wait. A red E2E test says "something in the chain is broken", not where.
- **Unit + integration are the base.** Logic is checked in unit tests, every border once against the real
  infrastructure in integration tests.
- **Contract tests instead of cross-checking every pair of services in E2E.** Each side is tested alone, the
  contract proves that the two sides agree. They also run without Docker.
- **Component tests for business scenarios** of the service with the richest behaviour (`resource-processor`):
  retry, idempotency, error handling, in natural language.
- **E2E only as a thin check** that the stack is wired (gateway routes, Eureka, compose, environment).

## 4. Risk to level

| What breaks                                              | Who catches it                                 |
|----------------------------------------------------------|------------------------------------------------|
| Logic of a service (order of steps, compensation, CSV)   | Unit                                           |
| SQL, table schema, S3 behaviour, AMQP settings           | Integration                                    |
| Retry border (5xx is retried, 4xx is not)                | Unit (`RemoteCallErrors`), Integration (real Feign + WireMock) |
| A service does not complete its business scenario        | Component                                      |
| One service changed a format or a status, the other did not notice | Contract                             |
| Wrong wiring of the stack (gateway, Eureka, compose)     | E2E                                            |

## 5. Traceability: where each behaviour is tested

| Behaviour                                               | Unit                       | Integration                                         | Component         | Contract     | E2E                     |
|---------------------------------------------------------|----------------------------|-----------------------------------------------------|-------------------|--------------|-------------------------|
| Upload: order S3, DB, event; compensation on DB failure | `ResourcesServiceImplTest` | `S3FileStorageIT`, `ResourcesRepositoryIT`          |                   |              | upload scenario         |
| Event `{"resourceId": N}`, persistent, durable queue    |                            | `RabbitResourceEventPublisherIT`                    |                   | C1 (message) | upload scenario         |
| Publisher retry, then no exception                      |                            | `RabbitResourceEventPublisherIT`, `...BrokerDownIT` |                   |              |                         |
| Metadata extraction from the MP3                        | `Mp3MetadataExtractorTest` |                                                     | scenario 1        |              | metadata appears        |
| Processor retry on 5xx, no retry on 4xx                 | `RemoteCallErrorsTest`     | `ProcessorRetryIT`                                  | scenario 3, 5     | C3, C6       |                         |
| Idempotency: 409 from song-service is a success         |                            | `ProcessorRetryIT`                                  | scenario 4        | C5           | 409 scenario            |
| Message is acknowledged and not delivered again         |                            |                                                     | scenarios 2, 4, 5 |              |                         |
| `GET /resources/{id}` and its error answers             |                            |                                                     |                   | C2, C3       | download, 404 scenarios |
| `POST /songs` (200, 409, 400)                           |                            |                                                     |                   | C4, C5, C6   | 409 scenario            |
| `DELETE /songs` from resource-service                   |                            |                                                     |                   | C7           | delete scenario         |
| Cascade delete: file, record, metadata                  | `ResourcesServiceImplTest` | `S3FileStorageIT`                                   |                   | C7           | delete scenario         |
| CSV validation of ids                                   | `CSVValidatorTest`         |                                                     |                   |              | invalid requests        |

## 6. Module 2 specifics

- **Asynchronous processing.** Tests never use `Thread.sleep`. They wait for a condition with Awaitility (component and
  E2E). "Exactly once" and "never" are checked by watching for a few seconds after the condition (`during`), so a late
  retry would be noticed.
- **Retry.** The attempt count is the production value (3). The pauses are shortened to 10 ms in the integration and
  contract tests. The component tests use the production pauses.
- **Idempotency.** `409` from song-service means "already saved" and is a success. It is checked at four levels
  (integration, component, contract, E2E).
- **The queue is checked at the broker.** "Acknowledged" is asked from RabbitMQ itself (`messages_ready` and
  `messages_unacknowledged` are both 0), not guessed from the logs.

## 7. What the tests found

Contract C3 (`GET /resources/999` with `Accept: audio/mpeg`, the header that the processor sends) failed on the first
run. `resource-service` answered an error with JSON, but a client that accepts only `audio/mpeg` can not get JSON, so
Spring answered **500 with an empty body instead of 404**. The processor treated the 500 as temporary and retried the
download of a missing resource three times. This was confirmed on the running stack with `curl`. The component test did
not see it because WireMock answered 404 itself. Fixed in `ErrorResponseFactory` of both services (explicit
`Content-Type: application/json`), pinned by C3 and by an E2E scenario.

## 8. Not covered on purpose

- Guaranteed delivery (Transactional Outbox) and a dead-letter queue: out of scope of Module 2, a lost event is logged.
- Load, performance and security tests.
- Unit tests of the Feign proxies (`SongServiceClientProxyImpl` of both services), `song-service` unit and
  integration tests, component tests of `resource-service`, the processor's
  `ResourceUploadedListener` as a separate integration test (the listener is exercised by the component and contract
  tests). Outside the agreed minimum.
- A general `@ExceptionHandler(Exception.class)`: exceptions that are not mapped (for example a NullPointerException)
  still go through the default Spring error handling. Not tested.

## 9. Coverage (JaCoCo, unit + integration tests)

`gradlew test integrationTest jacocoCoverageReport`. Report: `<module>/build/reports/jacoco/jacocoCoverageReport/html`.

| Module               | Lines | Branches | Packages at or above the 70% target                          | Below the target (unit and integration only)                     |
|----------------------|-------|----------|--------------------------------------------------------------|------------------------------------------------------------------|
| `resource-processor` | 80%   | 78%      | `client`, `client/resource`, `client/song`, `service`, `metadata`, `exception` (all 100%) | `messaging` 0% (the listener)                 |
| `resource-service`   | 57%   | 35%      | `service` 100%, `validator` 100%, `storage` 93%, `configuration` 92%, `mapper` 88%, `messaging` 83% | `client/song` 0%, `controller` 0%, `advice` 0%, `validation` 0% |

The packages below the target are not covered by unit and integration tests, but they are exercised by other test types:
the controllers and the error handling by the contract provider tests, the song client by contract C7, the listener by
the component and contract tests. The JaCoCo report counts only unit and integration tests, so those runs are **not**
in the numbers. The target of 70% is met for the business packages (`service`, `validator`, `messaging` in
`resource-service`; `client`, `service`, `metadata` in `resource-processor`) except the processor `messaging`.

## 10. Contract propagation and the production variant

Here the contracts travel through a folder: the consumer tests write pact files to `<module>/build/pacts`, the task
`publishPacts` copies them to `build/pacts-shared`, the provider tests replay them. `gradlew contractTests` runs the
chain. If a provider breaks a contract, its test fails.

In a real project the folder is replaced by a **Pact Broker** (or the GitLab package registry): the consumer build
publishes the pacts with its version, the provider build verifies them and publishes the result, and `can-i-deploy`
decides whether a version may go to an environment. The broker is not started here (outside the minimum).

## 11. How to run

| What                         | How                                                                                         |
|------------------------------|---------------------------------------------------------------------------------------------|
| Unit, integration, component, contract | `03_run_gradle_tests.cmd` (Docker is needed for integration and component tests)  |
| End-to-end                   | First `01_start_services_wsl.cmd` (the whole stack), then `04_run_e2e.cmd`                  |
| Evidence of the last run     | [module-3-tests-log.txt](module-3-tests-log.txt)                                            |
