# Bygger MINK på Windows. Kræver JDK 17 eller nyere (fx "winget install EclipseAdoptium.Temurin.21.JDK").
# Første gang hentes Android SDK'et (ca. 1 GB) til %USERPROFILE%\android-sdk.
# Brug (PowerShell, i repoets rod):  .\tools\build.ps1   ->  dist\MINK.apk
$ErrorActionPreference = 'Stop'
try {
    Set-Location (Join-Path $PSScriptRoot '..')
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java blev ikke fundet. Installér JDK 17 eller nyere, og åbn PowerShell igen.' }

    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE 'android-sdk' }
    $sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-36')) -or -not (Test-Path (Join-Path $sdk 'build-tools\36.0.0'))) {
        if (-not (Test-Path $sdkmanager)) {
            Write-Host 'Henter Android-kommandolinjeværktøjer ...'
            $zip = Join-Path $env:TEMP 'clt.zip'
            Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile $zip
            $tmp = Join-Path $env:TEMP 'clt'
            if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
            Expand-Archive $zip $tmp
            New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
            Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
            Remove-Item $zip, $tmp -Recurse -Force
        }
        Write-Host 'Henter Android SDK (platform 36, build-tools 36.0.0) ...'
        (1..30 | ForEach-Object { 'y' }) | & $sdkmanager "--sdk_root=$sdk" --licenses | Out-Null
        & $sdkmanager "--sdk_root=$sdk" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
        if ($LASTEXITCODE -ne 0) { throw 'sdkmanager fejlede' }
    }
    $env:ANDROID_HOME = $sdk

    & .\gradlew.bat --no-daemon testReleaseUnitTest assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Bygget fejlede (se ovenfor)' }
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item app\build\outputs\apk\release\app-release.apk dist\MINK.apk -Force
    Write-Host ''
    Write-Host "Færdig: $(Resolve-Path dist\MINK.apk)" -ForegroundColor Green
}
catch {
    Write-Host ''
    Write-Host "FEJL: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Read-Host 'Tryk Enter for at lukke'
}
