@echo off
REM Start the backend on http://localhost:8080
REM The JDK creates a Unix domain socket in the temp dir; a non-ASCII user name breaks it on Windows,
REM so point it at an ASCII-only folder.
if not exist "%PUBLIC%\jdk-tmp" mkdir "%PUBLIC%\jdk-tmp"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%PUBLIC%\jdk-tmp"
cd /d "%~dp0backend"
call .\mvnw.cmd spring-boot:run
