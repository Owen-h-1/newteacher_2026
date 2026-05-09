@echo off
chcp 65001 >nul
cd /d "%~dp0"
cd face_recognition_backend

echo ========================================
echo Face Recognition Backend (Port 8080)
echo ========================================
echo.

echo Checking Python...
python --version
if errorlevel 1 (
    echo ERROR: Python not found. Please install Python 3.9+
    pause
    exit /b 1
)

echo.
echo Setting up virtual environment...
if not exist "venv" (
    python -m venv venv
)

call venv\Scripts\activate.bat

echo.
echo Installing dependencies...
pip install -r ..\requirements.txt

echo.
echo Initializing database...
python manage.py makemigrations
python manage.py migrate

echo.
echo ========================================
echo Server running at: http://localhost:8080
echo Press Ctrl+C to stop
echo ========================================
python manage.py runserver 0.0.0.0:8080

pause
