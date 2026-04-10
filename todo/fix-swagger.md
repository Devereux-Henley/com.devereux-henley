Fork swagger-ui and apply changeset from https://github.com/swagger-api/swagger-ui/commit/940ebe0cff45885d4eaa36f5a0ee7624bc7b20bc

window.opener is null when redirecting off-origin for oauth2/openidconnect. This has not been fixed in upstream, so we need to fork the repository, apply fix locally, build swagger-ui and then drop all the swagger-ui resources on the resource path in rts-api under `resources/swagger-ui`.
