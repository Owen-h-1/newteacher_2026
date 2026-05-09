<template>
  <div class="login-page">
    <div class="login-backdrop" aria-hidden="true">
      <span class="orb orb-1"></span>
      <span class="orb orb-2"></span>
      <span class="orb orb-3"></span>
      <span class="grid-line"></span>
      <span class="grid-line grid-line-2"></span>
    </div>

    <div class="login-container">
      <section class="brand-side">
        <div class="brand-content">
          <div class="logo">
            <i class="fas fa-robot"></i>
            <span>智课云师</span>
          </div>

          <div class="brand-tagline">
            <span class="badge-ai">AI 赋能教育</span>
            <h2>智慧教育 · 因材施教</h2>
            <p>AI 数字人助教、专属学习路径与实时学情分析，让教与学更高效。</p>
          </div>

          <div class="feature-list">
            <div class="feature-item">
              <i class="fas fa-brain"></i>
              <div>
                <strong>智能分析</strong>
                <span>学情、作业、反馈一屏掌握</span>
              </div>
            </div>
            <div class="feature-item">
              <i class="fas fa-chalkboard-teacher"></i>
              <div>
                <strong>高效备课</strong>
                <span>课件生成、教学设计更轻松</span>
              </div>
            </div>
            <div class="feature-item">
              <i class="fas fa-video"></i>
              <div>
                <strong>沉浸互动</strong>
                <span>数字人视频互动，提升课堂参与感</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="form-side">
        <div class="form-card">
          <div class="form-header">
            <span class="section-label">欢迎回来</span>
            <h2>登录您的账号</h2>
            <p class="form-subtitle">请输入学号、工号或手机号继续使用系统</p>
          </div>

          <form @submit.prevent="handleLogin" class="login-form">
            <div class="form-group">
              <label for="username">账号</label>
              <div class="input-wrapper">
                <i class="fas fa-user"></i>
                <input
                  id="username"
                  v-model="username"
                  type="text"
                  placeholder="请输入学号/工号/手机号"
                  @blur="validateUsername"
                />
              </div>
              <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
            </div>

            <div class="form-group">
              <label for="password">密码</label>
              <div class="input-wrapper">
                <i class="fas fa-lock"></i>
                <input
                  id="password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码"
                  @blur="validatePassword"
                />
                <button
                  type="button"
                  class="password-toggle"
                  @click="showPassword = !showPassword"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                >
                  <i :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
                </button>
              </div>
              <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            </div>

            <div class="form-options">
              <label class="remember">
                <input type="checkbox" v-model="remember" />
                <span>记住账号</span>
              </label>
              <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
            </div>

            <button type="submit" class="btn-login" :disabled="isLogging">
              <i v-if="isLogging" class="fas fa-spinner fa-spin"></i>
              <span>{{ isLogging ? '登录中...' : '登 录' }}</span>
            </button>
          </form>

          <div class="register-link">
            还没有账号？ <router-link to="/register">立即注册</router-link>
          </div>
        </div>
      </section>
    </div>

    <PublicFooter />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { login, setSession, clearSession } from "@/utils/api";
import PublicFooter from "@/components/common/PublicFooter.vue";

const router = useRouter();
const username = ref("");
const password = ref("");
const remember = ref(false);
const showPassword = ref(false);
const isLogging = ref(false);
const errors = reactive({ username: "", password: "" });

onMounted(() => {
  const savedUsername = localStorage.getItem("zkys_remember_username");
  if (savedUsername) {
    username.value = savedUsername;
    remember.value = true;
  }
});

const validateUsername = () => {
  if (!username.value.trim()) {
    errors.username = "账号不能为空";
    return false;
  }
  errors.username = "";
  return true;
};

const validatePassword = () => {
  if (!password.value) {
    errors.password = "密码不能为空";
    return false;
  }
  errors.password = "";
  return true;
};

const handleLogin = async () => {
  const isUsernameValid = validateUsername();
  const isPasswordValid = validatePassword();

  if (!isUsernameValid || !isPasswordValid) return;

  isLogging.value = true;

  try {
    clearSession();
    const data = await login({
      username: username.value.trim(),
      password: password.value,
    });

    setSession({ token: data.token, user: data.user });

    if (remember.value) {
      localStorage.setItem("zkys_remember_username", username.value.trim());
    } else {
      localStorage.removeItem("zkys_remember_username");
    }

    if (data.user.role === "teacher") {
      router.push("/teacher/dashboard");
    } else {
      router.push("/student/learning");
    }
  } catch (err) {
    errors.password = err.message || "登录失败，请稍后重试";
  } finally {
    isLogging.value = false;
  }
};
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 20px 0;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(93, 135, 255, 0.22), transparent 30%),
    radial-gradient(circle at 85% 20%, rgba(255, 154, 82, 0.16), transparent 24%),
    linear-gradient(145deg, #081225 0%, #0f2344 50%, #162f59 100%);
}

.login-backdrop {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(4px);
  opacity: 0.55;
}

.orb-1 {
  width: 240px;
  height: 240px;
  left: -40px;
  top: 80px;
  background: rgba(76, 154, 255, 0.22);
}

.orb-2 {
  width: 180px;
  height: 180px;
  right: 80px;
  top: 120px;
  background: rgba(255, 141, 61, 0.18);
}

.orb-3 {
  width: 160px;
  height: 160px;
  right: 14%;
  bottom: 110px;
  background: rgba(123, 92, 255, 0.18);
}

.grid-line {
  position: absolute;
  inset: 10% auto auto 12%;
  width: 1px;
  height: 72%;
  background: linear-gradient(180deg, transparent, rgba(255, 255, 255, 0.18), transparent);
}

.grid-line-2 {
  left: auto;
  right: 12%;
  height: 58%;
}

.login-container {
  width: min(1220px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  border-radius: 34px;
  overflow: hidden;
  position: relative;
  z-index: 1;
  box-shadow: 0 28px 80px rgba(1, 9, 22, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(18px);
}

.brand-side {
  padding: 56px 48px;
  color: #fff;
  background: linear-gradient(145deg, rgba(10, 26, 52, 0.92), rgba(16, 46, 90, 0.88));
  position: relative;
  overflow: hidden;
}

.brand-side::after {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 20% 20%, rgba(255,255,255,0.12), transparent 28%);
  pointer-events: none;
}

.brand-content {
  position: relative;
  z-index: 1;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 44px;

  i {
    font-size: 34px;
    color: #ff9a44;
    filter: drop-shadow(0 0 12px rgba(255, 154, 68, 0.45));
  }

  span {
    font-size: 30px;
    font-weight: 800;
    letter-spacing: 0.02em;
    background: linear-gradient(90deg, #ffffff 0%, #b9d8ff 45%, #7ed6ff 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.badge-ai {
  display: inline-flex;
  align-items: center;
  padding: 7px 16px;
  border-radius: 999px;
  margin-bottom: 18px;
  font-size: 13px;
  font-weight: 700;
  color: #d6e9ff;
  background: rgba(128, 174, 255, 0.14);
  border: 1px solid rgba(158, 196, 255, 0.28);
}

.brand-tagline {
  margin-bottom: 42px;

  h2 {
    font-size: 34px;
    line-height: 1.2;
    margin-bottom: 14px;
  }

  p {
    max-width: 440px;
    color: rgba(220, 234, 255, 0.82);
    font-size: 16px;
    line-height: 1.75;
  }
}

.feature-list {
  display: grid;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);

  i {
    margin-top: 2px;
    font-size: 18px;
    color: #ff9a44;
  }

  strong {
    display: block;
    font-size: 15px;
    margin-bottom: 4px;
    color: #fff;
  }

  span {
    color: rgba(215, 230, 255, 0.78);
    font-size: 14px;
    line-height: 1.5;
  }
}

.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 44px;
  background: rgba(255, 255, 255, 0.96);
}

.form-card {
  width: 100%;
  max-width: 430px;
}

.form-header {
  margin-bottom: 28px;

  .section-label {
    display: inline-flex;
    margin-bottom: 12px;
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: #1c64f2;
  }

  h2 {
    font-size: 34px;
    color: #0d2342;
    line-height: 1.2;
    margin-bottom: 10px;
  }
}

.form-subtitle {
  color: #5d7694;
  line-height: 1.7;
}

.login-form {
  display: grid;
  gap: 20px;
}

.form-group {
  label {
    display: inline-block;
    margin-bottom: 10px;
    color: #18314f;
    font-size: 14px;
    font-weight: 700;
  }
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;

  i {
    position: absolute;
    left: 16px;
    color: #91a4bd;
    font-size: 16px;
  }

  input {
    width: 100%;
    height: 54px;
    padding: 0 48px 0 48px;
    border-radius: 16px;
    border: 1.5px solid #dbe6f4;
    background: #f8fbff;
    color: #10233f;
    font-size: 15px;
    transition: 0.2s ease;

    &:focus {
      outline: none;
      border-color: #ff8b2d;
      background: #fff;
      box-shadow: 0 0 0 4px rgba(255, 139, 45, 0.12);
    }

    &::placeholder {
      color: #9baec6;
    }
  }
}

.password-toggle {
  position: absolute;
  right: 10px;
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: #90a3ba;
  cursor: pointer;
  border-radius: 50%;
  transition: 0.2s ease;

  &:hover {
    color: #ff8b2d;
    background: rgba(255, 139, 45, 0.08);
  }
}

.error-message {
  display: inline-block;
  margin-top: 8px;
  color: #e64747;
  font-size: 13px;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.remember {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4f6884;
  font-size: 14px;
  cursor: pointer;

  input {
    width: 18px;
    height: 18px;
    accent-color: #1c64f2;
  }
}

.forgot-link {
  color: #1c64f2;
  text-decoration: none;
  font-size: 14px;
  font-weight: 600;

  &:hover {
    text-decoration: underline;
  }
}

.btn-login {
  width: 100%;
  height: 56px;
  border: 0;
  border-radius: 16px;
  color: #fff;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.08em;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: linear-gradient(135deg, #ff9a44 0%, #ff7f22 45%, #e95f0b 100%);
  box-shadow: 0 14px 28px rgba(233, 95, 11, 0.28);
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 18px 32px rgba(233, 95, 11, 0.34);
    filter: brightness(1.03);
  }

  &:disabled {
    opacity: 0.72;
    cursor: not-allowed;
    transform: none;
  }
}

.register-link {
  margin-top: 22px;
  text-align: center;
  color: #5d7694;
  font-size: 15px;

  a {
    color: #1c64f2;
    font-weight: 700;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

@media (max-width: 980px) {
  .login-container {
    grid-template-columns: 1fr;
  }

  .brand-side,
  .form-side {
    padding: 36px 24px;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 16px 12px 0;
  }

  .brand-tagline h2,
  .form-header h2 {
    font-size: 28px;
  }

  .form-options {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
