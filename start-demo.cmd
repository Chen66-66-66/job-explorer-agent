@echo off
REM Start the backend for a demo on http://localhost:8080
REM Uses a separate demo database (backend\data\demo.mv.db), so your everyday data is not touched.
REM To start the demo over, stop the backend and delete backend\data\demo.mv.db
if not exist "%PUBLIC%\jdk-tmp" mkdir "%PUBLIC%\jdk-tmp"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%PUBLIC%\jdk-tmp"
cd /d "%~dp0backend"
call .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:file:./data/demo --jobexplorer.demo-data=false"
