@echo off
chcp 65001 >nul
echo ========================================
echo  VirtualTeacher2.0 + FaceRecgnotion 集成系统 Docker 重建部署脚本
echo ========================================
echo.

cd /d d:\hu

echo [步骤 1/6] 检查 Docker 环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: Docker 未安装或未启动！
    echo 请先安装并启动 Docker Desktop
    pause
    exit /b 1
)
echo ✅ Docker 环境正常
echo.

echo [步骤 2/6] 停止当前运行的容器...
docker-compose -f VirtualTeacher2.0\installer\docker-compose.yaml down 2>nul
echo ✅ 容器已停止
echo.

echo [步骤 3/6] 重新构建 ChatVRM 前端镜像（包含人脸识别功能）...
echo ⏳ 这可能需要几分钟时间，请耐心等待...
echo.
cd VirtualTeacher2.0
docker build -f infrastructure-packaging\Dockerfile.ChatVRM -t okapi0129/virtualwife-chatvrm:face-recognition --build-arg CHATVRM_TAG=face-recognition .
if errorlevel 1 (
    echo ❌ ChatVRM 镜像构建失败！
    pause
    exit /b 1
)
echo ✅ ChatVRM 镜像构建成功（含人脸识别功能）
echo.

echo [步骤 4/6] 构建人脸识别后端镜像...
echo ⏳ 构建中...
echo.
cd ..\FaceRecgnotion
docker build -f face_recognition_backend\Dockerfile -t face-recognition-backend:latest .
if errorlevel 1 (
    echo ❌ 人脸识别后端镜像构建失败！
    pause
    exit /b 1
)
echo ✅ 人脸识别后端镜像构建成功
echo.

echo [步骤 5/6] 启动集成系统...
echo ⏳ 启动所有服务...
echo.
cd ..\
docker-compose -f docker-compose.integrated.yml up -d
if errorlevel 1 (
    echo ❌ 启动失败！检查日志: docker-compose -f docker-compose.integrated.yml logs
    pause
    exit /b 1
)
echo ✅ 所有服务已启动
echo.

echo [步骤 6/6] 验证服务状态...
timeout /t 10 /nobreak >nul
echo.
echo ========================================
echo  📊 服务状态检查
echo ========================================
echo.
docker-compose -f docker-compose.integrated.yml ps
echo.
echo ========================================
echo  ✅ 集成系统部署完成！
echo ========================================
echo.
echo  访问地址:
echo    🌐 主界面: http://localhost
echo    👤 AI数字人: http://localhost/chatvrm/
echo    🔍 人脸识别API: http://localhost:8080/api/
echo.
echo  使用方法:
echo    1. 打开浏览器访问 http://localhost
echo    2. 点击右上角 "🎭 表情" 按钮
echo    3. 允许浏览器使用摄像头
echo    4. 开始实时表情识别教学辅助
echo.
pause