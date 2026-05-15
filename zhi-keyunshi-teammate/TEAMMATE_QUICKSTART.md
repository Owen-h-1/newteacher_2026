# Teammate Quick Start (Windows)

This package contains source code only. Build artifacts and local secrets are excluded.

## 1) Prerequisites

- Node.js 18+ (npm included)
- JDK 17+ (Java 21 is also fine)
- Docker Desktop (recommended for MySQL)

## 2) Install dependencies

Run in project root:

```bash
npm install
```

## 3) Prepare environment file

```bash
copy .env.example .env
```

If your local MySQL port is not `3306`, update `DB_PORT` in `.env`.

## 4) Start database (recommended)

```bash
docker compose up -d
```

Check status:

```bash
docker compose ps
```

`healthy` means ready.

## 5) Start backend

```bash
npm run dev:server
```

If backend reports `Port 8080 was already in use`, pick one:

- stop the process using 8080, or
- change `server.port` in `backend/src/main/resources/application.yml` to another port (for example 8081), and then update frontend proxy target in `vite.config.js` accordingly.

## 6) Start frontend

Open another terminal in project root:

```bash
npm run dev
```

Then open the URL printed by Vite (usually `http://localhost:5173`).

## 7) Optional build check

```bash
npm run build
cd backend && mvnw.cmd -DskipTests package
```

## 8) Default test accounts

- Teacher: `teacher` / `123456`
- Student: `student` / `123456`

