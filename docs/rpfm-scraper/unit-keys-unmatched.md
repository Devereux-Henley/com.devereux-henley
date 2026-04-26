# Unit-key matcher: unmatched units (2026-04-26)

Captured from a full scraper run against the live WH3 install
(`bases/rpfm-scraper/data/` decoded via the rpfm MCP) on 2026-04-26
against branch `devereux-henley/scraper-unit-keys` (PR #36).

The scraper's display-name → unit-key matcher (`name_match.clj`) joins the
display name in each `seed-<faction>-units.sql` row to a
`main_units_tables.unit` engine key via the `land_units__.loc` index for
that faction's prefix set. Across 24 factions it resolved 1599 / 1662
units; the remaining **63 entries** below didn't match any loc string.

These are almost all spell-caster lords and heroes whose seed display
name diverges from the `land_units__.loc` value (e.g. seed says
`"Archmage"`, loc says `"Archmage (Lore of Beasts)"` etc.). A small
override map keyed on `[display-name faction]` would close the gap.

## Unmatched units, by faction

### grand-cathay (1)
- Dragon-blooded Shugengan Lord

### vampire-counts (1)
- Vampire

### chaos-dwarfs (2)
- Daemonsmith Sorcerer
- Sorcerer-Prophet

### tzeentch (5)
- Chaos Sorcerer Lord of Tzeentch
- Chaos Sorcerer of Tzeentch
- Exalted Lord of Change
- Herald of Tzeentch
- Iridescent Horror

### ogre-kingdoms (2)
- Butcher
- Slaughtermaster

### dark-elves (2)
- Sorceress
- Supreme Sorceress

### norsca (3)
- Fimir Balefiend
- Great Shaman-Sorcerer
- Shaman-Sorcerer

### slaanesh (5)
- Alluress
- Chaos Sorcerer Lord of Slaanesh
- Chaos Sorcerer of Slaanesh
- Exalted Keeper of Secrets
- Herald of Slaanesh

### beastmen (2)
- Bray-Shaman
- Great Bray-Shaman

### tomb-kings (1)
- Liche Priest

### daemons-of-chaos (9)
- Alluress
- Exalted Great Unclean One
- Exalted Keeper of Secrets
- Exalted Lord of Change
- Herald of Nurgle
- Herald of Slaanesh
- Herald of Tzeentch
- Iridescent Horror
- Plagueridden

### wood-elves (4)
- Malevolent Ancient Treeman
- Malevolent Branchwraith
- Spellsinger
- Spellweaver

### warriors-of-chaos (8)
- Chaos Sorcerer
- Chaos Sorcerer Lord
- Chaos Sorcerer Lord of Nurgle
- Chaos Sorcerer Lord of Slaanesh
- Chaos Sorcerer Lord of Tzeentch
- Chaos Sorcerer of Nurgle
- Chaos Sorcerer of Slaanesh
- Chaos Sorcerer of Tzeentch

### vampire-coast (1)
- Vampire Fleet Captain

### bretonnia (2)
- Damsel
- Prophetess

### skaven (1)
- Grey Seer

### nurgle (5)
- Chaos Sorcerer Lord of Nurgle
- Chaos Sorcerer of Nurgle
- Exalted Great Unclean One
- Herald of Nurgle
- Plagueridden

### lizardmen (5)
- Skink Priest
- Slann Mage-Priest (Beasts)
- Slann Mage-Priest (Death)
- Slann Mage-Priest (Metal)
- Slann Mage-Priest (Shadows)

### kislev (2)
- Frost Maiden
- Ice Witch

### high-elves (2)
- Archmage
- Mage

## Suggested fix

Add a per-faction `[display-name → unit-key]` overrides map in
`bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/overrides.clj`
that the matcher consults when the loc-based lookup misses. The
canonical engine keys for these casters can be pulled from
`main_units_tables.json` by faction prefix and known suffix patterns
(`*_cha_chaos_sorcerer_lord_*`, `*_cha_archmage_*`, etc.).

Once the overrides land, a re-run of the scraper should bring `unit.key`
coverage from 96.2% (1599 / 1662) to 100%.
