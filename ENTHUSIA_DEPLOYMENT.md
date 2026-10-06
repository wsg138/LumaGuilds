# Enthusia deployment note for this fork

This repository is a **noncanonical fork** of `BadgersMC/LumaGuilds`.

Do not use this fork's `main` branch as the primary implementation or deployment source for Enthusia wiki generation.

Current routing is maintained in:

- `wsg138/enthusia-server-state/DOCUMENTATION_INVENTORY.md`
- `wsg138/enthusia-server-state/repo-overlays/BadgersMC-LumaGuilds.md`

A current upstream-based documentation candidate also exists on this fork at branch:

`docs/enthusia-deployment-current`

Key current production facts that future tooling must preserve:

- LumaGuilds is installed.
- `claims_enabled: false`, so the generic land-claim system is not an active Enthusia player feature.
- Guild homes, ranks, bank/vault and integrations remain relevant.
- The live config sets `bank_mode: BOTH` and `use_physical_currency: true`, while current upstream physical-currency validation requires `bank_mode: PHYSICAL`. Therefore Raw Gold physical guild-bank/war behavior is **not verified** and must not be advertised as live until that mismatch is resolved.
- Multiple LumaGuilds JAR versions are currently present on the backend, so version-specific behavior requires runtime/artifact provenance.

Use `BadgersMC/LumaGuilds` for implementation semantics and the central deployment overlay for current Enthusia values.