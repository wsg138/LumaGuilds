# LumaGuilds menu redesign — source preview

This page previews the **real files used by the redesign**. It is intentionally not a generated GUI mockup.

A pixel-perfect full preview must be viewed in Minecraft because the final composition depends on the Minecraft chest GUI, the Nexo background glyph, the oversized item models, font shifts, and client GUI scale. The files below are the exact source assets used to produce that view.

## Main Guild Home slot map

The dashboard is a 6-row chest (`9 x 6`). The eight main cards use Nexo models scaled to visually cover a `2 x 2` area; transparent hitbox items make all four slots clickable.

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

`Menu Guide` is informational. `Feature Index`, `Members`, every task card, and `Close` are interactive.

## Actual icon PNGs

Open these files in GitHub to see the exact icons, at their native resolution:

- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_members.png` — Members & Ranks
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_money.png` — Money & Vault
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_level.png` — Level & Quests
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_homes.png` — Homes & Land
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_allies.png` — Allies & War
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_parties.png` — Parties & LFG
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_customize.png` — Customize Guild
- `nexo/pack/assets/lumaguilds/textures/gui/icons/lg_redesign_settings.png` — Guild Settings

There is also a transparent `lg_redesign_hitbox.png` used only for the large-card click area.

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

The inventory title composes it with the screen title using MiniMessage font shifts.

## Nexo card definitions

`nexo/items/lumaguilds_redesign.yml` defines the eight card items. Each card uses:

- `material: PAPER`
- `Pack.oversized_in_gui: true`
- a `lumaguilds/gui/...` item model
- a Nexo item-model component

The model JSON files under `nexo/pack/assets/lumaguilds/models/gui/` use a `2.0 x 2.0` GUI scale so the visible card spans more than one normal inventory slot.

## What the next screens contain

The home screen is deliberately not a wall of every individual feature. A card opens a short, task-focused index whose buttons route into the existing live feature menus.

- **Members & Ranks:** member directory/management, invite, ranks & permissions, promotion/demotion, moderation, join requirements.
- **Money & Vault:** bank, deposit/withdraw, physical vault, transactions, contributions, budget, automation, security, statistics.
- **Level & Quests:** progression/XP, weekly quests, perks, next rewards, XP sources, statistics.
- **Homes & Land:** homes, access, ally-home access, tracking, claims, trust/flags.
- **Allies & War:** relations, allies, enemies, truces, requests, war declaration, active wars/history, peace.
- **Parties & LFG:** party management, creation, requests, LFG, chat/moderation/access tools.
- **Customize Guild:** description, tag, emoji, banner, menu theme/appearance.
- **Guild Settings:** information, open/closed, peaceful/hostile, join requirements, integrations, ownership tools, leave, disband.

## Viewing the final real composition

For the exact visual result rather than the source pieces:

1. Copy `nexo/items/lumaguilds_redesign.yml` into the server's `plugins/Nexo/items/` directory.
2. Copy `nexo/glyphs/lumaguilds_redesign.yml` into `plugins/Nexo/glyphs/`.
3. Copy `nexo/pack/assets/lumaguilds/` into `plugins/Nexo/pack/assets/lumaguilds/`.
4. Rebuild/reload the Nexo resource pack and reconnect with the generated pack active.
5. Run the branch build and open `/guild` on a Java client.

That in-game view is the authoritative full preview. The vanilla fallbacks keep the menu usable if the Nexo pack is absent, but they do not reproduce the custom art direction.
