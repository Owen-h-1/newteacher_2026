@echo off
echo ========================================
echo 智能学习表情分析系统 - 后端启动
echo ========================================
echo.

cd face_recognition_backend

echo [1/3] 检查Python环境...
python --version
if errorlevel 1 (
    echo 错误: 未找到Python，请先安装Python 3.9+
    pause
    exit /b 1
)

echo.
echo [2/3] 初始化数据库...
python manage.py makemigrations
python manage.py migrate

echo.
echo [3/3] 启动Django服务器...
echo.
echo 后端服务将运行在: http://localhost:8000
echo 按 Ctrl+C 停止服务
echo.
python manage.py runserver 0.0.0.0:8000

pause
