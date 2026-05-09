@echo off
echo ========================================
echo 智能学习表情分析系统 - 后端启动（虚拟环境）
echo ========================================
echo.

cd face_recognition_backend

echo [1/4] 检查Python环境...
python --version
if errorlevel 1 (
    echo 错误: 未找到Python，请先安装Python 3.9+
    pause
    exit /b 1
)

echo.
echo [2/4] 检查/创建虚拟环境...
if not exist "venv" (
    echo 正在创建虚拟环境...
    python -m venv venv
    if errorlevel 1 (
        echo 错误: 创建虚拟环境失败
        pause
        exit /b 1
    )
)

echo 激活虚拟环境...
call venv\Scripts\activate.bat

echo.
echo [3/4] 安装依赖...
pip install -r ..\requirements.txt
if errorlevel 1 (
    echo 错误: 安装依赖失败
    pause
    exit /b 1
)

echo.
echo [4/4] 初始化数据库并启动服务器...
python manage.py makemigrations
python manage.py migrate

echo.
echo 后端服务将运行在: http://localhost:8000
echo 按 Ctrl+C 停止服务
echo.
python manage.py runserver 0.0.0.0:8000

pause
