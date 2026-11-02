#!/usr/bin/env bash
set -euo pipefail

# Keep diagnostics and Gradle in one shell: emulator-runner executes script lines separately.
adb logcat -v time -T 1 TaskBoardTest:I TestRunner:I AndroidRuntime:E '*:S' &
taskboardLogcatPid=$!
cleanup() {
    kill "$taskboardLogcatPid" 2>/dev/null || true
    wait "$taskboardLogcatPid" 2>/dev/null || true
}
trap cleanup EXIT

./gradlew :app:connectedDebugAndroidTest
