# Builds build\installer\out\CodeQuest-Setup-<version>.exe (needs JDK 25 and Inno Setup 6 installed).
# The version is projectVersion in gradle.properties: raise it for every release, or installed games will not update.
# When the release signing key is present it also fills build\installer\release with the two files a GitHub release needs
# (see tools\release\publish-release.ps1).
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$jdk = "C:\Program Files\Java\jdk-25"
$iscc = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
$version = ((Get-Content gradle.properties) -match '^projectVersion=' -replace '^projectVersion=', '').Trim()
if (-not $version) { throw "projectVersion is missing from gradle.properties" }
$key = Join-Path $env:USERPROFILE ".codequest-release\signing-key"

& .\gradlew.bat :core:test :lwjgl3:jar -q
if ($LASTEXITCODE -ne 0) { throw "build failed" }

$work = Join-Path $root "build\installer"
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$work\input", "$work\image" | Out-Null
# the same name in every version, so an update replaces it
Copy-Item "lwjgl3\build\libs\CodeQuest-$version.jar" "$work\input\CodeQuest.jar"

& "$jdk\bin\jpackage.exe" --type app-image --name CodeQuest --app-version $version --vendor "The Wolves" `
    --input "$work\input" --main-jar CodeQuest.jar --icon lwjgl3\icons\logo.ico --dest "$work\image" `
    --add-modules java.base,java.management,java.desktop,jdk.unsupported,java.net.http,jdk.crypto.ec,java.naming,java.logging,java.sql `
    --java-options "--enable-native-access=ALL-UNNAMED"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

& $iscc "/DAppVersion=$version" "tools\installer\CodeQuest.iss"
if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed" }

if (Test-Path $key) {
    New-Item -ItemType Directory -Force "$work\release" | Out-Null
    Copy-Item "$work\out\CodeQuest-Setup-$version.exe" "$work\release\CodeQuest-Setup.exe"
    & "$jdk\bin\java.exe" tools\release\ReleaseTool.java sign $key "$work\release\CodeQuest-Setup.exe" $version "$work\release\update.json"
    if ($LASTEXITCODE -ne 0) { throw "signing failed" }
} else {
    Write-Warning "No signing key at ${key}: this installer works, but it cannot be published as an automatic update."
}
Write-Host "Done: $work\out"
