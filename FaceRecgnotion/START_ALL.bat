@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================================
echo   Smart Learning System - Complete Startup Script
echo ============================================================
echo.
echo This script will start:
echo   1. VirtualTeacher2.0 (AI Digital Human) - Port 80, 8000
echo   2. Face Recognition System (Expression Analysis) - Port 3000, 8080
echo.
echo Press any key to continue...
pause >nul

echo.
echo [STEP 1/4] Checking system requirements...
echo.

python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found!
    pause
    exit /b 1
)
echo [OK] Python installed

node --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found!
    pause
    exit /b 1
)
echo [OK] Node.js installed

echo.
echo [STEP 2/4] Starting VirtualTeacher2.0 Backend (Port 8000)...
echo.

cd d:\hu\VirtualTeacher2.0\domain-chatbot

start "VT-Backend" cmd /k python manage.py runserver 0.0.0.0:8000

echo [WAITING] Waiting for VirtualTeacher2.0 backend to start...
timeout /t 5 /nobreak >nul

echo [CHECKING] Testing VirtualTeacher2.0 backend...
curl -s http://localhost:8000/admin/ >nul 2>&1
if errorlevel 1 (
    echo [WARNING] VirtualTeacher2.0 backend may still be starting...
    timeout /t 3 /nobreak >nul
)

echo [OK] VirtualTeacher2.0 backend started on port 8000
echo.

echo [STEP 3/4] Starting Face Recognition Backend (Port 8080)...
echo.

cd d:\hu\FaceRecgnotion\face_recognition_backend

if not exist "venv" (
    echo Creating virtual environment...
    python -m venv venv
)

call venv\Scripts\activate.bat

echo Installing dependencies...
pip install -q -r ..\requirements.txt

echo Initializing database...
python manage.py makemigrations >nul 2>&1
python manage.py migrate >nul 2>&1

start "FaceRecognition-Backend" cmd /k python manage.py runserver 0.0.0.0:8080

echo [WAITING] Waiting for Face Recognition backend to start...
timeout /t 5 /nobreak >nul

echo [OK] Face Recognition backend started on port 8080
echo.

echo [STEP 4/4] Starting Frontend Services...
echo.

echo Starting VirtualTeacher2.0 Frontend (Port 3000)...
cd d:\hu\VirtualTeacher2.0\domain-chatvrm
if not exist "node_modules" (
    npm install --silent
)
start "VT-Frontend" cmd /k npm run dev

timeout /t 8 /nobreak >nul

echo Starting Face Recognition Frontend (Port 3001)...
cd d:\hu\FaceRecgnotion\face_recognition_frontend
if not exist "node_modules" (
    npm install --silent
)
start "FaceRecognition-Frontend" cmd /k set PORT=3001 && npm run dev

timeout /t 8 /nobreak >nul

echo.
echo ============================================================
echo   ALL SYSTEMS STARTED SUCCESSFULLY!
echo ============================================================
echo.
echo Services Running:
echo   +------------------------------------------+
echo   | Service                    | URL         |
echo   +------------------------------------------+
echo   | VirtualTeacher2.0 Backend | :8000       |
echo   | Face Recognition Backend | :8080       |
echo   | VirtualTeacher2.0 Frontend| http://localhost:3000 |
echo   | Face Recognition UI      | http://localhost:3001 |
echo   +------------------------------------------+
echo.
echo Access Points:
echo   - AI Digital Human Chat:     http://localhost:3000
echo   - Expression Analysis:      http://localhost:3001
echo.
echo To stop all services, close the terminal windows or press Ctrl+C in each window.
echo.
echo Press any key to exit this script...
pause >nul

cd /d "%~dp0"
