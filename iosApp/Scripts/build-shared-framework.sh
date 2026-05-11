#!/bin/bash
# Builds + embeds the KMP shared framework for the active Xcode build.
# Invoked from the "KMP Build Shared Framework" PBXShellScriptBuildPhase.
#
# Why this script exists in its own file rather than inlined into the pbxproj:
#  • pbxproj string escaping is hostile to multi-line shell with redirects.
#  • Easier to run by hand for diagnostics: ./iosApp/Scripts/build-shared-framework.sh
#  • Self-bootstraps the gradle-wrapper.jar on first run so the text-only
#    repo scaffold compiles without manual setup steps.

set -eu

# Resolve repo root regardless of how we were invoked.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

LOG_DIR="${DERIVED_FILE_DIR:-/tmp}"
LOG_FILE="$LOG_DIR/kmp-build.log"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "==> KMP framework build"
echo "    PROJECT_ROOT=$PROJECT_ROOT"
echo "    CONFIGURATION=${CONFIGURATION:-?}  SDK_NAME=${SDK_NAME:-?}  ARCHS=${ARCHS:-?}"

# 1. Locate a Gradle-compatible JDK. Gradle 8.13 supports up to JDK 23 — JDK 24+
#    will fail the build with a cryptic version string in the error. Prefer
#    21 → 19 → 17 over the "any 17+" sweep that used to grab whatever's newest.
#    The user can still override with an explicit JAVA_HOME from Xcode (Product
#    → Scheme → Edit Scheme → Run → Arguments → Environment Variables).
if [ -z "${JAVA_HOME:-}" ]; then
  for v in 21 19 17 23 22 20 18; do
    if /usr/libexec/java_home -v "$v" >/dev/null 2>&1; then
      JAVA_HOME="$(/usr/libexec/java_home -v "$v")"
      export JAVA_HOME
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "    using JAVA_HOME=$JAVA_HOME"
      break
    fi
  done
fi
if ! java -version >/dev/null 2>&1; then
  cat >&2 <<'EOF'
error: No JDK 17–23 found on this machine.

Install one:
    brew install --cask temurin@21

Then quit and reopen Xcode (so it inherits the updated PATH/JAVA_HOME)
and rebuild. If JAVA_HOME is still not picked up, in Xcode go to
Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables
and add JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
EOF
  exit 1
fi
# Refuse to run on JDK 24+ — Gradle 8.13 chokes on them with a misleading
# "What went wrong: <version-string>" error message. Far better to fail
# fast with a real explanation here than to spend an hour deciphering it.
JDK_MAJOR="$(java -version 2>&1 | awk -F\" '/version/ {split($2, a, "."); print a[1]; exit}')"
if [ -n "$JDK_MAJOR" ] && [ "$JDK_MAJOR" -ge 24 ] 2>/dev/null; then
  cat >&2 <<EOF
error: Gradle 8.13 does not support JDK $JDK_MAJOR (only 17–23).

Currently picked: $JAVA_HOME

Install JDK 21:
    brew install --cask temurin@21

Then either:
  • Wait for Spotlight to index it and rerun, or
  • Set JAVA_HOME in Xcode → Product → Scheme → Edit Scheme → Run →
    Arguments → Environment Variables:
      JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
EOF
  exit 1
fi

# 2. Bootstrap gradle-wrapper.jar if absent.
WRAPPER_DIR="$PROJECT_ROOT/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "==> bootstrapping gradle-wrapper.jar"
  mkdir -p "$WRAPPER_DIR"
  curl -fsSL --retry 3 -o "$WRAPPER_JAR" \
    "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar"
fi
chmod +x "$PROJECT_ROOT/gradlew"

# 3. Run Gradle. embedAndSignAppleFrameworkForXcode reads CONFIGURATION,
#    SDK_NAME, ARCHS, BUILT_PRODUCTS_DIR, TARGET_BUILD_DIR,
#    FRAMEWORKS_FOLDER_PATH, EXPANDED_CODE_SIGN_IDENTITY from the env;
#    Xcode exports those automatically.
"$PROJECT_ROOT/gradlew" \
  -p "$PROJECT_ROOT" \
  --no-daemon \
  --no-configuration-cache \
  --console=plain \
  :shared:embedAndSignAppleFrameworkForXcode

echo "==> KMP framework build complete"
