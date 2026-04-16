# Database

## Overview

The database layer is split across two Polylith units: the `rts-data` component, which owns migrations and seed data, and the `rts-data-deploy` base, which is a standalone CLI tool for running migrations independent of the application.

Application startup runs migrations automatically via Integrant. The deploy base exists so migrations can also be applied in CI pipelines without starting the full application.

---

## Component and base structure

```
components/
└── rts-data/
    ├── deps.edn
    ├── src/.../migrations.clj        Integrant ::migrate key
    └── resources/rts-data/migrations/ Numbered Migratus SQL files

bases/
└── rts-data-deploy/
    ├── src/.../core.clj              CLI entry point
    └── test/.../migrations_test.clj  Migration up/down tests
```

### `rts-data` (component)

Owns the migration SQL files and the Integrant key that runs them. Any base that needs a database depends on `mono/rts-data`. The component has no knowledge of the application — it provides only the migration mechanism and the seed data.

### `rts-data-deploy` (base)

A runnable CLI tool for applying or rolling back migrations. See [docs/rts-data-deploy.md](rts-data-deploy.md).

---

## Migration tool

Migrations use [Migratus](https://github.com/yogthos/migratus) with the `:database` store. Migratus tracks applied migrations in a `schema_migrations` table that it creates automatically on first run. Calling `migratus/migrate` is safe to repeat — it is a no-op when all migrations have already been applied.

### File naming

Migration files live at `components/rts-data/resources/rts-data/migrations/` and follow this pattern:

```
{id}-{description}.up.sql
{id}-{description}.down.sql
```

IDs are zero-padded integers (6 digits). Migratus parses the numeric prefix as a `Long` and applies migrations in ascending ID order.

### Migration ordering

Migrations are ordered to satisfy foreign key dependencies. Tables with no foreign keys are created first; tables that reference them follow.

---

## Running migrations

### In the application (Integrant)

Bases that use the database declare the migration component before the connection in their Integrant configuration. The `::db/connection` key takes an `integrant.core/ref` to `::rts-data/migrate`, ensuring migrations complete before any handler can use the database.

```clojure
{::migrations/migrate {:db-spec       db/db-spec
                        :migration-dir "rts-data/migrations"}
 ::db/connection       {:migrations (integrant.core/ref ::migrations/migrate)}}
```

The `::db/connection` init-key ignores the `:migrations` value; the ref exists only to enforce initialisation order.

### In CI

See [docs/rts-data-deploy.md](rts-data-deploy.md).

---

## Seed data

Seed scripts are kept separate from migrations and live in `components/rts-data/resources/rts-data/sql/seed/`. They are not run automatically. To seed a local development database, call `(seed-db!)` from the REPL after the system has started and migrations have been applied.

---

## Classpath note

Migratus 0.9.0 discovers migration files using `clojure.java.classpath/classpath-directories`. The default transitive version of that library (`0.2.3`) cannot read the AppClassLoader in Java 9+ and returns an empty sequence, causing Migratus to find no migrations. `rts-data/deps.edn` pins `org.clojure/java.classpath` to `0.3.0`, which falls back to the `java.class.path` system property and resolves correctly under the Clojure CLI on any supported JVM.
