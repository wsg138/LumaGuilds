# LumaGuilds menu-redesign Nexo pack

This directory is the visual source bundle for `feature/full-menu-redesign`.

## Install

Copy these files into the matching folders under `plugins/Nexo/`:

- `items/lumaguilds_redesign.yml` -> `plugins/Nexo/items/`
- `glyphs/lumaguilds_redesign.yml` -> `plugins/Nexo/glyphs/`
- `pack/assets/lumaguilds/...` -> `plugins/Nexo/pack/assets/lumaguilds/...`

Reload/rebuild Nexo's pack and reconnect so the new pack is applied.

## Visual architecture

The background is the UI. It owns the frame, header/status wells, eight category-card wells and footer controls so the menu reads as one interface rather than separate illustrations sitting on top of a chest GUI.

The eight 64x64 category textures are transparent symbols only. They intentionally do **not** contain their own card border, background panel or baked text. They share one outline weight, one lighting direction and the same navy/teal/gold palette. Nexo renders them at 2x GUI scale inside the card wells.

The plugin uses invisible Nexo hitbox items around each category symbol so the complete 2x2 visual card behaves as one large clickable control even though the visible icon is only a centered symbol.

The background is a 256x256 Nexo glyph named `guild_redesign_bg_6_row`. Its current ascent is 13. Final pixel alignment should be checked on the server at the GUI scales Enthusia supports; that is a resource-pack calibration step, not a navigation or functionality dependency.

The visual constraints used for this pack and all future menu families are documented in `../VISUAL_SYSTEM.md`.

For a source-level preview without generating a mockup image, see `../PREVIEW.md`.

## Bedrock

The redesign routes Bedrock players through the same inventory UI and feature tree as Java. For visual parity, use a current Nexo Scaffolding + Geyser setup so Nexo custom GUI backgrounds, glyphs and item assets are translated for Bedrock. Without Scaffolding the inventory remains usable through Geyser with vanilla fallbacks, but it will not have the same custom visuals.

Bedrock is not allowed to lose features just because a particular visual effect cannot translate exactly. If a treatment proves unreliable in Scaffolding, the design should be simplified or given a visually equivalent Bedrock treatment while preserving the same actions and hierarchy.
