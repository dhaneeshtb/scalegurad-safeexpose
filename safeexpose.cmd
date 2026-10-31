@echo off
rem SafeExpose launcher for Windows: safeexpose 8080 [name]
setlocal
where java >nul 2>nul
if errorlevel 1 (
  echo safeexpose: Java 11 or newer is required but was not found. Install it with:
  echo   winget install EclipseAdoptium.Temurin.17.JRE
  echo or from https://adoptium.net
  exit /b 1
)
set "ROOT=%~dp0"
if "%SAFEEXPOSE_JAR%"=="" set "SAFEEXPOSE_JAR=%ROOT%target\safeexpose.jar"
if not exist "%SAFEEXPOSE_JAR%" (
  echo safeexpose: building...
  pushd "%ROOT%" && call mvn -q clean package -DskipTests && popd
)
java -jar "%SAFEEXPOSE_JAR%" %*
