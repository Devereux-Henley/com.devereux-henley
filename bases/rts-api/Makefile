build_docker:
	docker build -f Dockerfile -t rts-api ../..

run_docker:
	docker run -d -p "3001:3001" --name "rts_api_local" rts-api:latest

start_docker:
	docker container start "rts_api_local"

stop_docker:
	docker container stop "rts_api_local"

ory_local:
	ory tunnel --dev --project eloquent-yalow-whtijq6my4 "http://localhost:3001"
