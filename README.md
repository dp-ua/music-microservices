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
| `api-gateway`        | Single entry point (`:8080`), routes `/resources/**` and `/songs/**` to services via Eureka          |
| `eureka-server`      | Service registry (`:8761`)                                                                           |
| `resource-service`   | Accepts MP3 files, stores them in S3, keeps their location in its DB, publishes the upload event     |
| `song-service`       | Stores track metadata (title, artist, album, duration, year)                                         |
| `resource-processor` | Listens to the queue, downloads the MP3, extracts metadata (Apache Tika), saves it in `song-service` |

Infrastructure containers: `resource-db`, `song-db` (PostgreSQL), `localstack` (S3), `rabbitmq` (message broker).

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

### Unit Tests Are Disabled

All modules have `tasks.named('test') { enabled = false }` in their `build.gradle`. Testing is done exclusively through
Postman integration tests and the manual scenarios below.

---

## Integration Tests (Postman / Newman)

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

| Tool   | Version                                     |
|--------|---------------------------------------------|
| Java   | 21+ (only to build locally without Docker)  |
| Gradle | Wrapper included                            |
| Docker | Any recent version with Docker Compose      |
| Newman | Any recent version (integration tests only) |

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
