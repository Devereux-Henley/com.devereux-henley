# All docker targets run from the repo root so the Dockerfile's COPY paths
# (projects/api, components/, bases/) resolve correctly.
REPO_ROOT := $(shell git rev-parse --show-toplevel)

build_docker:
	docker build -f $(REPO_ROOT)/bases/rts-api/Dockerfile -t rts-api $(REPO_ROOT)

run_docker:
	mkdir -p $(REPO_ROOT)/tmp-data
	docker run -d -p "3001:3001" \
		-v $(REPO_ROOT)/tmp-data:/data \
		-e RTS_API_HOSTNAME=http://localhost:3001 \
		--name "rts_api_local" rts-api:latest

start_docker:
	docker container start "rts_api_local"

stop_docker:
	docker container stop "rts_api_local"
