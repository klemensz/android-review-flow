# Releasing

This document describes how to publish `reviewflow-core` and `reviewflow-compose` to Maven Central using the Central Portal plugin flow.

## Current coordinates

- Group: `com.zleptnig`
- Artifacts:
    - `reviewflow-core` KMP root plus Android and iOS target artifacts
    - `reviewflow-compose`
- Version source: `VERSION_NAME` in `gradle.properties`

## Prerequisites

1. Sonatype Central account with publishing permission for your namespace.
2. Namespace verified in Sonatype Central for `com.zleptnig`.
3. PGP key for artifact signing.
4. Credentials configured via Gradle properties or environment variables.

Credential names:

- Central Portal token:
    - Environment variables: `MAVEN_CENTRAL_PORTAL_USERNAME`, `MAVEN_CENTRAL_PORTAL_PASSWORD`
    - Gradle properties: `mavenCentralPortalUsername`, `mavenCentralPortalPassword`
- Signing:
    - `SIGNING_PASSWORD`
    - Either `SIGNING_KEY` (ASCII-armored private key content) or `SIGNING_KEY_FILE` (path to `.asc` file)

## Recommended local secret setup (no repo secrets)

Keep secrets out of project files and git.

1. Store private key locally:

```bash
mkdir -p "$HOME/.keys/maven"
chmod 700 "$HOME/.keys/maven"
# Example export (replace KEY_ID)
gpg --armor --export-secret-keys KEY_ID > "$HOME/.keys/maven/private-key.asc"
chmod 600 "$HOME/.keys/maven/private-key.asc"
```

2. Put credentials into `~/.gradle/gradle.properties`:

```properties
mavenCentralPortalUsername=...
mavenCentralPortalPassword=...
SIGNING_PASSWORD=...
SIGNING_KEY_FILE=/Users/<you>/.keys/maven/private-key.asc
```

Do not put these into the project `gradle.properties`.

## 1) Prepare the release

1. Ensure working tree is clean.
2. Update `VERSION_NAME` in `gradle.properties`.
    - Example release: `0.2.0`
    - This release flow is only for final versions, not `-SNAPSHOT` versions.
3. Verify that the version has not already been published.
4. Verify POM metadata values in `gradle.properties`:
    - `POM_URL`
    - `POM_SCM_*`
    - `POM_DEVELOPER_*`
    - `POM_LICENSE_*`
5. Commit the version change and any release notes.
6. Ensure the working tree is clean again. The commit at `HEAD` must be the exact source to publish.

## 2) Optional local preflight

The final release task enforces the same test gate. Run it separately only when you want an early, upload-free check:

```bash
./gradlew clean verifyMavenCentralRelease
```

Root release tasks:

- `verifyMavenCentralRelease`: runs common, Android, and iOS tests plus the ABI check for `reviewflow-core`, then the `reviewflow-compose` and `sample-app` tests; no upload.
- `prepareMavenCentralRelease`: clears old release staging outputs and builds both signed deployment bundles; no upload.
- `validateMavenCentralRelease`: uploads and validates both bundles without releasing them.
- `releaseToMavenCentralPortal`: runs preparation, verification, validation, and release in one Gradle invocation.

## 3) Publish and release via Central Portal

Use the combined task for a normal release:

```bash
./gradlew releaseToMavenCentralPortal
```

The task graph rebuilds both bundles and requires all tests to pass before either bundle is uploaded. Both deployments must then validate before either one is released.

Do **not** run `validateMavenCentralRelease` and `releaseToMavenCentralPortal` as two consecutive Gradle invocations. The plugin keeps deployment IDs only within the current Gradle invocation unless an ID is supplied explicitly. A later `releaseToMavenCentralPortal` invocation would upload new deployments instead of reusing the previously validated ones.

No manual OSSRH staging API transfer step is required in this flow.

## 4) Verify availability

Check Sonatype Central deployments and then verify Maven Central:

- `https://repo1.maven.org/maven2/com/zleptnig/reviewflow-core/<version>/`
- `https://repo1.maven.org/maven2/com/zleptnig/reviewflow-core-android/<version>/`
- `https://repo1.maven.org/maven2/com/zleptnig/reviewflow-core-iosarm64/<version>/`
- `https://repo1.maven.org/maven2/com/zleptnig/reviewflow-core-iossimulatorarm64/<version>/`
- `https://repo1.maven.org/maven2/com/zleptnig/reviewflow-core-iosx64/<version>/`
- `https://repo1.maven.org/maven2/com/zleptnig/reviewflow-compose/<version>/`

## 5) Tag and announce

1. Verify that `HEAD` is still the commit that was published and that the working tree is clean.
2. Create an annotated git tag for that commit, for example:
   `git tag -a "v<version>" -m "ReviewFlow <version>"`.
3. Push the tag: `git push origin "v<version>"`.
4. Create release notes (breaking changes, migration hints, new features).

## Troubleshooting

- `401 Unauthorized`:
  - Check your Central Portal token values.
  - Verify you are using the token username/password (not regular login password).
- Signing failures:
  - Verify `SIGNING_PASSWORD`.
  - Verify either `SIGNING_KEY` content or readable `SIGNING_KEY_FILE` path.
- Key not found on keyserver:
  - Upload your public key, then retry.
- Failed or orphaned deployment:
  - Inspect the deployment and copy its ID from the Central Portal or Gradle output.
  - To drop a failed or validated deployment, run the module task with that module's deployment ID:
    `./gradlew :reviewflow-core:dropMavenCentralPortalPublication -PpublishDeploymentId=<deployment-id>`
    (use the corresponding `review-compose` task for that module).
  - If a deployment was intentionally validated in an earlier invocation, release each module with its own ID:
    `./gradlew :reviewflow-core:releaseMavenCentralPortalPublication -PpublishDeploymentId=<deployment-id>`
    (repeat with the separate `review-compose` deployment ID).
- Snapshot rejected:
  - This flow publishes final releases. Configure the separate Central Portal snapshot repository for `-SNAPSHOT` versions.
