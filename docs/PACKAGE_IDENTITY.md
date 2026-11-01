# Package Identity Contract

This document is the canonical identity boundary for the Android module.

## Fixed identities

| Identity | Required value | Rule |
|---|---|---|
| Android module application ID | `ds.dsh.DeepSeek` | Keep stable for installed module identity and upgrades. |
| Android source namespace / Java package root | `ds.dsh.DeepSeek` | All first-party module Java/Kotlin sources and generated module classes use this root. |
| Relay package root | `ds.dsh.DeepSeek.relay` | Relay implementation is a child package of the module namespace. |
| DeepSeek host application ID | `com.deepseek.chat` | Host identity; never rename it to the module ID. |
| Xposed entry class | `ds.dsh.DeepSeek.Main` | Must match the class packaged in the APK and each `assets/xposed_init`. |
| Activation provider authority | `ds.dsh.DeepSeek.XposedService` | Preserve the exact existing authority until every producer/consumer is migrated together. |

## Migration requirements

- Source paths must mirror package declarations: `module/src/ds/dsh/DeepSeek/` and `module/src/ds/dsh/DeepSeek/relay/`.
- Manifest component names, reflection strings, generated source paths, D8 class collection paths, tests, CI checks, and packaging scripts must use the new module namespace.
- Both Legacy and Universal APKs must package `ds.dsh.DeepSeek.Main` as their Xposed entry.
- Compose launcher Gradle `namespace` and `applicationId` remain `ds.dsh.DeepSeek`.
- Do not replace host-side paths, package checks, process guards, or target matching for `com.deepseek.chat`.
- Historical migration notes may mention the old internal namespace, but executable source/configuration must not depend on it.

## Validation gates

1. Search all tracked text files for old package declarations, path fragments, class names, and stale build output paths.
2. Verify each Java/Kotlin package declaration agrees with its repository path.
3. Verify Manifest component names resolve to source classes.
4. Verify `assets/xposed_init` exactly names the compiled Xposed entry class.
5. Verify provider authority is identical across manifest declarations and every caller.
6. Build Legacy, Universal, and Compose launcher; inspect APK DEX, manifest, Xposed metadata, alignment, and signature.
7. Keep host-device LSPosed validation separate from source and CI validation.

A source-tree rename alone is not proof of a successful migration. Do not mark this migration complete until all validation gates have evidence.
