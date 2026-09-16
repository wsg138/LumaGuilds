# LumaGuilds menu redesign — source preview

This page previews the **real files used by the redesign**. It is intentionally not a generated GUI mockup.

A pixel-perfect full preview must be viewed in Minecraft because the final composition depends on the Minecraft chest GUI, the Nexo background glyph, oversized item models, font shifts and client GUI scale. The files below are the exact source assets used to produce that view.

## Main Guild Home slot map

The dashboard is a 6-row chest (`9 x 6`). The background owns the frame, status/header wells, eight primary card wells and footer controls. The eight Nexo category textures are transparent symbols centered inside those wells; transparent neighboring hitboxes make each visual card behave as a larger click target.

```text
Columns →    0        1        2        3        4        5        6        7        8
            ┌────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┐
Row 0       │ Guild  │        │        │Members │        │ Rank   │        │        │Activity│
            ├────────┴────────┼────────┴────────┼────────┴────────┼────────┴────────┼────────┤
Rows 1–2    │ Members & Ranks │ Money & Vault   │ Level & Quests  │ Homes & Land    │        │
            ├─────────────────┼─────────────────┼─────────────────┼─────────────────┼────────┤
Rows 3–4    │ Allies & War    │ Parties & LFG   │ Customize Guild │ Guild Settings  │        │
            ├────────┬────────┼────────┬────────┼────────┬────────┼────────┬────────┼────────┤
Row 5       │Feature │        │        │        │ Menu   │        │        │        │ Close  │
            │ Index  │        │        │        │ Guide  │        │        │        │        │
            └────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┘
```

`Menu Guide` is informational. `Feature Index`, `Members`, every task card and `Close` are interactive.

## Actual icon PNGs

Open these files in GitHub to see the exact transparent symbols at native resolution:

- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_members.png` — Members & Ranks
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_money.png` — Money & Vault
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_level.png` — Level & Quests
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_homes.png` — Homes & Land
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_allies.png` — Allies & War
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_parties.png` — Parties & LFG
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_customize.png` — Customize Guild
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_settings.png` — Guild Settings

None of these textures contains its own separate card frame or baked label. That is deliberate: the shared background provides the visual structure so the complete screen reads as one UI.

There is also a transparent `lg_redesign_hitbox.png` used only for the larger card click areas.

## Actual background

Open:

`nexo/pack/assets/lumaguilds/textures/gui/guild_redesign_bg_6_row.png`

The corresponding Nexo glyph is configured in:

`nexo/glyphs/lumaguilds_redesign.yml`

Current glyph settings:

```yaml
guild_redesign_bg_6_row:
  texture: lumaguilds:gui/guild_redesign_bg_6_row
  ascent: 13
  height: 256
```

The inventory title composes it with live text using MiniMessage font shifts.

## Nexo symbol definitions

`nexo/items/lumaguilds_redesign.yml` defines the eight category items. Each uses:

- `material: PAPER`
- `Pack.oversized_in_gui: true`
- a `lumaguilds/gui/...` item model
- a Nexo item-model component

The model JSON files under `nexo/pack/assets/lumaguilds/models/gui/` use a `2.0 x 2.0` GUI scale. The symbol can therefore be larger than a vanilla slot while the background remains responsible for the actual card shape.

## Shared visual system

`VISUAL_SYSTEM.md` defines the rules used by Guild Home and the remaining menu redesign:

- common pixel density and outline weight
- common navy/teal/gold/red palette
- shared header/footer and navigation locations
- category-grid, dense-list, detail/settings and confirmation/danger menu families
- background-owned panels with transparent icon symbols
- equal feature hierarchy for Java and Bedrock

The deeper live menus are backend destinations during migration; their **final visual treatment must be converted to these shared menu families** rather than preserving their current one-off layouts. A finished redesign therefore means the system carries through member lists, ranks/permissions, bank/vault, progression/quests, homes, diplomacy/war, parties/LFG, customization, settings and confirmation screens.

## Viewing the final real composition

For the exact visual result rather than the source pieces:

1. Copy `nexo/items/lumaguilds_redesign.yml` into the server's `plugins/Nexo/items/` directory.
2. Copy `nexo/glyphs/lumaguilds_redesign.yml` into `plugins/Nexo/glyphs/`.
3. Copy `nexo/pack/assets/lumaguilds/` into `plugins/Nexo/pack/assets/lumaguilds/`.
4. Rebuild/reload the Nexo resource pack and reconnect with the generated pack active.
5. Run the branch build and open `/guild` on a Java client.
6. Repeat on Bedrock through the production Geyser + Nexo Scaffolding stack before visual sign-off.

That in-game view is the authoritative full preview. Vanilla fallbacks keep the menu usable if the Nexo pack is absent, but they do not reproduce the custom art direction.
