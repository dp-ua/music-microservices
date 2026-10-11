# Music Microservices

A course project for "Microservices Fundamentals". Spring Boot services for storing MP3 files and their metadata.
Files are stored in a cloud storage (S3, emulated by LocalStack), their locations in PostgreSQL. Services discover each
other through Eureka and are reachable through a single API Gateway.

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
│                 │                             │ (stores metadata)│
└───┬─────────┬───┘                             └────────┬────────┘
    │         │                                          │
    ▼         ▼                                          ▼
┌────────┐ ┌──────────────┐                       ┌──────────┐
│LocalStack│ │ resource-db  │                       │ song-db  │
│  (S3)  │ │ (location)   │                       │          │
│ MP3 file│ └──────────────┘                       └──────────┘
└────────┘

Resource Processor: a separate Spring Boot application without a web interface;
for now it only extracts MP3 metadata (Apache Tika) and is not called by other services.
```

### Modules

| Module               | Description                                                                                    |
|----------------------|------------------------------------------------------------------------------------------------|
| `api-gateway`        | Single entry point (`:8080`), routes `/resources/**` and `/songs/**` to services via Eureka    |
| `eureka-server`      | Service registry (`:8761`)                                                                     |
| `resource-service`   | Accepts MP3 files, stores them in S3, keeps their location in its DB                           |
| `song-service`       | Stores track metadata (title, artist, album, duration, year)                                   |
| `resource-processor` | Extracts MP3 metadata with Apache Tika. No web interface, not connected to other services yet  |

Infrastructure containers: `resource-db`, `song-db` (PostgreSQL), `localstack` (S3).

---

## Service Interaction

### Upload: `POST /resources`

`resource-service` does **not** call any other service while uploading:

1. Validates that the body is not empty (`Content-Type: audio/mpeg`).
2. Stores the original MP3 in the S3 bucket under a generated key (`<uuid>.mp3`).
3. Saves the location (`storage_bucket`, `storage_key`) and the file size in the table `resources`.
4. Returns the resource `id`.

If saving to the DB fails, the file is removed from S3. No metadata is created in `song-service`: after an upload,
`GET /songs/{id}` returns 404 until the metadata is created (`POST /songs`).

### Download: `GET /resources/{id}`

The record is found in the DB, the file is read from S3 by the stored location. A missing record returns 404.

### Delete: `DELETE /resources?id=1,2,3`

A synchronous cleanup. For every existing resource:

1. The file is removed from S3.
2. The record is removed from the DB (physically, no soft delete in `resource-service`).
3. `DELETE /songs?id=...` is called on `song-service` via **OpenFeign** to delete the related metadata.

Unknown ids are ignored. `song-service` also ignores ids without metadata, so deleting a resource without a song is not
an error.

> **Important:** The record ID in `song-service` matches the resource ID in `resource-service`. This is the linking key
> between the two databases.

### Feign Error Handling

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
| `POST`   | `/songs`        | Save track metadata                              |
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

Requires Docker. Everything (services, databases, LocalStack, Eureka, gateway) is started by Docker Compose.

**Step 1 — create `.env`** from the template:

```cmd
copy .env.example .env
```

The `S3_*` variables are optional: `compose.yaml` has the same defaults.

**Step 2 — build and start:**

```cmd
docker compose up --build -d
```

If the DB schema changed (`init-scripts/resource-db/init.sql`), recreate the volumes, because init scripts run only on
an empty database:

```cmd
docker compose down -v
```

#### Containers and ports

| Container            | Purpose                           | Host Port | Notes                                    |
|----------------------|-----------------------------------|-----------|------------------------------------------|
| `api-gateway`        | Entry point                       | `8080`    |                                          |
| `eureka-server`      | Service registry                  | `8761`    |                                          |
| `resource-db`        | PostgreSQL for `resource-service` | `5433`    | `resource_service`, `postgres/postgres`  |
| `song-db`            | PostgreSQL for `song-service`     | `5434`    | `song_service`, `postgres/postgres`      |
| `localstack`         | S3 emulator                       | `4566`    | Bucket `resources` is created on startup |
| `resource-service`   | Resource API                      | none      | Port `8081` inside the network, Eureka   |
| `song-service`       | Song API                          | none      | Port `8082` inside the network, Eureka   |
| `resource-processor` | MP3 metadata extraction           | none      | Exits right after startup (see below)    |

#### Scaling `song-service`

Several instances of `song-service` can be started. Every instance registers itself in Eureka, and the API Gateway
(`lb://SONG-SERVICE`) balances requests between them:

```cmd
docker compose up -d --scale song-service=2
```

Requests must go through the gateway (`http://localhost:8080`): the instances are not published on host ports.

#### Configuration (`resource-service`)

| Variable        | Default                  | Description            |
|-----------------|--------------------------|------------------------|
| `S3_ENDPOINT`   | `http://localstack:4566` | S3 endpoint            |
| `S3_REGION`     | `us-east-1`              | S3 region              |
| `S3_BUCKET`     | `resources`              | Bucket for MP3 files   |
| `S3_ACCESS_KEY` | `test`                   | LocalStack credentials |
| `S3_SECRET_KEY` | `test`                   | LocalStack credentials |

To inspect the bucket from the host:

```cmd
docker exec localstack awslocal s3 ls s3://resources
```

---

## Resource Processor

A basic Spring Boot application (`web-application-type: none`) with no controllers. `Mp3MetadataExtractor` reads ID3
tags with **Apache Tika** (`Mp3Parser`) and returns `name`, `artist`, `album`, `duration` (`mm:ss`) and `year`, the
same fields as the song-service API.

In this module it is not called by other services and does nothing on its own, so the container exits right after
startup. This is expected.

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
Postman integration tests.

---

## Integration Tests (Postman / Newman)

Tests are located in the `test-data/` folder. [Newman](https://www.npmjs.com/package/newman) is required to run them.

```cmd
02_run_test.cmd
```

Equivalent:

```cmd
cd test-data
newman run introduction_to_microservices.postman_collection.json
```

> **The collection is not adapted to Module 1 yet.** Upload no longer creates song metadata, so the scenarios that
> expect metadata after an upload (get metadata, delete with metadata, duplicate id → 409) have to create it explicitly
> through `POST /songs`.
>
> The collection variable `song_service_url` points to `http://localhost:8081`, but Song Service is not published on a
> host port in `compose.yaml`. Use `http://localhost:8080` (the gateway) for both `resource_service_url` and
> `song_service_url`.

### Test Coverage

**Happy Path:**

- Create song metadata directly via song-service
- Upload a valid MP3 via resource-service
- Get the resource (file from S3) by ID
- Delete resource and metadata
- Verify 404 after deletion

**Error Cases — Resource Service:**

- Upload an invalid file (not MP3) → 400
- Get a non-existent resource → 404
- Invalid IDs: letters, decimals, negative, zero → 400
- Delete a non-existent resource → 200 (empty list)
- Invalid CSV: letters, exceeds max length → 400

**Error Cases — Song Service:**

- Invalid fields: wrong duration/year format → 400
- Missing required fields → 400
- Duplicate ID → 409
- Invalid IDs and CSV → 400

> **Test execution order matters.** Some tests depend on data created by earlier requests.

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
- **Spring Cloud** (OpenFeign, Netflix Eureka, Gateway)
- **PostgreSQL** 16 — databases via Docker
- **AWS SDK for Java v2 (S3)** 2.29.29 — file storage client
- **LocalStack** 3.8.1 — S3 emulator
- **Apache Tika** 3.0.0 — MP3 metadata extraction (`resource-processor`)
- **MapStruct** 1.5.5 — DTO to Entity mapping
- **Lombok** 1.18.36
- **springdoc-openapi** 2.8.8 — Swagger UI
- **Newman / Postman** — integration tests
