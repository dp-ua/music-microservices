# Music Microservices

A course project for "Microservices Fundamentals". Spring Boot services for storing MP3 files and their metadata.
Files are stored in a cloud storage (S3, emulated by LocalStack), their locations in PostgreSQL. After an upload the
metadata is extracted **asynchronously**: `resource-service` sends an event to RabbitMQ and `resource-processor`
handles it. Services discover each other through Eureka and are reachable through a single API Gateway.

## Architecture

```
Client / Postman
       │
       ▼
┌──────────────────┐        ┌──────────────────┐
│   API Gateway    │◄──────►│  Eureka Server   │  :8761
│      :8080       │        │ (service registry)│
└────┬────────┬────┘        └──────────────────┘
     │        │
     ▼        ▼
┌─────────────────┐   DELETE only (OpenFeign)   ┌─────────────────┐
│ Resource Service│────────────────────────────►│  Song Service   │
│                 │◄──────────────┐             │ (stores metadata)│
└─┬──────┬──────┬─┘               │             └───▲─────────┬───┘
  │      │      │ publish         │ GET binary      │ POST    │
  │      │      ▼ {resourceId}    │ (sync)          │ /songs  ▼
  │      │  ┌──────────┐   ┌──────┴───────────┐     │     ┌──────────┐
  │      │  │ RabbitMQ │──►│Resource Processor│─────┘     │ song-db  │
  │      │  │  queue   │   │ (Tika, no web UI)│  (sync)   └──────────┘
  │      │  └──────────┘   └──────────────────┘
  ▼      ▼
┌────────┐ ┌──────────────┐
│LocalStack│ │ resource-db  │
│  (S3)  │ │ (location)   │
│ MP3 file│ └──────────────┘
└────────┘
```

### Modules

| Module               | Description                                                                                          |
|----------------------|------------------------------------------------------------------------------------------------------|
| `api-gateway`        | Single entry point (`:8080`), routes `/resources/**`, `/songs/**` and `/storages/**` via Eureka       |
| `storage-service`    | Storage types STAGING / PERMANENT (bucket, path), CRUD API (`/storages`), database `storage-db`       |
| `eureka-server`      | Service registry (`:8761`)                                                                           |
| `resource-service`   | Accepts MP3 files, stores them in S3, keeps their location in its DB, publishes the upload event     |
| `song-service`       | Stores track metadata (title, artist, album, duration, year)                                         |
| `resource-processor` | Listens to the queue, downloads the MP3, extracts metadata (Apache Tika), saves it in `song-service` |

Infrastructure containers: `resource-db`, `song-db`, `storage-db` (PostgreSQL), `localstack` (S3), `rabbitmq` (message broker),
`elasticsearch` and `filebeat` (logs, see [Logging](#logging-module-7)), `prometheus` and `grafana` (see [Monitoring](#monitoring-module-7)), tracing: [Tracing](#tracing-module-7).

---

## Service Interaction

### Upload and processing: `POST /resources`

```
Client ─POST─► resource-service
                 1. file ─► S3
                 2. location ─► resource-db
                 3. {"resourceId": N} ─► RabbitMQ, queue "resource.uploaded"   (async)
                 4. response { "id": N }

RabbitMQ ─► resource-processor
              5. GET /resources/N ─► resource-service ─► S3                     (sync, binary)
              6. metadata extraction (Apache Tika)
              7. POST /songs {id=N, ...} ─► song-service                        (sync)
```

`resource-service` does **not** call any other service while uploading. It only publishes the event:

1. Validates that the body is not empty (`Content-Type: audio/mpeg`).
2. Stores the original MP3 in the S3 bucket under a generated key (`<uuid>.mp3`).
3. Saves the location (`storage_bucket`, `storage_key`) and the file size in the table `resources`.
4. Publishes `{"resourceId": N}` to the queue (after the DB record is committed, so the processor always finds it).
5. Returns the resource `id`.

If saving to the DB fails, the file is removed from S3 and nothing is published.

Metadata appears in `song-service` **a moment after** the upload response: `GET /songs/{id}` may return 404 until
`resource-processor` has handled the message.

**Message:** JSON `{"resourceId": 1}`, persistent, sent to the durable queue `resource.uploaded` through the default
exchange. The publisher waits for the broker confirm (and a message that is not routed to the queue counts as a
failure).

**Resource Processor** for every message:

1. `GET /resources/{id}` on `resource-service` (binary `audio/mpeg`).
2. Extracts `name`, `artist`, `album`, `duration`, `year` with Apache Tika.
3. `POST /songs` on `song-service` with `id` = resource id.

Both calls go to the service **by name through Eureka** (OpenFeign + Spring Cloud LoadBalancer), not through the
gateway. Every request is balanced (round robin) between all running instances of the service.

The metadata is sent **as it is**: nothing is filled in by default. If a tag is missing, the processor logs an ERROR
with the list of missing fields, `song-service` rejects the request (400), the error is logged and the message is
acknowledged (see "Error handling").

### Download: `GET /resources/{id}`

The record is found in the DB, the file is read from S3 by the stored location. A missing record returns 404.

### Delete: `DELETE /resources?id=1,2,3`

Deletion stays a **synchronous** cleanup, the message broker is not used. For every existing resource:

1. The file is removed from S3.
2. The record is removed from the DB (physically, no soft delete in `resource-service`).
3. `DELETE /songs?id=...` is called on `song-service` via **OpenFeign** to delete the related metadata.

Unknown ids are ignored. `song-service` also ignores ids without metadata, so deleting a resource without a song is not
an error.

> **Important:** The record ID in `song-service` matches the resource ID in `resource-service`. This is the linking key
> between the two databases.

### Retry

| Where                                          | What is retried                                        | Settings                                      |
|------------------------------------------------|--------------------------------------------------------|-----------------------------------------------|
| `resource-service` → RabbitMQ (publish)        | no broker confirm, connection error, message not routed | 3 attempts, confirm timeout 2 s, delay 200 ms × 2 (about 7 s at worst) |
| `resource-processor` → `resource-service` (GET) | connection error, timeout, no instance in Eureka, 5xx  | 3 attempts, delay 500 ms × 2                  |
| `resource-processor` → `song-service` (POST)   | connection error, timeout, no instance in Eureka, 5xx  | 3 attempts, delay 500 ms × 2                  |

Implemented with **Spring Retry** (`@Retryable`). 4xx responses are never retried: they will not change. The publish
retry is short on purpose, because the upload request waits for it. `DELETE /resources` → `song-service` has no retry,
for the same reason.

Settings are in `application.yml` (`messaging.resource-uploaded.retry.*` in `resource-service`, `processor.retry.*` in
`resource-processor`).

### Error handling and idempotency

- **Publish failed after all attempts:** `resource-service` logs an ERROR and still returns `200` with the id. The
  resource is saved, but it will not be processed (no metadata). A guaranteed delivery (Transactional Outbox) is out of
  scope of this module.
- **Processing failed** (song-service answered 400 or all retries are exhausted, resource not found, broken MP3):
  `resource-processor` logs one ERROR line with the reason and acknowledges the message. There is no dead-letter queue
  and no requeue, so a failing message is not processed in a loop.
- **Processor is down:** messages wait in the durable queue (persistent messages, volume `rabbitmq-data`) and are
  handled after the start.
- **Processor died before the acknowledgement:** the broker delivers the message again. Saving the same metadata twice
  is safe: `409 Conflict` from `song-service` is treated as success.

### Feign Error Handling (`resource-service` → `song-service`, DELETE)

`SongServiceClientProxyImpl` wraps all calls to `song-service`:

- `RetryableException` → HTTP 503 (service unavailable)
- `FeignException` → HTTP 502 (error on the song-service side)

### Storage Error Handling

`S3FileStorage` wraps S3 errors into `StorageException`:

- object not found → HTTP 404
- any other S3 error → HTTP 500

---

## API

All requests go through the API Gateway: `http://localhost:8080`.

### Resource Service

| Method   | URL                 | Description                                                            |
|----------|---------------------|------------------------------------------------------------------------|
| `POST`   | `/resources`        | Upload an MP3 file (raw body, `Content-Type: audio/mpeg`)              |
| `GET`    | `/resources/{id}`   | Download MP3 binary data by ID                                         |
| `DELETE` | `/resources?id=...` | Delete resources (S3 file, DB record, song metadata) by a list of IDs  |

### Song Service

| Method   | URL             | Description                                      |
|----------|-----------------|--------------------------------------------------|
| `POST`   | `/songs`        | Save track metadata (called by `resource-processor`) |
| `GET`    | `/songs/{id}`   | Get track metadata by ID                         |
| `DELETE` | `/songs?id=...` | Delete metadata by a comma-separated list of IDs |

The services are not published on host ports in `compose.yaml` (see the container table below), so Swagger UI is not
reachable from the host by default.

### Metadata Format

```json
{
  "id": 1,
  "name": "Track Name",
  "artist": "Artist Name",
  "album": "Album Name",
  "duration": "03:45",
  "year": "2023"
}
```

Field validation in `song-service`:

- `name`, `artist`, `album` — required, 1–100 characters
- `duration` — `mm:ss` format (e.g. `03:45`)
- `year` — 4 digits, range 1900–2099

---

## Running the Project

Requires Docker. Everything (services, databases, LocalStack, RabbitMQ, Eureka, gateway) is started by Docker Compose.

**Step 1 — create `.env`** from the template:

```cmd
copy .env.example .env
```

The `S3_*` and `RABBITMQ_*` variables are optional: `compose.yaml` has the same defaults.

**Step 2 — build and start:**

```cmd
docker compose up --build -d
```

If the DB schema changed (`init-scripts/resource-db/init.sql`), recreate the volumes, because init scripts run only on
an empty database. This also removes the queue data of RabbitMQ:

```cmd
docker compose down -v
```

#### Containers and ports

| Container            | Purpose                           | Host Port       | Notes                                          |
|----------------------|-----------------------------------|-----------------|------------------------------------------------|
| `api-gateway`        | Entry point                       | `8080`          |                                                |
| `eureka-server`      | Service registry                  | `8761`          |                                                |
| `resource-db`        | PostgreSQL for `resource-service` | `5433`          | `resource_service`, `postgres/postgres`        |
| `song-db`            | PostgreSQL for `song-service`     | `5434`          | `song_service`, `postgres/postgres`            |
| `localstack`         | S3 emulator                       | `4566`          | Bucket `resources` is created on startup       |
| `rabbitmq`           | Message broker                    | `5672`, `15672` | UI: http://localhost:15672, `rabbit/rabbit`    |
| `resource-service`   | Resource API                      | none            | Port `8081` inside the network, Eureka         |
| `song-service`       | Song API                          | none            | Port `8082` inside the network, Eureka         |
| `resource-processor` | Queue listener, metadata handling | none            | Runs permanently, no web interface             |
| `elasticsearch`      | Log storage                       | `9200`          | Index `music-logs-<date>`, security is off     |
| `filebeat`           | Log collector                     | none            | Docker logs of the services -> Elasticsearch   |
| `prometheus`         | Metrics storage                   | `9090`          | Finds the services through Eureka              |
| `grafana`            | Dashboards                        | `3000`          | `admin/admin`, dashboards JVM and API Gateway  |

#### Scaling `song-service`

Several instances of `song-service` can be started. Every instance registers itself in Eureka. The API Gateway
(`lb://SONG-SERVICE`) and `resource-processor` (OpenFeign) balance requests between them:

```cmd
docker compose up -d --scale song-service=2
```

Requests must go through the gateway (`http://localhost:8080`): the instances are not published on host ports.

#### Configuration

`resource-service`:

| Variable          | Default                  | Description                  |
|-------------------|--------------------------|------------------------------|
| `S3_ENDPOINT`     | `http://localstack:4566` | S3 endpoint                  |
| `S3_REGION`       | `us-east-1`              | S3 region                    |
| `S3_BUCKET`       | `resources`              | Bucket for MP3 files         |
| `S3_ACCESS_KEY`   | `test`                   | LocalStack credentials       |
| `S3_SECRET_KEY`   | `test`                   | LocalStack credentials       |

`resource-service` and `resource-processor`:

| Variable            | Default             | Description                  |
|---------------------|---------------------|------------------------------|
| `RABBITMQ_HOST`     | `rabbitmq`          | Broker host                  |
| `RABBITMQ_PORT`     | `5672`              | Broker AMQP port             |
| `RABBITMQ_USER`     | `rabbit`            | Broker user                  |
| `RABBITMQ_PASSWORD` | `rabbit`            | Broker password              |
| `RABBITMQ_QUEUE`    | `resource.uploaded` | Durable queue with events    |

To inspect the bucket from the host:

```cmd
docker exec localstack awslocal s3 ls s3://resources
```

To inspect the queue: open http://localhost:15672 (Queues → `resource.uploaded`) or:

```cmd
docker exec rabbitmq rabbitmqctl list_queues name messages consumers
```

---

## Logging (Module 7)

Logs of all services are collected in **Elasticsearch**.

```
service (JSON logs, ECS format, stdout) ─► Docker log file ─► Filebeat ─► Elasticsearch, index music-logs-<date>
```

- Every service writes one JSON document per line to stdout (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` in `compose.yaml`,
  built-in Spring Boot 3.4 structured logging, no extra library). Locally (IDE, tests) the logs stay plain text.
- **Filebeat** (`monitoring/filebeat/filebeat.yml`) reads the logs of the containers with the label
  `co.elastic.logs/enabled=true`, unpacks the JSON and sends it to Elasticsearch. The infrastructure containers
  (databases, RabbitMQ, ...) are not collected.
- Useful fields: `@timestamp`, `log.level`, `log.logger`, `message`, `service.name`, `container.name`.

Check:

```cmd
curl "http://localhost:9200/_cat/count/music-logs-*?v"
curl "http://localhost:9200/music-logs-*/_search?q=service.name:resource-service&size=3&pretty"
curl "http://localhost:9200/music-logs-*/_search?size=0&pretty" -H "Content-Type: application/json" -d "{\"aggs\":{\"s\":{\"terms\":{\"field\":\"service.name\"}}}}"
```

---

## Monitoring (Module 7)

```
services ── /actuator/prometheus ◄── Prometheus (:9090) ◄── Grafana (:3000) ──► Elasticsearch (logs)
```

- Every web service (gateway, resource, song, storage, eureka) has `micrometer-registry-prometheus` and exposes
  `/actuator/prometheus`. All metrics have the label `application`. `resource-processor` has no web server, so it
  has no metrics endpoint.
- **API Gateway route for the metrics:** `http://localhost:8080/actuator/prometheus` (the gateway is the only service
  with a published port). It also contains the metrics of the gateway routes (`spring_cloud_gateway_requests_*`).
- **Prometheus** (`monitoring/prometheus/prometheus.yml`) finds the other services in Eureka (`eureka_sd_configs`), so
  scaled instances (`--scale song-service=2`) are scraped automatically. Check: http://localhost:9090/targets.
- **Grafana** (http://localhost:3000, `admin/admin`, overridable by `GRAFANA_USER` / `GRAFANA_PASSWORD`) is configured
  from files in `monitoring/grafana`: the data sources Prometheus and Elasticsearch and two dashboards (auto-refresh 10 s):

| Dashboard     | Panels                                                                                                  |
|---------------|---------------------------------------------------------------------------------------------------------|
| `JVM`         | heap / non-heap memory, GC pauses, threads, CPU, loaded classes, uptime (filters: application, instance) |
| `API Gateway` | requests per second, p50/p95/p99 and average latency, 4xx / 5xx rate, requests and latency by route     |

Check: open **Dashboards -> JVM** and **API Gateway**. To see traffic on the gateway dashboard, send requests, e.g.
`curl http://localhost:8080/songs/1` in a loop. A dashboard can be edited in the UI and exported as JSON back to
`monitoring/grafana/dashboards`.

---

## Tracing (Module 7)

One upload goes through four services and RabbitMQ. All its log lines have the same **trace ID** (`traceId` field),
so the whole path of a request can be found by this single value.

```
client ─► api-gateway ─HTTP─► resource-service ─queue─► resource-processor ─HTTP─► song-service
  ▲        creates the         traceparent header       message header              traceparent header
  └─ X-Trace-Id response header          (the same trace ID everywhere, a new span ID in every step)
```

- **Library:** Micrometer Tracing (Brave bridge), no tracing server. Every request is traced
  (`management.tracing.sampling.probability=1.0`). Trace ID and span ID are put to the logging context (MDC), so
  they are written to every JSON log line (`traceId`, `spanId`).
- **Start:** `api-gateway` creates the trace ID for each incoming request (also for `POST /resources`) and returns it
  in the `X-Trace-Id` response header (`TraceIdFilter`).
- **HTTP:** the `traceparent` header (W3C) is added to every outgoing call (gateway routes, OpenFeign through
  `feign-micrometer`) and read by the receiving service.
- **RabbitMQ:** `spring.rabbitmq.template.observation-enabled` / `listener.simple.observation-enabled` put the same
  header to the message properties on send and read it on receive (`resource.uploaded`, `resource.processed`).
  `resource-processor` has the actuator dependency only for the observation auto-configuration, it has no endpoints.

**How to find the logs of a request:**

```cmd
curl -i -X POST http://localhost:8080/resources -H "Content-Type: audio/mpeg" --data-binary @test-data/valid-sample-with-required-tags.mp3
```

1. Copy the value of the `X-Trace-Id` response header.
2. Grafana (http://localhost:3000) -> Dashboards -> **Logs by Trace ID** -> paste the value to the `Trace ID` field.
   The panel shows the logs of all services of this request in time order. Alternatively in **Explore** with the
   data source Elasticsearch: `traceId:"<value>"`.
3. Or directly in Elasticsearch:
   `curl "http://localhost:9200/music-logs-*/_search?q=traceId:<value>&size=100&sort=@timestamp:asc&pretty"`.

---

## Storage Service and Circuit Breaker (Module 6)

`storage-service` (`:8083`, own database `storage-db`) describes **where** the files of each processing state live.
A storage has `storageType` (`STAGING` or `PERMANENT`), `bucket` and `path`. Two storages are pre-created on start
(`init-scripts/storage-db/init.sql`): `STAGING` -> `staging-bucket`, `PERMANENT` -> `permanent-bucket`, path `/files`.
The buckets are created in LocalStack by `init-scripts/localstack/01-create-bucket.sh`.

| Method   | Path                | Description                                                         | Codes         |
|----------|---------------------|---------------------------------------------------------------------|---------------|
| `POST`   | `/storages`         | `{"storageType":"PERMANENT","bucket":"b","path":"/files"}` -> `{"id":1}` | 200, 400, 500 |
| `GET`    | `/storages`         | List of `{id, storageType, bucket, path}`                           | 200, 500      |
| `DELETE` | `/storages?id=1,2`  | Deletes the listed storages (unknown ids are ignored) -> `{"ids":[1,2]}`, CSV < 200 characters | 200, 400, 500 |

### Flow of a file

```
POST /resources -> resource-service: STAGING storage (GET /storages, storage-service)
                -> file to s3://staging-bucket/files/<uuid>.mp3, state=STAGING in resource-db
                -> event "resource uploaded" (queue resource.uploaded)
resource-processor: download, extract metadata, save to song-service
                -> event "resource processed" (queue resource.processed)
resource-service: PERMANENT storage -> copy of the file to s3://permanent-bucket/files/,
                  state=PERMANENT and the new location in resource-db, then the staging file is deleted
```

S3 has no move, so the file is copied, the database is updated, and only then the source is deleted: whatever fails,
the file is not lost. A repeated "processed" message for a PERMANENT resource is ignored.

### Circuit breaker (Resilience4j)

The call `resource-service` -> `storage-service` (`StorageServiceClientProxyImpl`) is protected by the circuit breaker
`storage-service` (`resilience4j.circuitbreaker` in `resource-service/application.yml`: 5 calls window, 50% failures,
15 s in the OPEN state). If the call fails or the circuit is open, the **stub** from `storage.stub` is returned: the same
structure as the real answer (`id`, `storageType`, `bucket`, `path`) and the same buckets, so uploads and the move to
PERMANENT continue to work. The state is visible in the `resource-service` container at `/actuator/circuitbreakers`.

Demo:

```bash
docker compose stop storage-service
curl -X POST -H "Content-Type: audio/mpeg" --data-binary "@test-data/valid-sample-with-required-tags.mp3" http://localhost:8080/resources   # still 200
docker compose logs resource-service | grep "stub storages"        # fallback; circuit OPEN after the first failures
docker compose start storage-service                                # after ~15 s the circuit is HALF_OPEN -> CLOSED
```

## Service Discovery

All services find each other through the **Eureka** service registry (`eureka-server`, `:8761`) instead of fixed
addresses.

- **Registration:** `resource-service`, `song-service` and `api-gateway` register themselves in Eureka on startup
  (self-registration pattern) and send a heartbeat to keep the registration alive. `resource-processor` has no web
  endpoints, so it does not register. It only reads the registry to find the other services.
- **Discovery:** the client asks Eureka for the instances of a service name and picks one itself (client-side
  discovery). `resource-processor` does this with OpenFeign + Spring Cloud LoadBalancer, `api-gateway` with `lb://`
  routes.
- **Scaling:** any service with a registered name can be started in several instances
  (`docker compose up -d --scale <service>=N`). The instances have no fixed host ports, so they do not clash, and
  requests are balanced between them.

### Discovery endpoint

`api-gateway` shows what is registered in Eureka. It is placed in the gateway because it is the single entry point
with a published port; `resource-service` and `song-service` have none, and any of them can run in several instances.

| Request                                                | Result                                          |
|--------------------------------------------------------|-------------------------------------------------|
| `GET http://localhost:8080/service-instances`          | registered services with instance count, e.g. `{"api-gateway":1,"song-service":2}` |
| `GET http://localhost:8080/service-instances/song-service` | all instances of the service (host, port, uri) |

A new instance appears in the list after a few seconds (registration and registry refresh are every 5 seconds).

### Questions

**What is Service Discovery? What is the purpose of a Service Registry?**
In the cloud, instances get dynamic addresses and their number changes (scaling, failures, updates), so a client
cannot keep addresses in a config file. Service Discovery is the mechanism that lets a client find the current network
location of a service by its name. The Service Registry is the database behind it: instances register on startup,
refresh the registration with a heartbeat and are removed on shutdown or when the heartbeat stops. Clients query it
to get the list of available instances.

**What is the difference between server-side and client-side balancing?**
With client-side balancing, the client asks the registry itself, gets all instances and chooses one with its own
algorithm (Eureka + Spring Cloud LoadBalancer in this project). With server-side balancing, the client sends the
request to a load balancer (AWS ELB, NGINX, Kubernetes proxy), which asks the registry and forwards the request to one
of the instances. The client knows nothing about the instances.

**What are the benefits of server-side balancing?**
The discovery logic is hidden from the client, so it is not needed in every language and framework. Clients stay
simple and are not coupled to the registry. The balancing rules are in one place. Some environments (Kubernetes, AWS)
provide it for free. The price is one more highly available component that has to be run and managed.

## Resource Processor

A Spring Boot application (`web-application-type: none`) with no controllers. It is a listener of the queue
`resource.uploaded` (`@RabbitListener`, Spring AMQP). `Mp3MetadataExtractor` reads ID3 tags with **Apache Tika**
(`Mp3Parser`) and returns `name`, `artist`, `album`, `duration` (`mm:ss`) and `year`, the same fields as the
song-service API.

It registers nowhere in Eureka (it has no endpoints), it only looks up `resource-service` and `song-service`.
The container runs permanently and is restarted automatically (`restart: unless-stopped`).

---

## Notes and Gotchas

### Soft Delete

`song-service` marks songs as deleted (`is_deleted`) and a `GET` for them returns 404. `resource-service` does not use
soft delete: the DB record and the S3 file are removed physically.

### CSV Validation on Delete

The `id` parameter in DELETE requests accepts a comma-separated list (e.g. `1,2,3`).

- Cannot be blank
- Maximum string length: 200 characters
- Each element must be a positive integer

### Delete Is Not Transactional

S3 and `song-service` cannot be rolled back, so `DELETE /resources` is not transactional: the file is removed first,
then the DB record, then the metadata. If `song-service` is down, the resource is already deleted and the request fails
with 502/503.

---

## Testing

Five kinds of tests, each with its own Gradle task. The approach and the reasons for the mix are in
[task/modul3/testing-strategy.md](task/modul3/testing-strategy.md).

| Kind        | What it checks                                                | Command                                                    | Needs                           |
|-------------|---------------------------------------------------------------|------------------------------------------------------------|---------------------------------|
| Unit        | Logic of one class (JUnit 5, Mockito, AssertJ)                | `gradlew test`                                             | nothing                         |
| Integration | One layer with a real PostgreSQL, S3 (LocalStack), RabbitMQ   | `gradlew integrationTest`                                  | Docker (Testcontainers)         |
| Component   | `resource-processor` as a black box (Cucumber, WireMock)      | `gradlew componentTest`                                    | Docker (Testcontainers)         |
| Contract    | HTTP and messaging contracts between services (Pact)         | `gradlew contractTests`                                    | nothing                         |
| End-to-end  | The whole stack through the gateway (Cucumber, REST-assured)  | `04_run_e2e.cmd`                                           | the running stack, see below    |

All kinds except end-to-end at once, with the coverage report: `03_run_gradle_tests.cmd`.

**Docker for the tests.** Integration and component tests start their own containers (Testcontainers), nothing has to
run before them. If Docker runs inside WSL2, `gradle/testing.gradle` points Testcontainers to `tcp://localhost:2375`
(the Docker daemon must listen on it). Set `DOCKER_HOST` yourself to use another address.

**End-to-end tests** do not start anything. First start the whole stack, then run the tests:

```cmd
01_start_services_wsl.cmd        rem Docker inside WSL2
01_start_services_docker.cmd     rem Docker in the Windows PATH
04_run_e2e.cmd
```

`04_run_e2e.cmd` checks that the gateway answers and says what to do if it does not. Another address:
`set E2E_BASE_URL=http://host:8080`.

**Contract tests** work in three steps that `gradlew contractTests` runs in one command: the consumer tests
(`resource-processor`, `resource-service`) write pact files to `<module>/build/pacts`, `publishPacts` copies them to
`build/pacts-shared`, the provider tests (`song-service`, `resource-service`) replay them against the real controllers
and the real publisher. Seven contracts: the event `resource uploaded` (messaging), `GET /resources/{id}` (200, 404),
`POST /songs` (200, 409, 400), `DELETE /songs`.

**Reports** (open `index.html`): test results `<module>/build/reports/tests/<suite>`, Cucumber
`<module>/build/reports/cucumber`, coverage (unit + integration) `<module>/build/reports/jacoco/jacocoCoverageReport/html`.
Every suite also prints one line, for example `>>> :resource-service:test: 33 tests, 33 passed, 0 failed, 0 skipped <<<`.
The tests are never skipped as up to date.

Test sources: `<module>/src/{test,integrationTest,componentTest,contractConsumerTest,contractProviderTest}`, end-to-end
tests in the module `e2e-tests`. The MP3 samples are taken from `test-data/`.

---

## Regression Tests (Postman / Newman, Module 2)

Tests are located in the `test-data/` folder. [Newman](https://www.npmjs.com/package/newman) is required to run them.

```cmd
02_run_test.cmd
```

Equivalent (run it from `test-data`, the MP3 files are taken from there):

```cmd
cd test-data
newman run module_2.postman_collection.json
```

The collection `module_2.postman_collection.json` covers Module 2. Both `resource_service_url` and `song_service_url`
point to the gateway (`http://localhost:8080`). The older `introduction_to_microservices.postman_collection.json` is kept
for reference and is **not adapted** (upload no longer creates metadata synchronously).

### Test Coverage

**Happy Path (asynchronous processing):**

- Upload a valid MP3 → `200` and `id`
- Poll `GET /songs/{id}` until the metadata appears (it is created asynchronously) and check its fields
- Get the resource (file from S3) by ID
- Create the same metadata again → `409` (the response the processor treats as success)
- Delete the resource, check that the file and the metadata are gone (`404`)

**MP3 without required tags:**

- Upload → `200` anyway
- No metadata is created (`404`), nothing is filled in by default
- Delete such a resource → `200`

> **Test execution order matters.** The requests use the id of the previous upload. The collection variables
> `max_poll_attempts`, `poll_interval_ms` and `negative_wait_ms` set the polling.

### Manual scenarios (retry, persistence)

Run them with `docker compose logs -f resource-service resource-processor`. Upload with
`curl -X POST -H "Content-Type: audio/mpeg" --data-binary "@test-data/valid-sample-with-required-tags.mp3" http://localhost:8080/resources`.

1. **Normal flow:** the logs show `Event published`, `Received message`, `Resource N downloaded`, `Metadata saved for
   resource N`. In RabbitMQ UI the queue is empty again.
2. **`song-service` is down:** `docker compose stop song-service`, upload. The processor logs `Attempt failed` (retry),
   then one ERROR `Resource N was NOT processed`. If `song-service` is started before the attempts are over, the metadata
   is saved.
3. **RabbitMQ is down:** `docker compose stop rabbitmq`, upload. `resource-service` logs warnings for every attempt,
   then an ERROR `Event for resourceId=N was NOT published`, and the response is still `200` with the id.
4. **Processor is down (persistence):** `docker compose stop resource-processor`, upload several files, check that the
   queue holds the messages (UI or `rabbitmqctl list_queues`), `docker compose start resource-processor`. All messages are
   handled. Restarting `rabbitmq` itself keeps them too (durable queue, persistent messages, volume).
5. **MP3 without tags:** upload `test-data/invalid-sample-with-missed-tags.mp3`. The processor logs an ERROR with the
   missing fields and one ERROR with `status=400`; the message is acknowledged, there is no loop.
6. **Several `song-service` instances:** `docker compose up -d --scale song-service=2`, upload several files. The
   processor requests are distributed between both instances (see `docker compose logs song-service`).
7. **Idempotency:** `POST /songs` with an existing id returns `409`, the processor treats it as success.
8. **Delete:** `DELETE /resources?id=N` works as before, nothing is sent to the broker.

---

## Requirements

| Tool   | Version                                            |
|--------|----------------------------------------------------|
| Java   | 21+ (only to build locally without Docker)         |
| Gradle | Wrapper included                                   |
| Docker | Any recent version with Docker Compose             |
| Newman | Any recent version (Postman regression tests only) |

---

## Tech Stack

- **Spring Boot** 3.4.0
- **Spring Data JPA** + Hibernate
- **Spring Cloud** (OpenFeign, Netflix Eureka, LoadBalancer, Gateway)
- **Spring AMQP** + **RabbitMQ** 3 — asynchronous messaging
- **Spring Retry** — retry of the publishing and of the synchronous calls
- **PostgreSQL** 16 — databases via Docker
- **AWS SDK for Java v2 (S3)** 2.29.29 — file storage client
- **LocalStack** 3.8.1 — S3 emulator
- **Apache Tika** 3.0.0 — MP3 metadata extraction (`resource-processor`)
- **MapStruct** 1.5.5 — DTO to Entity mapping
- **Lombok** 1.18.36
- **springdoc-openapi** 2.8.8 — Swagger UI
- **Newman / Postman** — integration tests
