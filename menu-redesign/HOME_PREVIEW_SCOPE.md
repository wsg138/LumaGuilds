# Guild Home focused preview

This branch intentionally contains only the Guild Home visual/navigation preview plus the Nexo asset required to render it.

The goal is to let the main home screen be judged in Minecraft before continuing the full menu migration.

## What changed in this rebuild

- the old standalone category icons/models were removed
- the old transparent hitbox item was removed
- the entire top Guild Home presentation is now one integrated Nexo glyph texture
- the frame, eight card wells, Minecraft-themed artwork and labels are baked into that single skin
- the Bukkit inventory slots remain empty and act only as click coordinates
- no deeper LumaGuilds screen is part of this visual sign-off

## What to judge

- whether the top inventory looks like one custom GUI instead of a skinned chest
- overall visual cohesion
- icon/art readability
- card spacing and hierarchy
- whether the eight categories feel obvious
- whether the 2x2 click areas feel natural
- Java rendering first; Bedrock parity can be checked after the Java skin is approved

## Install the Nexo preview asset

Copy:

- `menu-redesign/nexo/glyphs/lumaguilds_home_preview.yml` to `plugins/Nexo/glyphs/`
- `menu-redesign/nexo/pack/assets/lumaguilds/` to `plugins/Nexo/pack/assets/lumaguilds/`

There is intentionally no Nexo item config for this rebuild.

Rebuild/reload the Nexo resource pack and reconnect before opening `/guild menu`.
