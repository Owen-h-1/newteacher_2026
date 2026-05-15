<template>
  <div class="register-page">
    <div class="register-container">
      <!-- 左侧品牌展示区（与登录页对称） -->
      <div class="brand-side">
        <div class="brand-content">
          <div class="logo">
            <i class="fas fa-robot"></i>
            <span>智课云师</span>
          </div>
          <div class="brand-tagline">
            <span class="badge-ai">AI 赋能</span>
            <h2>开启智慧学习之旅</h2>
            <p>注册即享 AI 数字人助教、智能课件、专属学习分析</p>
          </div>
          <div class="feature-list">
            <div class="feature-item">
              <i class="fas fa-check-circle"></i> 3分钟快速注册
            </div>
            <div class="feature-item">
              <i class="fas fa-check-circle"></i> 支持教师/学生双身份
            </div>
            <div class="feature-item">
              <i class="fas fa-check-circle"></i> 免费体验AI功能
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧注册表单 -->
      <div class="form-side">
        <div class="form-card">
          <h2>注册账号</h2>
          <p class="form-subtitle">填写信息完成注册</p>
          <form novalidate @submit.prevent="handleRegister">
            <div class="form-group">
              <label for="username">账号</label>
              <div class="input-wrapper">
                <i class="fas fa-user"></i>
                <input
                  type="text"
                  id="username"
                  v-model="form.username"
                  placeholder="请输入6-20位字母数字组合"
                  @blur="validateUsername"
                  @input="validateUsername"
                />
              </div>
              <span v-if="errors.username" class="error-message">{{
                errors.username
              }}</span>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label for="password">密码</label>
                <div class="input-wrapper">
                  <i class="fas fa-lock"></i>
                  <input
                    :type="showPassword ? 'text' : 'password'"
                    id="password"
                    v-model="form.password"
                    placeholder="6-20位字母/数字/符号"
                    @blur="validatePassword"
                    @input="validatePassword"
                  />
                  <i
                    class="password-toggle"
                    :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"
                    @click="showPassword = !showPassword"
                  ></i>
                </div>
              </div>
              <div class="form-group half">
                <label for="confirmPassword">确认密码</label>
                <div class="input-wrapper">
                  <i class="fas fa-lock"></i>
                  <input
                    :type="showConfirmPassword ? 'text' : 'password'"
                    id="confirmPassword"
                    v-model="form.confirmPassword"
                    placeholder="再次输入密码"
                    @blur="validateConfirmPassword"
                    @input="validateConfirmPassword"
                  />
                  <i
                    class="password-toggle"
                    :class="showConfirmPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"
                    @click="showConfirmPassword = !showConfirmPassword"
                  ></i>
                </div>
              </div>
            </div>
            <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            <span v-if="errors.confirmPassword" class="error-message">{{
              errors.confirmPassword
            }}</span>

            <div class="form-group">
              <label for="email">邮箱</label>
              <div class="input-wrapper">
                <i class="fas fa-envelope"></i>
                <input
                  type="email"
                  id="email"
                  v-model="form.email"
                  placeholder="用于接收通知和找回密码"
                  @blur="validateEmail"
                  @input="validateEmail"
                />
              </div>
              <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
            </div>

            <div class="form-group">
              <label>身份</label>
              <div class="role-selector">
                <label class="role-item">
                  <input type="radio" value="student" v-model="form.role" /> 学生
                </label>
                <label class="role-item">
                  <input type="radio" value="teacher" v-model="form.role" /> 教师
                </label>
              </div>
            </div>

            <div v-if="form.role === 'student'" class="form-group">
              <label for="realName">真实姓名</label>
              <div class="input-wrapper">
                <i class="fas fa-id-card"></i>
                <input
                  id="realName"
                  v-model.trim="form.realName"
                  type="text"
                  autocomplete="name"
                  placeholder="须与老师录入花名册的姓名完全一致"
                  @blur="validateStudentRoster"
                  @input="validateStudentRoster"
                />
              </div>
              <span v-if="errors.realName" class="error-message">{{ errors.realName }}</span>
            </div>

            <div v-if="form.role === 'student'" class="form-group">
              <label for="studentNo">学号</label>
              <div class="input-wrapper">
                <i class="fas fa-hashtag"></i>
                <input
                  id="studentNo"
                  v-model.trim="form.studentNo"
                  type="text"
                  inputmode="numeric"
                  autocomplete="off"
                  placeholder="6-20 位数字，与老师录入一致"
                  @blur="validateStudentRoster"
                  @input="validateStudentRoster"
                />
              </div>
              <span v-if="errors.studentNo" class="error-message">{{ errors.studentNo }}</span>
            </div>

            <div v-if="form.role === 'student'" class="form-group">
              <label for="inviteCode">班级邀请码</label>
              <div class="input-wrapper">
                <i class="fas fa-ticket-alt"></i>
                <input
                  id="inviteCode"
                  v-model.trim="form.inviteCode"
                  type="text"
                  autocomplete="off"
                  placeholder="向老师索取，例如 ZKYS-123456"
                  @blur="validateInviteCode"
                  @input="validateInviteCode"
                />
              </div>
              <p class="field-hint">学生须凭有效邀请码加入对应班级，无法自选班级。</p>
              <span v-if="errors.inviteCode" class="error-message">{{ errors.inviteCode }}</span>
            </div>

            <span v-if="errors.api" class="error-message api-error">{{ errors.api }}</span>

            <div class="form-group terms">
              <label class="checkbox">
                <input type="checkbox" v-model="form.agree" />
                <span
                  >我已阅读并同意 <a href="#" @click.prevent>《用户协议》</a> 和
                  <a href="#" @click.prevent>《隐私政策》</a></span
                >
              </label>
              <span v-if="errors.agree" class="error-message">{{ errors.agree }}</span>
            </div>

            <button type="submit" class="btn-register" :disabled="isRegistering">
              <i v-if="isRegistering" class="fas fa-spinner fa-spin"></i>
              <span>{{ isRegistering ? "注册中..." : "注 册" }}</span>
            </button>
          </form>
          <div class="login-link">
            已有账号？ <router-link to="/login">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="successModalVisible"
        class="success-modal-overlay"
        @click.self="dismissSuccessModal"
      >
        <div class="success-modal-card" role="dialog" aria-modal="true" aria-labelledby="reg-success-title">
          <div class="success-modal-icon" aria-hidden="true">
            <i class="fas fa-circle-check"></i>
          </div>
          <h3 id="reg-success-title">注册成功</h3>
          <p class="success-modal-text">
            <template v-if="successJoinedClass">
              系统已将你加入班级：<strong>{{ successJoinedClass }}</strong>。<br />
            </template>
            请使用刚才设置的账号与密码登录。
          </p>
          <div class="success-modal-actions">
            <button type="button" class="btn-success-login" @click="goLoginFromSuccess">去登录</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from "vue";
import { useRouter } from "vue-router";
import { register } from "@/utils/api";

const router = useRouter();

// 表单数据
const form = reactive({
  username: "",
  password: "",
  confirmPassword: "",
  email: "",
  role: "student",
  realName: "",
  studentNo: "",
  inviteCode: "",
  agree: false,
});

// 密码显示控制
const showPassword = ref(false);
const showConfirmPassword = ref(false);
const isRegistering = ref(false);
const successModalVisible = ref(false);
const successJoinedClass = ref("");

function dismissSuccessModal() {
  successModalVisible.value = false;
  successJoinedClass.value = "";
}

function goLoginFromSuccess() {
  dismissSuccessModal();
  router.push("/login");
}

// 错误信息
const errors = reactive({
  username: "",
  password: "",
  confirmPassword: "",
  email: "",
  inviteCode: "",
  realName: "",
  studentNo: "",
  agree: "",
  api: "",
});

watch(
  () => form.role,
  (r) => {
    if (r !== "student") {
      form.inviteCode = "";
      form.realName = "";
      form.studentNo = "";
      errors.inviteCode = "";
      errors.realName = "";
      errors.studentNo = "";
    }
  }
);

// 校验用户名
const validateUsername = () => {
  const val = form.username.trim();
  if (!val) {
    errors.username = "账号不能为空";
    return false;
  }
  if (!/^[a-zA-Z0-9]{6,20}$/.test(val)) {
    errors.username = "账号必须为6-20位字母或数字";
    return false;
  }
  errors.username = "";
  return true;
};

// 校验密码
const validatePassword = () => {
  const val = form.password;
  if (!val) {
    errors.password = "密码不能为空";
    return false;
  }
  if (val.length < 6 || val.length > 20) {
    errors.password = "密码长度需6-20位（与登录要求一致）";
    return false;
  }
  errors.password = "";
  return true;
};

// 校验确认密码
const validateConfirmPassword = () => {
  if (!form.confirmPassword) {
    errors.confirmPassword = "请再次输入密码";
    return false;
  }
  if (form.confirmPassword !== form.password) {
    errors.confirmPassword = "两次输入的密码不一致";
    return false;
  }
  errors.confirmPassword = "";
  return true;
};

// 校验邮箱
const validateEmail = () => {
  const val = form.email.trim();
  if (!val) {
    errors.email = "邮箱不能为空";
    return false;
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(val)) {
    errors.email = "请输入有效的邮箱地址";
    return false;
  }
  errors.email = "";
  return true;
};

const validateInviteCode = () => {
  if (form.role !== "student") {
    errors.inviteCode = "";
    return true;
  }
  const v = form.inviteCode.trim();
  if (!v) {
    errors.inviteCode = "请输入班级邀请码";
    return false;
  }
  errors.inviteCode = "";
  return true;
};

const validateStudentRoster = () => {
  if (form.role !== "student") {
    errors.realName = "";
    errors.studentNo = "";
    return true;
  }
  const name = form.realName.trim();
  if (!name) {
    errors.realName = "请填写真实姓名";
    return false;
  }
  errors.realName = "";
  const no = form.studentNo.trim();
  if (!no) {
    errors.studentNo = "请填写学号";
    return false;
  }
  if (!/^\d{6,20}$/.test(no)) {
    errors.studentNo = "学号须为6-20位数字";
    return false;
  }
  errors.studentNo = "";
  return true;
};

// 注册处理（真实接口）
const handleRegister = async () => {
  errors.api = "";
  errors.inviteCode = "";
  errors.realName = "";
  errors.studentNo = "";
  errors.agree = "";

  if (!form.agree) {
    errors.agree = "请同意用户协议和隐私政策";
    return;
  }

  const isUsernameValid = validateUsername();
  const isPasswordValid = validatePassword();
  const isConfirmValid = validateConfirmPassword();
  const isEmailValid = validateEmail();
  const isInviteValid = validateInviteCode();
  const isRosterValid = validateStudentRoster();

  if (
    !isUsernameValid ||
    !isPasswordValid ||
    !isConfirmValid ||
    !isEmailValid ||
    !isInviteValid ||
    !isRosterValid
  )
    return;

  isRegistering.value = true;

  try {
    const data = await register({
      username: form.username.trim(),
      password: form.password,
      email: form.email.trim(),
      role: form.role,
      name: form.role === "student" ? form.realName.trim() : form.username.trim(),
      studentNo: form.role === "student" ? form.studentNo.trim() : undefined,
      studentName: form.role === "student" ? form.realName.trim() : undefined,
      inviteCode: form.role === "student" ? form.inviteCode.trim() : undefined,
    });

    successJoinedClass.value = data?.className ? String(data.className) : "";
    successModalVisible.value = true;
  } catch (err) {
    const msg = err?.message || "注册失败，请稍后重试";
    if (form.role === "student" && /邀请码|班级|学生注册|花名册|学号|姓名|绑定/.test(msg)) {
      if (/姓名/.test(msg)) errors.realName = msg;
      else if (/学号/.test(msg)) errors.studentNo = msg;
      else if (/邀请码|班级|学生注册/.test(msg)) errors.inviteCode = msg;
      else errors.api = msg;
    } else {
      errors.api = msg;
    }
  } finally {
    isRegistering.value = false;
  }
};
</script>

<style scoped lang="scss">
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #f0f7ff, #e6f0fa);
  padding: 24px;
}

.register-container {
  max-width: 1200px;
  width: 100%;
  display: flex;
  background: white;
  border-radius: 48px;
  box-shadow: 0 20px 40px rgba(21, 94, 239, 0.08);
  overflow: hidden;
  min-height: 700px;
}

/* 左侧品牌区（与登录页保持一致） */
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
  max-width: 460px;
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
  .field-hint {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.45;
    color: #6c829b;
  }
}

.form-group {
  margin-bottom: 20px;
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

.form-row {
  display: flex;
  gap: 16px;
  .form-group.half {
    flex: 1;
  }
}

/* 身份选择 */
.role-selector {
  display: flex;
  gap: 24px;
  padding: 8px 0;
  .role-item {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #0b2b4a;
    cursor: pointer;
    input[type="radio"] {
      width: 18px;
      height: 18px;
      accent-color: #1e6df2;
    }
  }
}

/* 用户协议 */
.terms {
  .checkbox {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #4a668a;
    font-size: 14px;
    cursor: pointer;
    input[type="checkbox"] {
      width: 18px;
      height: 18px;
      accent-color: #1e6df2;
    }
    a {
      color: #1e6df2;
      text-decoration: none;
      &:hover {
        text-decoration: underline;
      }
    }
  }
}

.btn-register {
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
  margin-top: 16px;
  &:hover:not(:disabled) {
    background: #0a4bb0;
    transform: scale(1.02);
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.login-link {
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

.success-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(8, 25, 45, 0.5);
  backdrop-filter: blur(4px);
}

.success-modal-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 24px;
  padding: 32px 28px 28px;
  text-align: center;
  box-shadow: 0 24px 48px rgba(11, 43, 74, 0.18);
  border: 1px solid rgba(30, 109, 242, 0.12);
  animation: success-pop 0.28s ease-out;
}

.success-modal-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: linear-gradient(145deg, #e8f4ff, #d4e9fc);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1e6df2;
  font-size: 32px;
}

.success-modal-card h3 {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  color: #0b2b4a;
}

.success-modal-text {
  margin: 0 0 24px;
  font-size: 15px;
  line-height: 1.55;
  color: #5e7e9c;
}

.success-modal-actions {
  display: flex;
  justify-content: center;
}

.btn-success-login {
  min-width: 160px;
  padding: 14px 28px;
  border: none;
  border-radius: 40px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  box-shadow: 0 8px 20px rgba(30, 109, 242, 0.35);
  transition: transform 0.15s, opacity 0.15s;
  &:hover {
    opacity: 0.95;
    transform: scale(1.02);
  }
}

@keyframes success-pop {
  from {
    opacity: 0;
    transform: scale(0.94) translateY(8px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* 响应式 */
@media (max-width: 900px) {
  .register-container {
    flex-direction: column;
    min-height: auto;
  }
  .brand-side {
    padding: 32px;
  }
  .form-side {
    padding: 32px;
  }
  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
