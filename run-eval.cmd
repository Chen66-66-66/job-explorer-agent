@echo off
REM Run the extraction and eligibility evaluation. Needs LLM_PROVIDER and LLM_API_KEY.
REM Reports are written to eval\reports (ignored by git).
if not exist "%PUBLIC%\jdk-tmp" mkdir "%PUBLIC%\jdk-tmp"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%PUBLIC%\jdk-tmp"
set "EVAL_DIR=%~dp0eval"
cd /d "%~dp0backend"
call .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.arguments=--jobexplorer.eval.dir=%EVAL_DIR% --spring.main.web-application-type=none --jobexplorer.demo-data=false --spring.datasource.url=jdbc:h2:mem:eval"
