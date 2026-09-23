# LumaGuilds testing guide

This document explains where LumaGuilds tests live, how to run them, what the current test-hardening branch adds, and how to interpret failures.

## Where tests belong

Handwritten LumaGuilds unit, domain, application, persistence, architecture, and menu tests live in this repository under `src/test/kotlin/`.

Sentinel Sim is an additional runtime/compatibility layer. It is not the home for normal LumaGuilds regression tests and does not replace this repository's test suite.

## Current domain-contract hardening

The owner-directed test-hardening branch adds focused tests under:

- `src/test/kotlin/net/lumalyte/lg/domain/values/ClaimPermissionTest.kt`
- `src/test/kotlin/net/lumalyte/lg/domain/values/FlagTest.kt`
- `src/test/kotlin/net/lumalyte/lg/domain/values/ChatChannelContractTest.kt`

They protect contracts that previously had no direct test coverage:

- the explicit claim-permission enum surface;
- unique and correctly derived localization keys for every claim permission;
- the explicit non-player claim-flag surface;
- unique and correctly derived localization keys for every claim flag;
- the four chat-channel enum values;
- RoseChat channel IDs shared by commands/listeners;
- default and explicit chat-visibility state;
- default and explicit announcement/ping rate-limit state.

These are contract tests, not a claim that all guild behavior is covered. LumaGuilds already has separate tests for progression, quests, guild entities/services, repositories, architecture and selected GUI behavior.

## Active GUI work boundary

At the time this guide was added, GUI redesign PRs #2 and #3 were active. This hardening branch intentionally does **not** change:

- `src/main/**`;
- menu classes;
- GUI assets;
- i18n references;
- `Guild.kt`;
- `.github/workflows/**`;
- Gradle dependencies/build configuration.

Workers on GUI PRs should not copy these tests into their branches manually. Reconcile the final merged `main` first, then let normal Git history carry the tests forward.

## Running focused tests

Linux/macOS:

```bash
./gradlew test --tests 'net.lumalyte.lg.domain.values.ClaimPermissionTest' \
  --tests 'net.lumalyte.lg.domain.values.FlagTest' \
  --tests 'net.lumalyte.lg.domain.values.ChatChannelContractTest'
```

Windows PowerShell:

```powershell
./gradlew.bat test --tests 'net.lumalyte.lg.domain.values.ClaimPermissionTest' --tests 'net.lumalyte.lg.domain.values.FlagTest' --tests 'net.lumalyte.lg.domain.values.ChatChannelContractTest'
```

## Running the complete unit suite

```bash
./gradlew test
```

Use the repository's existing GitHub Actions workflows for exact-head CI evidence. Do not report an older SHA, skipped job, cancelled job, or zero-step runner failure as a passing result.

## Result locations

Gradle writes local results to:

- `build/test-results/test/` — JUnit XML;
- `build/reports/tests/test/` — HTML test report.

GitHub Actions is the durable source for PR-head validation.

## Interpreting the new failures

### Claim-permission or flag surface mismatch

A value was added, removed, or renamed. Do not automatically edit the expected set. First verify the product change is intentional and that listeners/services using the new permission or flag are also tested.

### Localization-key mismatch

The enum name and its `nameKey`/`loreKey` no longer correspond, or keys were duplicated. Treat this as a user-facing contract issue; verify locale resources and consumers before changing the test.

### RoseChat channel ID mismatch

These constants must match the configured RoseChat channel IDs. Coordinate any intentional change with RoseChat configuration and the relevant commands/listeners. Do not weaken the assertion just to accept arbitrary strings.

### Chat state/default mismatch

A default visibility or rate-limit value changed. Review migration/backward-compatibility impact and every persistence/command consumer before accepting a new default.

## Adding tests for future changes

When a feature changes:

1. put deterministic behavior tests close to the owning domain/application class;
2. cover negative and boundary paths, not only success;
3. add persistence tests for database/schema behavior;
4. use MockBukkit only where Bukkit lifecycle behavior is actually needed;
5. keep real-server/provider behavior in an appropriate integration/runtime lane;
6. avoid real player data, production databases, tokens, or private configuration;
7. run focused tests, then the full suite;
8. record exact-head CI evidence in the PR.

For menu/GUI changes, test externally meaningful behavior such as inventory actions, permissions, state transitions, Bedrock fallback, and resource-contract assumptions rather than snapshotting implementation details.

## Reviewing a test PR

Reviewers should verify that:

- the test fails for the intended regression;
- assertions prove a meaningful public/domain contract;
- a filename-only or tautological test was not added merely to satisfy coverage;
- active PR ownership is respected;
- build/workflow rules are not weakened;
- product changes are not hidden inside a test-hardening PR;
- final CI belongs to the exact reviewed head.

## Sentinel boundary

Use Sentinel for built-plugin loading, dependency combinations, runtime action sequences, restart behavior, compatibility probing, and real-Paper-style evidence. Keep pure guild/domain/application regression tests in LumaGuilds itself.
