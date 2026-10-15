# LumaGuilds `main` guild-role SHADOW promotion: blocked preflight

**Date:** 2026-10-15
**Coordinator:** [EnthusiaStaff #264](https://github.com/wsg138/EnthusiaStaff/issues/264)
**Target:** `wsg138/LumaGuilds` `main` at `daedf8cbdc2374ad34a7a5dc5938351cbffde548`
**Reference:** `integration/enthusia-discord-platform` at `6a0f279aa0fe0e6e1a261d9fd8ca1d7dc6630b4f` (PRs [#6](https://github.com/wsg138/LumaGuilds/pull/6) and [#11](https://github.com/wsg138/LumaGuilds/pull/11))
**Disposition:** **BLOCKED for code promotion**. No role writer, managed-role publisher, schema, runtime setting, or operational change is introduced by this review.

## Why the existing publisher cannot be cherry-picked

At the verified refs, integration is **165 commits ahead, 0 behind** `main`. It includes unrelated guild gameplay, menus, progression, resource packs, migrations, dependencies, and CI changes. Merging the branch wholesale would violate the narrow-promotion requirement.

More importantly, the current `main` tree **does not contain the legacy guild Discord-role feature** that the publisher observes. Its integration-only prerequisites include:

| Prerequisite | Integration location | Why it matters |
| --- | --- | --- |
| Live DiscordSRV writer | `application/services/GuildDiscordRoleService.kt`, `infrastructure/services/DiscordSrvGuildRoleGateway.kt`, `infrastructure/listeners/GuildDiscordRoleListener.kt` | Defines the existing role eligibility, update/delete behavior and role ID authority. Importing it into current `main` would introduce a **new live writer**, not preserve an unchanged writer. |
| Durable exact legacy role IDs | `application/persistence/GuildDiscordRoleRepository.kt`, `domain/entities/GuildDiscordRoleLink.kt`, `infrastructure/persistence/guilds/GuildDiscordRoleRepositorySQL.kt`, `infrastructure/persistence/migrations/GuildDiscordRoleSchema.kt` | The SHADOW publisher calls `repository.get(guildId)` to supply the exact `discordRoleId`. No equivalent source is present on `main`. Role names are **not** identity proof. |
| Guild-role policy/config | `config/MainConfig.kt`, `infrastructure/services/ConfigServiceBukkit.kt`, `resources/config.yml` | `main` lacks `discordGuildRoles` and `discord.guild_roles.enthusia_shadow_enabled`. The integration configuration defaults the SHADOW flag off. |
| DI and lifecycle | `di/Modules.kt`, `LumaGuilds.kt` | Integration wires a writer, subscriptions, persisted links and a separate SHADOW runtime. That wiring is not in `main`. |
| Neutral managed-role adapter | `application/services/GuildDiscordRoleBackend.kt`, `GuildDiscordRoleShadowPublisher.kt`, `infrastructure/services/EnthusiaGuildRoleBackend*.kt`, `infrastructure/listeners/GuildDiscordRoleShadowRuntime.kt` | These are downstream of the missing role writer, link store and config, not self-contained additions. |

The `main` `GuildService`, `MemberService`, and guild lifecycle events are present, but they do **not** reconstruct a legacy Discord role ID or create the absent role ownership records. Publishing claims without exact IDs would be potentially `ROLE_ID_UNBOUND`, not a meaningful parity check. A speculative name-based lookup, fake role ID, or imported empty table is unacceptable.

**Source-versus-production caveat:** This is a comparison of GitHub refs, not a claim about the bytecode or schema currently installed on Bloom. An owner-approved, read-only production source/JAR/schema inventory is needed before naming either branch as the authoritative deployed writer.

## A second correctness issue in the integration candidate

The live integration `GuildDiscordRoleService.reconcileGuild` checks `progressionRepository.getGuildProgression(...)` (falling back to `guild.level`) and withholds a new Discord role until `minimumLevel` (default **50**) unless the guild has a previously saved role link; previously unlocked roles persist after prestige.

The integration `GuildDiscordRoleShadowPublisher.reconcileGuild` currently submits claims for **every** existing guild whenever the global guild-role and SHADOW flags are enabled. It does not apply the matching level/unlock check. That can produce incorrect expected role counts and ID-unbound claims for never-unlocked guilds. Future promotion must share/test the writer's actual policy without changing gameplay. Ineligible never-unlocked guilds must not be counted as existing Discord roles.

Also verify restart completeness: the integration publisher's `reconcileAll` iterates current guilds, not deleted guild/link records. Event-time `deleteGuild` publishes tombstones, but a disband while the publisher is unavailable followed by a restart may require a durable tombstone/recovery mechanism. Prove this against the platform's persistence semantics rather than assuming deletion convergence.

## CI and dependency compatibility

Do not copy integration's entire build toolchain into current `main`:

- `main`: Kotlin **2.0.0**, Paper **1.21.11**, Java **21**; GitHub `Unit Test Gradle` builds pinned RoseChat pre-hook `3fe078a`, PlayTime API `2a5b57d`, and CombatLogX API `4812e85af1264ebb27da481b9d9cbf8de0956e53`.
- Integration: Kotlin **2.3.20**, Paper **26.2**, Java **25**, RoseChat official `179483128fa2020f3ad8b0e4192ada3d0a251c49`, Staff neutral API `d978c32e665f72ab0a81ec0c035f8f9dfc3117e7`, plus `ci/rosechat/simpleclans.init.gradle`.
- The API must remain `compileOnly` plus test dependency, **not shaded**; pin and build the exact `discord-platform-api` project from approved EnthusiaStaff source for whichever target toolchain is eventually selected.
- A clean local build may be blocked if `libs/` lacks real pinned `CombatLogX-api.jar`, `RoseChat-RC-2.jar`, `EnthusiaPlaytime-api.jar`, or `EnthusiaStaff-discord-platform-api.jar`. Rebuild from the exact source pins in the selected CI workflow. Never fabricate placeholder JARs or label missing dependencies as a regression.

## Smallest safe PR sequence (coordinator approval required)

1. **Resolve baseline ownership first.** Independently identify the exact source commit of the *currently installed* LumaGuilds JAR, its legacy Discord role writer, configured role-unlock rules, and the persisted role-ID schema using read-only evidence. If the current live writer is not on `main`, the coordinator must either designate an appropriate reviewed base or explicitly approve a separate legacy-feature adoption PR. **Worker A cannot silently add a new live writer or unapproved database migration to `main`.**
2. **Precursor role-ID/policy contract (only if already authorized).** Carry the existing writer and persisted exact mapping into the selected base *without changing its already-live semantics*. Gate schema compatibility and reject missing/stale IDs as incomplete parity; document how existing saved links are retained. If this necessitates adding role creation to `main` or a schema migration, treat that as a separately approved change, not part of a SHADOW-only PR.
3. **Narrow SHADOW-only publisher PR.** Port only `GuildDiscordRoleBackend`, publisher, lazy factory/neutral backend, independent runtime, opt-in config, proportional DI/lifecycle wiring and focused tests/docs. Reconcile eligibility with the established writer. On provider failure, count/report incomplete scans without impacting guild gameplay; never write Discord roles from the SHADOW path. Preserve namespace `luma-guilds`, key `guild:<uuid>`, canonical Minecraft UUID sets, and exact saved Discord role-ID hints. Handle stale-provider reload, missing mappings, and orphan/disband tombstones explicitly.
4. **Verify and stage separately.** Require clean build, `./gradlew test`, scoped tests, `git diff --check`, zero new valid hosted analyzer findings, and green hosted checks at the **exact PR head**. Compare repeated nonempty StaffBot SHADOW scans and legacy DiscordSRV behavior *only after separately authorized private staging*. Do not deploy or activate any writer through this PR.

## Required regression and acceptance matrix for the future SHADOW PR

- Guild creation below/at threshold; prestige level reset after unlock; rename; join/remove; disband; startup/periodic full scan; orphan and missed-disband recovery.
- Exact persisted role-ID reuse; absent/invalid mapping fails closed; same-name roles do not substitute for IDs; two namespaces claiming the same role ID produce ownership conflict.
- Multiple Minecraft UUIDs linked to one Discord account retain correct union; unlink/relink and stale-link churn must not revoke an otherwise eligible member.
- Empty guild member set publishes an **intentional complete empty desired state**, not a failed partial snapshot; failed member repository read produces no partial publication.
- Synchronous guild listing/member/config/backend errors and asynchronous reject/retry/outage/exception results become counted failed/incomplete probes; an isolated guild failure does not cancel healthy guilds.
- Lazy provider registration after startup, disabled/reloaded Staff plugin, classloader/provider change, repeated start/close, and failed periodic scans.
- No SHADOW call may invoke DiscordSRV role creation/grant/revoke/delete or an `AUTHORITATIVE`/`ENFORCE` transport.
- For real parity, require `sample_gate=ZERO_DRIFT_SAMPLE`, `complete=true`, `invalid_claims=0`, `unbound_role_claims=0`, `conflicting_role_ids=0`, `drift=0`, appropriate positive `luma_claims`, and documented PlayTime counts across repeated stable windows; `NO_CLAIMS` or partial results do not pass.

## Operational prohibition and rollback

No Bloom upload, restart, production command, role grant/revoke, credential access, database migration, DiscordSRV removal, or writer cutover is approved here. An eventual SHADOW-only candidate must be off by default. Its immediate safe rollback is disabling the SHADOW flag/reverting the candidate **without changing legacy DiscordSRV**, followed by approved ordinary plugin lifecycle management. A rollback cannot repair a missing role ID or silently certify incomplete parity.

**Decision requested from the primary coordinator:** Identify/approve the authoritative deployed legacy-role baseline and authorize or reject the missing-writer precursor before Worker A attempts further source promotion. The current review PR is intentionally documentation-only.
