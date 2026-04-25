use std::io::Cursor;
use std::process::ExitCode;

use rpfm_lib::files::{esf::ESF, Decodeable};
use serde_json::{json, Value};

fn main() -> ExitCode {
    let path = match std::env::args().nth(1) {
        Some(p) => p,
        None => {
            emit_error("missing argument: replay file path");
            return ExitCode::from(2);
        }
    };
    match run(&path) {
        Ok(v) => {
            println!("{}", v);
            ExitCode::SUCCESS
        }
        Err(msg) => {
            emit_error(&msg);
            ExitCode::FAILURE
        }
    }
}

fn run(path: &str) -> Result<String, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("read failed: {e}"))?;
    let mut cursor = Cursor::new(bytes);
    let esf = ESF::decode(&mut cursor, &None).map_err(|e| format!("decode failed: {e}"))?;
    let tree = serde_json::to_value(&esf).map_err(|e| format!("serialize failed: {e}"))?;
    let extracted = extract(&tree)?;
    serde_json::to_string(&extracted).map_err(|e| format!("encode failed: {e}"))
}

fn emit_error(msg: &str) {
    let body = json!({ "error": msg });
    eprintln!("{body}");
}

// ----- tree walking ----------------------------------------------------------

fn find_record<'a>(tree: &'a Value, name: &str) -> Option<&'a Value> {
    match tree {
        Value::Object(map) => {
            if let Some(rec) = map.get("Record") {
                if rec.get("name").and_then(Value::as_str) == Some(name) {
                    return Some(rec);
                }
            }
            for (_, v) in map {
                if let Some(found) = find_record(v, name) {
                    return Some(found);
                }
            }
            None
        }
        Value::Array(arr) => arr.iter().find_map(|v| find_record(v, name)),
        _ => None,
    }
}

fn find_records<'a>(tree: &'a Value, name: &str, out: &mut Vec<&'a Value>) {
    match tree {
        Value::Object(map) => {
            if let Some(rec) = map.get("Record") {
                if rec.get("name").and_then(Value::as_str) == Some(name) {
                    out.push(rec);
                }
            }
            for (_, v) in map {
                find_records(v, name, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|v| find_records(v, name, out)),
        _ => {}
    }
}

fn flat_children(record: &Value) -> Vec<&Value> {
    let mut out = Vec::new();
    if let Some(groups) = record.get("children").and_then(Value::as_array) {
        for g in groups {
            if let Some(arr) = g.as_array() {
                for v in arr {
                    out.push(v);
                }
            }
        }
    }
    out
}

// ----- primitive extraction -------------------------------------------------

fn as_u32(node: &Value) -> Option<u64> {
    node.get("U32")
        .and_then(|v| v.get("value"))
        .and_then(Value::as_u64)
}

fn as_ascii(node: &Value) -> Option<&str> {
    node.get("Ascii").and_then(Value::as_str)
}

fn as_utf16(node: &Value) -> Option<&str> {
    node.get("Utf16").and_then(Value::as_str)
}

fn as_bool(node: &Value) -> Option<bool> {
    node.get("Bool")
        .and_then(|v| v.get("value"))
        .and_then(Value::as_bool)
}

// ----- domain extraction ----------------------------------------------------

fn extract(tree: &Value) -> Result<Value, String> {
    let signature = tree
        .get("signature")
        .and_then(Value::as_str)
        .unwrap_or("UNKNOWN")
        .to_string();
    let creation_date = tree
        .get("creation_date")
        .and_then(Value::as_u64)
        .unwrap_or(0);

    let session = find_record(tree, "GAME_PERSISTENT_SESSION_ID");
    let match_id = session
        .and_then(|r| flat_children(r).into_iter().next().cloned())
        .as_ref()
        .and_then(as_utf16)
        .map(|s| s.to_string());

    let played_at = session
        .and_then(|r| find_record(r, "SAVE_GAME_HEADER_TIMESTAMP"))
        .map(extract_timestamp);

    let victory_condition = find_record(tree, "BATTLE_SETUP_VICTORY_CONDITION")
        .and_then(|r| flat_children(r).into_iter().next().cloned())
        .as_ref()
        .and_then(as_ascii)
        .map(|s| s.to_string());

    // BATTLE_SETUP_LOCAL.field[0] — which alliance the local player (= the
    // player who saved this replay) was on. Used by the server to map the
    // uploader's identity to a specific alliance when recording results.
    let uploader_local_alliance_idx = find_record(tree, "BATTLE_SETUP_LOCAL")
        .and_then(|r| flat_children(r).into_iter().next().cloned())
        .as_ref()
        .and_then(as_u32);

    let setup = find_record(tree, "BATTLE_SETUP")
        .ok_or_else(|| "missing BATTLE_SETUP record".to_string())?;
    let results = find_record(tree, "BATTLE_RESULTS")
        .ok_or_else(|| "missing BATTLE_RESULTS record".to_string())?;

    let setup_alliances = {
        let mut out = Vec::new();
        find_records(setup, "BATTLE_SETUP_ALLIANCE", &mut out);
        out
    };
    let result_alliances = {
        let mut out = Vec::new();
        find_records(results, "BATTLE_RESULT_ALLIANCE", &mut out);
        out
    };

    let alliances = setup_alliances
        .iter()
        .enumerate()
        .map(|(idx, setup_alliance)| {
            let result_alliance = result_alliances.get(idx).copied();
            extract_alliance(idx, setup_alliance, result_alliance)
        })
        .collect::<Vec<_>>();

    Ok(json!({
        "schema_version": 1,
        "format": signature,
        "creation_date_unix": creation_date,
        "match_id": match_id,
        "played_at": played_at,
        "victory_condition": victory_condition,
        "uploader_local_alliance_idx": uploader_local_alliance_idx,
        "alliances": alliances,
    }))
}

fn extract_timestamp(record: &Value) -> Value {
    let kids = flat_children(record);
    let at = |i: usize| kids.get(i).and_then(|n| as_u32(n)).unwrap_or(0);
    json!({
        "year":   at(0),
        "month":  at(1),
        "day":    at(2),
        "hour":   at(4),
        "minute": at(6),
        "second": at(7),
    })
}

fn extract_alliance(index: usize, setup_alliance: &Value, result_alliance: Option<&Value>) -> Value {
    let mut setup_armies = Vec::new();
    find_records(setup_alliance, "BATTLE_SETUP_ARMY", &mut setup_armies);
    let mut result_armies = Vec::new();
    if let Some(ra) = result_alliance {
        find_records(ra, "BATTLE_RESULT_ARMY", &mut result_armies);
    }

    let alliance_model_count = result_alliance
        .and_then(|a| flat_children(a).get(0).copied().cloned())
        .as_ref()
        .and_then(as_u32)
        .unwrap_or(0);

    let armies = setup_armies
        .iter()
        .enumerate()
        .map(|(i, setup_army)| extract_army(i, setup_army, result_armies.get(i).copied()))
        .collect::<Vec<_>>();

    let faction_key = setup_armies
        .first()
        .and_then(|a| find_record(a, "BATTLE_SETUP_FACTION"))
        .and_then(|fac| {
            flat_children(fac)
                .into_iter()
                .find_map(|c| as_ascii(c).filter(|s| s.starts_with("wh")).map(|s| s.to_string()))
        });

    json!({
        "index": index,
        "faction_key": faction_key,
        "model_count": alliance_model_count,
        "armies": armies,
    })
}

fn extract_army(index: usize, setup_army: &Value, result_army: Option<&Value>) -> Value {
    let setup_units = {
        let mut out = Vec::new();
        find_records(setup_army, "BATTLE_SETUP_UNIT", &mut out);
        out
    };
    let units = setup_units
        .iter()
        .map(|u| {
            let kids = flat_children(u);
            let key = kids.get(3).and_then(|n| as_ascii(n)).unwrap_or("").to_string();
            json!({ "key": key })
        })
        .collect::<Vec<_>>();

    let (commander_display, commander_portrait, faction_flag, force_value, is_reinforcement) =
        if let Some(ra) = result_army {
            let kids = flat_children(ra);
            let name = kids.get(1).and_then(|n| as_utf16(n)).unwrap_or("").to_string();
            let portrait = kids.get(2).and_then(|n| as_ascii(n)).unwrap_or("").to_string();
            let flag = kids.get(12).and_then(|n| as_ascii(n)).unwrap_or("").to_string();
            let fv = kids.get(4).and_then(|n| as_u32(n)).unwrap_or(0);
            let reinf = kids.get(27).and_then(|n| as_bool(n)).unwrap_or(false);
            (name, portrait, flag, fv, reinf)
        } else {
            (String::new(), String::new(), String::new(), 0, false)
        };

    json!({
        "index": index,
        "is_reinforcement": is_reinforcement,
        "commander_display": commander_display,
        "commander_portrait": commander_portrait,
        "faction_flag": faction_flag,
        "force_value": force_value,
        "units": units,
    })
}
