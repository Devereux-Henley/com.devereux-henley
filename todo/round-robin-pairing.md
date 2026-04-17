# Round-robin pairing is actually swiss

`generate-pairings` in `components/rts-domain/src/com/devereux_henley/rts_domain/handlers/tournament.clj`
routes the `"round-robin"` phase-type to `rules/swiss-pair`, same as
`"swiss"`. Swiss pairs adjacent players by point standings while avoiding
repeat matchups; a round-robin is supposed to have every player face every
other player exactly once, which is a different algorithm.

This is fine as a placeholder while the tournament surface is under
construction, but needs a real implementation before the phase-type is
exposed as a first-class option.

## What to do

- Add a `rules/round-robin-pair` that deterministically pairs all N
  players over (N-1) rounds using the circle method (fix one player,
  rotate the rest).
- Dispatch to it from `generate-pairings` when `phase-type` is
  `"round-robin"`.
- Adjust `generate-next-round`'s "phase exhausted" detection so
  round-robin phases end after (N-1) rounds instead of using a
  round-count configured up front.
- Unit tests: every pair plays exactly once across the phase; no bye
  is produced for even N.
