#!/bin/bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
case "${PLATFORM_NAME:-iphonesimulator}" in
  iphoneos) TARGET=IosArm64 ;;
  iphonesimulator)
    case "${NATIVE_ARCH_ACTUAL:-arm64}" in
      arm64) TARGET=IosSimulatorArm64 ;;
      *) TARGET=IosX64 ;;
    esac ;;
  *) echo "Unsupported platform: ${PLATFORM_NAME}" >&2; exit 1 ;;
esac
case "${CONFIGURATION:-Debug}" in
  Release) BUILD_TYPE=Release; FRAMEWORK_DIR=releaseFramework ;;
  *) BUILD_TYPE=Debug; FRAMEWORK_DIR=debugFramework ;;
esac
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -F -v '17+')"
  export JAVA_HOME
fi
cd "$REPO_ROOT"
./gradlew ":ios-demo-bridge:link${BUILD_TYPE}Framework${TARGET}" '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g'
case "$TARGET" in
  IosArm64) TARGET_DIR=iosArm64 ;;
  IosSimulatorArm64) TARGET_DIR=iosSimulatorArm64 ;;
  IosX64) TARGET_DIR=iosX64 ;;
esac
ditto "samples/ios/bridge/build/bin/$TARGET_DIR/$FRAMEWORK_DIR/ReviewFlowDemoKit.framework" "${BUILT_PRODUCTS_DIR:?Xcode build products directory is required}/ReviewFlowDemoKit.framework"
