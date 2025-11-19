#!/bin/bash
set -e

# === Validate input ===
if [ $# -eq 0 ]; then
  echo "Please provide the release version number as a parameter."
  exit 1
fi

if ! [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc)?$ ]]; then
  echo "The release version number does not match the semver format (X.Y.Z or X.Y.Z-rc)."
  exit 1
fi

version=$1
current_branch=$(git symbolic-ref --short HEAD)
echo "→ Currently on branch: $current_branch"

if [[ ! $current_branch =~ ^(release|support)/[0-9]+\.[0-9]+\.[0-9]+(-rc)?$ ]]; then
  echo "❌ The current Git branch ($current_branch) is not in the correct format."
  exit 1
fi

# === Update gradle.properties ===
properties_file="gradle.properties"
if [ ! -f "$properties_file" ]; then
  echo "❌ $properties_file not found."
  exit 1
fi

current_version=$(grep -E '^SDK_VERSION_NAME=' "$properties_file" | cut -d'=' -f2)
echo "→ Current SDK_VERSION_NAME: ${current_version:-<empty>}"
echo "→ Updating SDK_VERSION_NAME to $version"

if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' "s/^SDK_VERSION_NAME=.*/SDK_VERSION_NAME=$version/" "$properties_file"
else
  sed -i "s/^SDK_VERSION_NAME=.*/SDK_VERSION_NAME=$version/" "$properties_file"
fi

grep "^SDK_VERSION_NAME=" "$properties_file"
git add "$properties_file"

# === Update MindboxCommon.podspec (only s.version) ===
podspec_file="MindboxCommon.podspec"
if [ -f "$podspec_file" ]; then
  echo "→ Updating $podspec_file version to $version"

  if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' -E "s|^[[:space:]]*s\.version[[:space:]]*=.*|    s.version      = '$version'|" "$podspec_file"
  else
    sed -i -E "s|^[[:space:]]*s\.version[[:space:]]*=.*|    s.version      = '$version'|" "$podspec_file"
  fi

  grep "s.version" "$podspec_file"
  git add "$podspec_file"
else
  echo "⚠️  $podspec_file not found, skipping podspec update."
fi

# === Commit changes ===
echo ""
git commit -m "Bump Common SDK version to $version" || echo "No changes to commit"

# === Validation ===
echo "→ Validating version bump..."
new_version=$(grep -E '^SDK_VERSION_NAME=' "$properties_file" | cut -d'=' -f2)

if [ "$new_version" != "$version" ]; then
  echo "❌ Validation failed: expected SDK_VERSION_NAME=$version but found $new_version"
  exit 1
fi

if [ -f "$podspec_file" ]; then
  podspec_version=$(grep -E "^[[:space:]]*s\.version" "$podspec_file" | grep -Eo "[0-9]+\.[0-9]+\.[0-9]+(-rc)?")
  if [ "$podspec_version" != "$version" ]; then
    echo "❌ Validation failed: expected s.version=$version but found $podspec_version"
    exit 1
  fi
fi

echo "✅ Validation passed: version successfully updated to $version"

# === Push changes ===
echo "→ Pushing changes to branch: $current_branch"
if ! git push origin "$current_branch"; then
  echo "❌ Failed to push changes to origin/$current_branch"
  exit 1
fi

echo "✅ bump_versions_in_release_branch.sh completed successfully."
