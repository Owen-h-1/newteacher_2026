# 智能学习表情分析系统

基于面部表情识别的智能学习辅助系统，实现个性化学习体验。

## 功能特性

- 🎭 **表情识别** - 实时识别8种面部表情（开心、悲伤、愤怒、惊讶、中性、困惑、无聊、专注）
- 📈 **状态分析** - 将表情映射到学习状态（积极参与、感到困难、好奇探索、困惑不解、注意力分散等）
- 📊 **数据可视化** - 实时展示学习参与度变化曲线
- 🔔 **智能提示** - 根据学习状态自动提供干预建议
- 📚 **知识触发** - 自动触发相关知识点的讲解

## 技术架构

### 后端
- Django 4.2.1 - Web框架
- Django REST Framework - API接口
- OpenCV + MediaPipe - 人脸检测和表情识别
- SQLite - 数据存储

### 前端
- Next.js 14.0.0 - React框架
- React 18.2.0 - UI库
- Recharts - 数据可视化
- Tailwind CSS - 样式框架

## 项目结构

```
FaceRecgnotion/
├── face_recognition_backend/       # Django后端
│   ├── face_recognition_backend/   # 项目配置
│   ├── face_recognition_app/       # 表情识别应用
│   │   ├── models.py               # 数据模型
│   │   ├── views.py                # API视图
│   │   ├── urls.py                 # URL路由
│   │   ├── expression_engine.py    # 表情识别引擎
│   │   └── learning_state_mapper.py # 学习状态映射
│   └── manage.py
├── face_recognition_frontend/      # Next.js前端
│   ├── src/
│   │   ├── components/             # React组件
│   │   │   ├── CameraCapture.tsx   # 摄像头采集
│   │   │   ├── ExpressionDisplay.tsx # 表情显示
│   │   │   ├── EngagementChart.tsx # 参与度图表
│   │   │   └── InterventionAlert.tsx # 干预提示
│   │   ├── pages/
│   │   │   └── index.tsx           # 主页面
│   │   └── styles/
│   └── package.json
└── requirements.txt                 # Python依赖
```

## 快速开始

### 环境要求

- Python 3.9+
- Node.js 18+
- 摄像头设备

### 后端启动

1. 安装Python依赖：
```bash
cd FaceRecgnotion
pip install -r requirements.txt
```

2. 初始化数据库：
```bash
cd face_recognition_backend
python manage.py makemigrations
python manage.py migrate
```

3. 启动后端服务：
```bash
python manage.py runserver 0.0.0.0:8000
```

后端服务将运行在 `http://localhost:8000`

### 前端启动

1. 安装Node依赖：
```bash
cd face_recognition_frontend
npm install
```

2. 启动开发服务器：
```bash
npm run dev
```

前端服务将运行在 `http://localhost:3000`

## 使用说明

1. 打开浏览器访问 `http://localhost:3000`
2. 点击"开始学习会话"按钮
3. 允许浏览器访问摄像头
4. 系统将开始实时分析您的面部表情
5. 根据学习状态，系统会提供相应的干预建议
6. 点击"结束学习会话"按钮停止分析

## API接口

### 会话管理

- `POST /api/session/start` - 开始新的学习会话
- `POST /api/session/end` - 结束学习会话
- `GET /api/session/{session_id}/status` - 获取会话状态
- `GET /api/session/{session_id}/evaluation` - 获取学习评估

### 表情分析

- `POST /api/analyze` - 分析单帧图像

### 干预触发

- `POST /api/trigger` - 触发或忽略知识点讲解

## 表情类型

系统支持识别以下8种表情：

- 😊 开心
- 😢 悲伤
- 😠 愤怒
- 😲 惊讶
- 😐 中性
- 😕 困惑
- 😴 无聊
- 🤔 专注

## 学习状态映射

| 表情组合 | 学习状态 | 描述 |
|---------|---------|------|
| 开心/专注 | 积极参与 | 学生对内容感兴趣 |
| 困惑 | 困惑不解 | 需要重新解释概念 |
| 悲伤/愤怒 | 感到困难 | 需要放慢节奏 |
| 惊讶 | 好奇探索 | 可以深入探讨 |
| 无聊 | 注意力分散 | 需要变换教学方式 |

## 性能指标

- 识别延迟: < 300ms
- 表情分类准确率: > 85%
- 支持浏览器: Chrome, Firefox, Safari, Edge

## 注意事项

1. 确保在光照良好的环境中使用
2. 保持摄像头前适当距离（50-80cm）
3. 首次使用需要允许浏览器访问摄像头
4. 后端和前端服务需要同时运行

## 扩展功能

系统设计为可扩展，可以轻松添加：

- 更多表情类型
- 自定义学习状态映射
- 与VirtualTeacher2.0项目集成
- 历史数据分析和报表
- 多语言支持

## 许可证

MIT License
