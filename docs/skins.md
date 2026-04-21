# Skins

The product ships with a default **War Room** visual system (cartographic palette, terracotta accent, editorial serif + geometric sans typography) and supports optional per-game **skins** that relayer palette and component CSS on top. This page covers how a skin is dispatched and how new skins are wired in.

## Mental model

- **Tokens** (`components/resourcekit/resources/resourcekit/assets/tokens.css`) define every design decision — neutrals, primary/semantic palettes, type ramps, spacing, radii, motion — as CSS custom properties on `:root`.
- **War Room base** (`components/rts-web/resources/rts-web/asset/style/app.css`) provides the default product styling, driven entirely off tokens.
- **Skins** sit under `components/rts-web/resources/rts-web/asset/style/themes/<skin>.css` (palette + font overrides) and `themes/<skin>-app.css` (component-level overrides the War Room look doesn't express through tokens alone).
- A skin is dispatched per-game via a server-side lookup and only loaded when the active game requests it. Games with no skin configured render the War Room default.

## Dispatch

A tiny namespace maps a game's eid onto a skin identifier:

```clojure
;; components/rts-web/src/com/devereux_henley/rts_web/skin.clj
(ns com.devereux-henley.rts-web.skin)

(def game-eid->skin
  "Game eid → skin identifier. The identifier matches the theme filename stem
   (resources/rts-web/asset/style/themes/<skin>.css)."
  {"eea787d7-1065-45eb-a3f6-e26f32c294a1" "warhammer3"})

(defn skin-for-game [game-eid]
  (some-> game-eid str game-eid->skin))
```

The `web/view.clj` `game-context-middleware` calls `skin/skin-for-game` on every request that carries a `:game-eid` path parameter and attaches the result to the request as `:game-context {:skin …}`. The `base-context` helper then threads `skin` into the render map so `entrypoint.html` can see it.

## Loading

`entrypoint.html` appends the skin stylesheets to the end of the `<head>` when a skin is active:

```html
<link rel="stylesheet" href="/resourcekit/assets/tokens.css"/>
<link rel="stylesheet" href="/resourcekit/assets/reset.css"/>
<link rel="stylesheet" href="/resourcekit/assets/base.css"/>
<link rel="stylesheet" href="/resourcekit/assets/framework.css"/>
<link rel="stylesheet" href="/style/app.css"/>
{% ifequal skin "warhammer3" %}
<link rel="stylesheet" href="/style/themes/warhammer3.css"/>
<link rel="stylesheet" href="/style/themes/warhammer3-app.css"/>
{% endifequal %}
```

Load order matters. The skin stylesheets come AFTER `app.css`, so skin rules win on tied specificity. They should rely on cascading through `:root`-scoped variables for palette changes rather than reselecting every element.

**HTMX boost is intentionally disabled.** If we boosted navigation, HTMX would swap only the `<body>` and leave the `<head>` unchanged, stranding skin `<link>` tags from the previous page. With standard browser navigation the `<head>` is rebuilt on every request and the skin conditional re-evaluates. See `docs/frontend.md` for the broader navigation / title-update story.

## Adding a new skin

1. Add the game's eid → skin name to `skin/game-eid->skin`.
2. Create `themes/<skin>.css` for palette + font overrides. Restrict the file to `:root` variable re-declarations where possible so the War Room component rules inherit automatically.
3. If the skin needs non-token rule changes (e.g. unique banner treatments, filigree decorations), add `themes/<skin>-app.css` with component-level overrides.
4. Add a branch to the `entrypoint.html` conditional (`{% ifequal skin "<skin>" %}` or switch to a `{% if skin %}` + `{{skin}}` pair if you expect many).
5. Screenshot the landing view and one interior page; drop them under `images.com.devereux-henley/skins/<skin>/` and link from the skin's doc page.

Skins should be additive — a missing theme file must not break the default look. If you want to validate a skin locally, hit `/view/game/<eid>/index.html` in the browser and scan the `<head>` source for the theme link tags.

## Existing skins

### Warhammer 3

**Trigger:** game eid `eea787d7-1065-45eb-a3f6-e26f32c294a1`.

**Files:**
- `components/rts-web/resources/rts-web/asset/style/themes/warhammer3.css` (~74 lines) — palette + type overrides.
- `components/rts-web/resources/rts-web/asset/style/themes/warhammer3-app.css` (~2000 lines) — component reskins (banner-style panel headings, gold filigree decorations, unit-card visual rules).

**Palette highlights:**
- Neutrals shift from cool ink/parchment to warm iron/bone.
- Primary is heraldic gold (`--color-primary-600: #c4943a`, hover `#d6ab58`).
- Field-green secondary and blood-red danger lean into the warhammer palette.

**Typography:**
- Headings use **Cinzel** (all-caps, wide tracking).
- Body uses **Crimson Text**.
- Both are loaded via a `@import` from Google Fonts at the top of `warhammer3.css`.

**Component signatures the app.css file adds:**
- `.app-shell` swaps the War Room contour arcs for a red/gold radial glow plus a fractal-noise texture.
- `.unit-panel` gets a banner-style heading with a red-gradient `linear-gradient` fill and a gold filigree corner via `::before` (the filigree is also used on `.draft-section-header` and `.draft-category-header`).
- The sword icon rotates 45° wherever it appears (`.icon-sword { transform: rotate(45deg); }`).
- `.btn-primary` gets a gold box-shadow and Cinzel capitalization.

Because the skin owns its own `html, body` rules (`overflow: hidden; height: 100%`) and `.app-shell` background, those overrides land on the theme rather than the War Room base when a user navigates to a Warhammer-themed route.
