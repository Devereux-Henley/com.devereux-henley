# Asset CDN delivery + JAR slimming

## Problem

The rpfm-scraper writes 1,782 unit-card PNGs (~37 MB) plus an icon tree
(~32 MB) into `components/rts-web/resources/rts-web/asset/` and they ride
the resource classpath into the rts-api uber JAR. Jetty's
`ResourceHandler` serves them in production today.

Two costs:

1. **Bloat.** ~70 MB of static images in every deployed artifact.
   Builds, image pushes, and rolling deploys all carry the weight.
2. **Latency.** Every portrait request hits the JVM. A CDN with
   long-lived edge caches outperforms `ResourceHandler` for static
   binary assets, especially under post-match modal load where dozens
   of portraits render at once.

Both are addressed by moving `asset/card/` and `asset/icon/` out of the
JAR and onto a CDN, while keeping the dev-mode classpath path working
unchanged.

## Constraints

- **Local dev must keep working with no infra.** A developer running
  `claude-workspace/go!` should still see portraits without provisioning
  a bucket. Implies the URL prefix is opt-in and defaults empty.
- **Source-of-truth for assets stays in the repo.** The scraper writes
  into the resource tree, git tracks the PNGs, and the deploy pipeline
  syncs them outward. Avoids a separate asset-provisioning workflow.
- **No cache-bust dance.** Filenames are content-addressable
  (`<unit-eid>.png`); long-lived `Cache-Control` is safe.
- **`asset/style/` and `asset/image/` stay in the JAR.** They are small,
  chrome-critical, and bundling them keeps first paint independent of
  the CDN.

## Proposed approach

### 1. `ASSET_BASE_URL` env var + Selmer global

- Read the env var in `bases/rts-api/src/.../web.clj` config (default
  empty string).
- Expose it as a Selmer global (e.g. `{{asset-base-url}}`) so every
  template can compose URLs without per-handler plumbing.
- Sweep every `/card/...`, `/icon/...`, and `/image/...` reference under
  `components/rts-web/resources/rts-web/view/**/*.html` to prefix with
  `{{asset-base-url}}`.
- Default empty → URLs stay root-relative → Jetty serves from JAR
  (today's behaviour, dev-mode-friendly).

### 2. Strip large asset trees from the uber JAR

- Modify `projects/api/build.clj` to exclude `rts-web/asset/card/` and
  `rts-web/asset/icon/` from the resource paths bundled into the uber
  JAR.
- Keep `asset/style/` + `asset/image/`.
- Add a build-time assertion that `target/rts-api.jar` is below a sane
  size cap (e.g. 100 MB) to catch accidental re-bundling.

### 3. Provision the CDN bucket *(out-of-tree)*

- Pick infra (S3 + CloudFront, R2, or whatever fits the existing deploy
  story). Defer the choice until we are ready to cut over.
- Set up a sync step in the deploy pipeline (`rsync` or `aws s3 sync`
  from `components/rts-web/resources/rts-web/asset/` → bucket origin).
- Configure `Cache-Control: public, max-age=31536000, immutable` on
  the synced objects.

### 4. Production cutover

- Set `ASSET_BASE_URL=https://cdn.<domain>/` in the deploy environment.
- Verify in browser devtools that all `/card/...` and `/icon/...`
  requests resolve to the CDN.
- Verify the placeholder fallback still triggers for any unit whose
  PNG hasn't synced yet.

## Open questions

- **CDN choice.** Defer until cutover; not blocking on the JAR/template
  changes.
- **Versioned URLs?** Not needed today — eids are content-addressable.
  If invalidations become routine, add a content hash to the URL
  (`/card/unit/<eid>-<hash>.png`) and have the scraper emit it.
- **Ability/spell/item/mount icons.** Same path — they share the
  resource tree and the scraper produces them. The URL-prefix sweep
  in step 1 should cover all `asset/icon/...` refs in the same pass.
