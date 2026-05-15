<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="brand-wrap">
        <div class="brand">智课云师</div>
        <div class="subtitle">管理员端</div>
      </div>
      <nav class="nav">
        <router-link to="/admin/teachers" active-class="active">
          <span class="icon">👩‍🏫</span>
          <span>老师管理</span>
        </router-link>
        <router-link to="/admin/students" active-class="active">
          <span class="icon">🧑‍🎓</span>
          <span>学生管理</span>
        </router-link>
        <router-link to="/admin/subjects" active-class="active">
          <span class="icon">📚</span>
          <span>学科管理</span>
        </router-link>
      </nav>
      <button class="logout" @click="logout">退出登录</button>
    </aside>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import { clearSession } from "@/utils/api";

const router = useRouter();
function logout() {
  clearSession();
  router.push("/login");
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: #f3f7fd;
}
.sidebar {
  width: 260px;
  background: linear-gradient(180deg, #0b1f2e 0%, #102d44 100%);
  color: #fff;
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.brand-wrap {
  margin-bottom: 6px;
}
.brand {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #9bc0e7;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.nav a {
  color: #c8ddf8;
  text-decoration: none;
  padding: 11px 12px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}
.nav a:hover {
  background: rgba(56, 136, 255, 0.16);
  border-color: rgba(106, 169, 255, 0.35);
}
.nav a.active {
  background: #1e6df2;
  color: #fff;
  box-shadow: 0 8px 20px rgba(30, 109, 242, 0.35);
}
.icon {
  width: 18px;
  text-align: center;
}
.logout {
  margin-top: auto;
  height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  cursor: pointer;
  background: transparent;
  color: #d5e7fb;
}
.logout:hover {
  background: rgba(255, 255, 255, 0.1);
}
.content {
  flex: 1;
  padding: 24px;
}
</style>
