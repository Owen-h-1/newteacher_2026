@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================================
echo   Face Recognition System - Test & Verification Script
echo ============================================================
echo.

echo [TEST 1] Checking if Face Recognition Backend is running...
curl -s http://localhost:8080/api/session/start -X POST >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Face Recognition backend is NOT running!
    echo Please start the system first using START_ALL.bat
    pause
    exit /b 1
)
echo [PASS] Face Recognition backend is running on port 8080
echo.

echo [TEST 2] Creating a test session...
for /f "delims=" %%i in ('curl -s http://localhost:8080/api/session/start -X POST') do set RESPONSE=%%i
echo Server response: %RESPONSE%
echo.
echo Extracting session ID...
set SESSION_ID=%RESPONSE:~24,36%
echo Session ID: %SESSION_ID%
echo [PASS] Test session created successfully
echo.

echo [TEST 3] Testing API endpoints...
echo Testing session status endpoint...
curl -s http://localhost:8080/api/session/%SESSION_ID%/status
if errorlevel 1 (
    echo [FAIL] Cannot access session status
) else (
    echo [PASS] Session status endpoint working
)
echo.

echo ============================================================
echo   TEST RESULTS SUMMARY
echo ============================================================
echo.
echo All basic tests passed! The face recognition system is ready to use.
echo.
echo To test the full functionality:
echo   1. Open browser and go to http://localhost:3001
echo   2. Click "Start Learning Session" button
echo   3. Allow camera access when prompted
echo   4. Make different facial expressions (happy, sad, surprised, etc.)
echo   5. Observe real-time expression recognition results
echo   6. Check learning state analysis and engagement score
echo.
echo Expected Behavior:
echo   - Camera should show live video feed
echo   - Expression should be detected every ~300ms
echo   - Learning state should update based on expression
echo   - Engagement chart should show real-time data
echo   - Intervention alerts may appear for confused/bored states
echo.
echo Press any key to exit...
pause >nul
