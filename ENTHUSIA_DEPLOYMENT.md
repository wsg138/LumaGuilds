# Enthusia SMP deployment

The main README describes the full LumaGuilds product. This file records the important current Enthusia SMP deployment differences so player/wiki documentation does not advertise default-only or disabled functionality.

This repository is a fork of the canonical `BadgersMC/LumaGuilds` project. The values below are taken from Enthusia's latest sanitized production snapshot and checked against the current upstream implementation.

## Current status

LumaGuilds is installed on the Enthusia SMP and is the authoritative guild membership/rank/bank/vault provider used by other server systems.

The current production jar manifest contains LumaGuilds jars, including a newer `LumaGuilds-2.1.13.jar` alongside an older `2.1.10` copy. Because duplicate versions exist on disk, do not infer the loaded version from filename presence alone; current runtime/startup evidence should decide that if version-specific behavior matters.

## Claims are disabled on Enthusia

The live configuration has:

```yaml
claims_enabled: false
```

Therefore LumaGuilds' standalone bell/partition land-claiming system must **not** be presented as an active Enthusia player feature.

This disables the ordinary LumaGuilds claim workflow even though the generic README extensively documents it. Other plugins may still use guild membership/ranks independently of claims.

## Guild basics

Current live configuration includes:

- guild-name length: **5-32 characters**
- maximum configured guild count: **1000**
- maximum members per guild: **20**
- maximum custom ranks: **10**
- guild creation cost: **0**
- peaceful/hostile mode feature: **disabled**

The live guild-name filter is enabled. Its internal blocked-pattern list is moderation configuration and should not be copied into public documentation.

## Guild homes

Current live values:

- home teleport cooldown: **5 seconds**
- home set cooldown: **10 minutes**
- teleport warmup: **3 seconds**
- teleport safety checks: **enabled**

The exact number of available guild-home slots and any progression/rank-based slot rules should be taken from the current command/runtime implementation if a detailed home page is written; do not infer them from old design discussions.

## Ranks and permissions

LumaGuilds maps built-in and custom guild ranks to granular guild capabilities. The current live role mapping includes built-in Owner, Co-Owner, Admin, Mod and Member tiers with decreasing access.

Public documentation generally only needs to explain that guild owners can assign ranks/permissions. Internal permission enums can stay in the technical docs unless players need a detailed permission matrix.

## Guild bank

The live config contains the virtual bank system with these limits/settings:

- minimum deposit: **1**
- maximum single deposit: **100,000**
- maximum single withdrawal: **50% of balance**
- daily withdrawal limit: **50,000**
- configured virtual deposit fee: **1%**, capped at **128**
- configured virtual withdrawal fee: **2%**, capped at **15**
- configured interest rate: **0.5% per 24-hour compound period**
- maximum virtual balance: **1,000,000**
- 30-day audit retention

These values describe the live configuration. Before presenting every fee/interest rule as a guaranteed player mechanic, verify the exact deployed build path that consumes it.

## Physical guild vault

The live deployment has a physical guild-vault chest enabled and `bank_mode: BOTH`.

Current vault settings include:

- physical vault enabled,
- 5-second break-warning window,
- capacity scaling enabled,
- base capacity **9 slots**,
- maximum capacity **54 slots**,
- valuable-item flush/transaction safeguards,
- 30-day transaction-log retention,
- breaking/exploding the vault is configured to **drop items** rather than preserve them in the database.

That last behavior is riskier than the current upstream default, which recommends preserving items. Wiki text should describe the user-visible current behavior only if staff intentionally want players relying on it; operational docs should treat it as a configuration choice worth reviewing.

## Raw Gold physical-currency configuration mismatch

The live config currently contains both:

```yaml
vault:
  bank_mode: BOTH
  use_physical_currency: true
  physical_currency_material: RAW_GOLD
```

However, the current upstream `PhysicalCurrencyServiceBukkit.validateConfiguration()` explicitly rejects physical currency when `bank_mode` is anything other than `PHYSICAL`.

Therefore **do not advertise the guild bank/war system as currently using Raw Gold physical currency based on configuration alone**. The configuration is internally inconsistent with the current upstream validator. Treat physical-currency behavior as unverified until the deployed build/runtime confirms how it handles this mismatch or the config is corrected.

The configured physical-currency values, if/when valid, include:

- Raw Gold as the currency material,
- 1 Raw Gold = 1 currency unit,
- 0-item deposit fee,
- 1-item withdrawal fee,
- 10 Raw Gold configured daily war cost,
- 100 Raw Gold configured war-declaration cost,
- Raw Gold Blocks compress to 9 Raw Gold value.

These are configuration values, **not currently safe player claims** because of the validator mismatch.

## Guild banners

Banner copying is enabled. Current live values include:

- configured banner copy cost: **10**
- charge source: guild bank
- copy is not globally free
- physical-currency banner cost setting: **1 item** if the physical-currency path is actually valid/enabled.

Again, physical-cost behavior should not be published as live while the physical-currency configuration mismatch remains unresolved.

## Wars, parties and chat

The current upstream project supports wars, parties and guild/ally/party chat. The live config contains war/combat and party/chat settings, but public wiki pages should verify actual current command availability and integrations before promoting all generic README functionality.

Important current live configuration facts include:

- peaceful/hostile guild mode is disabled,
- guild/ally/party chat settings are present,
- war anti-farming/cooldown settings are present,
- claims remain disabled, so any war behavior described in terms of LumaGuilds claim territory must be checked carefully.

## Bedrock support

LumaGuilds includes Java inventory UI and Bedrock/Floodgate form paths. Enthusia supports Java and Bedrock players, so player documentation should provide equivalent Bedrock instructions when the UI differs rather than assuming Java-only inventory menus.

## Integrations

Other Enthusia systems use LumaGuilds as the guild/team authority. Examples include guild-owned market behavior and guild-aware KOTH/team mechanics.

Those integrations should read guild membership/ranks from LumaGuilds rather than maintaining a second guild database.

## Source-of-truth rule

For future Enthusia wiki generation:

- **software semantics:** current `BadgersMC/LumaGuilds` implementation,
- **current Enthusia toggles/numbers:** latest `enthusia-server-state` snapshot,
- **generic product features:** upstream README/docs,
- **current server claims:** disabled while `claims_enabled: false`,
- **physical Raw Gold guild-currency claims:** do not publish as live until the `bank_mode: BOTH` / `use_physical_currency: true` validation mismatch is resolved or runtime-confirmed.
