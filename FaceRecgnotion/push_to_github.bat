@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================================
echo   GitHub Push Helper - Face Recognition System
echo ============================================================
echo.

echo [INFO] Current Git Status:
git branch
echo.
git remote -v
echo.

echo [STEP 1] Checking network connectivity...
ping github.com -n 2 >nul
if errorlevel 1 (
    echo [WARNING] Cannot ping GitHub.com - check your internet connection
) else (
    echo [OK] GitHub.com is reachable
)
echo.

echo [STEP 2] Attempting to push to GitHub...
echo.

REM Try HTTP/2 first
echo Trying HTTPS (HTTP/2)...
git push -u origin main --force 2>&1
if not errorlevel 1 (
    echo.
    echo [SUCCESS] Push completed!
    goto :success
)

echo.
echo [WARNING] HTTPS failed, trying alternative methods...
echo.

REM Try with postBuffer increased
echo Increasing git buffer size...
git config --global http.postBuffer 524288000
git push -u origin main --force 2>&1
if not errorlevel 1 (
    echo.
    echo [SUCCESS] Push completed with increased buffer!
    goto :success
)

echo.
echo [ALTERNATIVE] If automatic push fails, please run manually:
echo.
echo   Option 1: Check proxy settings
echo     git config --global http.proxy
echo     git config --global https.proxy
echo.
echo   Option 2: Use SSH instead of HTTPS
echo     git remote set-url origin git@github.com:Owen-h-1/Student_FaceRecgnotion.git
echo     git push -u origin main --force
echo.
echo   Option 3: Push manually in Git Bash or VS Code
echo     Open this folder in VS Code or Git Bash and run:
echo     git push -u origin main --force
echo.

goto :end

:success
echo.
echo ============================================================
echo   ✅ SUCCESS! Project uploaded to GitHub
echo ============================================================
echo.
echo Repository URL: https://github.com/Owen-h-1/Student_FaceRecgnotion
echo Branch: main
echo Files committed: 41 files
echo.

:end
echo Press any key to exit...
pause >nul
