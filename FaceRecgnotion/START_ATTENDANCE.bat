@echo off
chcp 65001 >nul
echo ========================================
echo 学生到班人数统计系统 - 快速启动
echo ========================================
echo.

echo [1/3] 检查 Python 环境...
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ 未检测到 Python，请先安装 Python 3.10+
    pause
    exit /b 1
)
echo ✓ Python 环境正常

echo.
echo [2/3] 检查依赖包...
pip show face-recognition >nul 2>&1
if errorlevel 1 (
    echo.
    echo 正在安装依赖包...
    pip install -r requirements.txt
    if errorlevel 1 (
        echo ❌ 依赖安装失败
        pause
        exit /b 1
    )
)
echo ✓ 依赖包已安装

echo.
echo [3/3] 启动服务...
echo.
echo 正在启动后端服务...
start "Face Recognition Backend" cmd /k "cd face_recognition_backend && python manage.py runserver 0.0.0.0:8080"

timeout /t 3 /nobreak >nul

echo 正在启动前端服务...
start "Face Recognition Frontend" cmd /k "cd face_recognition_frontend && npm run dev"

echo.
echo ========================================
echo ✅ 系统启动成功！
echo ========================================
echo.
echo 📱 访问地址：
echo    前端界面：http://localhost:3000
echo    后端API：http://localhost:8080/api
echo    考勤管理：http://localhost:3000/attendance
echo.
echo 📖 使用说明：
echo    1. 访问 http://localhost:3000/attendance
echo    2. 在"学生注册"页面录入学生信息
echo    3. 在"上传合影"页面上传班级照片
echo    4. 在"考勤统计"页面查看考勤结果
echo.
echo 💡 提示：
echo    - 首次使用需要先注册学生
echo    - 确保照片清晰、光线充足
echo    - 关闭此窗口不会停止服务
echo.
pause
