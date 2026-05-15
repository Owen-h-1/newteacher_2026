# 智课云师（Spring Boot 后端版）

项目已从纯前端原型补齐为前后端联动：
- 前端：Vue 3 + Vite
- 后端：Spring Boot（Java 17）
- 数据库：MySQL（首次启动自动建表和初始化基础数据）

## 1. 安装依赖（前端）

```bash
npm install
```

## 2. 安装 Java

- JDK：建议 Java 17+（你当前是 Java 21，也可用）
- Maven 可不全局安装：项目已内置 Maven Wrapper（`backend/mvnw`）

## 3. 配置数据库（二选一）

### 方式 A：Docker 起 MySQL（推荐，无需本机单独安装 MySQL）

1. 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 并确保已启动。
2. 在项目根目录执行：

```bash
docker compose up -d
```

3. 等待容器就绪（约十几秒），可用 `docker compose ps` 查看，`STATUS` 为 `healthy` 即可。
4. 复制环境变量（默认已与 compose 里 root 密码、库名一致）：

```bash
copy .env.example .env
```

（macOS / Linux：`cp .env.example .env`）

若本机 **3306 已被占用**，可改 `docker-compose.yml` 里端口为 `"3307:3306"`，并在 `.env` 中设置 `DB_PORT=3307`。

停止数据库：`docker compose down`；**保留数据**再启动仍用同一命令 `up -d`。若要清空数据卷：`docker compose down -v`。

### 方式 B：本机已安装的 MySQL

1. 确保 MySQL 服务已启动（**可不预先建库**：后端启动时会自动执行 `CREATE DATABASE IF NOT EXISTS`）。
2. 复制环境变量并按你的实例修改 `DB_*`：

```bash
copy .env.example .env
```

## 4. 启动后端（Spring Boot）

```bash
npm run dev:server
```

默认监听 `http://localhost:8080`。

## 5. 启动前端

```bash
npm run dev
```

前端通过 Vite 代理把 `/api` 请求转发到后端。

## 6. 默认测试账号

- 教师：`teacher` / `123456`
- 学生：`student` / `123456`

## 7. 接口迁移状态

- 后端接口已迁移到 `backend/`（Spring Boot）
- 旧 Node.js 后端目录 `server/` 已下线，不再作为运行入口

## 8. AI 问答（免本地安装）

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
