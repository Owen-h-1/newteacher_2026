@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================================
echo   Smart Learning System - Docker Deployment
echo   (VirtualTeacher2.0 + Face Recognition)
echo ============================================================
echo.

echo [INFO] Checking prerequisites...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed or not running!
    echo Please install Docker Desktop first.
    pause
    exit /b 1
)
echo [OK] Docker is available

docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose is not available!
    pause
    exit /b 1
)
echo [OK] Docker Compose is available

echo.
echo [STEP 1] Building Docker images...
echo This may take 5-10 minutes on first run...
echo.

call docker-compose -f docker-compose.integrated.yml build

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed! Check the error messages above.
    pause
    exit /b 1
)

echo.
echo [STEP 2] Starting all services...
echo.

call docker-compose -f docker-compose.integrated.yml up -d

if errorlevel 1 (
    echo.
    echo [ERROR] Failed to start services!
    pause
    exit /b 1
)

echo.
echo Waiting for services to be ready...
timeout /t 15 /nobreak >nul

echo.
echo ============================================================
echo   ALL SERVICES STARTED SUCCESSFULLY!
echo ============================================================
echo.
echo Services:
echo   +------------------------------------------+
echo   | Service              | URL             |
echo   +------------------------------------------+
echo   | AI Teacher Backend   | :8000           |
echo   | AI Teacher Frontend  | http://localhost:3000 |
echo   | Face Recognition     | :8080           |
echo   | Nginx Gateway       | http://localhost:80   |
echo   +------------------------------------------+
echo.
echo Features Enabled:
echo   ✅ AI Digital Human Conversation
echo   ✅ Real-time Facial Expression Recognition
echo   ✅ Learning State Analysis
echo   ✅ Teaching Strategy Adjustment
echo   ✅ Intervention Alerts
echo.
echo Usage:
echo   1. Open browser: http://localhost:3000
echo   2. Click "表情" button in top-right corner
echo   3. Start face recognition monitoring
echo   4. Observe real-time expression analysis
echo   5. Watch teaching adjustments appear automatically
echo.
echo Management Commands:
echo   - View logs: docker-compose -f docker-compose.integrated.yml logs -f
echo   - Stop all:  docker-compose -f docker-compose.integrated.yml down
echo   - Restart:   docker-compose -f docker-compose.integrated.yml restart
echo.
echo Press any key to exit...
pause >nul
