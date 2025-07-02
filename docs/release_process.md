# Release Process for JavaFlow

This document describes how to release JavaFlow to Maven Central using Maven Central Portal.

## Prerequisites

1. Maven Central Portal User Token (username + token)
2. GPG key for signing artifacts

## Setting up GPG Signing

To sign the artifacts for Maven Central, you'll need to generate a GPG key:

```bash
# Generate a new GPG key
gpg --gen-key

# List keys to find the key ID
gpg --list-keys

# Export the private key in ASCII armor format
gpg --export-secret-keys --armor YOUR_KEY_ID > private.key

# Export the public key
gpg --export --armor YOUR_KEY_ID > public.key
```

## Setting up GitHub Secrets

Add the following secrets to your GitHub repository:

1. `MAVEN_CENTRAL_USERNAME`: Your Maven Central Portal username
2. `MAVEN_CENTRAL_TOKEN`: Your Maven Central Portal User Token
3. `SIGNING_KEY`: The contents of your exported private key (the entire file content)
4. `SIGNING_KEY_PASSWORD`: The passphrase for your GPG key
5. `RELEASE_TOKEN`: A GitHub Personal Access Token (PAT) with `repo` and `pull_request` permissions (required for creating pull requests)

### Creating a GitHub Personal Access Token for Releases

1. Go to GitHub Settings > Developer settings > Personal access tokens > Tokens (classic)
2. Click "Generate new token (classic)"
3. Give it a descriptive name like "JavaFlow Release Automation"
4. Select the following scopes:
   - `repo` (all permissions under repo)
   - `workflow` (to trigger workflows)
5. Click "Generate token" and copy the token
6. Add it as a repository secret named `RELEASE_TOKEN`

## Release Process

### Automated Release Process (Recommended)

1. Go to the "Actions" tab in GitHub
2. Select the "Prepare Release" workflow
3. Click "Run workflow"
4. Enter the requested information:
   - Release version (e.g., `1.0.0`)
   - Next development version (e.g., `1.1.0-SNAPSHOT`)
5. Click "Run workflow" to start the release process

The workflow will automatically:
1. Create a new branch named `release/{version}`
2. Update the version in build.gradle to the release version
3. Build and test the project with the release version
4. Commit the release version change
5. Update the version in build.gradle to the next development version
6. Commit the next development version change
7. Create a pull request to the main branch with the label "release"
8. Once the PR is merged, a separate workflow will:
   - Create a Git tag for the release on the appropriate commit
   - Create a GitHub Release
   - Trigger the "Publish to Maven Central" workflow that will:
     - Build the project
     - Sign the artifacts
     - Publish directly to Maven Central using the User Token

**Note**: Since the main branch is protected, the release process uses pull requests to ensure all changes are reviewed before being merged.

### Manual Release Process (Alternative)

If you prefer to manage the release manually:

1. Create a new GitHub release
   - Go to GitHub > Releases > Draft a new release
   - Create a new tag with the version (e.g., `v1.0.0`)
   - Title the release with the version
   - Add release notes
   - Publish the release

2. The GitHub Actions workflow will automatically:
   - Build the project
   - Set the version from the tag
   - Sign the artifacts
   - Publish directly to Maven Central using the User Token

### Publishing a Snapshot Version

To publish a snapshot version:

1. Ensure your `build.gradle` file has a version ending with `-SNAPSHOT` (e.g., `1.0.0-SNAPSHOT`)
2. Go to the "Actions" tab in GitHub
3. Select the "Publish Snapshot" workflow
4. Click "Run workflow"
5. Click "Run workflow" again to start the publishing process

The workflow will:
- Verify the version in build.gradle is a SNAPSHOT version
- Build the project
- Sign the artifacts
- Publish the snapshot to Maven Central

## Verifying the Release

After the release process completes, verify that your artifacts are available on Maven Central. This may take some time (usually up to an hour) for the artifacts to be fully synchronized.

You can search for your artifacts at:
https://central.sonatype.com/artifact/io.github.panghy/javaflow

## Local Testing

To test the release process locally before pushing to GitHub:

```bash
# Test building the artifacts
./gradlew build

# Test publishing to local maven repository (without signing)
./gradlew publishToMavenLocal

# Test publishing with signing to local maven repository
./gradlew publishToMavenLocal -PsigningInMemoryKey="$(cat private.key)" -PsigningInMemoryKeyPassword="your-key-password"
```

## Direct Publication

With the Maven Central Portal, artifacts are published directly to Maven Central without a staging repository. This means that once the publication completes successfully, your artifacts will be available immediately (after synchronization).