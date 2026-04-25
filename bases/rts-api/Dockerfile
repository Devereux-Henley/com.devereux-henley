# syntax=docker/dockerfile:1.7

# ---- Rust builder (tw-replay-parser) ---------------------------------------
FROM docker.io/library/rust:1.81-bookworm-slim AS rust-builder

WORKDIR /app
COPY tools/replay-parser/ tools/replay-parser/
RUN cd tools/replay-parser && cargo build --release

# ---- Clojure builder (rts-api.jar) -----------------------------------------
FROM docker.io/library/clojure:temurin-21-tools-deps-bookworm-slim AS builder

WORKDIR /app

# Copy just the deps.edn tree first so Docker can cache the dep download layer.
COPY projects/api/deps.edn                           projects/api/deps.edn
COPY projects/api/build.clj                          projects/api/build.clj
COPY bases/rts-api/deps.edn                          bases/rts-api/deps.edn
COPY bases/rts-data-deploy/deps.edn                  bases/rts-data-deploy/deps.edn
COPY components/content-negotiation/deps.edn         components/content-negotiation/deps.edn
COPY components/e2e/deps.edn                         components/e2e/deps.edn
COPY components/http/deps.edn                        components/http/deps.edn
COPY components/jdbc/deps.edn                        components/jdbc/deps.edn
COPY components/resourcekit/deps.edn                 components/resourcekit/deps.edn
COPY components/rts-data/deps.edn                    components/rts-data/deps.edn
COPY components/rts-data-access/deps.edn             components/rts-data-access/deps.edn
COPY components/rts-domain/deps.edn                  components/rts-domain/deps.edn
COPY components/rts-web/deps.edn                     components/rts-web/deps.edn
COPY components/schema/deps.edn                      components/schema/deps.edn

RUN cd projects/api && clojure -A:build -P

# Now bring in the actual source and build the uberjar.
COPY bases/                 bases/
COPY components/            components/
COPY projects/              projects/

RUN cd projects/api && clojure -T:build uber

# ---- Runtime ----------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:21-jre-noble

RUN groupadd --system --gid 10001 app \
 && useradd  --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app /data \
 && chown -R app:app /app /data

WORKDIR /app
COPY --from=builder      --chown=app:app /app/projects/api/target/rts-api.jar /app/rts-api.jar
COPY --from=rust-builder --chown=app:app /app/tools/replay-parser/target/release/tw-replay-parser /app/tw-replay-parser

USER app

ENV RTS_API_PORT=3001 \
    RTS_DB_CONNECTION_URI=jdbc:sqlite:/data/database.db \
    REPLAY_PARSER_BIN=/app/tw-replay-parser

EXPOSE 3001
VOLUME ["/data"]

ENTRYPOINT ["java", "-jar", "/app/rts-api.jar"]
