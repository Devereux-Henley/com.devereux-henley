# Database

## Overview

The database layer is split across two Polylith units: the `rts-data` component, which owns migrations and schema, and the `rts-data-deploy` base, which is a standalone CI tool for running migrations independent of the application.

Application startup runs migrations automatically via Integrant. The deploy base exists so migrations can also be applied in CI pipelines without starting the full application.

---

## Component and base structure

```
components/
└── rts-data/
    ├── deps.edn
    ├── src/
    │   └── com/devereux_henley/rts_data/
    │       └── migrations.clj        Integrant ::migrate key
    └── resources/
        └── rts-data/migrations/      Numbered Migratus SQL files

bases/
└── rts-data-deploy/
    ├── deps.edn
    ├── src/
    │   └── com/devereux_henley/rts_data_deploy/
    │       └── core.clj              CLI entry point
    └── test/
        └── com/devereux_henley/rts_data_deploy/
            └── migrations_test.clj   Migration up/down tests
```

### `rts-data` (component)

Owns the migration SQL files and the Integrant key that runs them. Any base that needs a database depends on `mono/rts-data`. The component has no knowledge of the application — it provides only the migration mechanism and the schema definitions.

### `rts-data-deploy` (base)

A runnable CLI tool. Its sole job is to connect to a database and apply or roll back migrations. It is used in CI to migrate the database before starting a new application version.

---

## Migration tool

Migrations use [Migratus](https://github.com/yogthos/migratus) with the `:database` store. Migratus tracks applied migrations in a `schema_migrations` table that it creates automatically on first run. Calling `migratus/migrate` is safe to repeat — it is a no-op when all migrations have already been applied.

### File naming

Migration files live at `components/rts-data/resources/rts-data/migrations/` and follow this pattern:

```
{id}-{description}.up.sql
{id}-{description}.down.sql
```

IDs are zero-padded integers. Migratus parses the numeric prefix as a `Long` and applies migrations in ascending ID order.

```
000001-create-game-table.up.sql
000001-create-game-table.down.sql
000002-create-social-media-platform-table.up.sql
000002-create-social-media-platform-table.down.sql
...
```

### Migration ordering

Migrations are ordered to satisfy foreign key dependencies. Tables with no foreign keys are created first; tables that reference them follow.

| ID | Table | Dependencies |
|---|---|---|
| 000001 | `game` | — |
| 000002 | `social_media_platform` | — |
| 000003 | `unit_type` | `game` |
| 000004 | `unit_category` | `game` |
| 000005 | `faction` | `game` |
| 000006 | `unit` | `game`, `faction`, `unit_type`, `unit_category` |
| 000007 | `game_social_link` | `game`, `social_media_platform` |

---

## Running migrations

### In the application (Integrant)

`rts-api` runs migrations automatically when the Integrant system initialises. The `::rts-data.migrations/migrate` component is declared before `::db/connection` in `configuration.clj`, and `::db/connection` takes an `integrant.core/ref` to it. This ensures migrations complete before any handler can use the database.

```clojure
{::migrations/migrate {:db-spec       db/db-spec
                        :migration-dir "rts-data/migrations"}
 ::db/connection       {:migrations (integrant.core/ref ::migrations/migrate)}}
```

The `::db/connection` init-key ignores the `:migrations` value; the ref exists only to enforce initialisation order.

### In CI (rts-data-deploy)

The `rts-data-deploy` base exposes a `-main` function that connects to the database and runs Migratus. It reads the connection URI from the `RTS_DB_CONNECTION_URI` environment variable, defaulting to `jdbc:sqlite:db/database.db`.

```
clojure -M:build migrate
clojure -M:build rollback
```

Calling `-main` with no argument defaults to `migrate`.

---

## Testing strategy

Migration tests live in `bases/rts-data-deploy/test/` and use real SQLite databases. There are no mocks. Each test creates an isolated temp database, runs migrations against it, and deletes the file on completion.

### What is tested

**Individual migration tests** — one test per migration. Each test:

1. Creates a fresh temp SQLite file.
2. Calls `(migratus/up config id)` for the target migration.
3. Asserts that the expected table exists in `sqlite_master`.
4. Calls `(migratus/down config id)` to roll back.
5. Asserts that the table no longer exists.

SQLite does not enforce foreign key constraints by default, so each migration can be exercised in isolation without applying its prerequisites first.

**Full cycle test** — a single test that:

1. Calls `(migratus/migrate config)` to apply all migrations.
2. Asserts every table exists.
3. Calls `(migratus/down config 7 6 5 4 3 2 1)` to roll back all migrations in reverse order.
4. Asserts every table is gone.

### Database cleanup

The `with-temp-db` macro wraps each test. It creates a temp file, opens a JDBC connection for assertion queries, and deletes the file in a `finally` block after the test body, whether or not an error occurred. `deleteOnExit` is also registered as a fallback for abnormal JVM exits.

```clojure
(with-temp-db [cfg conn]
  (migratus/up cfg 1)
  (is (table-exists? conn "game"))
  (migratus/down cfg 1)
  (is (not (table-exists? conn "game"))))
```

### Running the tests

From `bases/rts-data-deploy/`:

```
clojure -M:test -e "(require 'clojure.test)
                    (require 'com.devereux-henley.rts-data-deploy.migrations-test)
                    (clojure.test/run-tests 'com.devereux-henley.rts-data-deploy.migrations-test)"
```

---

## Classpath note

Migratus 0.9.0 discovers migration files using `clojure.java.classpath/classpath-directories`. The default transitive version of that library (`0.2.3`) cannot read the AppClassLoader in Java 9+ and returns an empty sequence, causing Migratus to find no migrations. `rts-data/deps.edn` pins `org.clojure/java.classpath` to `0.3.0`, which falls back to the `java.class.path` system property and resolves correctly under the Clojure CLI on any supported JVM.

---

## Seed data

Seed scripts are kept separate from migrations and live in `bases/rts-api/resources/rts-api/sql/seed/`. They are not run automatically. To seed a local development database, call `(db/seed-db)` from the REPL after the system has started and migrations have been applied.
