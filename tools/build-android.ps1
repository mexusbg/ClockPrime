param([switch]$Install, [switch]$Online, [string[]]$Tasks=@('assembleDebug'))
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if(-not (Test-Path $sdk)){throw 'Android SDK not found. Install Android Studio and SDK 35.'}
$env:ANDROID_HOME=$sdk
$env:DEBUG=''
$gradle=Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.10.2-bin') -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
if(-not $gradle){throw 'Gradle 8.10.2 not cached. Run the Gradle wrapper or install Gradle 8.10.2.'}
Push-Location $root
try {
    $arguments=@('--console=plain')+$Tasks
    if(-not $Online){$arguments+='--offline'}
    # Windows PowerShell 5 turns any stderr line (javac "Note: ...") into a terminating error under Stop,
    # which used to skip the APK copy below and leave a stale artifact.
    $ErrorActionPreference='Continue'
    & $gradle @arguments 2>&1 | ForEach-Object {"$_"}
    $code=$LASTEXITCODE;$ErrorActionPreference='Stop'
    if($code -ne 0){throw 'Android build failed'}
    New-Item -ItemType Directory -Force (Join-Path $root 'artifacts') | Out-Null
    $metadata=Get-Content -Raw (Join-Path $root 'app\build\outputs\apk\debug\output-metadata.json') | ConvertFrom-Json
    $apk=Join-Path $root ('artifacts\ClockPrime-'+$metadata.elements[0].versionName+'.apk')
    Copy-Item -LiteralPath (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk') -Destination $apk
    if($Install){adb install -r $apk;if($LASTEXITCODE -ne 0){throw 'Install failed'};adb shell am start -n com.clockprime.app/.MainActivity}
} finally {Pop-Location}
