# Base image that includes the Clojure CLI tools
FROM clojure:openjdk-17-tools-deps-buster

RUN mkdir -p /app
WORKDIR /app

# Prepare deps
COPY components/schema/deps.edn /app/components/schema/deps.edn
COPY components/content-negotiation/deps.edn /app/components/content-negotiation/deps.edn
COPY bases/rts-api/deps.edn /app/bases/rts-api/deps.edn
RUN clojure -P /app/bases/rts-api/deps.edn

# Add sources
COPY ./components/schema /app/components/schema
COPY ./components/content-negotiation /app/components/content-negotiation
COPY ./bases/rts-api /app/bases/rts-api

WORKDIR /app/bases/rts-api

CMD clojure -M:build