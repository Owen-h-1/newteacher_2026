@echo off
echo ========================================
echo 智能学习表情分析系统 - 前端启动
echo ========================================
echo.

cd face_recognition_frontend

echo [1/2] 检查Node.js环境...
node --version
if errorlevel 1 (
    echo 错误: 未找到Node.js，请先安装Node.js 18+
    pause
    exit /b 1
)

echo.
echo [2/2] 检查依赖...
if not exist "node_modules" (
    echo 正在安装依赖...
    call npm install
)

echo.
echo 启动Next.js开发服务器...
echo.
echo 前端服务将运行在: http://localhost:3000
echo 按 Ctrl+C 停止服务
echo.
call npm run dev

pause
