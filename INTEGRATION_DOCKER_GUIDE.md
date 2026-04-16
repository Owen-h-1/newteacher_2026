# 🎭 VirtualTeacher2.0 + FaceRecgnotion Docker 集成部署指南

## 📋 概述

本指南将帮助您将**人脸表情识别功能**集成到现有的 **VirtualTeacher2.0 AI数字人老师系统**中，并通过 **Docker** 完整部署。

### 集成后的新功能

- ✅ **实时人脸表情识别**：通过摄像头捕捉学生面部表情（8种表情）
- ✅ **学习状态智能分析**：自动判断学生的学习状态（专注/困惑/沮丧等6种状态）
- ✅ **教学策略自动调整**：根据学生表情动态调整教学节奏和内容
- ✅ **干预触发机制**：检测到异常学习状态时自动触发教学干预
- ✅ **VRM情感反馈**：AI数字人根据学生状态做出相应的表情和动作反应

---

## 🏗️ 系统架构

### 服务组成（4个Docker容器）

```
┌─────────────────────────────────────────────────────────────┐
│                     Nginx Gateway (:80)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │   ChatVRM    │  │   ChatBot    │  │ FaceRecognition  │   │
│  │  (前端:3000)  │  │ (后端:8000)  │  │  (后端:8080)     │   │
│  │              │  │              │  │                  │   │
│  │ • Next.js    │  │ • Django     │  │ • Django         │   │
│  │ • React      │  │ • Ollama LLM │  │ • OpenCV         │   │
│  │ • 人脸识别面板│  │ • WebSocket  │  │ • 表情识别引擎    │   │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘   │
│         │                 │                    │             │
│         └─────────────────┼────────────────────┘             │
│                           │                                  │
│                    smart-learning-network                     │
└─────────────────────────────────────────────────────────────┘
                              │
                         ┌────▼────┐
                         │  Ollama │
                         │ (LLM)   │
                         └─────────┘
```

### 数据流

```
摄像头 → 浏览器 → FaceRecognitionPanel组件
                    ↓
              faceRecognitionApi.ts (调用FaceRecgnotion API)
                    ↓
           FaceRecgnotion后端 (:8080)
                    ↓
        表情分析结果返回 → teachingStrategyService.ts
                    ↓
        教学调整建议 → index.tsx主页面
                    ↓
        VRM表情/动作调整 + 教学内容优化
```

---

## 🚀 快速开始（一键部署）

### 前置条件

1. ✅ **Docker Desktop** 已安装并运行
2. ✅ **Ollama** 已安装并运行（用于LLM）
3. ✅ **Git** 仓库已克隆到 `d:\hu\` 目录：
   - `d:\hu\VirtualTeacher2.0\`
   - `d:\hu\FaceRecgnotion\`

### 方法一：使用自动化脚本（推荐）⭐

```bash
# 双击运行或CMD中执行
d:\hu\rebuild_and_deploy_integrated.bat
```

脚本将自动完成以下操作：
1. ✅ 检查Docker环境
2. ✅ 停止当前运行的容器
3. ✅ 重新构建ChatVRM镜像（包含人脸识别代码）
4. ✅ 构建FaceRecgnotion后端镜像
5. ✅ 启动所有4个服务
6. ✅ 验证服务状态

### 方法二：手动逐步执行

#### 步骤 1: 停止现有容器

```bash
cd d:\hu\VirtualTeacher2.0
docker-compose -f installer/docker-compose.yaml down
```

#### 步骤 2: 重建 ChatVRM 前端镜像（关键！）

**这一步很重要！** 必须重新构建前端镜像才能包含我们新添加的人脸识别功能：

```bash
cd d:\hu\VirtualTeacher2.0
docker build -f infrastructure-packaging/Dockerfile.ChatVRM \
  -t okapi0129/virtualwife-chatvrm:face-recognition \
  --build-arg CHATVRM_TAG=face-recognition .
```

⏳ **预计时间**: 5-10分钟（取决于网络速度）

#### 步骤 3: 构建人脸识别后端镜像

```bash
cd d:\hu\FaceRecgnotion
docker build -f face_recognition_backend/Dockerfile \
  -t face-recognition-backend:latest .
```

⏳ **预计时间**: 3-5分钟

#### 步骤 4: 使用集成版 docker-compose 启动所有服务

```bash
cd d:\hu
docker-compose -f docker-compose.integrated.yml up -d
```

#### 步骤 5: 验证部署成功

```bash
# 查看运行中的容器
docker-compose -f docker-compose.integrated.yml ps

# 应该看到4个容器都在运行:
# ✓ chatbot       - AI数字人后端
# ✓ chatvrm       - AI数字人前端（含人脸识别）
# ✓ facerecognition - 人脸识别后端
# ✓ nginx         - 反向代理网关
```

---

## 🔧 配置说明

### 环境变量配置

在 `d:\hu\VirtualTeacher2.0\installer\.env` 文件中配置（如果不存在则创建）：

```env
# 时区设置
TIMEZONE=Asia/Shanghai

# Ollama配置（如果使用外部Ollama）
OLLAMA_HOST=http://host.docker.internal:11434

# Nginx端口
NGINX_HTTP_PORT=80
NGINX_HTTPS_PORT=443

# 镜像标签（可选，默认latest）
CHATBOT_TAG=latest
CHATVRM_TAG=face-recognition  # ⚠️ 重要！使用新标签
GATEWAY_TAG=latest
```

### 端口映射

| 服务 | 容器内部端口 | 外部访问端口 | 说明 |
|------|------------|------------|------|
| **Nginx Gateway** | 80 | **80** | 主入口（浏览器访问） |
| **ChatBot** | 8000 | 8000 | Django后端API |
| **ChatVRM** | 3000 | 不暴露 | 通过Nginx代理访问 |
| **FaceRecognition** | 8080 | **8080** | 人脸识别API（调试用） |

---

## 📖 使用教程

### 启动人脸识别功能

1. **打开浏览器**访问 `http://localhost` 或 `http://localhost/chatvrm/`

2. **找到"🎭 表情"按钮**
   - 位置：页面右上角（靠近设置按钮）
   - 点击后会展开人脸识别面板

3. **允许摄像头权限**
   - 浏览器会弹出权限请求
   - 点击"允许"

4. **开始实时识别**
   - 面板会显示您的实时摄像头画面
   - 每300ms分析一次表情
   - 显示当前识别的表情、置信度、学习状态

### 界面元素说明

#### 📊 人脸识别面板（右侧滑入）

```
┌──────────────────────────────┐
│  🎭 人脸识别监控              │
│  ┌────────────────────────┐  │
│  │                        │  │
│  │    [摄像头画面]          │  │
│  │                        │  │
│  └────────────────────────┘  │
│                              │
│  当前表情: 😊 开心 (92%)      │
│  学习状态: 🟢 专注学习        │
│  参与度评分: 85/100          │
│                              │
│  📈 最近10次分析:            │
│  [████████░░] 专注: 70%      │
│  [██████░░░░] 开心: 50%      │
│                              │
│  [停止识别] [关闭面板]        │
└──────────────────────────────┘
```

#### 🎯 教学调整指示器（页面顶部）

当系统检测到需要调整教学时，会在顶部显示提示：

```
┌──────────────────────────────────────────────┐
│ 🎯 教学建议: 放慢节奏，详细解释当前知识点     │
│    原因: 检测到学生表现出困惑表情              │
│    优先级: 高 🔴                              │
└──────────────────────────────────────────────┘
```

**颜色含义**:
- 🔴 **橙色** = 高优先级（如：困惑、沮丧）
- 🟡 **黄色** = 中优先级（如：分心、疲劳）
- 🔵 **蓝色** = 低优先级（如：正常、好奇）

### 自动触发机制

系统会根据学生的表情自动做出响应：

| 学生表情 | 学习状态 | AI数字人反应 | 教学调整 |
|---------|---------|-------------|---------|
| 困惑/疑惑 | confused | 思考姿势(Thinking) | 详细解释，放慢节奏 |
| 沮丧/愤怒 | frustrated | 悲伤表情(Sad) | 降低难度，给予鼓励 |
| 无聊/疲倦 | disengaged | 愤怒表情(Angry) | 增加互动，改变方式 |
| 开心/兴奋 | happy | 兴奋表情(Excited) | 保持当前节奏 |
| 惊讶 | surprised | 正常表情 | 确认理解程度 |
| 平静 | neutral | 正常表情 | 继续教学 |

---

## 🔍 故障排查

### 常见问题

#### ❌ 问题1: 无法访问 http://localhost

**原因**: Nginx容器未启动或端口被占用

**解决方案**:
```bash
# 检查容器状态
docker ps -a | grep gateway

# 查看日志
docker logs gateway

# 重启Nginx
docker restart gateway
```

#### ❌ 问题2: 点击"🎭 表情"按钮无反应

**原因**: ChatVRM镜像未包含最新的人脸识别代码

**解决方案**:
```bash
# 重新构建镜像（必须！）
cd d:\hu\VirtualTeacher2.0
docker build -f infrastructure-packaging/Dockerfile.ChatVRM \
  -t okapi0129/virtualwife-chatvrm:face-recognition .

# 重启chatvrm容器
docker-compose -f d:\hu\docker-compose.integrated.yml restart chatvrm
```

#### ❌ 问题3: 摄像头无法打开

**原因**: 浏览器权限问题或HTTPS要求

**解决方案**:
1. 确保使用 `http://localhost` 访问（不是IP地址）
2. 检查浏览器摄像头权限设置
3. 尝试刷新页面后重新点击"表情"按钮

#### ❌ 问题4: 人脸识别显示"服务连接失败"

**原因**: FaceRecgnotion后端未启动或网络不通

**解决方案**:
```bash
# 检查facerecognition容器是否运行
docker ps | grep facerecognition

# 手动启动
docker start facerecognition

# 测试API连通性
curl http://localhost:8080/api/session/start -X POST
```

#### ❌ 问题5: 识别准确率低或延迟高

**原因**: 光线不足、摄像头质量、Docker资源限制

**解决方案**:
1. **改善光线**: 确保面部光照充足均匀
2. **调整摄像头位置**: 距离50-100cm，正对脸部
3. **增加Docker资源**:
   - 打开 Docker Desktop → Settings → Resources
   - 将CPU增加到4核以上
   - 将内存增加到8GB以上

#### ❌ 问题6: 教学调整提示不显示

**原因**: 需要积累足够的数据（至少20次分析）

**解决方案**:
- 保持人脸识别运行30秒以上
- 确保面部持续出现在摄像头范围内
- 检查浏览器控制台是否有错误（F12打开开发者工具）

---

## 🛠️ 维护命令

### 日常运维

```bash
# 查看所有服务状态
docker-compose -f d:\hu/docker-compose.integrated.yml ps

# 查看日志
docker-compose -f d:\hu/docker-compose.integrated.yml logs -f chatvrm
docker-compose -f d:\hu/docker-compose.integrated.yml logs -f facerecognition

# 重启单个服务
docker-compose -f d:\hu/docker-compose.integrated.yml restart chatvrm
docker-compose -f d:\hu/docker-compose.integrated.yml restart facerecognition

# 停止所有服务
docker-compose -f d:\hu/docker-compose.integrated.yml down

# 重新启动所有服务
docker-compose -f d:\hu/docker-compose.integrated.yml up -d
```

### 更新人脸识别代码后重新部署

```bash
# 1. 停止服务
docker-compose -f d:\hu/docker-compose.integrated.yml down

# 2. 重新构建ChatVRM镜像（包含新的修改）
cd d:\hu\VirtualTeacher2.0
docker build -f infrastructure-packaging/Dockerfile.ChatVRM \
  -t okapi0129/virtualwife-chatvrm:face-recognition-v2 .

# 3. 重新构建FaceRecgnotion镜像（如果有修改）
cd d:\hu\FaceRecgnotion
docker build -f face_recognition_backend/Dockerfile \
  -t face-recognition-backend:v2 .

# 4. 更新docker-compose.integrated.yml中的镜像标签（可选）

# 5. 重新启动
cd d:\hu
docker-compose -f docker-compose.integrated.yml up -d
```

### 清理资源

```bash
# 清理未使用的镜像（节省磁盘空间）
docker image prune -f

# 查看占用空间
docker system df
```

---

## 📊 性能优化建议

### Docker资源配置

对于生产环境使用，建议调整Docker资源配置：

**Docker Desktop → Settings → Resources**:
- CPUs: **4核或更多**
- Memory: **8GB或更多**
- Swap: **2GB**
- Disk Image Size: **60GB**

### 生产环境部署建议

1. **使用域名和SSL证书**（替换localhost）
2. **启用Ollama GPU加速**（如果有NVIDIA显卡）
3. **配置日志持久化**（避免容器重启丢失数据）
4. **设置健康检查**（自动重启故障容器）
5. **使用镜像版本管理**（便于回滚）

---

## 🔄 数据流程详解

### 完整的一次交互流程示例

```
1. 学生打开网页 → 加载ChatVRM前端
2. 学生点击"🎭 表情"按钮 → 展开FaceRecognitionPanel
3. 浏览器请求摄像头权限 → 用户允许
4. 开始每300ms捕获一帧图像 → 转为Base64
5. 调用POST /api/analyze → 发送到FaceRecgnotion后端
6. 后端OpenCV分析 → 返回表情结果JSON
7. 前端接收结果 → 显示在面板上
8. 同时传递给teachingStrategyService → 积累20次数据
9. 达到阈值 → 触发getTeachingAdjustment()
10. 显示教学调整建议 → VRM做出相应表情
11. 10秒后自动清除建议 → 继续监测
```

---

## 📞 技术支持

### 日志查看位置

```bash
# ChatVRM前端日志（含人脸识别相关）
docker logs chatvrm 2>&1 | grep -i "face\|expression"

# FaceRecgnotion后端日志
docker logs facerecognition 2>&1 | grep -i "expression\|analyze"

# Nginx访问日志（查看API调用）
docker logs gateway 2>&1 | grep "8080"
```

### 关键文件位置

| 文件 | 用途 | 所在容器 |
|------|------|---------|
| `domain-chatvrm/src/features/faceRecognition/` | API服务和教学策略 | chatvrm |
| `domain-chatvrm/src/components/FaceRecognitionPanel.tsx` | UI组件 | chatvrm |
| `domain-chatvrm/src/pages/index.tsx` | 主页面集成逻辑 | chatvrm |
| `face_recognition_backend/face_recognition_app/expression_engine.py` | OpenCV表情引擎 | facerecognition |
| `face_recognition_backend/face_recognition_app/views.py` | API接口 | facerecognition |

---

## ✅ 部署检查清单

部署完成后，请逐项确认：

- [ ] Docker Desktop正在运行
- [ ] 4个容器全部处于"Up"状态
- [ ] 可以访问 `http://localhost`
- [ ] 页面右上角有"🎭 表情"按钮
- [ ] 点击按钮后面板可以展开
- [ ] 允许摄像头后可以看到画面
- [ ] 可以看到实时的表情识别结果
- [ ] 当表情变化时，顶部会显示教学建议
- [ ] AI数字人会根据表情做出相应反应

全部勾选✅即表示部署成功！

---

## 🎉 总结

现在您已经拥有了一个完整的**智能教学辅助系统**：

- 🤖 **AI数字人老师**：通过Ollama LLM提供智能对话
- 👁️ **人脸表情识别**：实时监测学生学习状态
- 🧠 **智能教学调整**：根据表情动态优化教学内容
- 🎭 **情感交互反馈**：AI数字人做出人性化反应

祝您使用愉快！如有问题请参考上方故障排查章节。
