# SafeExpose installer for Windows (PowerShell). Needs Java 11+.
#   irm https://raw.githubusercontent.com/dhaneeshtb/scalegurad-safeexpose/main/install.ps1 | iex
$ErrorActionPreference = "Stop"
$repo = "dhaneeshtb/scalegurad-safeexpose"
$dir = if ($env:SAFEEXPOSE_HOME) { $env:SAFEEXPOSE_HOME } else { Join-Path $env:USERPROFILE ".safeexpose" }
$url = if ($env:SAFEEXPOSE_URL) { $env:SAFEEXPOSE_URL } else { "https://raw.githubusercontent.com/$repo/main/dist/safeexpose.jar" }
$javaMissing = -not (Get-Command java -ErrorAction SilentlyContinue)
if ($javaMissing) { Write-Warning "Java was not found. SafeExpose needs Java 11 or newer. Install it with:  winget install EclipseAdoptium.Temurin.17.JRE   (or https://adoptium.net)" }
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$jar = Join-Path $dir "safeexpose.jar"
Invoke-WebRequest -Uri $url -OutFile "$jar.part"
try {
  $want = ((Invoke-WebRequest -Uri "$url.sha256").Content.Trim() -split "\s+")[0]
  if ((Get-FileHash "$jar.part" -Algorithm SHA256).Hash -ne $want.ToUpper()) { Remove-Item "$jar.part"; throw "checksum mismatch, not installed" }
} catch [System.Net.WebException] { }
Move-Item -Force "$jar.part" $jar
Set-Content -Path (Join-Path $dir "safeexpose.cmd") -Value "@echo off`r`nwhere java >nul 2>nul || (echo Java 11 or newer is required. Install: winget install EclipseAdoptium.Temurin.17.JRE & exit /b 1)`r`njava -jar `"$jar`" %*"
$user = [Environment]::GetEnvironmentVariable("Path", "User")
if ($user -notlike "*$dir*") { [Environment]::SetEnvironmentVariable("Path", "$user;$dir", "User"); Write-Host "Added $dir to your PATH (open a new terminal)." }
if ($javaMissing) { Write-Host "Installed, but install Java 11+ (see the warning above) before running safeexpose." } else { Write-Host "Installed. Try:  safeexpose 8080" }
