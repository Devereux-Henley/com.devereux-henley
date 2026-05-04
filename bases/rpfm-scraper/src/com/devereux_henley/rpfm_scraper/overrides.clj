(ns com.devereux-henley.rpfm-scraper.overrides)

(def display-name-unit-key-overrides
  "Explicit display-name → engine `unit` key map for unit rows whose display
  name is absent from the `land_units` loc table — typically generic
  spellcasters that share a name with multiple lore variants (Mage,
  Archmage, Damsel, Sorceress …).

  Mark-of-Chaos variants (\"Chaos Sorcerer of Tzeentch\", \"Herald of
  Nurgle\", …) are NOT pinned here: `nm/find-unit-key` resolves them
  mechanically by stripping the mark suffix off the display name,
  looking the base up in the loc-derived name index, and filtering
  candidates by `nm/mark-from-key`.  Adding mark-eligible families in
  future DLCs therefore doesn't grow this map.

  When `nm/find-unit-key`'s name index lookup misses, it falls back to
  this map.  The selected key is used both for `seed-unit-keys.sql` (so
  parsed replays can join back to the unit row) and as the
  `main_units_tables` lookup key for `extract-stats` — which then
  derives the `land_unit` key from the row.

  Where multiple lore variants are valid, the entry below picks one
  canonical variant; the `unit.key` is the engine key the post-match
  modal's enrich step joins against, so picking a single canonical key
  is the same trade-off the existing `unit-card-overrides` map makes."
  {;; Chaos Sorcerer / Lord — engine carries multiple equipment-mount
   ;; variants (`_warshrine`, `_chaos_steed`, `_daemonic_steed`,
   ;; `_<lore>_<n>`) per lore.  The seed pins the warshrine-mount
   ;; variant by convention, which has stats meaningfully different
   ;; from the foot variant.  The mark-aware resolver only narrows by
   ;; mark + lore, not by mount, so these stay pinned.
   "Chaos Sorcerer"              "wh3_dlc20_chs_cha_chaos_sorcerer_death_warshrine"
   "Chaos Sorcerer Lord"         "wh3_dlc20_chs_cha_chaos_sorcerer_lord_death_warshrine"
   ;; Slann Mage-Priest lore variants — `land_units_loc` stores their
   ;; display name as `{{tr:agent_subtypes_onscreen_name_override_*}}`
   ;; references (the actual string lives in `agent_subtypes_loc` which
   ;; the scraper doesn't load).  Without that resolution the
   ;; parenthetical-stripped index has no entries for the four lores
   ;; below, so the mechanical resolver collapses them all to the
   ;; un-loremarked `_campaign_0` canonical and the seed loses the
   ;; per-lore distinction.
   "Slann Mage-Priest (Beasts)"  "wh2_dlc13_lzd_cha_slann_mage_priest_beasts_0"
   "Slann Mage-Priest (Death)"   "wh2_dlc13_lzd_cha_slann_mage_priest_death_0"
   "Slann Mage-Priest (Metal)"   "wh2_dlc13_lzd_cha_slann_mage_priest_metal_0"
   "Slann Mage-Priest (Shadows)" "wh2_dlc13_lzd_cha_slann_mage_priest_shadows_0"})

(def faction-display-name-unit-key-overrides
  "Faction-scoped pin from a `(faction-prefix, display-name)` tuple to
  an engine `unit` key.  Consulted by `nm/find-unit-key` BEFORE the
  normal resolution path, so it forces a specific faction's row to a
  cross-faction canonical without changing how the same display name
  resolves under other faction prefixes.

  The default-resolution path picks one engine key per
  `(display-name, faction-prefix)`, which produces the right answer
  for nearly every unit but trips on a handful of cross-faction
  shorthand cases — e.g. multiplayer rosters where one faction's
  display name should mirror another's kit even though both have
  their own engine row.

  Entries here pin per-faction; non-listed (faction, name) tuples
  fall through to the normal resolver."
  {["dae" "Daemon Prince"]
   ;; The DoC Undivided Daemon Prince has a campaign-only land_unit
   ;; (`wh3_main_dae_cha_daemon_prince_0`) but no MP front-end agent
   ;; subtype — `agent_subtypes_tables.json` only carries `*_fe`
   ;; entries for the marked DoC variants.  The Warriors of Chaos
   ;; undivided variant (`wh3_dlc20_chs_cha_daemon_prince`) is the
   ;; only RPFM-canonical MP "Undivided Daemon Prince".  Pin DoC's
   ;; unmarked row to it so both factions ship the same Lore-of-Fire
   ;; kit (stats + abilities + lore) and a scraper rerun keeps them
   ;; aligned instead of re-divided onto each faction's land_unit.
   "wh3_dlc20_chs_cha_daemon_prince"})

(def unit-card-overrides
  "Explicit unit-name → icon/portrait stem overrides for units whose display
  name is absent from the land_units loc file (RoR units, variant units,
  lords/heroes with special names). Values are icon stems from ui/units/icons/
  OR portrait stems from ui/portraits/units/no_culture/ — both directories are
  searched by the copy routines."
  {"Amaxon Barbs (Razordon Hunting Pack)"                       "wh2_dlc13_lzd_razordon"
   "Amethyst Helstorm Rocket Battery"                           "wh_main_emp_helstorm_rocket"
   "Amethyst Outriders"                                         "wh_main_emp_outriders"
   "Armoured Squig Hoppers"                                     "wh_dlc06_grn_squig_hoppers"
   "Beastslayers of Bastonne (Foot Squires)"                    "wh_pro04_brt_ror_foot_squires"
   "Black-Horn's Ravagers (Gor Herd - Shields)"                 "wh_pro04_bst_ror_gor_herd_shields"
   "Blackhole Flayers (Doom-Flayers)"                           "wh2_dlc12_skv_doom_flayers_ror"
   "Blessed Razordon Hunting Pack"                              "wh2_dlc13_lzd_razordon_blessed"
   "Blessed Sacred Kroxigor"                                    "wh2_dlc13_lzd_sacred_kroxigors_blessed"
   "Butchers of Kalkengard (Minotaurs - Shields)"               "wh_pro04_bst_ror_minotaurs_shield"
   "Chaos Spawn"                                                "wh_dlc03_bst_spawn"
   "Chaos Warhounds"                                            "wh_dlc03_bst_warhounds"
   "Chaos Warhounds (Poison)"                                   "wh_dlc03_bst_warhounds_poison"
   ;; Warriors of Chaos rank-and-file: source filenames drop the
   ;; "chaos" prefix and use singular ("marauder" not "marauders") so
   ;; the heuristic resolver can't reach them on its own.
   "Marauders"                                                  "wh_main_chs_marauder"
   "Marauders (Great Weapons)"                                  "wh_main_chs_marauder_great_weapons"
   "Chaos Knights (Lances)"                                     "wh_main_chs_knights_lance"
   ;; Warriors of Chaos lord/hero portraits — the warshrine-mounted
   ;; canonical engine key has no card asset, only a unit portrait
   ;; under `units/no_culture/`.  Pin to the portrait basename.
   "Chaos Sorcerer"                                             "chs_sorcerer_campaign_01_0"
   "Chaos Sorcerer Lord"                                        "chs_sorcerer_lord_campaign_01_0"
   ;; Mark-of-Chaos character variants — engine keys like
   ;; `wh3_dlc20_chs_cha_chaos_lord_mkho` have no card stem; their
   ;; artwork lives under
   ;; `portraits/units/no_culture/dae_<mark>_<role>_campaign_01_0.png`.
   ;; Without these pins, `unit-key->portrait-base` strips the `_mkho`
   ;; tail and the prefix-fallback in `find-portrait` collapses every
   ;; marked variant to the unmarked `chs_<role>` portrait (or, for
   ;; sorcerer/sorcerer-lord which Khorne can't field, to whatever the
   ;; previous-rank portrait happens to be — same png reused across
   ;; multiple eids).
   "Chaos Lord of Khorne"                                       "dae_kho_chaos_lord_campaign_01_0"
   "Chaos Lord of Nurgle"                                       "dae_nur_chaos_lord_campaign_01_0"
   "Chaos Lord of Slaanesh"                                     "dae_sla_chaos_lord_campaign_01_0"
   "Chaos Lord of Tzeentch"                                     "dae_tze_chaos_lord_campaign_01_0"
   "Chaos Sorcerer of Nurgle"                                   "dae_nur_chaos_sorcerer_campaign_01_0"
   "Chaos Sorcerer of Slaanesh"                                 "dae_sla_chaos_sorcerer_campaign_01_0"
   "Chaos Sorcerer of Tzeentch"                                 "dae_tze_chaos_sorcerer_campaign_01_0"
   "Chaos Sorcerer Lord of Nurgle"                              "dae_nur_chaos_sorcerer_lord_campaign_01_0"
   "Chaos Sorcerer Lord of Slaanesh"                            "dae_sla_chaos_sorcerer_lord_campaign_01_0"
   "Chaos Sorcerer Lord of Tzeentch"                            "dae_tze_chaos_sorcerer_lord_campaign_01_0"
   "Exalted Hero of Khorne"                                     "dae_kho_exalted_hero_campaign_01_0"
   "Exalted Hero of Nurgle"                                     "dae_nur_exalted_hero_campaign_01_0"
   "Exalted Hero of Slaanesh"                                   "dae_sla_exalted_hero_campaign_01_0"
   "Exalted Hero of Tzeentch"                                   "dae_tze_exalted_hero_campaign_01_0"
   ;; Variant / RoR engine keys that don't share a normalized stem
   ;; with their card icon — pin explicitly so the icon resolver
   ;; doesn't fall through to the wrong (or no) image.
   "Chaos Feral Manticore"                                      "chs_feral_manticore_0"
   "Chaos Warriors (Halberds)"                                  "wh_main_chs_warriors_halberd"
   "Chosen (Great Weapons)"                                     "wh_main_chs_chosen_great_weapons"
   "Knights of Immolation (Doom Knights of Tzeentch)"           "wh3_twa07_tze_cav_doom_knights_ror_0"
   "Summoners of Rage (Dragon Ogres)"                           "wh_pro04_chs_ror_dragon_ogre"
   ;; Warriors of Chaos Regiments of Renown: source uses
   ;; `wh_pro04_chs_ror_<unit>` (no `_inf_chaos_…_ror_0` middle).
   "Mirror Guard (Chaos Warriors)"                              "wh_pro04_chs_ror_warriors"
   "Swords of Chaos (Chaos Knights)"                            "wh_pro04_chs_ror_knights"
   "Wyrd Spawn (Chaos Spawn)"                                   "wh_pro04_chs_ror_spawn"
   "Minotaurs (Great Weapons)"                                  "wh_dlc03_bst_minotaurs_great_weapons"
   "Minotaurs (Shields)"                                        "wh_dlc03_bst_minotaurs_shield"
   "Cairn Wraiths"                                              "wh_main_vmp_cairn_wraith"
   "Centigors (Great Weapons)"                                  "wh_dlc03_bst_centigors_great_weapons"
   "Centigors (Throwing Axes)"                                  "wh_dlc03_bst_centigors_throwing_axes"
   "Centigors of Tzeentch"                                      "wh3_dlc24_tze_inf_centigors"
   "Chosen of the Gods (Ushabti - Great Bows)"                  "wh2_dlc09_tmb_ushabti_bow_ror"
   "Death Dealers (Ratling Guns)"                               "wh2_dlc12_skv_ratling_gun_team_ror"
   "Defenders of the Fleur-de-lis (Knights Errant)"             "wh_pro04_brt_ror_knights_errant"
   "Destroyers of the Drakwald (Ungor Spearmen Herd - Shields)" "wh_pro04_bst_ror_ungor_spearmen_shield"
   "Ungor Spearmen Herd (Shields)"                              "wh_dlc03_bst_ungor_spearmen_shield"
   "Doom Diver Catapults"                                       "wh_main_grn_doom_diver"
   "Doom-Flayers"                                               "wh2_dlc12_skv_doom_flayers"
   "Dwarf-Thing Menace (Doom-Flayers)"                          "wh2_dlc12_skv_doom_flayers_ror"
   "Exalted Great Unclean One (Death)"                          "wh3_dlc25_nur_exalted_great_unclean_one_qb_boss"
   "Exalted Great Unclean One (Nurgle)"                         "wh3_dlc25_nur_exalted_great_unclean_one_qb_boss"
   "Flame Cannons (Grudge Settlers)"                            "wh_main_dwf_flame_cannon"
   "Glade Lord (bow)"                                           "wef_glade_female_lord_kalara_sword_bow_ror_01_0"
   "Groghooves of Wolf's Run (Centigors - Throwing Axes)"       "wh2_dlc17_bst_centigors_throwing_axes_ror"
   "Grudge Throwers (Grudge Settlers)"                          "wh_main_dwf_grudge_thrower"
   "Gyrocopters (Trollhammers - Grudge Settlers)"               "wh_main_dwf_gyrocopter"
   "Halberdiers"                                                "wh2_dlc13_emp_halberdiers_ror"
   "Hammerers (Grudge Settlers)"                                "wh_main_dwf_hammerers"
   "Hawk-Eyes of Drakira (Waywatchers)"                         "wh_dlc05_wef_waywatchers"
   "Helstorm Rocket Battery"                                    "wh_main_emp_helstorm_rocket"
   "Irondrakes (Grudge Settlers)"                               "wh_main_dwf_irondrakes"
   "Khargan the Crazed"                                         "wh3_dlc25_nur_exalted_great_unclean_one_qb_boss"
   "Khorrok's Manrippers (Bestigor Herd)"                       "wh_pro04_bst_ror_bestigor_herd"
   "Knights of Morr (Empire Knights)"                           "wh2_dlc13_emp_empire_knights_morr_ror"
   "Knights of the Everlasting Light (Empire Knights)"          "wh2_dlc13_emp_empire_knights_everlasting_light_ror"
   "Knights of the Lionhearted (Knights of the Realm)"          "wh_pro04_brt_ror_knights_realm"
   "Lava Arachnarok Spider"                                     "wh_main_grn_arachnarok_spider"
   "Loec's Tricksters (Wardancers - Asrai Spears)"              "wh_dlc05_wef_wardancers_spear"
   "Longbeards (Great Weapons - Grudge Settlers)"               "wh_main_dwf_longbeards_great_weapons"
   "Lost Sylvan Knights (Great Stag Knights)"                   "wh2_dlc16_wef_great_stag_knights"
   "Luminark of Hysh"                                           "wh_main_emp_luminark"
   "Malevolent Ancient Treeman (Beasts)"                        "wh2_dlc16_wef_malicious_treekin"
   "Malevolent Ancient Treeman (Life)"                          "wh2_dlc16_wef_malicious_treekin"
   "Malevolent Ancient Treeman (Shadows)"                       "wh2_dlc16_wef_malicious_treekin"
   "Malevolent Branchwraith (Beasts)"                           "wh2_dlc16_wef_malicious_dryads"
   "Malevolent Branchwraith (Life)"                             "wh2_dlc16_wef_malicious_dryads"
   "Malevolent Branchwraith (Shadows)"                          "wh2_dlc16_wef_malicious_dryads"
   "Morskittar's Hellion (Mutant Rat Ogre)"                     "wh2_dlc16_skv_rat_ogre_mutant"
   "Peasant Bowmen"                                             "wh_main_brt_bowmen"
   "Peasant Bowmen (Fire Arrows)"                               "wh_dlc07_brt_bowmen_fire"
   "Peasant Bowmen (Pox Arrows)"                                "wh_dlc07_brt_bowmen_pox"
   "Quarrellers (Great Weapons - Grudge Settlers)"              "wh_main_dwf_quarrellers_great_weapons"
   "Raven Heralds (Dark Riders)"                                "wh2_dlc10_def_raven_heralds"
   "Razordon Hunting Pack"                                      "wh2_dlc13_lzd_razordon"
   "Skeleton Archer Chariots"                                   "wh2_dlc09_tmb_skeleton_archers_ror"
   "Skin Wolf Werekin"                                          "wh_dlc08_nor_skin_wolves"
   "Mortars"                                                    "wh_main_emp_mortar"
   "Outriders (Grenade Launchers)"                              "wh_main_emp_outriders_grenade_launcher"
   "Spearmen (Shields)"                                         "wh_main_emp_spearmen_shield"
   "Daemon Prince"                                              "dae_chs_daemon_prince_1_0"
   "Caress, the Darkling Prince"                                "dae_sla_daemon_prince_1_0"
   "Grand Vomitus, Prince of Buboes"                            "dae_nur_daemon_prince_1_0"
   "Red Ulgor, Prince of Slaughter"                             "dae_kho_daemon_prince_1_0"
   "Zarrivyk, Feathered-Prince"                                 "dae_tze_daemon_prince_1_0"
   "Exalted Keeper of Secrets"                                  "dae_keeper_of_secret_exalted_campaign_01_0"
   "Sons of Ghorros (Centigors - Great Weapons)"                "wh_pro04_bst_ror_centigors_great_weapons"
   "Steam Tank"                                                 "wh_main_emp_steamtank"
   "Steam Tank (Volley Gun)"                                    "wh3_dlc25_emp_veh_steam_tank_helblaster"
   "Teeth-Breakers (Ratling Guns)"                              "wh2_dlc12_skv_ratling_gun_team_ror"
   "The Companions of Quenelles (Questing Knights)"             "wh_pro04_brt_ror_questing_knights"
   "The Daemonspew (Forsaken)"                                  "wh_dlc01_chs_forsaken"
   "The Eye of Morrslieb (Cygor)"                               "wh_pro04_bst_ror_cygor"
   "The Feasters in the Dusk (Crypt Ghouls)"                    "wh_dlc04_vmp_feasters"
   "The Holy Wardens of La Maisontaal (Battle Pilgrims)"        "wh_pro04_brt_ror_battle_pilgrims"
   "The Stubborn Bulls (Empire Knights - Greatswords)"          "wh2_dlc13_emp_empire_knights_everlasting_light_ror"
   "The Tide of Skjold (Zombie Pirate Deckhand Mob)"            "wh2_dlc11_cst_zombie_deckhands_ror"
   "Thunderers (Grudge-Rakers)"                                 "wh_main_dwf_thunderers"
   "Wardens of Cythral (Wildwood Rangers)"                      "wh_dlc05_wef_wildwood_ranger"
   "Mounted Yeomen Archers"                                     "wh_main_brt_mounted_yeomen_archers"
   "Wardens of Montfort (Mounted Yeomen Archers)"               "wh_pro04_brt_ror_mounted_yeomen_archers"
   "Wild Hunters of Kurnous (Wild Riders - Shields)"            "wh_dlc05_wef_wild_riders_shield"
   "Wildwood Rangers"                                           "wh_dlc05_wef_wildwood_ranger"
   "Winterheart Guard (Eternal Guard - Shields)"                "wh_dlc05_wef_eternal_guard_shield"
   "Zintler's Reiksguard (Reiksguard)"                          "wh_dlc04_emp_zintlers"
   "Zombie Pirate Deckhand Mob"                                 "wh2_dlc11_cst_zombie_deckhands"
   "Zombie Pirate Deckhand Mob (Polearms)"                      "wh2_dlc11_cst_zombie_deckhands_polearm"
   "Count Noctilus"                                             "cst_cha_count_noctilus_0"
   "Plaguebearers of Nurgle"                                    "wh3_main_nur_inf_plaguebearers_2"
   "Zombies"                                                    "wh_main_vmp_zombies"

   "Ancient Stegadon (Engine of the Gods)"                      "wh2_dlc12_lzd_mon_ancient_stegadon_engine_gpds"
   "Bastiladon (Ark of Sotek)"                                  "wh2_dlc12_lzd_mon_bastiladon_ark_of_sotek"
   "Bastiladon (Revivification Crystal)"                        "wh2_main_lzd_mon_bastiladon_healing_platform"
   "Bastiladon (Solar Engine)"                                  "wh2_main_lzd_mon_bastiladon_solar"
   "Blessed Ancient Salamander"                                 "wh2_dlc12_lzd_mon_ancient_salamander_blessed"
   "Blessed Bastiladon (Solar Engine)"                          "wh2_main_lzd_mon_bastiladon_solar_blessed"
   "Blessed Chameleon Stalkers"                                 "wh2_dlc17_lzd_inf_chameleon_stalkers_blessed"
   "Blessed Ripperdactyl Riders"                                "wh2_dlc12_lzd_cav_ripperdactyl_riders_blessed"
   "Blessed Salamander Hunting Pack"                            "wh2_dlc12_lzd_mon_salamander_hunting_pack_blessed"
   "Blessed Saurus Spears (Shields)"                            "wh2_main_lzd_inf_saurus_spearmen_shields_blesssed"
   "Blessed Saurus Warriors (Shields)"                          "wh2_main_lzd_inf_saurus_warriors_shields_blessed"
   "Blessed Terradon Riders"                                    "wh2_main_lzd_mon_terradon_blessed"
   "Blessed Terradon Riders (Fireleech Bolas)"                  "wh2_main_lzd_mon_terradon_fireleech_blessed"
   "Blades of the Blood Queen (Har Ganeth Executioners)"        "wh2_dlc10_def_har_ganeth_executioners_ror"
   "Bugman's Rangers"                                           "wh_dlc06_dwf_rangers_bugmans"
   "Cave Bats"                                                  "wh2_dlc16_wef_ror_dryads"
   "Chill of Sontar (War Hydra)"                                "wh2_dlc10_def_war_hydra_ror"
   "Clan Vulkn Tailslashers (Clanrats - Shields)"               "wh2_dlc12_skv_inf_clanrats_shields_ror"
   "Cold One Riders"                                            "wh2_main_lzd_cav_cold_one_riders"
   "Cold One Spear-Riders"                                      "wh2_main_lzd_cav_cold_one_spearriders"
   "Council Guard (Stormvermin - Halberds)"                     "wh2_dlc12_skv_inf_stormvermin_halberds_ror"
   "Death Shriek Terrorgheist"                                  "wh2_dlc11_cst_death_shriek_terrogheist"
   "Doom Knights of Tzeentch"                                   "wh3_twa07_tze_cav_doom_knights_ror_0"
   "Dwarf Warriors"                                             "wh_main_dwf_warriors"
   "Dwarf Warriors (Great Weapons)"                             "wh_main_dwf_warriors_great_weapons"
   "Enigmas of Ghyran (Zoats)"                                  "wh2_dlc16_wef_ror_zoats"
   "Everqueen's Court Guards (Sisters of Avelorn)"              "wh2_dlc10_hef_inf_sisters_of_avelorn_ror"
   "Exalted Flamer of Tzeentch"                                 "wh3_main_tze_mon_exalted_flamer_0"
   "Explosive Squig"                                            "wh_dlc06_grn_squig_herd"
   "Feral Bastiladon"                                           "wh2_main_lzd_mon_bastiladon_feral"
   "Feral Hydra"                                                "wh2_main_def_war_hydra"
   "Field Trebuchets"                                           "wh_main_brt_trebuchet"
   "Firebark Elders (Tree Kin)"                                 "wh_pro04_wef_ror_treekin"
   "Forest Goblin Spider Rider Archers"                         "wh_main_grn_goblin_spider_rider_bow"
   "Forest Goblin Spider Riders"                                "wh_main_grn_goblin_spider_rider_spear"
   "Gorgers"                                                    "wh3_main_ogr_mon_gorger_0"
   "Great Cannons"                                              "wh_main_emp_cannon"
   "Gwindalor"                                                  "wh2_dlc16_wef_ror_zoats"
   "Iron Daemon - Dreadquake Mortar"                            "wh3_dlc23_chd_veh_iron_daemon"
   "Ithilmar Chariots"                                          "wh2_main_hef_cav_ithilmar_tiranoc_chariot"
   "Keepers of the Flame (Phoenix Guard)"                       "wh2_dlc10_hef_phoenix_guard_ror"
   "Khemrian Warsphinx"                                         "wh2_dlc09_tmb_warsphinx"
   "Knights of the Ebon Claw (Dread Knights)"                   "wh2_dlc10_def_cold_one_dread_knights_ror"
   "Legion of Chaqua (Saurus Spears)"                           "wh2_dlc12_lzd_inf_saurus_spearmen_shields_ror"
   "Lion Chariots of Chrace"                                    "wh2_dlc15_hef_veh_lion_chariot"
   "Lothern Skycutters"                                         "wh3_dlc27_hef_veh_sky_cutter_bows"
   "Lothern Skycutters (Bolt Throwers)"                         "wh3_dlc27_hef_veh_sky_cutter_bolt_thrower"
   "Maws of Savagery (Skin Wolves - Armoured)"                  "wh_pro04_nor_ror_skin_wolves_armoured"
   "Norscan Giant"                                              "wh_dlc08_nor_giant"
   "Norscan Ice Trolls"                                         "wh_dlc08_nor_ice_trolls"
   "Norscan Ice Wolves"                                         "wh_dlc08_nor_ice_wolves"
   "Noxbringer (Soul Grinder of Nurgle)"                        "wh3_dlc25_nur_mon_soul_grinder_ror"
   "Obsinite Gyrocopters"                                       "wh3_dlc25_dwf_veh_gyrocopter_1_grudge_unit"
   "Pahaux Sentinels (Terradon Riders)"                         "wh2_dlc12_lzd_mon_terradon_ror"
   "Rahagra's Pride (War Lions of Chrace)"                      "wh2_dlc15_hef_mon_war_lions_ror"
   "Salamander Hunting Pack"                                    "wh2_dlc12_lzd_mon_salamander_hunting_pack"
   "Sisters of the Singing Doom (Witch Elves)"                  "wh2_dlc10_def_witch_elves_ror"
   "Skin Wolves"                                                "wh_dlc08_nor_skin_wolves"
   "Skin Wolves (Armoured)"                                     "wh_dlc08_nor_skin_wolves_armoured"
   "Skullcracker - Dreadquake Mortar"                           "wh3_dlc23_chd_veh_skullcracker"
   "Slaanesh's Harvesters (Doomfire Warlocks)"                  "wh2_dlc10_def_cav_doomfire_warlocks_ror"
   "Slayers (Grudge Settlers)"                                  "wh3_dlc25_dwf_inf_slayers_grudge_unit"
   "Soopa-Squig!"                                               "wh_dlc06_grn_squig_herd"
   "Spider Hatchlings"                                          "wh_dlc06_grn_spider_hatchling"
   "Stormvermin (Halberds)"                                     "wh2_main_skv_inf_stormvermin_halberds"
   "Stormvermin (Swords & Shields)"                             "wh2_main_skv_inf_stormvermin_shields"
   "Supply Train"                                               "wh3_dlc23_chd_veh_iron_daemon"
   "Terradon Riders"                                            "wh2_dlc12_lzd_mon_terradon_ror"
   "Terradon Riders (Fireleech Bolas)"                          "wh2_main_lzd_mon_terradon_fireleech"
   "The Daemon's Tongue - Dreadquake Mortar"                    "wh3_dlc23_chd_veh_iron_daemon_ror"
   "The Konigstein Stalkers (Skeleton Warriors)"                "wh_main_vmp_skeleton_warrior_sword"
   "Demigryph Knights (Halberds)"                               "wh_main_emp_demigryph_knights_halberd"
   "The Royal Altdorf Gryphites (Demigryph Knights)"            "wh_dlc04_emp_royal_gryphites"
   "War Wagons (Mortars)"                                       "wh2_dlc13_emp_war_wagon_mortar"
   "The Umbral Tide (Salamander Hunting Pack)"                  "wh2_dlc12_lzd_mon_salamander_hunting_pack_ror"
   "Tzar Guard"                                                 "wh3_twa06_ksl_inf_tzar_guard_ror_0"
   "Tzar Guard (Great Weapons)"                                 "wh3_twa06_ksl_inf_tzar_guard_ror_0"
   "War Lions of Chrace"                                        "wh2_dlc15_hef_mon_war_lions"
   "Wraiths of the Frozen Heart (Dryads)"                       "wh2_dlc16_wef_ror_dryads"
   "Yoked Carnosaur"                                            "wh2_dlc17_lzd_mon_troglodon_ror"

   "Aekold Helbrass"                                            "chs_ch_aekold_0"
   "Alith Anar"                                                 "hef_alith_anar_0"
   "Aranessa Saltspite"                                         "cst_cha_aranessa_saltspite_0"
   "Arbaal the Undefeated"                                      "dae_arbaal_0"
   "Ataman"                                                     "ksl_cha_boris_campaign_0"
   "Azazel"                                                     "dae_azazel_0"
   "Azrik the Maze Keeper"                                      "chs_ch_arzik_0"
   "Boris Ursus"                                                "ksl_cha_boris_campaign_0"
   "Dechala, the Denied One"                                    "dae_dechala_0"
   "Deathmaster Snikch"                                         "skv_snikch_tzarkan_0"
   "Epidemius"                                                  "dae_epidemius_0"
   "Eshin Sorcerer"                                             "skv_warlord_campaign_01_0"
   "Felix"                                                      "neu_felix_0"
   "Festus the Leechlord"                                       "dae_festus_0"
   "Gotrek"                                                     "neu_gotrek_0"
   "Grimgor Ironhide"                                           "grn_ch_grimgor_0"
   "Harald Hammerstorm"                                         "dae_chs_harald_hammerstorm_0"
   "Helman Ghorst"                                              "vmp_ch_master_necromancer_helman_0"
   "High Beastmaster"                                           "def_cha_lokhir_0"
   "Kairos Fateweaver"                                          "dae_kairos_0"
   "Karanak"                                                    "dae_karanak_0"
   "Kayzk the Befouled"                                         "dae_nur_kayzk_the_befouled_0"
   "Khainite Assassin"                                          "def_cha_lokhir_0"
   "Kihar the Tormentor"                                        "chs_ch_kihar_0"
   "Kroq-Gar"                                                   "lzd_lord_kroq_gar_0"
   "Ku'gath Plaguefather"                                       "dae_epidemius_0"
   "Lokhir Fellheart"                                           "def_cha_lokhir_0"
   "Luthor Harkon"                                              "cst_cha_luthor_harkon_0"
   "Master Assassin"                                            "skv_warlord_campaign_01_0"
   "Master Engineer"                                            "emp_master_engineer_campaign_01_0"
   "Mother Ostankya"                                            "ksl_ostankya_0"
   "N'Kari"                                                     "dae_nkari_0"
   "Prince Sigvald the Magnificent"                             "chs_ch_sigvald_0"
   "Saurus Oldblood"                                            "lzd_lord_saurus_old_blood_campaign_01_0"
   "Saurus Scar-Veteran"                                        "lzd_hero_saurus_scar_veteran_campaign_01_0"
   "Scyla Anfingrimm"                                           "dae_kho_scyla_anfingrimm_0"
   "Skarbrand the Exiled"                                       "dae_skarbrand_0"
   "Skarr Bloodwrath"                                           "dae_skarr_bloodwrath_0"
   "Skulltaker"                                                 "dae_skulltaker_0"
   "Tamurkhan the Maggot Lord"                                  "dae_tamurkhan_0"
   "The Blue Scribes"                                           "dae_blue_scribes_0"
   "The Changeling"                                             "dae_changeling_0"
   "The Masque of Slaanesh"                                     "dae_masque_of_slaanesh_0"
   "The Red Duke"                                               "vmp_the_red_duke_0"
   "Thorgrim Grudgebearer"                                      "dwf_ch_thorgrim_0"
   "Throgg"                                                     "nor_cha_throgg_0"
   "Valkia the Bloody"                                          "dae_valkia_0"
   "Vilitch the Curseling"                                      "dae_vilitch_0"
   "Wargor"                                                     "bst_wargor_campaign_01_0"
   "Wulfrik the Wanderer"                                       "nor_cha_wulfrik_0"
   "Yuan Bo, the Jade Dragon"                                   "wh3_dlc24_cth_cha_yuan_bo_dragon"

   "Ulrika Magdova"                                             "neu_ulrika_0"

   "Alluress (Shadows)"                                         "dae_daemonette_alluress_campaign_01_0"
   "Alluress (Slaanesh)"                                        "dae_daemonette_alluress_campaign_01_0"
   "Bloodreaper"                                                "dae_bloodreaper_campaign_01_0"
   "Bloodspeaker"                                               "dae_bloodspeaker_campaign_01_0"
   "Burplesmirk Spewpit"                                        "dae_nur_nor_skin_wolf_werekin_chieftain_0"
   "Cultist of Khorne"                                          "dae_cultist_of_khorne_campaign_01_0"
   "Cultist of Nurgle"                                          "dae_cultist_of_nurgle_campaign_01_0"
   "Cultist of Slaanesh"                                        "dae_cultist_of_slaanesh_campaign_01_0"
   "Cultist of Tzeentch"                                        "dae_cultist_of_tzeentch_campaign_01_0"
   "Daemon Prince of Khorne"                                    "dae_kho_daemon_prince_0"
   "Daemon Prince of Nurgle"                                    "dae_nur_daemon_prince_1_0"
   "Daemon Prince of Slaanesh"                                  "dae_sla_daemon_prince_1_0"
   "Daemon Prince of Tzeentch"                                  "dae_tze_daemon_prince_1_0"
   "Dragon-blooded Shugengan Lord (Yang)"                       "cth_shugengan_lord_campaign_01_0"
   "Dragon-blooded Shugengan Lord (Yin)"                        "cth_shugengan_lord_campaign_01_0"
   "Exalted Bloodthirster"                                      "dae_bloodthirster_exalted_campaign_01_0"
   "Exalted Keeper of Secrets (Shadows)"                        "dae_daemonette_alluress_campaign_01_0"
   "Exalted Keeper of Secrets (Slaanesh)"                       "dae_daemonette_alluress_campaign_01_0"
   "Exalted Lord of Change (Metal)"                             "dae_exalted_lord_of_change_campaign_01_0"
   "Exalted Lord of Change (Tzeentch)"                          "dae_exalted_lord_of_change_campaign_01_0"
   "Ezar Doombolt"                                              "dae_plaguebearer_plagueridden_campaign_01_0"
   "Bray-Shaman (Death)"                                        "bst_bray_shaman_campaign_02_0"
   "Bray-Shaman (Shadows)"                                      "bst_bray_shaman_campaign_03_0"
   "Bray-Shaman (Wild)"                                         "bst_bray_shaman_campaign_04_0"
   "Great Bray-Shaman (Death)"                                  "bst_great_bray_shaman_campaign_02_0"
   "Great Bray-Shaman (Shadows)"                                "bst_great_bray_shaman_campaign_03_0"
   "Great Bray-Shaman (Wild)"                                   "bst_great_bray_shaman_campaign_04_0"
   "Grukmur Three-Horn"                                         "bst_great_bray_shaman_campaign_01_0"
   "Herald of Khorne"                                           "dae_herald_of_khorne_campaign_01_0"
   "Herald of Nurgle (Death)"                                   "dae_plaguebearer_herald_campaign_01_0"
   "Herald of Nurgle (Nurgle)"                                  "dae_plaguebearer_herald_campaign_01_0"
   "Herald of Slaanesh (Shadows)"                               "dae_daemonette_herald_campaign_01_0"
   "Herald of Slaanesh (Slaanesh)"                              "dae_daemonette_herald_campaign_01_0"
   "Herald of Tzeentch (Metal)"                                 "dae_horror_herald_campaign_01_0"
   "Herald of Tzeentch (Tzeentch)"                              "dae_horror_herald_campaign_01_0"
   "Iridescent Horror (Metal)"                                  "dae_horror_herald_campaign_01_0"
   "Iridescent Horror (Tzeentch)"                               "dae_horror_herald_campaign_01_0"
   "Ketzak Fimdirach"                                           "dae_plaguebearer_plagueridden_campaign_01_0"
   "Killgore Slaymaim"                                          "dae_herald_of_khorne_campaign_01_0"
   "Lord Kroak"                                                 "lzd_lord_kroak_0"
   "Mournhowl"                                                  "dae_nur_nor_skin_wolf_werekin_chieftain_0"
   "Plagueridden (Death)"                                       "dae_plaguebearer_plagueridden_campaign_01_0"
   "Plagueridden (Nurgle)"                                      "dae_plaguebearer_plagueridden_campaign_01_0"
   "Prophetess (Heavens)"                                       "brt_prophetess_campaign_02_0"
   "Prophetess (Life)"                                          "brt_prophetess_campaign_03_0"
   "Damsel (Life)"                                              "brt_damsel_campaign_02_0"
   "Shaman-Sorcerer (Death)"                                    "nor_great_shaman_sorcerer_death_campaign_01_0"
   "Shaman-Sorcerer (Fire)"                                     "nor_great_shaman_sorcerer_fire_campaign_01_0"
   "Shaman-Sorcerer (Metal)"                                    "nor_great_shaman_sorcerer_metal_campaign_01_0"
   "Slann Mage-Priest (Beasts)"                                 "lzd_slann_campaign_01_0"
   "Slann Mage-Priest (Death)"                                  "lzd_slann_campaign_01_0"
   "Slann Mage-Priest (Metal)"                                  "lzd_slann_campaign_01_0"
   "Slann Mage-Priest (Shadows)"                                "lzd_slann_campaign_01_0"})

(def faction-key-map
  "Faction slug (matching seed-<slug>-units.sql) to the list of unit-key
  infix prefixes that identify that faction in RPFM unit keys."
  {"empire"            ["emp"]
   "beastmen"          ["bst"]
   "bretonnia"         ["brt"]
   "chaos-dwarfs"      ["chd"]
   "daemons-of-chaos"  ["dae"]
   "dark-elves"        ["def"]
   "dwarfs"            ["dwf"]
   "grand-cathay"      ["cth"]
   "greenskins"        ["grn"]
   "high-elves"        ["hef"]
   "khorne"            ["kho"]
   "kislev"            ["kis" "ksl"]
   "lizardmen"         ["lzd"]
   "norsca"            ["nor"]
   "nurgle"            ["nur"]
   "ogre-kingdoms"     ["ogr"]
   "skaven"            ["skv"]
   "slaanesh"          ["sla"]
   "tomb-kings"        ["tmb"]
   "tzeentch"          ["tze"]
   "vampire-coast"     ["cst"]
   "vampire-counts"    ["vmp"]
   "warriors-of-chaos" ["chs" "woc"]
   "wood-elves"        ["wef"]})
