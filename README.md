# 智慧教育平台 - 整合版

这是一个整合了多个智慧教育功能的平台，包含 AI 数字人教师、人脸识别考勤、在线教学等功能。

## 📁 项目结构

```
newteacher_2026/
├── zhi-keyunshi-teammate/          # 主平台（Vue.js + Node.js）
│   ├── src/                        # 前端源码
│   ├── server/                     # 后端服务
│   ├── public/                     # 静态资源
│   └── package.json                # 依赖配置
│
├── FaceRecgnotion/                 # 人脸识别模块
│   ├── face_recognition_backend/   # Django 后端
│   ├── face_recognition_frontend/  # Next.js 前端
│   └── requirements.txt            # Python 依赖
│
└── VirtualTeacher2.0/              # AI 数字人教师
    ├── domain-chatbot/             # 聊天机器人后端
    ├── domain-chatvrm/             # VRM 数字人前端
    └── installer/                  # 部署脚本
```

## 🚀 快速开始

### 前置要求

- Node.js 18+
- Python 3.11+
- MySQL 8.0+
- Ollama（用于本地 LLM）

### 1. 克隆仓库

```bash
git clone https://github.com/Owen-h-1/newteacher_2026.git
cd newteacher_2026
```

### 2. 安装依赖

#### 主平台
```bash
cd zhi-keyunshi-teammate
npm install
```

#### 人脸识别模块
```bash
cd ../FaceRecgnotion
pip install -r requirements.txt
cd face_recognition_frontend
npm install
```

#### AI 数字人教师
```bash
cd ../../VirtualTeacher2.0/domain-chatvrm
npm install
cd ../domain-chatbot
pip install -r requirements.txt
```

### 3. 配置环境变量

复制 `.env.example` 为 `.env` 并填写配置：

```bash
# 主平台
cp zhi-keyunshi-teammate/.env.example zhi-keyunshi-teammate/.env

# 人脸识别
cp FaceRecgnotion/.env.example FaceRecgnotion/.env

# AI 数字人
cp VirtualTeacher2.0/installer/env_example VirtualTeacher2.0/.env
```

### 4. 启动服务

#### 方式一：使用启动脚本

```bash
# 主平台
cd zhi-keyunshi-teammate
npm run dev

# 人脸识别（新终端）
cd FaceRecgnotion
python START_ATTENDANCE.bat

# AI 数字人（新终端）
cd VirtualTeacher2.0/installer/windows
start.bat
```

#### 方式二：Docker 部署

```bash
cd VirtualTeacher2.0/installer
docker-compose up -d
```

## 📱 功能模块

### 1. 主平台 (zhi-keyunshi-teammate)

- **学生端**
  - 作业管理
  - 自测练习
  - 学习中心
  - AI 问答

- **教师端**
  - 班级管理
  - 作业发布
  - 签到系统
  - AI 课件生成
  - 教学设计

**访问地址：** http://localhost:5174

### 2. 人脸识别模块 (FaceRecgnotion)

- **表情识别**
  - 实时摄像头捕捉
  - 8 种表情识别
  - 学习状态映射
  - 参与度评分

- **考勤管理**
  - 学生注册
  - 班级合影识别
  - 考勤统计

**访问地址：**
- 表情识别：http://localhost:3001
- 考勤管理：http://localhost:3001/attendance

### 3. AI 数字人教师 (VirtualTeacher2.0)

- VRM 3D 数字人
- 实时对话
- 表情动画
- 语音合成

**访问地址：** http://localhost:3000

## 🔧 配置说明

### 数据库配置

主平台使用 MySQL，人脸识别使用 SQLite。

```env
# MySQL 配置
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=root
DB_PASSWORD=your_password
DB_NAME=zhi_keyunshi
```

### LLM 配置

支持 Ollama 本地模型：

```env
LLM_PROVIDER=ollama
OLLAMA_API_BASE=http://127.0.0.1:11434
OLLAMA_MODEL=qwen2.5:1.5b
```

## 📖 详细文档

- [主平台文档](./zhi-keyunshi-teammate/README.md)
- [人脸识别文档](./FaceRecgnotion/ATTENDANCE_GUIDE.md)
- [AI 数字人文档](./VirtualTeacher2.0/README.md)

## 🛠️ 技术栈

### 前端
- Vue 3 + Vite
- Next.js 14
- React 18
- Tailwind CSS

### 后端
- Node.js + Express
- Django 4.2
- Python 3.11

### AI/ML
- OpenCV
- Ollama
- VRM

### 数据库
- MySQL 8.0
- SQLite

## 📝 开发指南

### 代码规范

- 使用 ESLint 进行代码检查
- 使用 Prettier 进行代码格式化
- 遵循 Vue 官方风格指南

### 提交规范

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式调整
refactor: 代码重构
test: 测试相关
chore: 构建/工具相关
```

## 🐛 常见问题

### 1. 端口被占用

修改对应项目的端口号：
- 主平台：`vite.config.js`
- 人脸识别：`next.config.js`
- AI 数字人：`package.json`

### 2. 数据库连接失败

检查 MySQL 服务是否启动，确认 `.env` 中的配置正确。

### 3. Ollama 连接失败

确保 Ollama 服务已启动：
```bash
ollama serve
ollama pull qwen2.5:1.5b
```

## 📄 许可证

MIT License

## 👥 贡献者

- Owen-h-1

## 🙏 致谢

感谢所有开源项目的贡献者。
