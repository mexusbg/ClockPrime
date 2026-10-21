#!/bin/sh
# Installs the debug and test APKs from $1 (CI artifact layout or the Gradle output folders)
# and runs SmokeTests on the connected device. Exit status follows the suite result, which
# `am instrument` itself does not report.
set -eu
dir=${1:-app/build/outputs/apk}
app=$(find "$dir" -name app-debug.apk | head -n 1)
tests=$(find "$dir" -name app-debug-androidTest.apk | head -n 1)
adb wait-for-device
adb install -r "$app"
adb install -r "$tests"
adb shell pm grant com.clockprime.app android.permission.POST_NOTIFICATIONS
adb shell am instrument -w com.clockprime.app.test/com.clockprime.app.SmokeTests | tee smoke-tests.txt
grep -q "CHECKS PASSED" smoke-tests.txt
