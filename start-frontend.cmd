@echo off
REM Start the frontend on http://localhost:5173 (requests to /api are proxied to the backend)
cd /d "%~dp0frontend"
if not exist node_modules call npm install
call npm run dev
