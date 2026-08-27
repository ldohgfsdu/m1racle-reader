# Android device-test data safety

## Protected and automated packages

- The normal debug/release application ID is `com.m1racle.reader`. It may contain irreplaceable
  local reading state and must be updated only with `adb install -r`.
- Automated instrumentation uses the `qa` build type and application ID
  `com.m1racle.reader.qa`. Its launcher label is **M1racle Reader QA**.
- The two application IDs have independent Android sandboxes. Room databases,
  SharedPreferences, DataStore and `files/` content are therefore isolated without changing the
  production schema, migrations or persistence behavior.
- Test tooling can reinstall or uninstall its target APK. Never assume application data survives
  a connected test.

## Prohibited operations

Never run `connectedDebugAndroidTest` (or any connected variant other than QA) against a device
that holds user data. Never run either of these commands:

```text
adb uninstall com.m1racle.reader
adb shell pm clear com.m1racle.reader
```

The Gradle task graph rejects non-QA connected tests before install tasks can run. The supported
entry point additionally reads the built APK metadata and prints the resolved target:

```powershell
.\scripts\run-qa-connected-tests.ps1
# ANDROID_TEST_TARGET applicationId=com.m1racle.reader.qa
```

Direct QA commands are available when the same guard remains enabled:

```powershell
.\gradlew.bat :app:assembleQa --no-daemon --console=plain
.\gradlew.bat :app:compileQaAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:connectedQaAndroidTest --no-daemon --console=plain
```

## Back up before device work

The debug package must be installed and debuggable. The backup script verifies both conditions,
uses `run-as`, force-stops the process for a consistent Room/WAL snapshot, and captures
`databases/`, `shared_prefs/` and `files/`. It creates package/version/device/time metadata plus
per-file and archive SHA-256 values. An incomplete or empty backup is deleted and reported as an
error.

```powershell
.\scripts\backup-debug-app-data.ps1 -PackageName com.m1racle.reader
```

Backups default to `.local-backups/m1racle-reader-debug/`, which is ignored by Git. Keep the entire
backup directory together; `manifest.json`, `app-data.tar`, and `payload/` are all required.
Review `BACKUP_COMPLETE`, the file count, byte count and SHA-256 before proceeding.

## Restore and verify

Routine restore testing must use QA, never the current main-package data. Install QA, create a QA
backup, deliberately change only QA state, then restore it:

```powershell
.\scripts\restore-debug-app-data.ps1 `
  -PackageName com.m1racle.reader.qa `
  -BackupDirectory .\.local-backups\m1racle-reader-debug\<qa-backup>
```

Restore verifies applicationId, manifest entries, sizes and hashes before stopping the target.
It restores the database directory as one unit, including Room WAL/SHM files, and swaps
`databases/`, `shared_prefs/` and `files/` with rollback protection. It then starts QA and runs the
QA instrumentation probe, which opens Room and checks its sandbox.

Main-package restore is an emergency operation and is refused unless
`-AllowMainPackageRestore` is supplied explicitly. Do not use that switch for test validation.

## Incident record

On 2026-07-13 a second connected-test installation failed after the Android test tooling had
already removed `com.m1racle.reader`. The package sandbox and device-local application data were
cleared. This record intentionally contains no user document content. The incident is the reason
QA-only device testing and pre-test backups are mandatory.
