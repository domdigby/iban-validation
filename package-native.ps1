<#
.SYNOPSIS
    Builds a self-contained Windows app image (bundled JRE included) so the
    IBAN validator can run on a PC with no Java installed at all.

.DESCRIPTION
    Runs `mvn clean package` to produce the shaded fat jar, then runs jpackage
    against an isolated input directory (containing only that jar) to avoid
    jpackage's --input and --dest overlapping (which causes a runaway
    recursive self-copy on Windows once jpackage starts copying --input into
    the app image that --dest is also writing into).

    Output: target\dist\iban-validator\ - copy this whole folder to any
    Windows PC and run iban-validator.exe from inside it. No Java required.
#>

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
Set-Location $projectRoot

Write-Host "==> mvn clean package"
mvn clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

$inputDir = Join-Path $projectRoot "target\jpackage-input"
$tempDir  = Join-Path $projectRoot "target\jpackage-temp"
$destDir  = Join-Path $projectRoot "target\dist"

foreach ($dir in @($inputDir, $tempDir, $destDir)) {
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
}
New-Item -ItemType Directory $inputDir | Out-Null

Copy-Item (Join-Path $projectRoot "target\iban-validator-1.0.0.jar") $inputDir

Write-Host "==> jpackage"
jpackage `
    --type app-image `
    --input $inputDir `
    --main-jar iban-validator-1.0.0.jar `
    --main-class com.affinis.Main `
    --name iban-validator `
    --app-version 1.0.0 `
    --win-console `
    --temp $tempDir `
    --dest $destDir
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Remove-Item -Recurse -Force $inputDir, $tempDir

Write-Host "==> Done: $destDir\iban-validator\iban-validator.exe"
Write-Host "    Copy the whole 'iban-validator' folder to any Windows PC and run iban-validator.exe from inside it."
