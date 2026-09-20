# LumaGuilds Visual System

This redesign is one UI system, not a collection of individually illustrated buttons.

## Core rule

The **GUI background owns structure**. Frames, panels, button wells, separators, header/footer areas and navigation chrome belong to the background/template. Nexo item textures are transparent symbols placed into those structures.

Do not bake a second card frame, label, title, or unrelated backdrop into an icon texture.

## Visual language

- Pixel-art only; no anti-aliased/vector-looking edges.
- One common virtual pixel density across every icon.
- One dark outline family: near-black/navy, never pure random black outlines of different widths.
- One lighting direction: upper-left highlight, lower-right shadow.
- Primary structural palette: deep navy/blue-gray.
- Interaction accent: restrained teal/cyan.
- Attention/reward accent: warm gold.
- Destructive accent: muted red.
- White/off-white is reserved for readable highlights and status contrast.
- Icons should read clearly before detail is added. Prefer one strong metaphor over several overlapping objects.
- No icon should contain baked text. Live text remains Minecraft/Adventure text.

## Scale and spacing

- Primary category symbols: 64x64 source texture with transparent padding.
- The visible symbol should occupy roughly the same visual mass in every category.
- Primary dashboard cards use 2x2 logical click regions.
- The background renders the complete card well; the icon is centered within it.
- Neighboring invisible hitbox items make the whole visual card clickable.
- Status and footer controls use the same frame geometry and padding language as category cards.

## Dashboard template

The six-row Guild Home has:

1. integrated status/header strip
2. four primary cards
3. continuation of first card row
4. four primary cards
5. continuation of second card row
6. integrated footer/navigation strip

The eight task symbols are:

- Members & Ranks: grouped-member/rank insignia
- Money & Vault: coin/vault mark
- Level & Quests: progression star/rank mark
- Homes & Land: guild-home silhouette
- Allies & War: diplomacy/war crest
- Parties & LFG: grouped-party mark
- Customize Guild: guild-identity/style crest
- Guild Settings: mechanical/settings mark

The icon metaphor must be recognizable without its label; however, the label remains real GUI text rather than pixels baked into the PNG.

## Other menu families

Every player-facing LumaGuilds menu should eventually use one of these shared families rather than inventing its own frame/layout.

### Category grid
For feature collections such as Money & Vault or Allies & War.
- same header/footer chrome as Guild Home
- smaller integrated wells for feature entries
- current state shown next to the action, not hidden in long lore

### Dense list
For members, ranks, transaction history, wars, requests, parties and similar data.
- maximize usable rows
- one consistent list-row treatment
- pagination always in the same footer positions
- selecting a row opens a detail screen rather than duplicating actions in the list

### Detail / settings
For one member, rank, guild home, war, relation, bank setting, etc.
- identity/status area at top
- related actions grouped by intent
- destructive actions visually isolated

### Confirmation / danger
For leave, disband, kick, ownership transfer and similar actions.
- dedicated muted-red visual treatment
- explicit consequence text
- confirm and cancel positions remain consistent everywhere

## Bedrock

Bedrock must receive the same feature hierarchy and capability as Java. The target is the same inventory architecture through Geyser plus Nexo Scaffolding visual translation. If a visual effect cannot be translated exactly, simplify the art for both platforms or provide a visually equivalent Bedrock treatment; never remove the underlying feature from Bedrock.

## Review rule

Do not approve an individual icon in isolation. Review the background and complete icon set together at actual Minecraft GUI scale. If one asset needs a different outline, perspective, card frame, palette, or rendering style to look good, the asset should be redesigned to fit the system rather than creating a one-off exception.
