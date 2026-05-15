<template>
  <div class="login-page">
    <div class="login-container">
      <!-- 左侧品牌展示区（科技感） -->
      <div class="brand-side">
        <div class="brand-content">
          <div class="logo">
            <i class="fas fa-robot"></i>
            <span>智课云师</span>
          </div>
          <div class="brand-tagline">
            <span class="badge-ai">AI 赋能</span>
            <h2>智慧教育 · 因材施教</h2>
            <p>AI 数字人助教，专属学习路径，让教与学更高效</p>
          </div>
          <div class="feature-list">
            <div class="feature-item">
              <i class="fas fa-check-circle"></i> AI 智能课件生成
            </div>
            <div class="feature-item">
              <i class="fas fa-check-circle"></i> 实时学情分析
            </div>
            <div class="feature-item">
              <i class="fas fa-check-circle"></i> 数字人视频互动
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧登录表单 -->
      <div class="form-side">
        <div class="form-card">
          <h2>欢迎回来</h2>
          <p class="form-subtitle">请登录您的账号</p>
          <form @submit.prevent="handleLogin">
            <div class="form-group">
              <label for="username">账号</label>
              <div class="input-wrapper">
                <i class="fas fa-user"></i>
                <input
                  type="text"
                  id="username"
                  v-model="username"
                  placeholder="请输入学号/工号/手机号"
                  @blur="validateUsername"
                />
              </div>
              <span v-if="errors.username" class="error-message">{{
                errors.username
              }}</span>
            </div>
            <div class="form-group">
              <label for="password">密码</label>
              <div class="input-wrapper">
                <i class="fas fa-lock"></i>
                <input
                  :type="showPassword ? 'text' : 'password'"
                  id="password"
                  v-model="password"
                  placeholder="请输入密码"
                  @blur="validatePassword"
                />
                <i
                  class="password-toggle"
                  :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"
                  @click="showPassword = !showPassword"
                ></i>
              </div>
              <span v-if="errors.password" class="error-message">{{
                errors.password
              }}</span>
            </div>
            <div class="form-options">
              <label class="remember">
                <input type="checkbox" v-model="remember" /> 记住账号
              </label>
              <router-link to="/forgot-password" class="forgot-link"
                >忘记密码？</router-link
              >
            </div>
            <button type="submit" class="btn-login" :disabled="isLogging">
              <i v-if="isLogging" class="fas fa-spinner fa-spin"></i>
              <span>{{ isLogging ? "登录中..." : "登 录" }}</span>
            </button>
          </form>
          <div class="register-link">
            还没有账号？ <router-link to="/register">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { login, setSession, clearSession } from "@/utils/api";

const router = useRouter();

// 表单数据
const username = ref("");
const password = ref("");
const remember = ref(false);
const showPassword = ref(false);
const isLogging = ref(false);

// 错误信息
const errors = reactive({
  username: "",
  password: "",
});

onMounted(() => {
  const savedUsername = localStorage.getItem("zkys_remember_username");
  if (savedUsername) {
    username.value = savedUsername;
    remember.value = true;
  }
});

// 校验用户名
const validateUsername = () => {
  if (!username.value.trim()) {
    errors.username = "账号不能为空";
    return false;
  }
  errors.username = "";
  return true;
};

// 校验密码
const validatePassword = () => {
  if (!password.value) {
    errors.password = "密码不能为空";
    return false;
  }
  errors.password = "";
  return true;
};

// 登录处理（真实接口）
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

    setSession({
      token: data.token,
      user: data.user,
    });

    if (remember.value) {
      localStorage.setItem("zkys_remember_username", username.value.trim());
    } else {
      localStorage.removeItem("zkys_remember_username");
    }

    if (data.user.role === "teacher") {
      router.push("/teacher/dashboard");
    } else if (data.user.role === "admin") {
      router.push("/admin/teachers");
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
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #f0f7ff, #e6f0fa);
  padding: 24px;
}

.login-container {
  max-width: 1200px;
  width: 100%;
  display: flex;
  background: white;
  border-radius: 48px;
  box-shadow: 0 20px 40px rgba(21, 94, 239, 0.08);
  overflow: hidden;
  min-height: 600px;
}

/* 左侧品牌区 */
.brand-side {
  flex: 1;
  background: linear-gradient(145deg, #0b1f2e, #0a1a28);
  padding: 48px 40px;
  display: flex;
  align-items: center;
  color: white;
}

.brand-content {
  width: 100%;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
  i {
    font-size: 36px;
    color: #4b9fff;
  }
  span {
    font-size: 28px;
    font-weight: 700;
    background: linear-gradient(135deg, #fff, #b8d6ff);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.brand-tagline {
  margin-bottom: 40px;
  .badge-ai {
    display: inline-block;
    background: rgba(30, 109, 242, 0.2);
    border: 1px solid rgba(30, 109, 242, 0.5);
    color: #a0c6ff;
    padding: 6px 18px;
    border-radius: 40px;
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 20px;
  }
  h2 {
    font-size: 32px;
    font-weight: 700;
    margin-bottom: 16px;
  }
  p {
    font-size: 16px;
    color: #b8d6ff;
    line-height: 1.6;
  }
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  .feature-item {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 16px;
    i {
      color: #2eb85c;
    }
  }
}

/* 右侧表单区 */
.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: white;
}

.form-card {
  width: 100%;
  max-width: 400px;
  h2 {
    font-size: 32px;
    font-weight: 700;
    color: #0b2b4a;
    margin-bottom: 8px;
  }
  .form-subtitle {
    color: #5e7e9c;
    margin-bottom: 32px;
  }
}

.form-group {
  margin-bottom: 24px;
  label {
    display: block;
    font-size: 14px;
    font-weight: 600;
    color: #0b2b4a;
    margin-bottom: 8px;
  }
  .input-wrapper {
    position: relative;
    display: flex;
    align-items: center;
    i {
      position: absolute;
      left: 16px;
      color: #b8c9dd;
      font-size: 18px;
    }
    .password-toggle {
      left: auto;
      right: 16px;
      cursor: pointer;
      &:hover {
        color: #1e6df2;
      }
    }
    input {
      width: 100%;
      padding: 14px 16px 14px 48px;
      border: 2px solid #e2eaf2;
      border-radius: 18px;
      font-size: 15px;
      transition: 0.2s;
      background: #f8fcff;
      &:focus {
        border-color: #1e6df2;
        outline: none;
        box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
        background: white;
      }
      &::placeholder {
        color: #a0bedb;
      }
    }
  }
  .error-message {
    display: block;
    margin-top: 6px;
    font-size: 13px;
    color: #ff4d4d;
  }
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  .remember {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #4a668a;
    font-size: 14px;
    cursor: pointer;
    input[type="checkbox"] {
      width: 18px;
      height: 18px;
      accent-color: #1e6df2;
    }
  }
  .forgot-link {
    color: #1e6df2;
    font-size: 14px;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
}

.btn-login {
  width: 100%;
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  border: none;
  color: white;
  padding: 16px;
  border-radius: 40px;
  font-weight: 700;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  cursor: pointer;
  transition: 0.2s;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
  &:hover:not(:disabled) {
    background: #0a4bb0;
    transform: scale(1.02);
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.register-link {
  margin-top: 28px;
  text-align: center;
  color: #5e7e9c;
  font-size: 15px;
  a {
    color: #1e6df2;
    font-weight: 600;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
}

/* 响应式 */
@media (max-width: 900px) {
  .login-container {
    flex-direction: column;
    min-height: auto;
  }
  .brand-side {
    padding: 32px;
  }
  .form-side {
    padding: 32px;
  }
}
</style>
