# LumaGuilds menu-redesign Nexo pack

This directory is the visual source bundle for `feature/full-menu-redesign`.

## Install

Copy these files into the matching folders under `plugins/Nexo/`:

- `items/lumaguilds_redesign.yml` -> `plugins/Nexo/items/`
- `glyphs/lumaguilds_redesign.yml` -> `plugins/Nexo/glyphs/`
- `pack/assets/lumaguilds/...` -> `plugins/Nexo/pack/assets/lumaguilds/...`

Reload/rebuild Nexo's pack and reconnect so the new pack is applied.

The eight primary icons are 64x64 textures rendered at 2x GUI scale. Nexo's `Pack.oversized_in_gui: true` prevents Minecraft 1.21.6+ from clipping them to one slot. The plugin uses invisible Nexo hitbox items around each visual card so a multi-slot-looking card behaves like a large button.

The background is a 256x256 Nexo glyph named `guild_redesign_bg_6_row`. Its current ascent is 13. Final pixel alignment should be checked on the server at the GUI scales Enthusia supports; that is a resource-pack calibration step, not a navigation or functionality dependency.

For a source-level preview without generating a mockup image, see `../PREVIEW.md`.

## Bedrock

The redesign routes Bedrock players through the same inventory UI and feature tree as Java. For visual parity, use a current Nexo Scaffolding + Geyser setup so Nexo custom GUI backgrounds, glyphs and item assets are translated for Bedrock. Without Scaffolding the inventory remains usable through Geyser with vanilla fallbacks, but it will not have the same custom visuals.
