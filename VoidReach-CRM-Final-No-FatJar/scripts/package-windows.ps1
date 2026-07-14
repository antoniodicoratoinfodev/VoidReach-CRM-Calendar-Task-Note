$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$InputDir = Join-Path $RootDir "target/jpackage-input"
$OutputDir = Join-Path $RootDir "target/packages/windows"
$AppJar = "CRMApp-1.0-SNAPSHOT.jar"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn was not found on PATH. Install Apache Maven 3.9+ and reopen PowerShell."
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found on PATH. Install a JDK 26+ and make sure its 'bin' directory is on PATH."
}

Set-Location $RootDir
Remove-Item -Recurse -Force $InputDir, $OutputDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $InputDir, $OutputDir | Out-Null

mvn -q package "-DskipTests" dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$InputDir"
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit code $LASTEXITCODE)." }

Copy-Item (Join-Path $RootDir "target/$AppJar") (Join-Path $InputDir $AppJar)

# Note: --win-menu / --win-shortcut are only valid for installer types (msi/exe),
# not for --type app-image, so they must not be passed here.
jpackage `
  --type app-image `
  --name VoidReach `
  --app-version 1.0 `
  --input $InputDir `
  --main-jar $AppJar `
  --main-class com.crm.app.AppLauncher `
  --icon (Join-Path $RootDir "src/main/packaging/windows/VoidReach.ico") `
  --java-options "--enable-native-access=javafx.graphics" `
  --dest $OutputDir
if ($LASTEXITCODE -ne 0) { throw "jpackage failed (exit code $LASTEXITCODE)." }

Write-Host "App image created under $OutputDir\VoidReach"
