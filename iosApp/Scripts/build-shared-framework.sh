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

# 1. Locate a JDK (17+). On macOS, /usr/bin/java is a stub that prompts for
#    install if no JDK is present — we must verify by running `java -version`.
if [ -z "${JAVA_HOME:-}" ]; then
  if /usr/libexec/java_home -v 17+ >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17+)"
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi
if ! java -version >/dev/null 2>&1; then
  cat >&2 <<'EOF'
error: No JDK 17+ found on this machine.

Install one:
    brew install --cask temurin@21

Then quit and reopen Xcode (so it inherits the updated PATH/JAVA_HOME)
and rebuild. If JAVA_HOME is still not picked up, in Xcode go to
Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables
and add JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
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
