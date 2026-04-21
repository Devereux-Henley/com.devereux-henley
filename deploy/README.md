# DigitalOcean deployment

Target: a single DigitalOcean Droplet running `docker compose`, fronted by Caddy for
TLS, with SQLite persisted on an attached DO Block Storage volume.

## Layout

- `Dockerfile` — lives at `bases/rts-api/Dockerfile`, builds an uberjar from
  `projects/api/` and ships it on a slim JRE. Built + pushed to GHCR by
  `.github/workflows/publish-image.yml` on every push to `main` and every
  `stable-*` tag.
- `deploy/docker-compose.yml` — two services: `app` (the API container) and
  `caddy` (TLS-terminating reverse proxy). SQLite is persisted in a host
  directory mounted at `/data`.
- `deploy/Caddyfile` — reverse-proxies `${APP_DOMAIN}` to the `app` container
  and handles Let's Encrypt automatically.
- `deploy/.env.example` — the full list of env vars the deploy needs. Copy to
  `deploy/.env` on the host and fill in real values.

## One-time Droplet bootstrap

1. **Create a Droplet** — Ubuntu 24.04, `s-2vcpu-2gb` is plenty. SSH-key auth.
2. **Create and attach a Block Storage volume** (e.g. 10 GB). DO's web console
   writes fstab for you; confirm with `lsblk` and `mount | grep rts-data`.
   Target mount: `/mnt/rts-data`. The container runs as UID 10001, so the
   mount point needs to be writable by that UID:
   ```bash
   chown -R 10001:10001 /mnt/rts-data
   ```
3. **Install Docker + compose plugin**:
   ```bash
   apt-get update
   apt-get install -y ca-certificates curl
   install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
     -o /etc/apt/keyrings/docker.asc
   chmod a+r /etc/apt/keyrings/docker.asc
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
     https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
     > /etc/apt/sources.list.d/docker.list
   apt-get update
   apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
   ```
4. **Put the deploy files on the host**:
   ```bash
   mkdir -p /opt/rts-api
   scp deploy/docker-compose.yml deploy/Caddyfile deploy/.env.example \
       root@<droplet-ip>:/opt/rts-api/
   ssh root@<droplet-ip>
   cd /opt/rts-api
   cp .env.example .env
   # edit .env: APP_IMAGE, APP_DOMAIN, RTS_API_HOSTNAME, AUTH_HOSTNAME,
   # AUTH_SLUG, DATA_DIR=/mnt/rts-data
   ```
5. **Log in to GHCR** so the Droplet can pull the private image. Generate a
   classic PAT with `read:packages` scope at github.com/settings/tokens:
   ```bash
   echo $GHCR_TOKEN | docker login ghcr.io -u <your-gh-username> --password-stdin
   ```
6. **Point DNS** — an A record for `${APP_DOMAIN}` → Droplet IPv4. Caddy obtains
   the cert on first boot; this only works once DNS is live.
7. **First deploy**:
   ```bash
   cd /opt/rts-api
   docker compose pull
   docker compose up -d
   docker compose logs -f app
   ```
   Migrations run automatically on startup (see `::rts-data/migrate` in
   `configuration.clj`). `GET /status` should return `{"status":"ok"}`.

## Subsequent deploys

```bash
cd /opt/rts-api
docker compose pull   # fetches the new image tag (update APP_IMAGE in .env
                      # if pinning to a SHA/stable-* tag)
docker compose up -d  # recreates the app container with zero-downtime-ish swap
```

Caddy stays up across deploys; only `app` is recreated.

## Ory Cloud configuration

Not a code change but easy to forget. In the Ory project dashboard:

- Add `https://${APP_DOMAIN}` to **Allowed return URLs** for login + logout
  flows so Ory redirects the user back after auth.
- Either set up a **custom domain** on Ory (e.g. `auth.${APP_DOMAIN}`) so the
  `ory_session_${AUTH_SLUG}` cookie is set on a domain the browser will
  present back to this app, or accept that cross-site cookies require
  `SameSite=None` + HTTPS on both ends.
- `AUTH_HOSTNAME` in `.env` must point at the fully-qualified Ory endpoint
  (the slug URL or the custom domain).

## Backups

SQLite on a volume survives container restarts but not data corruption. Set up
a nightly cron on the Droplet:

```cron
0 3 * * * sqlite3 /mnt/rts-data/database.db ".backup /tmp/backup.db" \
  && s3cmd put /tmp/backup.db s3://rts-backups/$(date -u +\%Y-\%m-\%d).db \
  && rm /tmp/backup.db
```

DO Spaces is S3-compatible and cheap (~$5/mo). Configure `s3cmd` with the
Spaces access key. Keep 30 days of backups via a lifecycle rule.

## Local smoke test

From the repo root (not the host):

```bash
docker build -f bases/rts-api/Dockerfile -t rts-api:test .

mkdir -p tmp-data
docker run --rm -p 3001:3001 \
  -v $(pwd)/tmp-data:/data \
  -e RTS_API_HOSTNAME=http://localhost:3001 \
  -e AUTH_HOSTNAME=http://localhost:4000 \
  -e AUTH_SLUG=eloquentyalowwhtijq6my4 \
  rts-api:test

# in another shell
curl -s http://127.0.0.1:3001/status   # => {"status":"ok"}
```
