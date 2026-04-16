# rts-data-deploy

A standalone CLI tool for running database migrations independent of the application. Used in CI pipelines to apply or roll back migrations before starting a new application version.

## Sub-documents

- [Migration Testing](rts-data-deploy/migration-testing.md) — how migration up/down tests work

## Key paths

| Path | Purpose |
|------|---------|
| `bases/rts-data-deploy/src/.../core.clj` | CLI entry point |
| `bases/rts-data-deploy/test/.../migrations_test.clj` | Migration up/down tests |
| `components/rts-data/resources/rts-data/migrations/` | Migratus SQL files |

## Usage

```bash
clojure -M:build -A deploy-data uber
java -jar target/rts-data-deploy.jar migrate
java -jar target/rts-data-deploy.jar rollback
```

The `-main` function reads the connection URI from `RTS_DB_CONNECTION_URI`, defaulting to `jdbc:sqlite:db/database.db`. Calling with no argument defaults to `migrate`.
