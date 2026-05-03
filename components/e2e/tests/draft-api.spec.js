const { test, expect } = require('@playwright/test');

const BASE = process.env.RTS_API_BASE_URL || "http://localhost:3001";
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const EMPIRE_FACTION_EID = '35dd38fa-2bcc-4492-8f58-a106d0d02cbb';
const LAND_BATTLE_MODE_EID = 'a1b2c3d4-0001-4000-8000-000000000001';
const DOMINATION_MODE_EID = 'a1b2c3d4-0002-4000-8000-000000000002';
const ARCH_LECTOR_EID = '00010001-0000-4000-8000-000000000000';
const GENERAL_EID = '00010006-0000-4000-8000-000000000000';
const SWORDSMEN_EID = '0001002a-0000-4000-8000-000000000000';
const BALTHASAR_GELT_EID = '00010002-0000-4000-8000-000000000000';

const HEADERS = {
  Accept: 'application/hal+json',
  'Content-Type': 'application/json',
  Cookie: 'dev_impersonation=dev-admin',
};

async function createDraft(request, gameModeEid) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/api/draft/${eid}?version=1`, {
    headers: HEADERS,
    data: {
      'faction-eid': EMPIRE_FACTION_EID,
      'game-mode-eid': gameModeEid,
      'game-eid': GAME_EID,
    },
  });
  expect(res.status()).toBe(201);
  const body = await res.json();
  expect(body.eid).toBe(eid);
  return body;
}

async function addUnit(request, draftEid, unitEid, section) {
  const res = await request.post(
    `${BASE}/api/draft/${draftEid}/unit/${unitEid}?section=${section}`,
    { headers: HEADERS, data: {} }
  );
  return { status: res.status(), body: await res.json() };
}

test.describe('Draft API (application/hal+json)', () => {
  test('create a draft returns draft resource with _links', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);

    expect(draft.type).toBe('game/draft');
    expect(draft['faction-eid']).toBe(EMPIRE_FACTION_EID);
    expect(draft['game-mode-eid']).toBe(LAND_BATTLE_MODE_EID);
    expect(draft['player-sub']).toBe('dev-admin');
    expect(draft._links).toBeDefined();
    expect(draft._links.self).toContain(`/api/draft/${draft.eid}`);
  });

  test('get draft unit returns unit resource with statistics', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);

    const res = await request.get(
      `${BASE}/api/draft/${draft.eid}/unit/${ARCH_LECTOR_EID}`,
      { headers: HEADERS }
    );
    expect(res.status()).toBe(200);
    const unit = await res.json();

    expect(unit.type).toBe('draft/unit');
    expect(unit.name).toBe('Arch Lector');
    expect(unit['unit-category-name']).toBe('Lord');
    expect(unit['unit-statistics']).toBeDefined();
    expect(unit['unit-statistics'].length).toBeGreaterThan(0);
    expect(unit._links).toBeDefined();
    expect(unit._links.self).toContain(`/unit/${ARCH_LECTOR_EID}`);
    expect(unit._links.draft).toContain(`/api/draft/${draft.eid}`);
  });

  test('add lord to main returns add-success with budget', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { status, body } = await addUnit(request, draft.eid, ARCH_LECTOR_EID, 'main');

    expect(status).toBe(200);
    expect(body.type).toBe('draft/add-success');
    expect(body['new-unit'].name).toBe('Arch Lector');
    expect(body['new-unit']['is-lord']).toBe(true);
    expect(body['new-unit']['entry-eid']).toBeDefined();
    expect(body['new-unit']['total-cost']).toBe(550);
    expect(body.section.section).toBe('main');
    expect(body.budget['section-cost']).toBe(550);
    expect(body.budget['section-max']).toBe(12400);
    expect(body.budget['section-over-budget']).toBe(false);
  });

  test('add infantry to main section', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { status, body } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'main');

    expect(status).toBe(200);
    expect(body.type).toBe('draft/add-success');
    expect(body['new-unit'].name).toBe('Swordsmen');
    expect(body['new-unit']['total-cost']).toBe(375);
    expect(body.budget['section-cost']).toBe(375);
  });

  test('add unit to reinforcements section', async ({ request }) => {
    const draft = await createDraft(request, DOMINATION_MODE_EID);
    const { status, body } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'reinforcements');

    expect(status).toBe(200);
    expect(body.type).toBe('draft/add-success');
    expect(body.section.section).toBe('reinforcements');
    expect(body.budget.section).toBe('reinforcements');
    expect(body.budget['section-cost']).toBe(375);
    expect(body.budget['section-max']).toBe(9000);
  });

  test('get draft entry returns entry resource with _links', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { body: addBody } = await addUnit(request, draft.eid, ARCH_LECTOR_EID, 'main');
    const entryEid = addBody['new-unit']['entry-eid'];

    const res = await request.get(
      `${BASE}/api/draft/${draft.eid}/entry/${entryEid}?section=main`,
      { headers: HEADERS }
    );
    expect(res.status()).toBe(200);
    const entry = await res.json();

    expect(entry.type).toBe('draft/entry');
    expect(entry.eid).toBe(entryEid);
    expect(entry['draft-eid']).toBe(draft.eid);
    expect(entry['unit-eid']).toBe(ARCH_LECTOR_EID);
    expect(entry.section).toBe('main');
    expect(entry._links.self).toContain(`/entry/${entryEid}`);
    expect(entry._links.draft).toContain(`/api/draft/${draft.eid}`);
    expect(entry._links.unit).toContain(`/unit/${ARCH_LECTOR_EID}`);
  });

  test('get draft entry with embed=unit includes _embedded.unit', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { body: addBody } = await addUnit(request, draft.eid, ARCH_LECTOR_EID, 'main');
    const entryEid = addBody['new-unit']['entry-eid'];

    const res = await request.get(
      `${BASE}/api/draft/${draft.eid}/entry/${entryEid}?section=main&embed=unit`,
      { headers: HEADERS }
    );
    const entry = await res.json();

    expect(entry._embedded).toBeDefined();
    expect(entry._embedded.unit).toBeDefined();
    expect(entry._embedded.unit.name).toBe('Arch Lector');
    expect(entry._embedded.unit['unit-category-name']).toBe('Lord');
    expect(entry._embedded.unit['unit-statistics'].length).toBeGreaterThan(0);
  });

  test('update placed unit selections changes cost', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { body: addBody } = await addUnit(request, draft.eid, BALTHASAR_GELT_EID, 'main');
    const entryEid = addBody['new-unit']['entry-eid'];
    const costBefore = addBody['new-unit']['total-cost'];

    const res = await request.get(
      `${BASE}/api/draft/${draft.eid}/entry/${entryEid}?section=main&embed=unit`,
      { headers: HEADERS }
    );
    const entry = await res.json();
    const spell = entry._embedded.unit['draftable-spells']?.[0];
    const item = entry._embedded.unit.items?.[0];
    const selectionKey = spell?.key || item?.key;
    const selectionField = spell ? 'spells' : 'items';

    if (selectionKey) {
      const patchRes = await request.patch(
        `${BASE}/api/draft/${draft.eid}/entry/${entryEid}?section=main`,
        { headers: HEADERS, data: { [selectionField]: [selectionKey] } }
      );
      expect(patchRes.status()).toBe(200);
      const patchBody = await patchRes.json();

      expect(patchBody.type).toBe('draft/update-success');
      expect(patchBody['entry-eid']).toBe(entryEid);
      expect(patchBody['total-cost']).toBeGreaterThan(costBefore);
      expect(patchBody.budget['section-cost']).toBe(patchBody['total-cost']);
    }
  });

  test('remove unit returns remove-success and resets budget', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { body: addBody } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'main');
    const entryEid = addBody['new-unit']['entry-eid'];

    expect(addBody.budget['section-cost']).toBe(375);

    const res = await request.delete(
      `${BASE}/api/draft/${draft.eid}/entry/${entryEid}?section=main`,
      { headers: HEADERS }
    );
    expect(res.status()).toBe(200);
    const body = await res.json();

    expect(body.type).toBe('draft/remove-success');
    expect(body['removed-entry-eid']).toBe(entryEid);
    expect(body['removed-is-lord']).toBe(false);
    expect(body.budget['section-cost']).toBe(0);
  });

  test('removing a lord resets removed-is-lord flag', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    const { body: addBody } = await addUnit(request, draft.eid, ARCH_LECTOR_EID, 'main');

    const res = await request.delete(
      `${BASE}/api/draft/${draft.eid}/entry/${addBody['new-unit']['entry-eid']}?section=main`,
      { headers: HEADERS }
    );
    const body = await res.json();

    expect(body['removed-is-lord']).toBe(true);
  });

  test('lord cap rejects second lord with 422', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    await addUnit(request, draft.eid, ARCH_LECTOR_EID, 'main');
    const { status, body } = await addUnit(request, draft.eid, GENERAL_EID, 'main');

    expect(status).toBe(422);
    expect(body.type).toBe('draft/add-error');
    expect(body.message).toMatch(/lord/i);
  });

  test('per-unit cap rejects 5th copy with 422', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);
    for (let i = 0; i < 4; i++) {
      const { status } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'main');
      expect(status).toBe(200);
    }

    const { status, body } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'main');
    expect(status).toBe(422);
    expect(body.type).toBe('draft/add-error');
    expect(body.message).toMatch(/4 copies/i);
  });

  test('budget accumulates across multiple units', async ({ request }) => {
    const draft = await createDraft(request, LAND_BATTLE_MODE_EID);

    const { body: first } = await addUnit(request, draft.eid, ARCH_LECTOR_EID, 'main');
    expect(first.budget['section-cost']).toBe(550);

    const { body: second } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'main');
    expect(second.budget['section-cost']).toBe(550 + 375);

    const { body: third } = await addUnit(request, draft.eid, SWORDSMEN_EID, 'main');
    expect(third.budget['section-cost']).toBe(550 + 375 + 375);
  });
});
