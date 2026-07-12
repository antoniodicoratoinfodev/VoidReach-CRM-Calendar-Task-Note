$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$InputDir = Join-Path $RootDir "target/jpackage-input"
$OutputDir = Join-Path $RootDir "target/packages/windows"
$AppJar = "CRMApp-1.0-SNAPSHOT.jar"

Set-Location $RootDir
Remove-Item -Recurse -Force $InputDir, $OutputDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $InputDir, $OutputDir | Out-Null

mvn -q package -DskipTests dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$InputDir"
Copy-Item (Join-Path $RootDir "target/$AppJar") (Join-Path $InputDir $AppJar)

jpackage `
  --type app-image `
  --name VoidReach `
  --app-version 1.0 `
  --input $InputDir `
  --main-jar $AppJar `
  --main-class com.crm.app.AppLauncher `
  --icon (Join-Path $RootDir "src/main/packaging/windows/VoidReach.ico") `
  --win-menu `
  --win-shortcut `
  --java-options "--enable-native-access=javafx.graphics" `
  --dest $OutputDir
