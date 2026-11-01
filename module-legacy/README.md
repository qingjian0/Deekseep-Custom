# Universal Xposed Adapter

This internal adapter builds the domestic and Google Play universal APKs for
traditional Xposed-compatible environments.

The universal APKs do not maintain a second feature fork. `build.sh`
compiles the canonical sources under `../module/src/ds/dsh/DeepSeek`, generates
a traditional `handleLoadPackage` entry from canonical `Main.java`, and bridges
its around-hook contract through `compat/LegacyXposedModule.java`.

`src/de/robv/android/xposed` contains compile-only signatures supplied by the
real framework at runtime.

```bash
cd module-legacy
bash build.sh
bash test-adapter-regression.sh
```

The unrenamed output is an internal adapter APK. Use
`scripts/build-all-legacy.sh` from the repository root to build both domestic and
Google Play Universal validation APKs, run the canonical regressions, verify both
channel layouts, package the artifacts under `dist/`, and write `SHA256SUMS.txt`.
These artifacts use the shared local debug key; they are not formally release-signed.

For the standalone Compose Open build, use `scripts/build-all.sh`, which produces
an unsigned, zip-aligned APK for signing with your own key.
