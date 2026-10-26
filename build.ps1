param([string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$oldJava=$env:JAVA_HOME
$oldPath=$env:Path
try {
    if(!(Test-Path -LiteralPath (Join-Path $JdkPath 'bin\java.exe'))){throw 'JDK 17+ is required.'}
    $env:JAVA_HOME=$JdkPath
    $env:Path=(Join-Path $JdkPath 'bin')+';'+$oldPath
    Push-Location $PSScriptRoot
    try {
        & '.\gradlew.bat' clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --no-watch-fs --console=plain
        if($LASTEXITCODE -ne 0){throw 'Build or verification failed.'}
        $destination=Join-Path $PSScriptRoot 'dist'
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        $apk=Join-Path $destination 'YinYuanyuanTimetable-1.0.2.apk'
        Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination $apk -Force
        $hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        Set-Content -LiteralPath ($apk+'.sha256') -Value ($hash+'  '+[IO.Path]::GetFileName($apk)) -Encoding ascii
        Write-Output ('APK: '+$apk)
        Write-Output ('SHA256: '+$hash)
    } finally {Pop-Location}
} finally {$env:JAVA_HOME=$oldJava;$env:Path=$oldPath}
