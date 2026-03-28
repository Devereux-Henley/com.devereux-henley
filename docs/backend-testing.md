# Backend Testing

## Philosophy

Tests in this repository are **unit tests that stub the database boundary**. The goal is to verify that each layer of the backend does exactly what it is responsible for, without requiring a live database, running server, or any external infrastructure.

`clojure.test` is the only test framework used. There are no integration tests, no test fixtures that spin up a database, and no HTTP-level tests.

---

## Layer responsibilities and what is tested

The backend is divided into three layers, each with its own test concern.

### Domain schemas (`domain/`)

Tests validate Malli schemas directly using `malli.core/validate`. Each test confirms that a given map either satisfies or violates a schema. These tests cover:

- A valid input that should pass
- A missing required field
- A field with the wrong type
- A field that is `nil` when it should not be

```clojure
(deftest game-valid-input
  (is (malli/validate domain.game/game {:name "Total War: Warhammer III"})))

(deftest game-missing-name
  (is (not (malli/validate domain.game/game {}))))
```

### Handler functions (`handlers/`)

Tests verify that handler functions correctly transform data from the database layer before returning it to callers. The database namespace is stubbed with `with-redefs`. These tests cover:

- `:type` is assigned to the correct value (e.g. `:game/unit`)
- All fields from the database result are preserved on the returned map
- Collection handlers assign `:type` to every element
- Collection handlers return the correct count
- Empty collections are handled without error
- `nil` is returned when the database returns `nil` (not-found case)

```clojure
(deftest get-unit-by-eid-assigns-type
  (with-redefs [db.game/get-unit-by-eid (fn [_ _] {:eid unit-eid :name "Karl Franz"})]
    (let [result (handlers.game/get-unit-by-eid test-deps unit-eid)]
      (is (= :game/unit (:type result))))))

(deftest get-unit-by-eid-preserves-fields
  (with-redefs [db.game/get-unit-by-eid (fn [_ _] {:eid unit-eid :name "Karl Franz" :description "Emperor of the Empire"})]
    (let [result (handlers.game/get-unit-by-eid test-deps unit-eid)]
      (is (= unit-eid (:eid result)))
      (is (= "Karl Franz" (:name result))))))
```

The dependency map passed to handlers under test is always `{:connection nil}`. Because the database function is fully replaced by `with-redefs`, the connection value is never used.

### Handler core utilities (`handlers/core.clj`)

Tests for the shared handler helpers (`assign-model-type`, etc.) verify the utility behaviour in isolation, including edge cases like `nil` input and overwriting an existing `:type` field.

---

## File layout

Tests mirror the source tree under `bases/<base>/test/`.

```
bases/rts-api/
├── src/
│   └── com/devereux_henley/rts_api/
│       ├── domain/game.clj
│       ├── handlers/core.clj
│       ├── handlers/game.clj
│       └── handlers/tournament.clj
└── test/
    └── com/devereux_henley/rts_api/
        ├── domain/game_test.clj
        ├── handlers/core_test.clj
        ├── handlers/game_test.clj
        └── handlers/tournament_test.clj
```

Component tests follow the same pattern under `components/<component>/test/`.

---

## What is not tested

- **Database queries.** SQL files are not exercised by any test. Correctness is verified manually against a running SQLite database.
- **Web handlers and routes.** The Integrant `init-key` methods, reitit route definitions, and Ring middleware are not tested.
- **HTML templates.** Selmer templates are not rendered in tests.
- **Content negotiation.** Muuntaja format encoding is not exercised by any test.

---

## Running tests

From a base or component directory:

```
clojure -A:test -e "(require 'clojure.test '<namespace>) (clojure.test/run-tests '<namespace>)"
```

Or use a REPL and evaluate `(clojure.test/run-all-tests)` after loading the relevant namespaces.
