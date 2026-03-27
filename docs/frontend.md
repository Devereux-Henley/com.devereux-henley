# Frontend

## Resourcekit

Resourcekit is the shared CSS framework for this repository. Any server-rendered application in this monorepo that serves htmx responses should use resourcekit to provide consistent styling.

Assets live in the `components/resourcekit` polylith component under `resources/resourcekit/assets/` and are served via `clojure.java.io/resource` from explicit routes in each application.

### Design principles

- **Component-based.** Styles are organised by UI component, not by utility class. Components combine native CSS with htmx and Hyperscript behaviours.
- **CSS variables for theming.** All design decisions (color, spacing, typography, radius, shadow) are expressed as CSS custom properties defined in `tokens.css`. Applications override these variables to apply their own theme without modifying the framework itself.
- **No build step.** CSS files are plain CSS served as static resources. There is no preprocessor, bundler, or compile step.

### File structure

```
components/resourcekit/resources/resourcekit/assets/
├── tokens.css                  # CSS custom properties — served separately, app-overridable
├── reset.css                   # Minimal box-model and element reset
├── base.css                    # Body, typography, and element defaults
├── layout.css                  # App shell, containers, stack/cluster/grid, htmx indicators
├── framework.css               # Entry point — @imports everything except tokens.css
└── components/
    ├── alert.css               # Info / success / warning / danger alerts
    ├── badge.css               # Inline status labels
    ├── button.css              # Button variants and sizes
    ├── card.css                # Card with header, body, and footer regions
    ├── dialog.css              # Native <dialog> modal
    ├── dropdown.css            # Positioned dropdown menus
    ├── form.css                # Form layout, labels, hints, and errors
    ├── input.css               # Input, textarea, select, checkbox, radio
    ├── nav.css                 # Top navbar and sidebar navigation
    └── table.css               # Data tables with striped and htmx-request variants
```

### Loading order

Applications must load `tokens.css` before `framework.css`. This allows each application to override CSS variables between the two loads if needed.

```html
<link rel="stylesheet" href="/resourcekit/assets/tokens.css">
<!-- optional: application theme overrides here -->
<link rel="stylesheet" href="/resourcekit/assets/framework.css">
```

`framework.css` uses CSS `@import` to pull in all component files, so the browser will request each component file relative to the `framework.css` route. All paths under `/resourcekit/assets/` must be routable.

### Serving assets

Each application serves resourcekit files via `clojure.java.io/resource` from explicit routes. The resource path root is `resourcekit/assets/`.

```clojure
(require '[clojure.java.io :as io])

(io/resource "resourcekit/assets/framework.css")
(io/resource "resourcekit/assets/tokens.css")
(io/resource "resourcekit/assets/components/button.css")
```

### Theming

`tokens.css` defines semantic CSS variables that components reference. Applications override these to apply a custom theme without touching the framework files.

```css
/* application theme override — loaded after tokens.css */
:root {
  --color-accent:       #7c3aed;
  --color-accent-hover: #6d28d9;
  --color-accent-fg:    #ffffff;
}
```

The full set of available variables is documented in `tokens.css`.

### Design tokens

#### Color

| Token | Default | Purpose |
|---|---|---|
| `--color-bg` | `neutral-50` | Page background |
| `--color-surface` | `#ffffff` | Card, modal, input backgrounds |
| `--color-border` | `neutral-200` | Borders and dividers |
| `--color-text` | `neutral-900` | Primary text |
| `--color-text-muted` | `neutral-500` | Secondary / placeholder text |
| `--color-accent` | `primary-600` | Interactive elements |
| `--color-accent-hover` | `primary-700` | Hover state for interactive elements |
| `--color-accent-fg` | `#ffffff` | Foreground on accent background |

Raw palettes (`--color-neutral-*`, `--color-primary-*`, `--color-success-*`, `--color-warning-*`, `--color-danger-*`) are also available for use in application themes.

#### Spacing

4px base unit. Scale: `--space-1` (0.25rem) through `--space-24` (6rem).

#### Typography

| Token | Value |
|---|---|
| `--font-family-base` | System UI stack |
| `--font-family-mono` | System monospace stack |
| `--font-size-xs` through `--font-size-4xl` | 0.75rem – 2.25rem |
| `--font-weight-normal` / `medium` / `semibold` / `bold` | 400 / 500 / 600 / 700 |
| `--line-height-tight` / `base` / `relaxed` | 1.25 / 1.5 / 1.75 |

#### Other tokens

- **Border radius:** `--radius-sm` through `--radius-full`
- **Shadows:** `--shadow-sm`, `--shadow-md`, `--shadow-lg`
- **Transitions:** `--transition-fast` (100ms), `--transition-base` (150ms), `--transition-slow` (300ms)
- **Z-index:** `--z-base` through `--z-toast`

### Components

#### Layout

The app shell pattern:

```html
<body class="app-shell">
  <nav class="navbar">...</nav>
  <div class="app-main">
    <aside class="sidebar">...</aside>
    <main class="app-content">...</main>
  </div>
</body>
```

Composition helpers:

- `.stack` + `.stack-{1–8}` — vertical flex with gap
- `.cluster` + `.cluster-{1–6}` — horizontal flex with gap, wrapping
- `.grid` + `.grid-{2–4}`, `.grid-auto` — CSS grid layouts
- `.container`, `.container-{sm|md|lg|xl}` — centred max-width wrappers

#### htmx integration

Resourcekit includes first-class htmx support:

- `.btn.htmx-request` — dims button and shows wait cursor during a request
- `.table tbody tr.htmx-request td` — dims row during a request
- `.htmx-indicator` — hidden by default, visible when `.htmx-request` is present on a parent

#### Button

```html
<button class="btn btn-primary">Save</button>
<button class="btn btn-secondary">Cancel</button>
<button class="btn btn-ghost">Reset</button>
<button class="btn btn-danger btn-sm">Delete</button>
<button class="btn btn-primary btn-lg">Submit</button>
<button class="btn btn-secondary btn-icon" aria-label="Settings">...</button>
```

Variants: `btn-primary`, `btn-secondary`, `btn-ghost`, `btn-danger`
Sizes: `btn-sm`, `btn-lg`, `btn-icon`

#### Card

```html
<div class="card">
  <div class="card-header">
    <div>
      <div class="card-title">Title</div>
      <div class="card-description">Supporting text</div>
    </div>
    <button class="btn btn-secondary btn-sm">Action</button>
  </div>
  <div class="card-body">...</div>
  <div class="card-footer">...</div>
</div>
```

#### Form and inputs

```html
<form class="form">
  <div class="form-group">
    <label class="form-label required">Email</label>
    <input class="input" type="email" placeholder="you@example.com">
    <span class="form-hint">We will never share your email.</span>
  </div>
  <div class="form-group">
    <label class="form-label">Role</label>
    <select class="select">
      <option>Admin</option>
      <option>Member</option>
    </select>
  </div>
  <div class="form-group">
    <label class="form-label">Notes</label>
    <textarea class="textarea"></textarea>
    <span class="form-error">This field is required.</span>
  </div>
  <div class="form-actions">
    <button class="btn btn-primary">Save</button>
    <button class="btn btn-ghost">Cancel</button>
  </div>
</form>
```

Add `.is-error` to `.input`, `.textarea`, or `.select` when a field has a validation error.

#### Alert

```html
<div class="alert alert-success">
  <div class="alert-body">
    <div class="alert-title">Saved</div>
    Your changes have been applied.
  </div>
</div>
```

Variants: `alert-neutral`, `alert-info`, `alert-success`, `alert-warning`, `alert-danger`

Alerts are well-suited for htmx out-of-band swaps (`hx-swap-oob`) to show server-side feedback.

#### Badge

```html
<span class="badge badge-success">Active</span>
<span class="badge badge-warning">Pending</span>
<span class="badge badge-danger">Rejected</span>
<span class="badge badge-neutral">Draft</span>
<span class="badge badge-primary">New</span>
```

#### Table

```html
<div class="table-container">
  <table class="table">
    <thead>
      <tr><th>Name</th><th>Status</th></tr>
    </thead>
    <tbody>
      <tr><td>Example</td><td><span class="badge badge-success">Active</span></td></tr>
    </tbody>
  </table>
</div>
```

Add `.table-striped` to `.table` for alternating row backgrounds.

#### Dialog (modal)

```html
<dialog class="modal" id="confirm-dialog">
  <div class="modal-header">
    <span class="modal-title">Confirm</span>
    <button class="modal-close" onclick="this.closest('dialog').close()">✕</button>
  </div>
  <div class="modal-body">Are you sure?</div>
  <div class="modal-footer">
    <button class="btn btn-ghost" onclick="this.closest('dialog').close()">Cancel</button>
    <button class="btn btn-danger">Delete</button>
  </div>
</dialog>
```

Uses the native `<dialog>` element. Open with `document.getElementById('confirm-dialog').showModal()`.

#### Dropdown

```html
<div class="dropdown">
  <button class="btn btn-secondary">Options</button>
  <div class="dropdown-menu dropdown-menu-end">
    <div class="dropdown-label">Actions</div>
    <a class="dropdown-item" href="#">Edit</a>
    <a class="dropdown-item" href="#">Duplicate</a>
    <div class="dropdown-divider"></div>
    <a class="dropdown-item danger" href="#">Delete</a>
  </div>
</div>
```

Toggle visibility of `.dropdown-menu` by removing or setting the `hidden` attribute. Align to the right with `.dropdown-menu-end`.

#### Navigation

Top navbar:

```html
<nav class="navbar">
  <a class="navbar-brand" href="/">Acme</a>
  <div class="nav">
    <a class="nav-item active" href="/dashboard">Dashboard</a>
    <a class="nav-item" href="/settings">Settings</a>
  </div>
  <div class="navbar-end">
    <button class="btn btn-ghost btn-sm">Sign out</button>
  </div>
</nav>
```

Sidebar:

```html
<aside class="sidebar">
  <div class="sidebar-section">
    <div class="sidebar-label">Main</div>
    <a class="nav-item active" href="/dashboard">Dashboard</a>
    <a class="nav-item" href="/reports">Reports</a>
  </div>
</aside>
```

Use `aria-current="page"` or `.active` on `.nav-item` to indicate the current route.
