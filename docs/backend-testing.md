# Backend Testing

## Philosophy

Tests in this repository are **unit tests that stub the database boundary**. The goal is to verify that each layer of the backend does exactly what it is responsible for, without requiring a live database, running server, or any external infrastructure.

`clojure.test` is the only test framework used. There are no integration tests, no test fixtures that spin up a database, and no HTTP-level tests.

---

## Layer responsibilities and what is tested

The backend is divided into three layers, each with its own test concern.

### Domain schemas

Tests validate Malli schemas directly using `malli.core/validate`. Each test confirms that a given map either satisfies or violates a schema. These tests cover:

- A valid input that should pass
- A missing required field
- A field with the wrong type
- A field that is `nil` when it should not be

```clojure
(deftest valid-input
  (is (malli/validate resource-schema {:name "Example"})))

(deftest missing-name
  (is (not (malli/validate resource-schema {}))))
```

### Handler functions

Tests verify that handler functions correctly transform data from the database layer before returning it to callers. The database namespace is stubbed with `with-redefs`. These tests cover:

- `:type` is assigned to the correct value
- All fields from the database result are preserved on the returned map
- Collection handlers assign `:type` to every element
- Collection handlers return the correct count
- Empty collections are handled without error
- `nil` is returned when the database returns `nil` (not-found case)

```clojure
(deftest get-by-eid-assigns-type
  (with-redefs [db/get-by-eid (fn [_ _] {:eid eid :name "Example"})]
    (let [result (handlers/get-by-eid test-deps eid)]
      (is (= :domain/resource (:type result))))))
```

The dependency map passed to handlers under test is always `{:connection nil}`. Because the database function is fully replaced by `with-redefs`, the connection value is never used.

---

## File layout

Tests mirror the source tree. Component tests live under `components/<component>/test/`; base tests live under `bases/<base>/test/`.

---

## What is not tested

- **Database queries.** SQL files are not exercised by unit tests. Correctness is verified by e2e tests against a running server.
- **Web handlers and routes.** Integrant `init-key` methods, reitit route definitions, and Ring middleware are not unit-tested.
- **HTML templates.** Selmer templates are not rendered in unit tests.
- **Content negotiation.** Muuntaja format encoding is not exercised by unit tests.

These layers are covered by [E2E tests](rts-api/e2e-testing.md).

---

## Running tests

```bash
# All tests across affected components
clojure -M:poly test

# All tests regardless of changes
clojure -M:poly test :all

# A specific namespace
clojure -A:test -e "(require 'clojure.test '<namespace>) (clojure.test/run-tests '<namespace>)"
```

Or use a REPL and evaluate `(clojure.test/run-all-tests)` after loading the relevant namespaces.
