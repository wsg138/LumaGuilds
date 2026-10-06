# Enthusia Discord Platform Migration

Tracking umbrella: `wsg138/EnthusiaStaff#264`.

## Goal

Migrate LumaGuilds Discord role management away from DiscordSRV and onto the provider-neutral Enthusia Discord platform without changing guild business rules.

## Current boundary

LumaGuilds currently owns guild-role policy and persists the Discord role ID for each guild. DiscordSRV is used as the infrastructure adapter for account-link lookup and Discord role creation/mutation.

The migration must keep the existing application boundary (`DiscordGuildRoleGateway`) and replace only the infrastructure implementation once the Enthusia platform contract is stable.

## Target design

- LumaGuilds continues deciding when a guild role should exist and which Minecraft players should hold it.
- LumaGuilds does not own a Discord Gateway/JDA connection.
- LumaGuilds does not depend on DiscordSRV after cutover.
- LumaGuilds does not depend on EnthusiaStaff moderation internals.
- The Enthusia Discord platform resolves Minecraft UUIDs through canonical Enthusia account links and performs Discord role mutations.
- Managed role ownership is namespaced so LumaGuilds can only mutate roles it owns.
- Unmanaged Discord roles are never removal candidates.

Expected adapter direction:

```text
GuildDiscordRoleService
        |
DiscordGuildRoleGateway
        |
EnthusiaDiscordGuildRoleGateway
        |
provider-neutral Enthusia Discord platform contract
```

## Compatibility requirements

The replacement must preserve the observable behavior of the existing DiscordSRV gateway:

- ensure/create the configured guild role;
- grant the role for a Minecraft UUID;
- revoke the role for a Minecraft UUID;
- revoke unexpected members during reconciliation;
- delete a Luma-owned role when appropriate;
- handle unavailable Discord infrastructure without breaking unrelated guild behavior;
- retain current serialization/reconciliation protections against role-update races.

One Discord account may be linked to multiple Minecraft accounts. Desired membership must therefore be calculated without accidentally revoking a role while another linked Minecraft account still qualifies.

## Checkpoint 1: provider-neutral unlink identity

The first migration checkpoint intentionally leaves DiscordSRV operational while removing a provider-specific identity leak from application orchestration.

- `DiscordAccountReference` is a bounded printable opaque account reference; LumaGuilds does not parse provider-specific identity formats.
- `GuildDiscordRoleService` now performs unlink cleanup through the opaque reference overload.
- `DiscordGuildRoleGateway` exposes `revokeRoleForAccount(...)` as the provider-neutral cleanup seam.
- the DiscordSRV adapter alone unwraps the reference into its legacy Discord snowflake;
- the raw `String` cleanup overload remains temporarily as a compatibility seam for existing callers/test doubles and will be removed after consumers migrate;
- focused validation covers opaque-value preservation and malformed/unbounded reference rejection.

This checkpoint does **not** add an Enthusia transport client, change the configured provider, or authorize DiscordSRV removal.

## Checkpoint 2: provider-neutral managed-role SHADOW publication

Implemented on the managed-role migration branch after EnthusiaStaff #342 merged.

- LumaGuilds keeps DiscordSRV as the live role writer during the migration window.
- `GuildDiscordRoleShadowPublisher` publishes complete guild membership snapshots through the shared `ManagedRolePlatform`.
- The provider namespace is `luma-guilds`; each guild uses the stable local key `guild:<guild-uuid>`.
- Claims contain Minecraft UUID membership only. LumaGuilds never receives or resolves Discord user IDs.
- The persisted DiscordSRV role ID is supplied as an exact migration hint so StaffBot compares against the real legacy role instead of adopting a same-name role.
- Startup/periodic reconciliation plus guild create, rename, member join/remove, and disband lifecycle events refresh the shadow state.
- One guild's synchronous publication failure is isolated and counted instead of aborting the entire full pass.
- The lazily loaded Enthusia backend is invalidated when EnthusiaStaff is disabled/reloaded, avoiding stale classloader/provider reuse.
- Closing the shadow runtime unregisters Bukkit event handlers before the same instance can be started again.
- SQLite rehearsal snapshots use SQLite itself so committed WAL state is included in migration verification.

### Enablement

The Luma side is opt-in and defaults off:

```yaml
discord:
  guild_roles:
    enthusia_shadow_enabled: true
```

The StaffBot managed-role SHADOW runtime must also be enabled separately. DiscordSRV must remain installed and authoritative while parity is being collected.

### Acceptance gate

Do not switch writers from this checkpoint alone. Require repeated complete StaffBot summaries with no unexplained drift:

```text
managed_role_shadow_summary complete=true ... drift=0 ... invalid_claims=0
```

Also investigate any `managed_role_shadow_drift`, `managed_role_shadow_incomplete`, or `managed_role_shadow_cycle_failed` records before cutover. A zero-drift SHADOW pass is migration evidence only; it does not authorize DiscordSRV removal or generic managed-role enforcement.

## Migration sequence

1. **Complete:** keep the existing DiscordSRV implementation working while the Enthusia platform contract is introduced.
2. **Complete for SHADOW:** publish complete desired guild-role membership through the Enthusia managed-role platform while DiscordSRV remains the writer.
3. **Complete:** cover provider rejection/unavailability and migration lifecycle behavior with focused tests, including per-guild failure isolation.
4. **Next operational gate:** deploy the reviewed Luma build with SHADOW enabled together with StaffBot managed-role SHADOW and collect repeated complete zero-unexplained-drift scans.
5. After parity is explicitly accepted, implement/authorize the writer cutover so the shared Enthusia platform owns actual Discord role reconciliation.
6. Remove direct DiscordSRV/JDA dependencies and legacy account-link compatibility only after the replacement writer is accepted.
7. Physical DiscordSRV removal remains governed by EnthusiaStaff #264/#268 and the other network consumers; Luma parity alone is not sufficient.

## Non-goals for this PR

- Do not redesign guild progression or role-unlock policy.
- Do not move guild business logic into EnthusiaStaff.
- Do not make Discord roles authoritative for Minecraft permissions or staff authorization.
- Do not remove DiscordSRV from the network as part of the initial Luma adapter PR.
- Do not perform production Discord mutations during development.

## Quality gates

Before merge:

- full Gradle build/tests pass;
- focused role synchronization tests cover success, rejection, retry/outage, reconciliation conflict, and multi-linked-account behavior;
- changed methods remain within project complexity limits where practical;
- provider-specific infrastructure remains separated from application/domain policy;
- no internal Enthusia persistence records are exposed across the public contract;
- hosted analyzers report zero new valid findings.
