#!/usr/bin/env python3
"""
Updates all seed SQL data from RPFM-decoded WH3 game files.

Replaces update_unit_stats.py and update_spell_gold_costs.py, reading directly
from RPFM-decoded game tables instead of the stale twwstats.com API.

Usage:
  python3 scripts/update_from_rpfm.py --data-dir <path-to-rpfm-decoded-files>

The --data-dir should contain the RPFM-decoded JSON files named exactly as
produced by the RPFM MCP decode_packed_file tool for these tables:
  land_units_tables.json
  main_units_tables.json
  melee_weapons_tables.json
  battle_entities_tables.json
  missile_weapons_tables.json
  projectiles_tables.json
  unit_special_abilities_tables.json
  unit_abilities_tables.json
  land_units_loc.json
  unit_abilities_loc.json
  agent_subtypes_tables.json
  ancillaries_included_agent_subtypes_tables.json
  ancillaries_tables.json

To regenerate these files, use Claude Code with the RPFM MCP server and run
decode_packed_file for each table from GameFiles, then save the output to
the named files above.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys

SEED_DIR = "components/rts-data/resources/rts-data/sql/seed"
UNIT_CARD_ASSET_DIR = os.path.join("components", "rts-web", "resources", "rts-web", "asset", "card", "unit")

# ---------------------------------------------------------------------------
# Explicit unit-name → icon/portrait key overrides
#
# Used for units whose display name is absent from the land_units loc file
# (RoR units, variant units, lords/heroes with special names).
#
# Values are icon stems from ui/units/icons/ OR portrait stems from
# ui/portraits/units/no_culture/ — the functions below will search both dirs.
# ---------------------------------------------------------------------------

UNIT_CARD_OVERRIDES: dict[str, str] = {
    # ── Units with known possible_cards (icon overrides) ─────────────────────
    "Amaxon Barbs (Razordon Hunting Pack)":                "wh2_dlc13_lzd_razordon",
    "Amethyst Helstorm Rocket Battery":                    "wh_main_emp_helstorm_rocket",
    "Amethyst Outriders":                                  "wh_main_emp_outriders",
    "Armoured Squig Hoppers":                              "wh_dlc06_grn_squig_hoppers",
    "Beastslayers of Bastonne (Foot Squires)":             "wh_dlc07_brt_foot_squires",
    "Blackhole Flayers (Doom-Flayers)":                    "wh2_dlc12_skv_doom_flayers_ror",
    "Blessed Razordon Hunting Pack":                       "wh2_dlc13_lzd_razordon_blessed",
    "Blessed Sacred Kroxigor":                             "wh2_dlc13_lzd_sacred_kroxigors_blessed",
    "Butchers of Kalkengard (Minotaurs - Shields)":        "wh_pro04_bst_ror_minotaurs_shield",
    "Chaos Spawn":                                          "wh_dlc03_bst_spawn",
    "Chaos Warhounds":                                      "wh_dlc03_bst_warhounds",
    "Wargor":                                               "wh2_dlc17_bst_wargor",
    "Chaos Warhounds (Poison)":                             "wh_dlc03_bst_warhounds_poison",
    "Minotaurs (Great Weapons)":                            "wh_dlc03_bst_minotaurs_great_weapons",
    "Minotaurs (Shields)":                                  "wh_dlc03_bst_minotaurs_shield",
    "Cairn Wraiths":                                       "wh_main_vmp_cairn_wraith",
    "Centigors (Great Weapons)":                             "wh_dlc03_bst_centigors_great_weapons",
    "Centigors (Throwing Axes)":                            "wh_dlc03_bst_centigors_throwing_axes",
    "Centigors of Tzeentch":                               "wh3_dlc24_tze_inf_centigors",
    "Chosen of the Gods (Ushabti - Great Bows)":           "wh2_dlc09_tmb_ushabti_bow_ror",
    "Death Dealers (Ratling Guns)":                        "wh2_dlc12_skv_ratling_gun_team_ror",
    "Defenders of the Fleur-de-lis (Knights Errant)":      "wh_dlc07_brt_knights_errant",
    "Destroyers of the Drakwald (Ungor Spearmen Herd - Shields)": "wh_pro04_bst_ror_ungor_spearmen_shield",
    "Ungor Spearmen Herd (Shields)":                            "wh_dlc03_bst_ungor_spearmen_shield",
    "Doom Diver Catapults":                                "wh_main_grn_doom_diver",
    "Doom-Flayers":                                        "wh2_dlc12_skv_doom_flayers",
    "Dwarf-Thing Menace (Doom-Flayers)":                   "wh2_dlc12_skv_doom_flayers_ror",
    "Exalted Great Unclean One (Death)":                   "wh3_dlc25_nur_exalted_great_unclean_one_qb_boss",
    "Exalted Great Unclean One (Nurgle)":                  "wh3_dlc25_nur_exalted_great_unclean_one_qb_boss",
    "Flame Cannons (Grudge Settlers)":                     "wh_main_dwf_flame_cannon",
    "Glade Lord (bow)":                                    "wef_glade_female_lord_kalara_sword_bow_ror_01_0",
    "Groghooves of Wolf's Run (Centigors - Throwing Axes)": "wh2_dlc17_bst_centigors_throwing_axes_ror",
    "Grudge Throwers (Grudge Settlers)":                   "wh_main_dwf_grudge_thrower",
    "Gyrocopters (Trollhammers - Grudge Settlers)":        "wh_main_dwf_gyrocopter",
    "Halberdiers":                                         "wh2_dlc13_emp_halberdiers_ror",
    "Hammerers (Grudge Settlers)":                         "wh_main_dwf_hammerers",
    "Hawk-Eyes of Drakira (Waywatchers)":                  "wh_dlc05_wef_waywatchers",
    "Helstorm Rocket Battery":                             "wh_main_emp_helstorm_rocket",
    "Irondrakes (Grudge Settlers)":                        "wh_main_dwf_irondrakes",
    "Khargan the Crazed":                                  "wh3_dlc25_nur_exalted_great_unclean_one_qb_boss",
    "Khorrok's Manrippers (Bestigor Herd)":                "wh_dlc03_bst_bestigor_herd",
    "Knights of Morr (Empire Knights)":                    "wh2_dlc13_emp_empire_knights_morr_ror",
    "Knights of the Everlasting Light (Empire Knights)":   "wh2_dlc13_emp_empire_knights_everlasting_light_ror",
    "Knights of the Lionhearted (Knights of the Realm)":   "wh_main_brt_knights_realm",
    "Lava Arachnarok Spider":                              "wh_main_grn_arachnarok_spider",
    "Loec's Tricksters (Wardancers - Asrai Spears)":       "wh_dlc05_wef_wardancers_spear",
    "Longbeards (Great Weapons - Grudge Settlers)":        "wh_main_dwf_longbeards_great_weapons",
    "Lost Sylvan Knights (Great Stag Knights)":            "wh2_dlc16_wef_great_stag_knights",
    "Luminark of Hysh":                                    "wh_main_emp_luminark",
    "Malevolent Ancient Treeman (Beasts)":                 "wh2_dlc16_wef_malicious_treekin",
    "Malevolent Ancient Treeman (Life)":                   "wh2_dlc16_wef_malicious_treekin",
    "Malevolent Ancient Treeman (Shadows)":                "wh2_dlc16_wef_malicious_treekin",
    "Malevolent Branchwraith (Beasts)":                    "wh2_dlc16_wef_malicious_dryads",
    "Malevolent Branchwraith (Life)":                      "wh2_dlc16_wef_malicious_dryads",
    "Malevolent Branchwraith (Shadows)":                   "wh2_dlc16_wef_malicious_dryads",
    "Morskittar's Hellion (Mutant Rat Ogre)":              "wh2_dlc16_skv_rat_ogre_mutant",
    "Peasant Bowmen":                                      "wh_dlc07_brt_peasant_mob",
    "Peasant Bowmen (Fire Arrows)":                        "wh_dlc07_brt_peasant_mob",
    "Peasant Bowmen (Pox Arrows)":                         "wh_dlc07_brt_peasant_mob",
    "Quarrellers (Great Weapons - Grudge Settlers)":       "wh_main_dwf_quarrellers_great_weapons",
    "Raven Heralds (Dark Riders)":                         "wh2_dlc10_def_raven_heralds",
    "Razordon Hunting Pack":                               "wh2_dlc13_lzd_razordon",
    "Skeleton Archer Chariots":                            "wh2_dlc09_tmb_skeleton_archers_ror",
    "Skin Wolf Werekin":                                   "wh_dlc08_nor_skin_wolves",
    "Mortars":                                              "wh_main_emp_mortar",
    "Outriders (Grenade Launchers)":                        "wh_main_emp_outriders_grenade_launcher",
    "Spearmen (Shields)":                                   "wh_main_emp_spearmen_shield",
    "Sons of Ghorros (Centigors - Great Weapons)":         "wh_pro04_bst_ror_centigors_great_weapons",
    "Steam Tank":                                          "wh_main_emp_steamtank",
    "Steam Tank (Volley Gun)":                             "wh3_dlc25_emp_veh_steam_tank_helblaster",
    "Teeth-Breakers (Ratling Guns)":                       "wh2_dlc12_skv_ratling_gun_team_ror",
    "The Companions of Quenelles (Questing Knights)":      "wh_dlc07_brt_questing_knights",
    "The Daemonspew (Forsaken)":                           "wh_dlc01_chs_forsaken",
    "The Feasters in the Dusk (Crypt Ghouls)":             "wh_dlc04_vmp_feasters",
    "The Holy Wardens of La Maisontaal (Battle Pilgrims)": "wh_dlc07_brt_battle_pilgrims",
    "The Stubborn Bulls (Empire Knights - Greatswords)":   "wh2_dlc13_emp_empire_knights_everlasting_light_ror",
    "The Tide of Skjold (Zombie Pirate Deckhand Mob)":     "wh2_dlc11_cst_zombie_deckhands_ror",
    "Thunderers (Grudge-Rakers)":                          "wh_main_dwf_thunderers",
    "Wardens of Cythral (Wildwood Rangers)":               "wh_dlc05_wef_wildwood_ranger",
    "Wardens of Montfort (Mounted Yeomen Archers)":        "wh_main_brt_mounted_yeomen_archers",
    "Wild Hunters of Kurnous (Wild Riders - Shields)":     "wh_dlc05_wef_wild_riders_shield",
    "Wildwood Rangers":                                    "wh_dlc05_wef_wildwood_ranger",
    "Winterheart Guard (Eternal Guard - Shields)":         "wh_dlc05_wef_eternal_guard_shield",
    "Zintler's Reiksguard (Reiksguard)":                   "wh_dlc04_emp_zintlers",
    "Zombie Pirate Deckhand Mob":                          "wh2_dlc11_cst_zombie_deckhands",
    "Zombie Pirate Deckhand Mob (Polearms)":               "wh2_dlc11_cst_zombie_deckhands_polearm",

    # ── Regular units matched by unit-key prefix scan ─────────────────────────
    "Ancient Stegadon (Engine of the Gods)":               "wh2_dlc12_lzd_mon_ancient_stegadon_engine_gpds",
    "Bastiladon (Ark of Sotek)":                           "wh2_dlc12_lzd_mon_bastiladon_ark_of_sotek",
    "Bastiladon (Revivification Crystal)":                 "wh2_main_lzd_mon_bastiladon_healing_platform",
    "Bastiladon (Solar Engine)":                           "wh2_main_lzd_mon_bastiladon_solar",
    "Blessed Ancient Salamander":                          "wh2_dlc12_lzd_mon_ancient_salamander_blessed",
    "Blessed Bastiladon (Solar Engine)":                   "wh2_main_lzd_mon_bastiladon_solar_blessed",
    "Blessed Chameleon Stalkers":                          "wh2_dlc17_lzd_inf_chameleon_stalkers_blessed",
    "Blessed Ripperdactyl Riders":                         "wh2_dlc12_lzd_cav_ripperdactyl_riders_blessed",
    "Blessed Salamander Hunting Pack":                     "wh2_dlc12_lzd_mon_salamander_hunting_pack_blessed",
    "Blessed Saurus Spears (Shields)":                     "wh2_main_lzd_inf_saurus_spearmen_shields_blesssed",
    "Blessed Saurus Warriors (Shields)":                   "wh2_main_lzd_inf_saurus_warriors_shields_blessed",
    "Blessed Terradon Riders":                             "wh2_main_lzd_mon_terradon_blessed",
    "Blessed Terradon Riders (Fireleech Bolas)":           "wh2_main_lzd_mon_terradon_fireleech_blessed",
    "Blades of the Blood Queen (Har Ganeth Executioners)": "wh2_dlc10_def_har_ganeth_executioners_ror",
    "Bugman's Rangers":                                    "wh_dlc06_dwf_rangers_bugmans",
    "Cave Bats":                                           "wh2_dlc16_wef_ror_dryads",
    "Chill of Sontar (War Hydra)":                         "wh2_dlc10_def_war_hydra_ror",
    "Clan Vulkn Tailslashers (Clanrats - Shields)":        "wh2_dlc12_skv_inf_clanrats_shields_ror",
    "Cold One Riders":                                     "wh2_main_lzd_cav_cold_one_riders",
    "Cold One Spear-Riders":                               "wh2_main_lzd_cav_cold_one_spearriders",
    "Council Guard (Stormvermin - Halberds)":              "wh2_dlc12_skv_inf_stormvermin_halberds_ror",
    "Death Shriek Terrorgheist":                           "wh2_dlc11_cst_death_shriek_terrogheist",
    "Doom Knights of Tzeentch":                            "wh3_twa07_tze_cav_doom_knights_ror_0",
    "Dwarf Warriors":                                      "wh_main_dwf_warriors",
    "Dwarf Warriors (Great Weapons)":                      "wh_main_dwf_warriors_great_weapons",
    "Enigmas of Ghyran (Zoats)":                           "wh2_dlc16_wef_ror_zoats",
    "Everqueen's Court Guards (Sisters of Avelorn)":       "wh2_dlc10_hef_inf_sisters_of_avelorn_ror",
    "Exalted Flamer of Tzeentch":                          "wh3_main_tze_mon_exalted_flamer_0",
    "Explosive Squig":                                     "wh_dlc06_grn_squig_herd",
    "Feral Bastiladon":                                    "wh2_main_lzd_mon_bastiladon_feral",
    "Feral Hydra":                                         "wh2_main_def_war_hydra",
    "Field Trebuchets":                                    "wh_main_brt_trebuchet",
    "Firebark Elders (Tree Kin)":                          "wh_pro04_wef_ror_treekin",
    "Forest Goblin Spider Rider Archers":                  "wh_main_grn_goblin_spider_rider_bow",
    "Forest Goblin Spider Riders":                         "wh_main_grn_goblin_spider_rider_spear",
    "Gorgers":                                             "wh3_main_ogr_mon_gorger_0",
    "Great Cannons":                                       "wh_main_emp_cannon",
    "Gwindalor":                                           "wh2_dlc16_wef_ror_zoats",
    "Iron Daemon - Dreadquake Mortar":                     "wh3_dlc23_chd_veh_iron_daemon",
    "Ithilmar Chariots":                                   "wh2_main_hef_cav_ithilmar_tiranoc_chariot",
    "Keepers of the Flame (Phoenix Guard)":                "wh2_dlc10_hef_phoenix_guard_ror",
    "Khemrian Warsphinx":                                  "wh2_dlc09_tmb_warsphinx",
    "Knights of the Ebon Claw (Dread Knights)":            "wh2_dlc10_def_cold_one_dread_knights_ror",
    "Legion of Chaqua (Saurus Spears)":                    "wh2_dlc12_lzd_inf_saurus_spearmen_shields_ror",
    "Lion Chariots of Chrace":                             "wh2_dlc15_hef_veh_lion_chariot",
    "Lothern Skycutters":                                  "wh3_dlc27_hef_veh_sky_cutter_bows",
    "Lothern Skycutters (Bolt Throwers)":                  "wh3_dlc27_hef_veh_sky_cutter_bolt_thrower",
    "Maws of Savagery (Skin Wolves - Armoured)":           "wh_pro04_nor_ror_skin_wolves_armoured",
    "Norscan Giant":                                       "wh_dlc08_nor_giant",
    "Norscan Ice Trolls":                                  "wh_dlc08_nor_ice_trolls",
    "Norscan Ice Wolves":                                  "wh_dlc08_nor_ice_wolves",
    "Noxbringer (Soul Grinder of Nurgle)":                 "wh3_dlc25_nur_mon_soul_grinder_ror",
    "Obsinite Gyrocopters":                                "wh3_dlc25_dwf_veh_gyrocopter_1_grudge_unit",
    "Pahaux Sentinels (Terradon Riders)":                  "wh2_dlc12_lzd_mon_terradon_ror",
    "Rahagra's Pride (War Lions of Chrace)":               "wh2_dlc15_hef_mon_war_lions_ror",
    "Salamander Hunting Pack":                             "wh2_dlc12_lzd_mon_salamander_hunting_pack",
    "Sisters of the Singing Doom (Witch Elves)":           "wh2_dlc10_def_witch_elves_ror",
    "Skin Wolves":                                         "wh_dlc08_nor_skin_wolves",
    "Skin Wolves (Armoured)":                              "wh_dlc08_nor_skin_wolves_armoured",
    "Skullcracker - Dreadquake Mortar":                    "wh3_dlc23_chd_veh_skullcracker",
    "Slaanesh's Harvesters (Doomfire Warlocks)":           "wh2_dlc10_def_cav_doomfire_warlocks_ror",
    "Slayers (Grudge Settlers)":                           "wh3_dlc25_dwf_inf_slayers_grudge_unit",
    "Soopa-Squig!":                                        "wh_dlc06_grn_squig_herd",
    "Spider Hatchlings":                                   "wh_dlc06_grn_spider_hatchling",
    "Stormvermin (Halberds)":                              "wh2_main_skv_inf_stormvermin_halberds",
    "Stormvermin (Swords & Shields)":                      "wh2_main_skv_inf_stormvermin_shields",
    "Supply Train":                                        "wh3_dlc23_chd_veh_iron_daemon",
    "Terradon Riders":                                     "wh2_dlc12_lzd_mon_terradon_ror",
    "Terradon Riders (Fireleech Bolas)":                   "wh2_main_lzd_mon_terradon_fireleech",
    "The Daemon's Tongue - Dreadquake Mortar":             "wh3_dlc23_chd_veh_iron_daemon_ror",
    "The Ice-Forged Legion (Hellcannons)":                 "wh_dlc08_nor_art_hellcannon_god",
    "The Konigstein Stalkers (Skeleton Warriors)":         "wh_main_vmp_skeleton_warrior_sword",
    "The Royal Altdorf Gryphites (Demigryph Knights)":     "wh_main_emp_demigryph_knights_halberd",
    "The Umbral Tide (Salamander Hunting Pack)":           "wh2_dlc12_lzd_mon_salamander_hunting_pack_ror",
    "Tzar Guard":                                          "wh3_twa06_ksl_inf_tzar_guard_ror_0",
    "Tzar Guard (Great Weapons)":                          "wh3_twa06_ksl_inf_tzar_guard_ror_0",
    "War Lions of Chrace":                                 "wh2_dlc15_hef_mon_war_lions",
    "Wraiths of the Frozen Heart (Dryads)":                "wh2_dlc16_wef_ror_dryads",
    "Yoked Carnosaur":                                     "wh2_dlc17_lzd_mon_troglodon_ror",

    # ── Heroes / lords → portrait filenames ───────────────────────────────────
    # Named characters (specific portraits)
    "Aekold Helbrass":                                     "chs_ch_aekold_0",
    "Alith Anar":                                          "hef_alith_anar_0",
    "Aranessa Saltspite":                                  "cst_cha_aranessa_saltspite_0",
    "Arbaal the Undefeated":                               "dae_arbaal_0",
    "Ataman":                                              "ksl_cha_boris_campaign_0",  # generic Kislev lord
    "Azazel":                                              "dae_azazel_0",
    "Azrik the Maze Keeper":                               "chs_ch_arzik_0",
    "Boris Ursus":                                         "ksl_cha_boris_campaign_0",
    "Dechala, the Denied One":                             "dae_dechala_0",
    "Deathmaster Snikch":                                  "skv_snikch_tzarkan_0",
    "Epidemius":                                           "dae_epidemius_0",
    "Eshin Sorcerer":                                      "skv_warlord_campaign_01_0",
    "Felix":                                               "neu_felix_0",
    "Festus the Leechlord":                                "dae_festus_0",
    "Gotrek":                                              "neu_gotrek_0",
    "Grimgor Ironhide":                                    "grn_ch_grimgor_0",
    "Harald Hammerstorm":                                  "chs_ch_aekold_0",  # closest chs named char
    "Helman Ghorst":                                       "vmp_ch_master_necromancer_helman_0",
    "Hertwig van Hal":                                     "emp_witch_hunter_hertwig_ror_0",
    "High Beastmaster":                                    "def_cha_lokhir_0",  # generic Dark Elf hero
    "Jorek Grimm":                                         "emp_ch_boris_todbringer_0",
    "Kairos Fateweaver":                                   "dae_kairos_0",
    "Kalara":                                              "emp_ch_boris_todbringer_0",
    "Karanak":                                             "dae_karanak_0",
    "Kayzk the Befouled":                                  "dae_nur_kayzk_the_befouled_0",
    "Khainite Assassin":                                   "def_cha_lokhir_0",
    "Kihar the Tormentor":                                 "chs_ch_kihar_0",
    "Kroq-Gar":                                            "lzd_lord_kroq_gar_0",
    "Ku'gath Plaguefather":                                "dae_epidemius_0",
    "Lokhir Fellheart":                                    "def_cha_lokhir_0",
    "Luthor Harkon":                                       "cst_cha_luthor_harkon_0",
    "Master Assassin":                                     "skv_warlord_campaign_01_0",
    "Master Engineer":                                     "emp_master_engineer_campaign_01_0",
    "Mother Ostankya":                                     "ksl_ostankya_0",
    "N'Kari":                                              "dae_nkari_0",
    "Prince Sigvald the Magnificent":                      "chs_ch_sigvald_0",
    "Rodrik L'Anguille":                                   "emp_paladin_rodrik_0",
    "Saurus Oldblood":                                     "lzd_lord_saurus_old_blood_campaign_01_0",
    "Saurus Scar-Veteran":                                 "lzd_hero_saurus_scar_veteran_campaign_01_0",
    "Scyla Anfingrimm":                                    "dae_kho_scyla_anfingrimm_0",
    "Skarbrand the Exiled":                                "dae_skarbrand_0",
    "Skarr Bloodwrath":                                    "dae_skarr_bloodwrath_0",
    "Skulltaker":                                          "dae_skulltaker_0",
    "Tamurkhan the Maggot Lord":                           "dae_tamurkhan_0",
    "The Blue Scribes":                                    "dae_blue_scribes_0",
    "The Changeling":                                      "dae_changeling_0",
    "The Masque of Slaanesh":                              "dae_masque_of_slaanesh_0",
    "The Red Duke":                                        "vmp_the_red_duke_0",
    "Thorgrim Grudgebearer":                               "dwf_ch_thorgrim_0",
    "Throgg":                                              "nor_cha_throgg_0",
    "Valkia the Bloody":                                   "dae_valkia_0",
    "Vilitch the Curseling":                               "dae_vilitch_0",
    "Wulfrik the Wanderer":                                "nor_cha_wulfrik_0",
    "Yuan Bo, the Jade Dragon":                            "wh3_dlc24_cth_cha_yuan_bo_dragon",

    "Ulrika Magdova":                                      "neu_ulrika_0",

    # ── Heroes/lords → generic portrait by type ───────────────────────────────
    "Alluress (Shadows)":                                  "dae_daemonette_alluress_campaign_01_0",
    "Alluress (Slaanesh)":                                 "dae_daemonette_alluress_campaign_01_0",
    "Bloodreaper":                                         "dae_bloodreaper_campaign_01_0",
    "Bloodspeaker":                                        "dae_bloodspeaker_campaign_01_0",
    "Burplesmirk Spewpit":                                 "dae_nur_nor_skin_wolf_werekin_chieftain_0",
    "Cultist of Khorne":                                   "dae_cultist_of_khorne_campaign_01_0",
    "Cultist of Nurgle":                                   "dae_cultist_of_nurgle_campaign_01_0",
    "Cultist of Slaanesh":                                 "dae_cultist_of_slaanesh_campaign_01_0",
    "Cultist of Tzeentch":                                 "dae_cultist_of_tzeentch_campaign_01_0",
    "Daemon Prince of Khorne":                             "dae_kho_daemon_prince_0",
    "Daemon Prince of Nurgle":                             "dae_nur_daemon_prince_1_0",
    "Daemon Prince of Slaanesh":                           "dae_sla_daemon_prince_1_0",
    "Daemon Prince of Tzeentch":                           "dae_tze_daemon_prince_1_0",
    "Dragon-blooded Shugengan Lord (Yang)":                "cth_shugengan_lord_campaign_01_0",
    "Dragon-blooded Shugengan Lord (Yin)":                 "cth_shugengan_lord_campaign_01_0",
    "Exalted Bloodthirster":                               "dae_bloodthirster_exalted_campaign_01_0",
    "Exalted Keeper of Secrets (Shadows)":                 "dae_daemonette_alluress_campaign_01_0",
    "Exalted Keeper of Secrets (Slaanesh)":                "dae_daemonette_alluress_campaign_01_0",
    "Exalted Lord of Change (Metal)":                      "dae_exalted_lord_of_change_campaign_01_0",
    "Exalted Lord of Change (Tzeentch)":                   "dae_exalted_lord_of_change_campaign_01_0",
    "Ezar Doombolt":                                       "dae_plaguebearer_plagueridden_campaign_01_0",
    "Bray-Shaman (Death)":                                 "bst_bray_shaman_campaign_02_0",
    "Bray-Shaman (Shadows)":                               "bst_bray_shaman_campaign_03_0",
    "Bray-Shaman (Wild)":                                  "bst_bray_shaman_campaign_04_0",
    "Great Bray-Shaman (Death)":                           "bst_great_bray_shaman_campaign_02_0",
    "Great Bray-Shaman (Shadows)":                         "bst_great_bray_shaman_campaign_03_0",
    "Great Bray-Shaman (Wild)":                            "bst_great_bray_shaman_campaign_04_0",
    "Grukmur Three-Horn":                                  "bst_great_bray_shaman_campaign_01_0",
    "Herald of Khorne":                                    "dae_herald_of_khorne_campaign_01_0",
    "Herald of Nurgle (Death)":                            "dae_plaguebearer_herald_campaign_01_0",
    "Herald of Nurgle (Nurgle)":                           "dae_plaguebearer_herald_campaign_01_0",
    "Herald of Slaanesh (Shadows)":                        "dae_daemonette_herald_campaign_01_0",
    "Herald of Slaanesh (Slaanesh)":                       "dae_daemonette_herald_campaign_01_0",
    "Herald of Tzeentch (Metal)":                          "dae_horror_herald_campaign_01_0",
    "Herald of Tzeentch (Tzeentch)":                       "dae_horror_herald_campaign_01_0",
    "Iridescent Horror (Metal)":                           "dae_horror_herald_campaign_01_0",
    "Iridescent Horror (Tzeentch)":                        "dae_horror_herald_campaign_01_0",
    "Ketzak Fimdirach":                                    "dae_plaguebearer_plagueridden_campaign_01_0",
    "Killgore Slaymaim":                                   "dae_herald_of_khorne_campaign_01_0",
    "Lord Kroak":                                          "lzd_lord_kroak_0",
    "Mournhowl":                                           "dae_nur_nor_skin_wolf_werekin_chieftain_0",
    "Plagueridden (Death)":                                "dae_plaguebearer_plagueridden_campaign_01_0",
    "Plagueridden (Nurgle)":                               "dae_plaguebearer_plagueridden_campaign_01_0",
    "Shaman-Sorcerer (Death)":                             "nor_great_shaman_sorcerer_death_campaign_01_0",
    "Shaman-Sorcerer (Fire)":                              "nor_great_shaman_sorcerer_fire_campaign_01_0",
    "Shaman-Sorcerer (Metal)":                             "nor_great_shaman_sorcerer_metal_campaign_01_0",
    "Slann Mage-Priest (Beasts)":                          "lzd_slann_campaign_01_0",
    "Slann Mage-Priest (Death)":                           "lzd_slann_campaign_01_0",
    "Slann Mage-Priest (Metal)":                           "lzd_slann_campaign_01_0",
    "Slann Mage-Priest (Shadows)":                         "lzd_slann_campaign_01_0",
}

FACTION_KEY_MAP = {
    "empire":            ["emp"],
    "beastmen":          ["bst"],
    "bretonnia":         ["brt"],
    "chaos-dwarfs":      ["chd"],
    "daemons-of-chaos":  ["dae"],
    "dark-elves":        ["def"],
    "dwarfs":            ["dwf"],
    "grand-cathay":      ["cth"],
    "greenskins":        ["grn"],
    "high-elves":        ["hef"],
    "khorne":            ["kho"],
    "kislev":            ["kis", "ksl"],
    "lizardmen":         ["lzd"],
    "norsca":            ["nor"],
    "nurgle":            ["nur"],
    "ogre-kingdoms":     ["ogr"],
    "skaven":            ["skv"],
    "slaanesh":          ["sla"],
    "tomb-kings":        ["tmb"],
    "tzeentch":          ["tze"],
    "vampire-coast":     ["cst"],
    "vampire-counts":    ["vmp"],
    "warriors-of-chaos": ["chs", "woc"],
    "wood-elves":        ["wef"],
}


# ---------------------------------------------------------------------------
# Generic RPFM table parser
# ---------------------------------------------------------------------------

def _val(cell):
    """Extract the scalar value from an RPFM typed cell dict."""
    for v in cell.values():
        return v
    return None


def parse_rpfm_table(filepath):
    """
    Parse an RPFM-decoded table file.
    Returns (field_names: list[str], rows: list[dict]).
    """
    with open(filepath) as f:
        raw = json.load(f)
    # The decode output wraps in [{type, text}]
    if isinstance(raw, list) and raw and "text" in raw[0]:
        obj = json.loads(raw[0]["text"])
    else:
        obj = raw
    info = obj.get("DBRFileInfo") or obj.get("LocRFileInfo")
    entry = info[0]
    table = entry["table"]
    fields = [f["name"] for f in table["definition"]["fields"]]
    rows = []
    for row in table["table_data"]:
        rows.append({fields[i]: _val(cell) for i, cell in enumerate(row)})
    return fields, rows


def parse_loc_file(filepath):
    """
    Parse an RPFM-decoded .loc file.
    Returns dict {key: text}.
    """
    with open(filepath) as f:
        raw = json.load(f)
    if isinstance(raw, list) and raw and "text" in raw[0]:
        obj = json.loads(raw[0]["text"])
    else:
        obj = raw
    info = obj.get("LocRFileInfo")
    entry = info[0]
    table = entry["table"]
    result = {}
    for row in table["table_data"]:
        k = _val(row[0])
        t = _val(row[1])
        result[k] = t
    return result


# ---------------------------------------------------------------------------
# Build lookup maps from game tables
# ---------------------------------------------------------------------------

def build_armour_map(rows):
    """key -> armour_value (int)"""
    return {r["key"]: r["armour_value"] for r in rows}


def build_entity_map(rows):
    """key -> {hit_points, run_speed, size}"""
    return {r["key"]: {
        "hit_points": r["hit_points"],
        "run_speed": r["run_speed"],
        "size": r["size"],
    } for r in rows}


def build_melee_weapon_map(rows):
    """key -> {damage, ap_damage, is_magical, ignition_amount}"""
    return {r["key"]: {
        "damage": r["damage"],
        "ap_damage": r["ap_damage"],
        "is_magical": r["is_magical"],
        "ignition_amount": r.get("ignition_amount", 0) or 0,
    } for r in rows}


def build_missile_weapon_map(rows):
    """key -> default_projectile key"""
    return {r["key"]: r["default_projectile"] for r in rows}


def build_projectile_map(rows):
    """key -> {effective_range, damage, ap_damage, is_magical, ignition_amount}"""
    return {r["key"]: {
        "effective_range": r["effective_range"],
        "damage": r["damage"],
        "ap_damage": r["ap_damage"],
        "is_magical": r["is_magical"],
        "ignition_amount": r.get("ignition_amount", 0) or 0,
    } for r in rows}


def build_land_unit_map(rows, armour_map, entity_map, melee_map, missile_wep_map, projectile_map):
    """land_unit key -> computed stat dict"""
    result = {}
    for r in rows:
        key = r["key"]
        armour_val = armour_map.get(r["armour"] or "", 0)
        entity = entity_map.get(r["man_entity"] or "", {})
        melee = melee_map.get(r["primary_melee_weapon"] or "", {})

        missile_key = r.get("primary_missile_weapon") or ""
        proj_key = missile_wep_map.get(missile_key, "") if missile_key else ""
        proj = projectile_map.get(proj_key, {}) if proj_key else {}

        melee_types = []
        if melee.get("is_magical"):
            melee_types.append("magical")
        if (melee.get("ignition_amount") or 0) > 0:
            melee_types.append("flaming")

        missile_types = []
        if proj.get("is_magical"):
            missile_types.append("magical")
        if (proj.get("ignition_amount") or 0) > 0:
            missile_types.append("flaming")

        melee_dmg = (melee.get("damage") or 0) + (melee.get("ap_damage") or 0)
        missile_dmg = (proj.get("damage") or 0) + (proj.get("ap_damage") or 0) if proj else None

        run_speed = entity.get("run_speed")
        size = entity.get("size", "small")
        is_large = size in ("large", "very_large", "massive", "ultra")

        result[key] = {
            "bonus_hit_points": r["bonus_hit_points"],
            "armour": armour_val,
            "melee_attack": r["melee_attack"],
            "melee_defence": r["melee_defence"],
            "morale": r["morale"],
            "charge_bonus": r["charge_bonus"],
            "primary_ammo": r.get("primary_ammo") or 0,
            "melee_attack_types": melee_types,
            "weapon_damage": melee.get("damage"),
            "weapon_ap_damage": melee.get("ap_damage"),
            "weapon_strength": melee_dmg if melee else None,
            "run_speed": run_speed,
            "is_large": is_large,
            # missile
            "missile_range": proj.get("effective_range") if proj else None,
            "missile_damage": missile_dmg,
            "missile_base_damage": proj.get("damage") if proj else None,
            "missile_ap_damage": proj.get("ap_damage") if proj else None,
            "missile_damage_types": missile_types,
        }
    return result


def build_main_unit_map(rows):
    """unit key -> {land_unit, num_men, mp_cost, barrier, is_monstrous}"""
    return {r["unit"]: {
        "land_unit": r["land_unit"],
        "num_men": r["num_men"],
        "mp_cost": r["multiplayer_cost"],
        "barrier": int(r.get("barrier_health") or 0),
        "is_monstrous": r.get("is_monstrous", False),
    } for r in rows}


def build_special_ability_map(rows):
    """ability key -> gold_cost (round(additional_melee_cp + additional_missile_cp))"""
    return {r["key"]: round((r.get("additional_melee_cp") or 0) + (r.get("additional_missile_cp") or 0))
            for r in rows}


def build_agent_subtype_map(rows):
    """associated_unit_override (land_unit key) -> agent_subtype key.
    The associated_unit_override is the campaign unit that corresponds to this
    agent subtype. Multiple subtypes can share a unit; we keep the last-seen
    (order in table is stable across patches so this is deterministic).
    """
    result = {}
    for r in rows:
        unit_key = r.get("associated_unit_override") or ""
        if unit_key:
            result[unit_key] = r["key"]
    return result


def build_equipment_map(rows):
    """agent_subtype key -> [ancillary_key, ...] (non-mount ancillaries only).
    Mount ancillaries are recognised by '_anc_mount_' in their key and are
    excluded here since they are already captured in the 'mounts' field.
    """
    result = {}
    for r in rows:
        subtype = r["agent_subtype"]
        ancillary = r["ancillary"]
        if "_anc_mount_" not in ancillary:
            result.setdefault(subtype, []).append(ancillary)
    return result


def build_ancillary_cost_map(rows):
    """ancillary key -> gold_cost (uniqueness_score from ancillaries_tables).
    The uniqueness_score field stores the MP gold cost for each item.
    """
    return {r["key"]: r["uniqueness_score"] for r in rows if r.get("uniqueness_score") is not None}


def build_unit_ability_map(rows):
    """ability key -> {icon_name, type}"""
    return {r["key"]: {"icon_name": r["icon_name"], "type": r["type"]} for r in rows}


def build_ability_loc_maps(loc):
    """Returns (name_map, tooltip_map): ability_key -> display name / tooltip description."""
    name_prefix    = "unit_abilities_onscreen_name_"
    tooltip_prefix = "unit_abilities_tooltip_"
    names    = {}
    tooltips = {}
    for k, v in loc.items():
        if k and k.startswith(name_prefix):
            names[k[len(name_prefix):]] = v
        elif k and k.startswith(tooltip_prefix):
            tooltips[k[len(tooltip_prefix):]] = v
    return names, tooltips


# ---------------------------------------------------------------------------
# Ability icon copying
# ---------------------------------------------------------------------------

# Parses seed-abilities.sql to build: ability_key -> eid
ABILITY_SEED_EID_RE = re.compile(
    r"\(\d+,\s*'([0-9a-f\-]+)',\s*'([^']+)',"
)


def build_ability_key_eid_map(seed_filepath):
    """Returns {ability_key: eid} from the seed-abilities.sql INSERT rows."""
    result = {}
    with open(seed_filepath, encoding="utf-8") as f:
        for line in f:
            m = ABILITY_SEED_EID_RE.search(line)
            if m:
                result[m.group(2)] = m.group(1)
    return result


def copy_ability_icons(icons_dir, asset_dir, unit_ability_map, key_eid_map,
                       dry_run=False):
    """
    For each ability key in unit_ability_map, copies
      {icons_dir}/{icon_name}.png  →  {asset_dir}/{eid}.png
    then trims transparent borders with mogrify.
    """
    copied = 0
    missing_src = []
    missing_eid = []

    for key, info in unit_ability_map.items():
        icon_name = info.get("icon_name") or ""
        if not icon_name:
            continue

        eid = key_eid_map.get(key)
        if not eid:
            missing_eid.append(key)
            continue

        src = os.path.join(icons_dir, icon_name + ".png")
        if not os.path.isfile(src):
            missing_src.append(icon_name)
            continue

        dest = os.path.join(asset_dir, eid + ".png")
        if not dry_run:
            shutil.copy2(src, dest)
            subprocess.run(
                ["mogrify", "-fuzz", "20%", "-trim", "+repage", dest],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        copied += 1

    print(f"  [icons] copied+trimmed {copied} icons", file=sys.stderr)
    if missing_src:
        print(f"  [icons] {len(missing_src)} source PNGs not found "
              f"(e.g. {missing_src[:3]})", file=sys.stderr)
    if missing_eid:
        print(f"  [icons] {len(missing_eid)} keys have no eid in seed "
              f"(e.g. {missing_eid[:3]})", file=sys.stderr)


# ---------------------------------------------------------------------------
# Unit card copying
# ---------------------------------------------------------------------------

# Matches one INSERT row in a unit seed file:
# (id, 'eid', 'name', ...)
UNIT_SEED_ROW_RE = re.compile(
    r"\(\s*\d+,\s*'([0-9a-f\-]+)',\s*'((?:[^']|'')*)',"
)


def build_unit_name_eid_map(seed_dir):
    """Parse all faction unit seed SQL files → list of (unit_name, eid, faction) triples.

    Returns a list rather than a dict so that units sharing a display name across
    factions (e.g. Tzaangors appearing in both Tzeentch and Beastmen seeds) are
    all processed — a dict would silently drop all but the last-seen eid.

    faction is the slug from the filename, e.g. "empire" from seed-empire-units.sql,
    matching the keys of FACTION_KEY_MAP.
    """
    result = []
    for filename in sorted(os.listdir(seed_dir)):
        if not (filename.startswith("seed-") and filename.endswith("-units.sql")):
            continue
        # seed-{faction}-units.sql → faction slug
        faction = filename[len("seed-"):-len("-units.sql")]
        filepath = os.path.join(seed_dir, filename)
        with open(filepath, encoding="utf-8") as f:
            content = f.read()
        for m in UNIT_SEED_ROW_RE.finditer(content):
            eid  = m.group(1)
            name = m.group(2).replace("''", "'")
            result.append((name, eid, faction))
    return result


# Category infixes present in unit keys but often absent from icon filenames.
_CATEGORY_RE = re.compile(r"_(inf|cav|mon|veh|art|cha|hrd|rng|mis)_")
# Game version prefixes on unit keys: wh_main_, wh2_dlc17_, wh3_twa04_, etc.
_GAME_PREFIX_RE = re.compile(r"^wh[23]?_(main|dlc\d+|pro\d+|twa\d+)_")


def _normalize_unit_key(uk):
    """Strip category infix and trailing numeric suffix from a unit key."""
    return re.sub(r"_\d+$", "", _CATEGORY_RE.sub("_", uk))


_STOPWORDS = {"of", "the", "at", "a", "an", "and", "in", "on", "for"}


def _strip_stopwords(key):
    """Remove common English stopwords from a snake_case key."""
    return "_".join(w for w in key.split("_") if w not in _STOPWORDS)


def _find_icon(uk, available, available_list, display_name=None):
    """Return the best-matching icon stem for a unit_key, or None.

    Matching order (first hit wins):
      1. Exact unit_key
      2. unit_key with numeric suffix stripped
      3. unit_key with category infix stripped
      4. unit_key with both stripped
      5. Prefix match against normalised key; if multiple hits, prefer the one
         whose suffix matches the parenthetical variant in the display name
         (e.g. "(Shields)" → prefer icons ending in "_shields")
      6. Stopword-stripped variants of all the above
    """
    # 1. Exact
    if uk in available:
        return uk
    # 2. Strip numeric suffix
    s1 = re.sub(r"_\d+$", "", uk)
    if s1 in available:
        return s1
    # 3. Strip category infix
    s2 = _CATEGORY_RE.sub("_", uk)
    if s2 in available:
        return s2
    # 4. Strip both
    s3 = _normalize_unit_key(uk)
    if s3 in available:
        return s3
    # 5. Prefix match
    prefix = s3 + "_"
    matches = [ik for ik in available_list if ik.startswith(prefix)]
    if matches:
        if len(matches) > 1 and display_name:
            # Try to pick the right variant using the parenthetical in the name,
            # e.g. "Gor Herd (Shields)" → prefer icon ending in "_shields".
            paren = re.search(r"\(([^)]+)\)", display_name)
            if paren:
                variant = re.sub(r"\s+", "_", paren.group(1).lower().strip())
                for m in matches:
                    if m.endswith("_" + variant):
                        return m
        return matches[0]
    # 6. Stopword-stripped versions of all the above
    s4 = _strip_stopwords(s3)
    if s4 != s3:
        if s4 in available:
            return s4
        prefix4 = s4 + "_"
        matches4 = [ik for ik in available_list if ik.startswith(prefix4)]
        if matches4:
            if len(matches4) > 1 and display_name:
                paren = re.search(r"\(([^)]+)\)", display_name)
                if paren:
                    variant = re.sub(r"\s+", "_", paren.group(1).lower().strip())
                    for m in matches4:
                        if m.endswith("_" + variant):
                            return m
            return matches4[0]
        # Also try icon keys with stopwords stripped
        for ik in available_list:
            if _strip_stopwords(ik) == s4:
                return ik
    return None


def _unit_key_to_portrait_base(uk):
    """
    Reduce a unit key to {faction}_{role} form for portrait matching.
    e.g. wh2_dlc17_bst_cha_beastlord_2 -> bst_beastlord
         wh_main_emp_cha_wizard_light_0 -> emp_wizard_light
    """
    uk = _GAME_PREFIX_RE.sub("", uk)   # strip wh_main_ / wh2_dlc17_ etc.
    uk = _CATEGORY_RE.sub("_", uk)     # strip _cha_ / _inf_ etc.
    uk = re.sub(r"_\d+$", "", uk)      # strip trailing _0/_1
    uk = re.sub(r"_+", "_", uk).strip("_")
    return uk


# ---------------------------------------------------------------------------
# Portrait copying (lords / heroes)
# ---------------------------------------------------------------------------

def build_portrait_role_map(portraits_dir):
    """
    Scans portraits_dir for base PNGs (no _maskN suffix) and returns
    {role_key: best_filename} where role_key strips the _campaign_XX suffix
    and best_filename prefers the campaign_01 / quality-0 variant.
    """
    role_map: dict[str, str] = {}
    for fname in sorted(os.listdir(portraits_dir)):
        if not fname.endswith(".png") or "_mask" in fname:
            continue
        stem = fname[:-4]
        m = re.match(r"^(.+)_(\d+)$", stem)
        if not m:
            continue
        base, quality = m.group(1), int(m.group(2))
        if quality != 0:
            continue   # only keep quality-0 portraits
        # role = base without _campaign_XX
        role = re.sub(r"_campaign_\d+$", "", base)
        # Prefer campaign_01 over higher-numbered variants
        if role not in role_map:
            role_map[role] = fname
        elif "_campaign_01" in base and "_campaign_01" not in role_map[role]:
            role_map[role] = fname
    return role_map


def _find_portrait(base, role_map, role_list):
    """
    Try to find a portrait for a normalised unit key `base`.
    Attempts (in order):
      1. Exact role match
      2. Any role that starts with base (covers bst_beastlords vs bst_beastlord)
      3. base starts with role (portrait role is a prefix of base)
      4. Named-character variant: insert _ch_ after faction prefix
      5. Progressively drop trailing words from base and retry 1-4
    """
    def _try(key):
        if key in role_map:
            return role_map[key]
        # forward prefix: any role starts with key (catches plural/suffix variants)
        for r in role_list:
            if r.startswith(key):
                return role_map[r]
        # reverse prefix: key starts with role
        for r in role_list:
            if key.startswith(r):
                return role_map[r]
        return None

    def _try_with_ch(key):
        """Also attempt {faction}_ch_{rest} for named character portraits."""
        parts = key.split("_", 1)
        if len(parts) == 2:
            ch_key = parts[0] + "_ch_" + parts[1]
            result = _try(ch_key)
            if result:
                return result
        return _try(key)

    result = _try_with_ch(base)
    if result:
        return result
    # Stopword-stripped variant
    stripped = _strip_stopwords(base)
    if stripped != base:
        result = _try_with_ch(stripped)
        if result:
            return result
    # Progressively drop last word
    parts = base.split("_")
    for drop in range(1, len(parts) - 1):   # keep at least faction + 1 word
        shorter = "_".join(parts[:-drop])
        result = _try_with_ch(shorter)
        if result:
            return result
        sw = _strip_stopwords(shorter)
        if sw != shorter:
            result = _try_with_ch(sw)
            if result:
                return result
    return None


def _apply_override(name, eid, cards_dir, portraits_dir, asset_dir, dry_run):
    """
    Look up name in UNIT_CARD_OVERRIDES and copy the file if found.
    Searches cards_dir first, then portraits_dir.
    Returns True if copied (or would copy in dry_run), False if key not in override.
    """
    key = UNIT_CARD_OVERRIDES.get(name)
    if key is None:
        return False
    for search_dir in (cards_dir, portraits_dir):
        if search_dir is None:
            continue
        src = os.path.join(search_dir, key + ".png")
        if os.path.exists(src):
            dest = os.path.join(asset_dir, eid + ".png")
            if not dry_run:
                shutil.copy2(src, dest)
                subprocess.run(
                    ["mogrify", "-fuzz", "20%", "-trim", "+repage", dest],
                    check=True,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
            return True
    # Override key listed but file not found in either dir — still consumed
    print(f"  [override] WARNING: override key '{key}' not found for '{name}'",
          file=sys.stderr)
    return True


def copy_unit_portraits(portraits_dir, asset_dir, name_index, unit_name_eid_pairs,
                        cards_dir=None, dry_run=False):
    """
    For each (name, eid) pair in unit_name_eid_pairs that has no card yet, find
    a matching portrait and copy it to {asset_dir}/{eid}.png.
    Skips units that already have a card file.
    UNIT_CARD_OVERRIDES are applied first (before name_index lookup).
    """
    role_map  = build_portrait_role_map(portraits_dir)
    role_list = sorted(role_map.keys())

    # Skip units that already have a card
    existing = {os.path.splitext(f)[0] for f in os.listdir(asset_dir)
                if f.endswith(".png")}

    copied      = 0
    missing_key = []
    no_portrait = []

    for entry in unit_name_eid_pairs:
        name, eid = entry[0], entry[1]
        faction   = entry[2] if len(entry) > 2 else None

        if eid in existing:
            continue

        # Check explicit override first
        if _apply_override(name, eid, cards_dir, portraits_dir, asset_dir, dry_run):
            copied += 1
            continue

        all_candidates = name_index.get(normalize_name(name), [])
        if not all_candidates:
            missing_key.append(name)
            continue

        if faction and len(all_candidates) > 1:
            faction_prefixes = FACTION_KEY_MAP.get(faction, [])
            filtered = [(uk, lk) for uk, lk in all_candidates
                        if any(f"_{p}_" in uk for p in faction_prefixes)]
            candidates = filtered if filtered else all_candidates
        else:
            candidates = all_candidates

        portrait_file = None
        for uk, _lk in candidates:
            base = _unit_key_to_portrait_base(uk)
            portrait_file = _find_portrait(base, role_map, role_list)
            if portrait_file:
                break

        if not portrait_file:
            no_portrait.append(name)
            continue

        src  = os.path.join(portraits_dir, portrait_file)
        dest = os.path.join(asset_dir, eid + ".png")
        if not dry_run:
            shutil.copy2(src, dest)
            subprocess.run(
                ["mogrify", "-fuzz", "20%", "-trim", "+repage", dest],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        copied += 1

    print(f"  [portraits] copied+trimmed {copied} portraits", file=sys.stderr)
    if missing_key:
        print(f"  [portraits] {len(missing_key)} units not in name index "
              f"(e.g. {missing_key[:3]})", file=sys.stderr)
    if no_portrait:
        print(f"  [portraits] {len(no_portrait)} units with no matching portrait "
              f"(e.g. {no_portrait[:5]})", file=sys.stderr)


def copy_unit_cards(cards_dir, asset_dir, name_index, unit_name_eid_pairs,
                    portraits_dir=None, dry_run=False):
    """
    For each (name, eid) pair in unit_name_eid_pairs, finds the best-matching
    icon, copies {cards_dir}/{icon}.png → {asset_dir}/{eid}.png, then trims.

    unit_name_eid_pairs is a list of (name, eid) tuples (not a dict) so that
    units sharing a display name across factions are all processed.

    UNIT_CARD_OVERRIDES are applied first (before name_index lookup).
    """
    available      = {os.path.splitext(f)[0] for f in os.listdir(cards_dir)
                      if f.endswith(".png")}
    available_list = sorted(available)

    copied      = 0
    missing_key = []
    missing_src = []

    for entry in unit_name_eid_pairs:
        name, eid = entry[0], entry[1]
        faction   = entry[2] if len(entry) > 2 else None

        # Check explicit override first (covers RoR units and missing-loc units)
        if _apply_override(name, eid, cards_dir, portraits_dir, asset_dir, dry_run):
            copied += 1
            continue

        all_candidates = name_index.get(normalize_name(name), [])
        if not all_candidates:
            missing_key.append(name)
            continue

        # Prefer candidates matching this unit's faction key prefix when there
        # are multiple (e.g. "Giant" appears in beastmen, greenskins, norsca).
        if faction and len(all_candidates) > 1:
            faction_prefixes = FACTION_KEY_MAP.get(faction, [])
            filtered = [(uk, lk) for uk, lk in all_candidates
                        if any(f"_{p}_" in uk for p in faction_prefixes)]
            candidates = filtered if filtered else all_candidates
        else:
            candidates = all_candidates

        unit_key = None
        for uk, _lk in candidates:
            result = _find_icon(uk, available, available_list, display_name=name)
            if result is not None:
                unit_key = result
                break

        if unit_key is None:
            missing_src.append(name)
            continue

        src  = os.path.join(cards_dir, unit_key + ".png")
        dest = os.path.join(asset_dir,  eid      + ".png")
        if not dry_run:
            shutil.copy2(src, dest)
            subprocess.run(
                ["mogrify", "-fuzz", "20%", "-trim", "+repage", dest],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        copied += 1

    print(f"  [unit cards] copied+trimmed {copied} cards", file=sys.stderr)
    if missing_key:
        print(f"  [unit cards] {len(missing_key)} units not in name index "
              f"(e.g. {missing_key[:3]})", file=sys.stderr)
    if missing_src:
        print(f"  [unit cards] {len(missing_src)} units with no matching icon "
              f"(e.g. {missing_src[:3]})", file=sys.stderr)


# ---------------------------------------------------------------------------
# Name → unit key index via loc
# ---------------------------------------------------------------------------

def normalize_name(name):
    return (name
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .strip())


def build_name_index(land_units_loc, main_unit_map, land_unit_stats):
    """
    Returns: name -> [list of (unit_key, land_unit_key)] pairs.
    Parses onscreen_name entries from the land_units loc.
    """
    # land_units loc keys: "land_units_onscreen_name_<land_unit_key>" -> display name
    prefix = "land_units_onscreen_name_"
    lu_name_map = {}  # land_unit_key -> display_name
    for k, v in land_units_loc.items():
        if k.startswith(prefix):
            lu_key = k[len(prefix):]
            lu_name_map[lu_key] = normalize_name(v)

    # Build: name -> [(unit_key, land_unit_key), ...]
    # Go through main_units, look up land_unit display name
    index = {}
    for unit_key, mu in main_unit_map.items():
        lu_key = mu["land_unit"]
        name = lu_name_map.get(lu_key)
        if not name:
            continue
        index.setdefault(name, []).append((unit_key, lu_key))
    return index


def find_unit_key(unit_name, faction_prefixes, name_index, main_unit_map):
    """
    Find the best matching unit_key for a given display name + faction.
    Returns (unit_key, land_unit_key) or (None, None).
    """
    norm = normalize_name(unit_name)
    candidates = name_index.get(norm, [])
    if not candidates:
        return None, None

    if len(candidates) == 1:
        return candidates[0]

    # Multiple candidates: filter by faction prefix
    filtered = []
    for (unit_key, lu_key) in candidates:
        for prefix in faction_prefixes:
            if f"_{prefix}_" in unit_key or f"_{prefix}_" in lu_key:
                filtered.append((unit_key, lu_key))
                break

    if len(filtered) == 1:
        return filtered[0]
    if filtered:
        # Prefer latest game (wh3 > wh2 > wh)
        for game_prefix in ("wh3_", "wh2_", "wh_"):
            for item in filtered:
                if item[0].startswith(game_prefix):
                    return item
        return filtered[0]

    # Fallback: no faction match, return first candidate
    return candidates[0]


# ---------------------------------------------------------------------------
# Stats extraction
# ---------------------------------------------------------------------------

def extract_stats(unit_key, main_unit_map, land_unit_stats,
                  agent_subtype_map=None, equipment_map=None, ancillary_cost_map=None):
    mu = main_unit_map.get(unit_key)
    if not mu:
        return None
    land_unit_key = mu["land_unit"]
    lu = land_unit_stats.get(land_unit_key)
    if not lu:
        return None

    speed = lu.get("run_speed")
    if speed is not None:
        speed = round(speed)

    stats = {
        "cost": mu["mp_cost"],
        "is_large": lu["is_large"] or mu.get("is_monstrous", False),
        "unit_size": mu["num_men"],
        "health": lu["bonus_hit_points"],
        "barrier": mu["barrier"] or 0,
        "armor": lu["armour"],
        "leadership": lu["morale"],
        "speed": speed,
        "melee_attack": lu["melee_attack"],
        "melee_attack_types": lu["melee_attack_types"],
        "melee_defence": lu["melee_defence"],
        "weapon_strength": lu["weapon_strength"],
        "weapon_damage": lu["weapon_damage"],
        "weapon_ap_damage": lu["weapon_ap_damage"],
        "charge_bonus": lu["charge_bonus"],
        "ammunition": lu["primary_ammo"],
        "missile_damage_types": lu["missile_damage_types"],
    }

    if lu.get("missile_range"):
        stats["range"] = lu["missile_range"]
    if lu.get("missile_damage") is not None:
        stats["missile_damage"] = lu["missile_damage"]
    if lu.get("missile_base_damage") is not None:
        stats["missile_base_damage"] = lu["missile_base_damage"]
    if lu.get("missile_ap_damage") is not None:
        stats["missile_ap_damage"] = lu["missile_ap_damage"]

    # Equipment: unique items for this character from ancillaries_included_agent_subtypes.
    # Only populated for legendary lords/heroes that have character-specific items.
    if agent_subtype_map and equipment_map:
        agent_subtype = agent_subtype_map.get(land_unit_key)
        if agent_subtype:
            items = equipment_map.get(agent_subtype)
            if items:
                stats["equipment"] = [
                    {"key": k, "gold_cost": (ancillary_cost_map or {}).get(k)}
                    for k in items
                ]

    return {k: v for k, v in stats.items() if v is not None}


# ---------------------------------------------------------------------------
# Seed file updating
# ---------------------------------------------------------------------------

STATS_BLOCK_RE = re.compile(
    r"(\(\d+,\s*'[0-9a-f\-]+',\s*'((?:[^']|'')*)',\s*'(?:[^']|'')*',\s*\n\s*\d+,\s*\d+,\s*\d+,\s*\d+,\s*')(\{[^']*\})(')"
)


def update_unit_seed_file(filepath, faction_name, faction_prefixes, name_index,
                          main_unit_map, land_unit_stats,
                          agent_subtype_map=None, equipment_map=None, ancillary_cost_map=None):
    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    not_found = []
    found = 0
    no_game_data = []

    def replacer(m):
        nonlocal found
        prefix_str = m.group(1)
        raw_name = m.group(2).replace("''", "'")
        old_stats_str = m.group(3)
        suffix = m.group(4)

        unit_key, lu_key = find_unit_key(raw_name, faction_prefixes, name_index, main_unit_map)
        if unit_key is None:
            not_found.append(raw_name)
            return m.group(0)

        new_stats = extract_stats(unit_key, main_unit_map, land_unit_stats,
                                  agent_subtype_map, equipment_map, ancillary_cost_map)
        if new_stats is None:
            no_game_data.append(raw_name)
            return m.group(0)

        # Preserve non-stat fields from existing stats (abilities, mounts, draftable-spells,
        # equipment) and append them in canonical order at the end. Equipment from RPFM
        # takes precedence over any previously stored value.
        try:
            old_stats = json.loads(old_stats_str)
        except Exception:
            old_stats = {}

        for preserve_key in ("abilities", "draftable-spells", "mounts"):
            if preserve_key in old_stats:
                new_stats[preserve_key] = old_stats[preserve_key]
        # equipment is sourced from game data — only fall back to old value if RPFM
        # returned nothing (unit not in ancillaries_included_agent_subtypes).
        if "equipment" not in new_stats and "equipment" in old_stats:
            new_stats["equipment"] = old_stats["equipment"]

        found += 1
        return prefix_str + json.dumps(new_stats, separators=(",", ":")) + suffix

    new_content = STATS_BLOCK_RE.sub(replacer, content)

    if not_found:
        print(f"  [{faction_name}] {len(not_found)} units not matched in loc: "
              f"{not_found[:5]}{'...' if len(not_found) > 5 else ''}", file=sys.stderr)
    if no_game_data:
        print(f"  [{faction_name}] {len(no_game_data)} units matched but no game data: "
              f"{no_game_data[:5]}", file=sys.stderr)
    print(f"  [{faction_name}] updated {found} units", file=sys.stderr)
    return new_content


# ---------------------------------------------------------------------------
# Ability seed updating
# ---------------------------------------------------------------------------

# Matches one INSERT row in seed-abilities.sql:
# (id, 'eid', 'key', 'name', 'description', 'ability_type', ...)
# Captures: full_match, id, eid, key, name, description, ability_type, rest_of_row
ABILITY_ROW_RE = re.compile(
    r"(\(\d+,\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)'(,\s*[^)]+))",
    re.DOTALL,
)

# Same pattern but with icon column already present — 7th string column is icon.
ABILITY_ROW_WITH_ICON_RE = re.compile(
    r"(\(\d+,\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)',\s*'([^']*)'(,\s*[^)]+))",
    re.DOTALL,
)


def _sql_escape(s):
    return (s or "").replace("'", "''")


def update_ability_seed_file(filepath, ability_name_map, ability_tooltip_map):
    """
    Rewrites seed-abilities.sql refreshing name and description from game loc.
    Icon is NOT stored in the DB — it is derived from the eid at render time.
    """
    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    # Strip icon column from INSERT header if present (idempotent)
    content = content.replace(
        "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type, icon,",
        "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type,",
    ).replace(
        "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type, icon,",
        "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type,",
    )

    updated   = 0
    not_found = 0

    def refresh_row(m):
        nonlocal updated, not_found
        full         = m.group(0)
        eid          = m.group(2)
        key          = m.group(3)
        _name        = m.group(4)
        _desc        = m.group(5)
        ability_type = m.group(6)
        rest         = m.group(7)
        id_part      = full.lstrip("(").split(",")[0].strip()
        name = ability_name_map.get(key)
        desc = ability_tooltip_map.get(key)
        if name is None and desc is None:
            not_found += 1
            return full
        name = name or _name.replace("''", "'")
        desc = desc or _desc.replace("''", "'")
        updated += 1
        return (f"({id_part}, '{eid}', '{key}', '{_sql_escape(name)}', "
                f"'{_sql_escape(desc)}', '{ability_type}'{rest}")

    # Handle rows that may still have an icon column value (7 string cols)
    def strip_icon_and_refresh(m):
        nonlocal updated, not_found
        full         = m.group(0)
        eid          = m.group(2)
        key          = m.group(3)
        _name        = m.group(4)
        _desc        = m.group(5)
        ability_type = m.group(6)
        rest         = m.group(8)   # skip icon group (7)
        id_part      = full.lstrip("(").split(",")[0].strip()
        name = ability_name_map.get(key) or _name.replace("''", "'")
        desc = ability_tooltip_map.get(key) or _desc.replace("''", "'")
        updated += 1
        return (f"({id_part}, '{eid}', '{key}', '{_sql_escape(name)}', "
                f"'{_sql_escape(desc)}', '{ability_type}'{rest}")

    if ABILITY_ROW_WITH_ICON_RE.search(content):
        new_content = ABILITY_ROW_WITH_ICON_RE.sub(strip_icon_and_refresh, content)
    else:
        new_content = ABILITY_ROW_RE.sub(refresh_row, content)

    print(f"  [abilities] updated {updated} rows, {not_found} without loc entry",
          file=sys.stderr)
    return new_content


# ---------------------------------------------------------------------------
# Spell seed updating
# ---------------------------------------------------------------------------

# Regex for when gold_cost column already exists in the INSERT:
# Matches: ..., spell_type, mana_cost, CURRENT_gold_cost, game_id, version, ...
# Groups: (prefix_through_mana_cost, spell_key, current_gold_cost, post_gold_cost_suffix)
SPELL_UPDATE_RE = re.compile(
    r"(\(\d+,\s*'[0-9a-f\-]+',\s*'([^']+)',\s*'(?:[^']|'')*',\s*'(?:[^']|'')*',\s*'[^']+',\s*\d+,\s*)(\d+)(,\s*\d+,\s*\d+,\s*')"
)

# Regex for when gold_cost column does NOT exist yet:
# Matches: ..., spell_type, mana_cost, game_id, version, ...
# Groups: (prefix_through_spell_type, spell_key, mana_cost, post_mana_cost_suffix)
SPELL_INSERT_RE = re.compile(
    r"(\(\d+,\s*'[0-9a-f\-]+',\s*'([^']+)',\s*'(?:[^']|'')*',\s*'(?:[^']|'')*',\s*'[^']+',\s*)(\d+)(,\s*\d+,\s*\d+,\s*')"
)


def update_spell_seed_file(filepath, special_ability_map):
    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    not_found = []
    found = 0
    has_gold_cost = "gold_cost" in content

    if has_gold_cost:
        pattern = SPELL_UPDATE_RE
    else:
        print("  seed-spells.sql does not have gold_cost column — skipping spell update", file=sys.stderr)
        return content

    def replacer(m):
        nonlocal found
        prefix_str = m.group(1)  # everything up to and including mana_cost (with gold_cost mode: up to mana_cost)
        spell_key = m.group(2)
        _old_gold = m.group(3)  # existing gold_cost value (to be replaced)
        suffix = m.group(4)     # ", game_id, version, 'uuid'..."

        gold_cost = special_ability_map.get(spell_key)
        if gold_cost is None:
            not_found.append(spell_key)
            gold_cost = 0
        else:
            found += 1

        return prefix_str + str(gold_cost) + suffix

    new_content = pattern.sub(replacer, content)
    print(f"  Updated {found} spell gold costs, {len(not_found)} not found", file=sys.stderr)
    return new_content


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Update seed data from RPFM-decoded game tables.")
    parser.add_argument("--data-dir", required=True,
                        help="Directory containing RPFM-decoded JSON table files.")
    parser.add_argument("--icons-dir",
                        help="Directory containing extracted ability icon PNGs "
                             "(named by icon_name from unit_abilities_tables). "
                             "If omitted, icon copying is skipped.")
    parser.add_argument("--unit-cards-dir",
                        help="Directory containing extracted unit card PNGs "
                             "(ui/units/icons/ from game files, named by unit key). "
                             "If omitted, unit card copying is skipped.")
    parser.add_argument("--portraits-dir",
                        help="Directory containing extracted portrait PNGs "
                             "(ui/portraits/units/no_culture/ from game files). "
                             "Used for lords/heroes without a unit card. "
                             "If omitted, portrait copying is skipped.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print what would change without writing files.")
    args = parser.parse_args()

    d = args.data_dir

    def path(name):
        return os.path.join(d, name)

    print("Loading game tables...", file=sys.stderr)

    _, armour_rows = parse_rpfm_table(path("unit_armour_types_tables.json"))
    armour_map = build_armour_map(armour_rows)
    print(f"  armour types: {len(armour_map)}", file=sys.stderr)

    _, entity_rows = parse_rpfm_table(path("battle_entities_tables.json"))
    entity_map = build_entity_map(entity_rows)
    print(f"  battle entities: {len(entity_map)}", file=sys.stderr)

    _, melee_rows = parse_rpfm_table(path("melee_weapons_tables.json"))
    melee_map = build_melee_weapon_map(melee_rows)
    print(f"  melee weapons: {len(melee_map)}", file=sys.stderr)

    _, mwep_rows = parse_rpfm_table(path("missile_weapons_tables.json"))
    missile_wep_map = build_missile_weapon_map(mwep_rows)
    print(f"  missile weapons: {len(missile_wep_map)}", file=sys.stderr)

    _, proj_rows = parse_rpfm_table(path("projectiles_tables.json"))
    projectile_map = build_projectile_map(proj_rows)
    print(f"  projectiles: {len(projectile_map)}", file=sys.stderr)

    _, lu_rows = parse_rpfm_table(path("land_units_tables.json"))
    land_unit_stats = build_land_unit_map(lu_rows, armour_map, entity_map, melee_map,
                                          missile_wep_map, projectile_map)
    print(f"  land units: {len(land_unit_stats)}", file=sys.stderr)

    _, mu_rows = parse_rpfm_table(path("main_units_tables.json"))
    main_unit_map = build_main_unit_map(mu_rows)
    print(f"  main units: {len(main_unit_map)}", file=sys.stderr)

    _, sa_rows = parse_rpfm_table(path("unit_special_abilities_tables.json"))
    special_ability_map = build_special_ability_map(sa_rows)
    print(f"  special abilities: {len(special_ability_map)}", file=sys.stderr)

    land_units_loc = parse_loc_file(path("land_units_loc.json"))
    print(f"  land units loc: {len(land_units_loc)} entries", file=sys.stderr)

    _, agent_subtype_rows = parse_rpfm_table(path("agent_subtypes_tables.json"))
    agent_subtype_map = build_agent_subtype_map(agent_subtype_rows)
    print(f"  agent subtypes: {len(agent_subtype_map)}", file=sys.stderr)

    _, anc_subtype_rows = parse_rpfm_table(path("ancillaries_included_agent_subtypes_tables.json"))
    equipment_map = build_equipment_map(anc_subtype_rows)
    print(f"  equipment (agent subtypes with items): {len(equipment_map)}", file=sys.stderr)

    _, ancillaries_rows = parse_rpfm_table(path("ancillaries_tables.json"))
    ancillary_cost_map = build_ancillary_cost_map(ancillaries_rows)
    print(f"  ancillary gold costs: {len(ancillary_cost_map)}", file=sys.stderr)

    _, ua_rows = parse_rpfm_table(path("unit_abilities_tables.json"))
    unit_ability_map = build_unit_ability_map(ua_rows)
    print(f"  unit abilities (icons): {len(unit_ability_map)}", file=sys.stderr)

    ua_loc = parse_loc_file(path("unit_abilities_loc.json"))
    ability_name_map, ability_tooltip_map = build_ability_loc_maps(ua_loc)
    print(f"  ability loc: {len(ability_name_map)} names, {len(ability_tooltip_map)} tooltips",
          file=sys.stderr)

    print("Building name index...", file=sys.stderr)
    name_index = build_name_index(land_units_loc, main_unit_map, land_unit_stats)
    print(f"  {len(name_index)} unique unit names indexed", file=sys.stderr)

    print("Updating unit seed files...", file=sys.stderr)
    for faction_name, faction_prefixes in FACTION_KEY_MAP.items():
        filename = f"seed-{faction_name}-units.sql"
        filepath = os.path.join(SEED_DIR, filename)
        if not os.path.exists(filepath):
            print(f"  SKIP (not found): {filename}", file=sys.stderr)
            continue

        new_content = update_unit_seed_file(
            filepath, faction_name, faction_prefixes,
            name_index, main_unit_map, land_unit_stats,
            agent_subtype_map, equipment_map, ancillary_cost_map
        )
        if not args.dry_run:
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(new_content)

    print("Updating ability descriptions...", file=sys.stderr)
    ability_file = os.path.join(SEED_DIR, "seed-abilities.sql")
    new_abilities = update_ability_seed_file(ability_file, ability_name_map, ability_tooltip_map)
    if not args.dry_run:
        with open(ability_file, "w", encoding="utf-8") as f:
            f.write(new_abilities)

    if args.unit_cards_dir or args.portraits_dir:
        print("Copying and trimming unit cards...", file=sys.stderr)
        unit_name_eid_pairs = build_unit_name_eid_map(SEED_DIR)
        print(f"  {len(unit_name_eid_pairs)} units in seed", file=sys.stderr)
        if args.unit_cards_dir:
            copy_unit_cards(args.unit_cards_dir, UNIT_CARD_ASSET_DIR,
                            name_index, unit_name_eid_pairs,
                            portraits_dir=args.portraits_dir,
                            dry_run=args.dry_run)
        else:
            print("  [unit cards] --unit-cards-dir not provided, skipping", file=sys.stderr)

        if args.portraits_dir:
            print("Copying and trimming lord/hero portraits...", file=sys.stderr)
            copy_unit_portraits(args.portraits_dir, UNIT_CARD_ASSET_DIR,
                                name_index, unit_name_eid_pairs,
                                cards_dir=args.unit_cards_dir,
                                dry_run=args.dry_run)
        else:
            print("  [portraits] --portraits-dir not provided, skipping", file=sys.stderr)

    if args.icons_dir:
        print("Copying and trimming ability icons...", file=sys.stderr)
        ability_seed_file = os.path.join(SEED_DIR, "seed-abilities.sql")
        key_eid_map = build_ability_key_eid_map(ability_seed_file)
        asset_dir = os.path.join("bases", "rts-web", "resources", "rts-web",
                                 "asset", "icon", "ability")
        copy_ability_icons(args.icons_dir, asset_dir, unit_ability_map,
                           key_eid_map, dry_run=args.dry_run)
    else:
        print("  [icons] --icons-dir not provided, skipping icon copy", file=sys.stderr)

    print("Updating spell gold costs...", file=sys.stderr)
    spell_file = os.path.join(SEED_DIR, "seed-spells.sql")
    new_spell = update_spell_seed_file(spell_file, special_ability_map)
    if not args.dry_run:
        with open(spell_file, "w", encoding="utf-8") as f:
            f.write(new_spell)

    print("Done.", file=sys.stderr)


if __name__ == "__main__":
    main()
