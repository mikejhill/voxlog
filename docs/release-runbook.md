# Release runbook

VoxLog ships through three channels, all from one signed, reproducible APK:

| Channel | Automation | Users install via |
|---|---|---|
| GitHub Releases | Fully automatic on a `v*` tag (`release.yml`) | Download, or Obtainium |
| Self-hosted F-Droid repo on GitHub Pages | Fully automatic on each release (`fdroid-repo.yml`) | F-Droid / Droid-ify client → add repo |
| Official F-Droid repo | Automatic after a one-time merge request: F-Droid's bot detects tags, rebuilds from source and verifies it matches our APK | F-Droid client |

Because builds are reproducible and F-Droid is configured with our signing certificate, F-Droid
publishes **our** signed APK. All three channels are therefore update-compatible.

---

## One-time setup

### 1. Create the release signing key

Run on a trusted machine. Keep the keystore offline and backed up in two places (for example a
password manager attachment and an encrypted USB drive). **Losing it means users must uninstall to
update.**

```bash
keytool -genkeypair -v -keystore voxlog-release.jks -alias voxlog -keyalg RSA -keysize 4096 -validity 36500
```

Print the certificate fingerprint (needed for F-Droid):

```bash
keytool -list -v -keystore voxlog-release.jks -alias voxlog
```

### 2. GitHub repository secrets

Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 voxlog-release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | `voxlog` |
| `RELEASE_KEY_PASSWORD` | Key password |
| `FDROID_REPO_KEYSTORE_BASE64` | Keystore for signing the self-hosted F-Droid repo index (separate key; create with `fdroid init` or `keytool`) |
| `FDROID_REPO_KEYSTORE_PASSWORD` | Its password |
| `FDROID_REPO_KEY_ALIAS` | Its alias |

### 3. GitHub Pages

Settings → Pages → Source: **GitHub Actions**. The repo is published at
`https://mikejhill.github.io/voxlog/fdroid/repo`.

### 4. Official F-Droid inclusion (once)

1. Fork <https://gitlab.com/fdroid/fdroiddata>.
2. Copy `fdroid/com.mikejhill.voxlog.yml` from this repository to `metadata/com.mikejhill.voxlog.yml`.
3. Replace `AllowedAPKSigningKeys` with the SHA-256 certificate fingerprint from step 1 (lowercase,
   no colons).
4. Validate locally: `fdroid lint com.mikejhill.voxlog` and `fdroid build -v -l com.mikejhill.voxlog`
   (or rely on the `fdroid-metadata` CI job, which runs both in the fdroidserver container).
5. Open a merge request against `fdroiddata`, following their template. Respond to reviewer comments.

After the merge, F-Droid's `checkupdates` bot picks up new `v*` tags automatically
(`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`). No further manual steps are needed per release.

---

## Per-release checklist

1. **Make sure `main` is green** (CI: checks, instrumented tests, benchmarks, reproducibility).
2. **Bump the version.** Semantic versioning `MAJOR.MINOR.PATCH`; the version code is
   `MAJOR*10000 + MINOR*100 + PATCH` (e.g. `1.2.3` → `10203`). The script updates
   `voxlog-app/build.gradle.kts` and creates the changelog file:

   ```bash
   python scripts/bump_version.py 1.2.3
   ```

3. **Write the changelog** in the created `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   (≤ 500 characters, user-facing).
4. **Refresh store screenshots** if the UI changed: `./gradlew :voxlog-app:generateStoreScreenshots`.
5. **Commit**, then tag and push:

   ```bash
   git tag -s v1.2.3 -m "VoxLog 1.2.3"
   ```

   ```bash
   git push origin v1.2.3
   ```

6. **Watch the workflows**: `release.yml` (build, sign, verify reproducibility, GitHub Release) then
   `fdroid-repo.yml` (update the self-hosted repo).
7. **Verify**:
   - The GitHub Release has `voxlog-<version>.apk` and its `.sha256`.
   - The self-hosted repo index shows the new version (F-Droid client → refresh).
   - Within ~1–3 days, <https://monitor.f-droid.org/builds> shows a successful build for
     `com.mikejhill.voxlog`, and the update appears in F-Droid.

## Hotfix

1. Branch from the release tag: `git switch -c hotfix/1.2.4 v1.2.3`.
2. Fix, add a changelog file for the new version code, merge to `main`.
3. Tag `v1.2.4` from `main` and follow the checklist from step 5.

## If F-Droid's reproducibility check fails

F-Droid then refuses to publish (it never silently signs with its own key, because
`AllowedAPKSigningKeys` is set). To diagnose:

1. Download both APKs (ours from the GitHub Release, theirs from the F-Droid build log artifacts).
2. Compare with `diffoscope ours.apk theirs.apk`.
3. Common causes and fixes:
   - **Different NDK/CMake** → versions are pinned in `gradle/libs.versions.toml` and `fdroid/*.yml`;
     keep them identical.
   - **Absolute paths in native code** → `-ffile-prefix-map` is set in the CMake file; check new
     native sources.
   - **Baseline profile changed** → regenerate and commit; never generate during the release build.
   - **Different JDK** → CI and F-Droid both use JDK 21.
4. Fix, then release a new patch version (tags are immutable).

## Key rotation or loss

- **Rotation (key still available):** Android supports APK signature scheme v3 key rotation. Sign with
  `apksigner rotate` lineage, update `AllowedAPKSigningKeys` in fdroiddata with both fingerprints,
  release.
- **Loss:** Generate a new key. The application id stays the same, but every user must uninstall
  and reinstall because Android rejects updates signed by a different key. Announce it in the
  changelog and README, and update all secrets and the fdroiddata fingerprint.
