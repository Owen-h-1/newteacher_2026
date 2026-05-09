# 智课云师（前后端版）

项目已从纯前端原型补齐为前后端联动：
- 前端：Vue 3 + Vite
- 后端：Node.js + Express
- 数据库：MySQL（首次启动自动建表和初始化基础数据）

## 1. 安装依赖

```bash
npm install
```

## 2. 配置 MySQL

1. 确保本机 MySQL 服务已启动（**可不预先建库**：后端启动时会自动执行 `CREATE DATABASE IF NOT EXISTS`）
2. 复制环境变量模板并填写连接信息（或使用项目根目录已有的 `.env`）

```bash
copy .env.example .env
```

## 3. 启动后端

```bash
npm run dev:server
```

默认监听 `http://localhost:3000`。

## 4. 启动前端

```bash
npm run dev
```

前端通过 Vite 代理把 `/api` 请求转发到后端。

## 5. 默认测试账号

- 教师：`teacher` / `123456`
- 学生：`student` / `123456`

## 6. 已打通的核心功能

- 注册（`/api/auth/register`）
- 登录（`/api/auth/login`）
- 教师发布作业（`/api/homework`）
- 学生查看作业列表（`/api/homework`）
- 学生提交作业（`/api/homework/:id/submit`）
- 教师签到管理（班级、签到码、签到记录、手动签到/撤销）
- 学生自主练习题库（题目列表、收藏）
- 学生 AI 问答（`/api/llm/chat`，默认优先云端开源模型）

## 7. AI 问答（免本地安装）

推荐直接配置云端开源模型（OpenRouter）：

```env
LLM_PROVIDER=auto
OPENROUTER_API_KEY=你的Key
OPENROUTER_MODEL=qwen/qwen3-8b:free
```

说明：
- `auto` 会优先尝试云端开源模型；
- 如果云端不可用，再自动尝试本地 Ollama；
- 你也可以手动固定：`LLM_PROVIDER=openrouter`。
