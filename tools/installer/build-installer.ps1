# Builds build\installer\out\CodeQuest-Setup-<version>.exe (needs JDK 25 and Inno Setup 6 installed).
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$jdk = "C:\Program Files\Java\jdk-25"
$iscc = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"

& .\gradlew.bat :core:test :lwjgl3:jar -q
if ($LASTEXITCODE -ne 0) { throw "build failed" }

$work = Join-Path $root "build\installer"
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$work\input", "$work\image" | Out-Null
Copy-Item "lwjgl3\build\libs\CodeQuest-1.0.0.jar" "$work\input\"

& "$jdk\bin\jpackage.exe" --type app-image --name CodeQuest --app-version 1.0.0 --vendor "The Wolves" `
    --input "$work\input" --main-jar CodeQuest-1.0.0.jar --icon lwjgl3\icons\logo.ico --dest "$work\image" `
    --add-modules java.base,java.management,java.desktop,jdk.unsupported,java.net.http,jdk.crypto.ec,java.naming,java.logging,java.sql `
    --java-options "--enable-native-access=ALL-UNNAMED"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

& $iscc "tools\installer\CodeQuest.iss"
if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed" }
Write-Host "Done: $work\out"
