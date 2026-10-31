@echo off
rem SafeExpose launcher for Windows: safeexpose 8080 [name]
setlocal
set "ROOT=%~dp0"
if "%SAFEEXPOSE_JAR%"=="" set "SAFEEXPOSE_JAR=%ROOT%target\safeexpose.jar"
if not exist "%SAFEEXPOSE_JAR%" (
  echo safeexpose: building...
  pushd "%ROOT%" && call mvn -q clean package -DskipTests && popd
)
java -jar "%SAFEEXPOSE_JAR%" %*
