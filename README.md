# Music Microservices

A course project for "Introduction to Microservices". Implements two independent Spring Boot services for storing MP3
files and their metadata. The services communicate with each other over HTTP via OpenFeign, each with its own dedicated
database.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Client / Postman                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
          ┌────────────▼────────────┐
          │    Resource Service     │  :8080
          │   (stores MP3 files)    │
          │                         │
          │  DB: resource_service   │
          └────────────┬────────────┘
                       │ OpenFeign (HTTP)
          ┌────────────▼────────────┐
          │      Song Service       │  :8081
          │  (stores metadata)      │
          │                         │
          │  DB: song_service       │
          └─────────────────────────┘
```

### Modules

| Module             | Description                                                                |
|--------------------|----------------------------------------------------------------------------|
| `common-lib`       | Shared library: error handling, CSV validation, base exceptions            |
| `resource-service` | Accepts MP3 files, parses ID3 tags, stores binary data, calls song-service |
| `song-service`     | Stores track metadata (title, artist, album, duration, year)               |

---

## Service Interaction

When an MP3 file is uploaded, `resource-service` executes the following chain:

1. Validates the file (`.mp3` extension, not empty).
2. Saves the binary data to its own DB (table `resources`).
3. Parses ID3 tags (v1 / v2) using the `mp3agic` library.
4. Sends the metadata to `song-service` via **OpenFeign** (`POST /songs`).
5. Returns the saved resource `id` to the client.

When resources are deleted, both services perform a **soft delete** (`is_deleted = true`). `resource-service` marks its
own records first, then calls `DELETE /songs?id=...` on `song-service`.

> **Important:** The record ID in `song-service` matches the resource ID in `resource-service`. This is the linking key
> between the two databases.

### Feign Error Handling

`SongServiceClientProxyImpl` wraps all calls to `song-service`:

- `RetryableException` → HTTP 503 (service unavailable)
- `FeignException` → HTTP 502 (error on the song-service side)

---

## API

### Resource Service (`http://localhost:8080`)

| Method   | URL                        | Description                                            |
|----------|----------------------------|--------------------------------------------------------|
| `POST`   | `/resources`        | Upload an MP3 file (multipart/form-data, field `file`) |
| `GET`    | `/resources/{id}`   | Download MP3 binary data by ID                         |
| `DELETE` | `/resources?id=...` | Delete resources by a comma-separated list of IDs      |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`  
H2 Console (dev): `http://localhost:8080/h2-console`

### Song Service (`http://localhost:8081`)

| Method   | URL                    | Description                                      |
|----------|------------------------|--------------------------------------------------|
| `POST`   | `/songs`        | Save track metadata (called by resource-service) |
| `GET`    | `/songs/{id}`   | Get track metadata by ID                         |
| `DELETE` | `/songs?id=...` | Delete metadata by a comma-separated list of IDs |

Swagger UI: `http://localhost:8081/swagger-ui/index.html`  
H2 Console (dev): `http://localhost:8081/h2-console`

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

### Mode 1 — H2 (in-memory, no Docker)

Used by default. The database is created in memory on startup and cleared on shutdown.

```cmd
01_start_services.cmd
```

Equivalent:

```cmd
gradle :resource-service:bootRun :song-service:bootRun --parallel
```

### Mode 2 — PostgreSQL (via Docker)

Requires Docker to be running. Starts two PostgreSQL containers, then launches the services with the `docker` profile.

**Step 1 — start the databases:**

```cmd
docker compose up -d
```

**Step 2 — start the services:**

```cmd
01_start_services_with_postgress.cmd
```

Equivalent:

```cmd
set SPRING_PROFILES_ACTIVE=docker
gradle :resource-service:bootRun :song-service:bootRun --parallel
```

#### PostgreSQL Container Parameters

| Container     | Database           | Host Port | Credentials         |
|---------------|--------------------|-----------|---------------------|
| `resource-db` | `resource_service` | `5433`    | `postgres/postgres` |
| `song-db`     | `song_service`     | `5434`    | `postgres/postgres` |

> The `docker` profile configuration lives in `application-h2.yml` in each service.

---

## Notes and Gotchas

### MP3 Tag Parsing

- Both **ID3v1** and **ID3v2** tags are supported.
- If the `title` tag is missing or empty, the filename (without extension) is used as the track name.
- The year is normalized: the first 4 digits are extracted and validated against the range 1900–2099.
- Duration is formatted as `mm:ss`.
- Parsing is done via a temporary file on disk, because `mp3agic` requires a file path rather than an input stream.

### Soft Delete

Both entities (`AudioResource`, `Song`) use an `is_deleted` flag. Records are never physically removed from the
database. A `GET` request for a deleted record returns 404.

### CSV Validation on Delete

The `id` parameter in DELETE requests accepts a comma-separated list (e.g. `1,2,3`). Constraints enforced by
`common-lib`:

- Cannot be blank
- Maximum string length: 200 characters
- Each element must be a positive integer

### Unit Tests Are Disabled

Both services have `tasks.named('test') { enabled = false }` in their `build.gradle`. Testing is done exclusively
through Postman integration tests.

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

### Test Coverage

**Happy Path:**

- Create song metadata directly via song-service
- Upload a valid MP3 via resource-service
- Get resource and metadata by ID
- Delete resource and metadata (cascaded)
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

> **Test execution order matters.** Some tests depend on data created by earlier requests (e.g. the duplicate test
> requires an MP3 to be uploaded first).

---

## Requirements

| Tool   | Version                                     |
|--------|---------------------------------------------|
| Java   | 21+                                         |
| Gradle | Wrapper included                            |
| Docker | Any recent version (PostgreSQL mode only)   |
| Newman | Any recent version (integration tests only) |

---

## Tech Stack

- **Spring Boot** 3.4.0
- **Spring Data JPA** + Hibernate
- **Spring Cloud OpenFeign** — HTTP client between services
- **H2** — in-memory database for development
- **PostgreSQL** 16 — production-like database via Docker
- **mp3agic** 0.9.1 — ID3 tag parsing
- **MapStruct** 1.5.5 — DTO to Entity mapping
- **Lombok** 1.18.36
- **springdoc-openapi** 2.8.8 — Swagger UI
- **Newman / Postman** — integration tests
