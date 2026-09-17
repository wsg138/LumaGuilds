# Guild Home focused preview

This branch intentionally contains only the Guild Home visual/navigation preview plus the Nexo assets required to render it.

The goal is to let the menu be judged in Minecraft before continuing the full menu migration.

## What to judge

- overall visual cohesion
- Minecraft-native icon style
- card spacing and hierarchy
- header/status readability
- whether the eight categories feel obvious
- whether the 2x2 card hit areas feel natural
- Java and Bedrock rendering parity

The deeper menus remain the existing live LumaGuilds screens on this branch. They are destinations only and are not part of this visual sign-off.

## Install the Nexo preview assets

Copy:

- `menu-redesign/nexo/items/lumaguilds_redesign.yml` to `plugins/Nexo/items/`
- `menu-redesign/nexo/glyphs/lumaguilds_home_preview.yml` to `plugins/Nexo/glyphs/`
- `menu-redesign/nexo/pack/assets/lumaguilds/` to `plugins/Nexo/pack/assets/lumaguilds/`

Rebuild/reload the Nexo pack and reconnect before opening `/guild`.

For Bedrock, test through the production Geyser + Nexo Scaffolding path so the same Guild Home inventory is rendered rather than the old reduced control-panel form.
