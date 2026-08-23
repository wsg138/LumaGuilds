# Enthusia SMP deployment

The main README describes the full LumaGuilds product. This file records the important differences in Enthusia SMP's current production configuration so future wiki tooling does not advertise disabled or default-only functionality.

## Claims are disabled on Enthusia

The current live configuration has:

```yaml
claims_enabled: false
```

Therefore LumaGuilds' standalone bell/partition land-claiming system must **not** be documented as an active Enthusia SMP player feature. The generic README describes the product's claim system because LumaGuilds supports it, but Enthusia currently uses LumaGuilds primarily for guild functionality/integrations rather than player land claims.

## Guild limits and basics

Current production configuration includes:

- guild-name length: **5-32 characters**
- maximum configured guild count: **1000**
- maximum members per guild: **20**
- maximum custom ranks: **10**
- guild creation cost: **0**
- peaceful/hostile guild-mode switching: **disabled**

The guild-name filter/normalization system is enabled. Exact blocked-pattern lists are moderation configuration and should not be copied into public wiki content.

## Guild homes

The current production configuration has:

- home teleport cooldown: **5 seconds**
- home set cooldown: **10 minutes**
- home teleport warmup: **3 seconds**
- teleport safety checks: **enabled**

If the deployed build/config provides multiple guild-home slots, the exact slot limit should be taken from the current command/runtime implementation rather than inferred from old generic README text.

## Guild bank

The configured guild bank includes:

- minimum deposit: 1
- maximum single deposit: 100,000
- maximum single withdrawal: 50% of bank balance
- daily withdrawal limit: 50,000
- 1% configured deposit fee (capped at 128)
- 2% configured withdrawal fee (capped at 15)
- 0.5% configured interest per 24-hour compound period
- maximum virtual bank balance: 1,000,000
- transaction/audit tracking

These are current configuration values, but future wiki text should verify which fee/interest paths are actively used by the deployed build before presenting every numeric bank rule as player-facing policy.

## Physical guild vault

The current deployment uses `bank_mode: BOTH`, so the configuration supports both the virtual guild balance and a physical guild-vault chest.

Current vault behavior/configuration includes:

- physical vault enabled,
- 5-second break-warning/confirmation window,
- capacity scaling enabled,
- base capacity 9 slots,
- maximum capacity 54 slots,
- valuable-item transaction/flush safeguards,
- transaction logging.

The current config is set to drop vault items on break/explosion rather than preserve them in the database. Future safety changes should update this document when that behavior changes.

## Guild roles

The deployment maps guild roles to granular territory/action permissions. Built-in role names include Owner, Co-Owner, Admin, Mod and Member, with decreasing access. Custom ranks are also supported up to the configured limit.

For public documentation, explain that guild owners can assign ranks/permissions rather than publishing every internal permission enum unless a detailed guild-permissions page is needed.

## Guild banners

Banner copying is enabled. Current configuration charges the guild bank by default and has a configured copy cost. The generic plugin also supports physical-currency modes depending on economy setup.

## Cross-platform support

LumaGuilds contains both Java inventory UI and Bedrock/Floodgate form paths. Enthusia supports Java and Bedrock players, so guild documentation should avoid Java-only instructions when a Bedrock-specific form exists.

## Integrations used by Enthusia

Other Enthusia systems use LumaGuilds as the guild/team authority. Examples include KOTH guild mode and other guild-aware mechanics. Those integrations should call LumaGuilds rather than maintaining a second guild membership database.

## Source-of-truth rule

For future wiki generation:

- use this file and the latest `enthusia-server-state` snapshot for **what Enthusia currently enables**,
- use the main README/docs for the broader LumaGuilds product,
- do not advertise the bell-based claims system on Enthusia while `claims_enabled` remains false.