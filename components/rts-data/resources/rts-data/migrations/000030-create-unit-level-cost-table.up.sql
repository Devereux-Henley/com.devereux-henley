CREATE TABLE IF NOT EXISTS unit_level_cost (
  level INTEGER PRIMARY KEY,
  fixed_cost INTEGER NOT NULL,
  cost_multiplier REAL NOT NULL,
  fatigue INTEGER NOT NULL DEFAULT 0,
  melee_cp REAL NOT NULL DEFAULT 0,
  missile_cp REAL NOT NULL DEFAULT 0
);
