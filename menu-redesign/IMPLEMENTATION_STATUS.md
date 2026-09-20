# Full menu redesign implementation status

The redesign is a complete UI-system migration, not only a dashboard reskin.

## Implemented architecture
- Player-intent Guild Home and Feature Index.
- Shared Home / Grid / List / Detail / Danger visual-surface model.
- Dense member directory and contextual member profile.
- Dense rank hierarchy.
- Rank detail screen organized around player-facing permission groups.
- Focused per-group permission toggle screen.
- Bedrock enters the same feature hierarchy as Java.

## Permission groups
1. Members & Ranks
2. Guild Appearance
3. Homes & Territory
4. Relations & War
5. Parties
6. Bank
7. Physical Vault
8. Communication
9. Advanced

## Migration still required before merge
Existing feature implementations for economy/vault, progression/quests, homes/land, relations/war, parties/LFG, customization/settings and confirmations must use the same shared surface/navigation rules. Existing behavior should be preserved while their presentation is migrated. Placeholder/chat-only pseudo-destinations must not remain as final player-facing UX.

## Validation
- Gradle/locale contracts must remain green after each migration batch.
- Java resource-pack alignment must be verified in client.
- Bedrock feature parity and Nexo Scaffolding rendering must be verified on a Bedrock client before merge.
