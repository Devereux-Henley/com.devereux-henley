# Flow: Draft Operations

Covers the full draft lifecycle — creation, unit selection, adding/removing units, editing selections, and validation. Tested by `components/e2e/tests/draft.spec.js`, `draft-operations.spec.js`, and `draft-api.spec.js`.

## My Drafts

The "My Drafts" page lists all drafts for the authenticated user within the current game, sorted by `updated-at` descending so recently-edited drafts surface first. Each row carries an icon, the draft display-name, a faction pill, and the last-edited date. A faction filter strip above the list (rendered when at least one draft exists) lets the user narrow the list to a single faction; the count chips reflect the unfiltered totals so the surface stays stable as filters toggle. The strip uses `hx-get` against the same `/draft/me.html` route with a `?faction=<name>` query param, swapping only the `.drafts-list` element. A "Create Draft" button in the header navigates to the creation form.

![My Drafts](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-my-drafts.png)

## Create Draft

The create form has two required fields: Faction and Game Mode. Submitting creates the draft via `PUT /api/draft/:eid` and redirects to the draft builder page.

![Create Draft](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-create-form.png)

## Empty draft

A new draft shows the army builder on the left (Main Army section with an empty lord slot) and the unit roster on the right (faction units grouped by category). The stats panel placeholder is hidden until a unit is selected.

![Empty draft](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-empty.png)

## Unit selection

Clicking a unit card in the roster loads its stats panel via HTMX (`GET /api/draft/:draft-eid/unit/:eid`). The panel shows the unit name, stats, abilities, spells, items, and mounts. "Add to Main Army" and optionally "Add to Reinforcements" buttons appear.

![Unit selected](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-unit-selected.png)

## Adding a lord

Clicking "Add to Main Army" with a Lord selected places them in the lord slot. The budget meter updates to reflect the unit's cost. Lords occupy the dedicated lord slot at the top of the section.

![Lord added](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-lord-added.png)

## Adding infantry

Non-lord units fill the general slots below the lord. The budget meter accumulates costs across all placed units.

![Units added](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-units-added.png)

## Editing a placed unit

Clicking a placed unit's card opens its entry detail panel with checkboxes for spells, items, and abilities. Toggling a selection sends a `PATCH /api/draft/:draft-eid/entry/:eid` and the budget updates via OOB swap.

![Editing unit](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/draft-editing-unit.png)

## Validation rules (tested via e2e)

The following rules are enforced server-side and tested in `draft-operations.spec.js` and `draft-api.spec.js`:

- **Lord cap**: only one lord per section. Adding a second returns 422 with an error message.
- **Per-unit cap**: maximum 4 copies of the same unit. The 5th attempt returns 422.
- **Budget tracking**: costs accumulate correctly across adds, updates, and removes.
- **Remove**: hovering a placed slot reveals a remove button. Removing a unit resets its cost from the budget.
