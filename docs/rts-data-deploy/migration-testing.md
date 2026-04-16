# Migration Testing

Migration tests live in `bases/rts-data-deploy/test/` and use real SQLite databases. There are no mocks. Each test creates an isolated temp database, runs migrations against it, and deletes the file on completion.

## What is tested

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
3. Rolls back all migrations in reverse order.
4. Asserts every table is gone.

## Database cleanup

The `with-temp-db` macro wraps each test. It creates a temp file, opens a JDBC connection for assertion queries, and deletes the file in a `finally` block after the test body. `deleteOnExit` is also registered as a fallback.

```clojure
(with-temp-db [cfg conn]
  (migratus/up cfg 1)
  (is (table-exists? conn "game"))
  (migratus/down cfg 1)
  (is (not (table-exists? conn "game"))))
```

## Running the tests

```bash
clojure -M:poly test :all
```

Or directly:

```bash
cd bases/rts-data-deploy
clojure -M:test -e "(require 'clojure.test)
                    (require 'com.devereux-henley.rts-data-deploy.migrations-test)
                    (clojure.test/run-tests 'com.devereux-henley.rts-data-deploy.migrations-test)"
```
