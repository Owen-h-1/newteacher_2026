@echo off
chcp 65001 >nul
cd /d "%~dp0"
cd face_recognition_frontend

echo ========================================
echo Face Recognition Frontend
echo ========================================
echo.

echo Checking Node.js...
node --version
if errorlevel 1 (
    echo ERROR: Node.js not found. Please install Node.js 18+
    pause
    exit /b 1
)

echo.
echo Installing dependencies if needed...
if not exist "node_modules" (
    npm install
)

echo.
echo ========================================
echo Server running at: http://localhost:3000
echo Press Ctrl+C to stop
echo ========================================
npm run dev

pause
