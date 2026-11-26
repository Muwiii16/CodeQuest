# Publishes the built version as a GitHub release. Installed games pick it up the next time they are opened.
# Run tools\installer\build-installer.ps1 first, and commit and push your changes so the release points at them.
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$version = ((Get-Content gradle.properties) -match '^projectVersion=' -replace '^projectVersion=', '').Trim()
$dir = Join-Path $root "build\installer\release"
$manifest = Get-Content "$dir\update.json" -Raw | ConvertFrom-Json
if ($manifest.version -ne $version) { throw "The built release is $($manifest.version) but gradle.properties says $version. Build again." }
if (git status --porcelain) { throw "There are uncommitted changes. Commit and push them first." }
$commit = (git rev-parse HEAD).Trim()

gh release create "v$version" "$dir\CodeQuest-Setup.exe" "$dir\update.json" --target $commit --latest `
    --title "CodeQuest $version" --notes "CodeQuest $version. Download CodeQuest-Setup.exe to install; installed copies update by themselves."
if ($LASTEXITCODE -ne 0) { throw "the release was not published" }
Write-Host "Published. Permanent download link:"
Write-Host "https://github.com/Muwiii16/CodeQuest/releases/latest/download/CodeQuest-Setup.exe"
