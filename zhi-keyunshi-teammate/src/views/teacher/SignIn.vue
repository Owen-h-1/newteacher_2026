<template>
  <div class="sign-in-page">
    <div class="page-header">
      <div class="title-icon">
        <i class="fas fa-qrcode"></i>
      </div>
      <div class="title-text">
        <h1>课堂签到</h1>
        <p class="subtitle">人脸识别智能考勤系统</p>
      </div>
      <div class="class-selector">
        <i class="fas fa-book-open"></i>
        <select v-model="currentClassId">
          <option v-for="c in classList" :key="c.classId" :value="c.classId">{{ c.className }}</option>
        </select>
      </div>
    </div>

    <!-- 功能Tab切换 -->
    <div class="feature-tabs">
      <button 
        :class="['tab-btn', { active: activeFeature === 'recognize' }]" 
        @click="activeFeature = 'recognize'"
      >
        <i class="fas fa-camera"></i>
        <span>人脸签到</span>
      </button>
      <button 
        :class="['tab-btn', { active: activeFeature === 'register' }]" 
        @click="activeFeature = 'register'"
      >
        <i class="fas fa-user-plus"></i>
        <span>学生注册</span>
      </button>
      <button 
        :class="['tab-btn', { active: activeFeature === 'statistics' }]" 
        @click="activeFeature = 'statistics'"
      >
        <i class="fas fa-chart-bar"></i>
        <span>考勤统计</span>
      </button>
    </div>

    <!-- 跳转到原有人脸识别系统 -->
    <div class="quick-jump-section">
      <a href="http://localhost:3001/attendance" target="_blank" class="btn-jump">
        <i class="fas fa-external-link-alt"></i>
        打开原有人脸识别系统
      </a>
    </div>

    <!-- ========== 人脸签到模块 ========== -->
    <div v-if="activeFeature === 'recognize'" class="feature-panel">
      <div class="control-card">
        <div class="photo-section">
          <div class="photo-uploader" @click="triggerPhotoInput">
            <input
              ref="photoInputRef"
              type="file"
              accept="image/*"
              style="display: none"
              @change="onSelectPhoto"
            />
            <i class="fas fa-camera"></i>
            <h3>{{ selectedPhotoName || "点击上传课堂照片" }}</h3>
            <p>支持 jpg / png / webp，人脸识别自动签到</p>
          </div>
          
          <div class="photo-capture-btns">
            <button class="btn-capture" @click="openCamera" :disabled="isCameraOpen">
              <i class="fas fa-video"></i> 拍照
            </button>
            <label class="btn-upload-label">
              <i class="fas fa-folder-open"></i> 选择照片
              <input type="file" accept="image/*" style="display: none" @change="onSelectPhoto">
            </label>
          </div>

          <div v-if="isCameraOpen" class="camera-preview">
            <video ref="videoRef" autoplay playsinline class="camera-video"></video>
            <div class="camera-actions">
              <button class="btn-capture-photo" @click="capturePhoto">
                <i class="fas fa-camera-retro"></i> 拍摄
              </button>
              <button class="btn-close-camera" @click="closeCamera">
                <i class="fas fa-times"></i> 关闭
              </button>
            </div>
            <canvas ref="canvasRef" style="display: none"></canvas>
          </div>

          <div v-if="selectedPhotoData && !isCameraOpen" class="photo-preview">
            <img :src="selectedPhotoData" alt="课堂照片" />
          </div>

          <div class="photo-actions">
            <button 
              class="btn-recognize" 
              :disabled="photoUploading || !selectedPhotoData" 
              @click="submitPhotoForRecognition"
            >
              <i :class="['fas', photoUploading ? 'fa-spinner fa-spin' : 'fa-magic']"></i>
              {{ photoUploading ? "识别中..." : "开始识别" }}
            </button>
            <div v-if="photoStatusText" class="photo-status" :class="{ error: photoStatusText.includes('失败') || photoStatusText.includes('错误') }">
              <i class="fas fa-info-circle"></i> {{ photoStatusText }}
            </div>
          </div>

          <!-- 识别结果内联展示 -->
          <div v-if="recognitionResult" class="recognition-result-inline">
            <div class="recognition-result-header">
              <h3><i class="fas fa-user-check"></i> 识别结果</h3>
              <div class="result-stats-row">
                <div class="rs-item">
                  <span class="rs-value">{{ recognitionResult.total_detected }}</span>
                  <span class="rs-label">检测人数</span>
                </div>
                <div class="rs-item success">
                  <span class="rs-value">{{ recognitionResult.total_recognized }}</span>
                  <span class="rs-label">识别成功</span>
                </div>
                <div class="rs-item warning">
                  <span class="rs-value">{{ recognitionResult.total_detected - recognitionResult.total_recognized }}</span>
                  <span class="rs-label">未识别</span>
                </div>
              </div>
            </div>

            <div v-if="recognitionResult.result_image" class="result-image-inline">
              <img :src="recognitionResult.result_image" alt="识别结果图" />
            </div>

            <div v-if="recognitionResult.recognized_students && recognitionResult.recognized_students.length" class="recognized-students-inline">
              <h4><i class="fas fa-users"></i> 已识别学生名单</h4>
              <div class="recognized-table-wrap">
                <table class="recognized-table">
                  <thead>
                    <tr>
                      <th>学号</th>
                      <th>姓名</th>
                      <th>置信度</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(student, idx) in recognitionResult.recognized_students" :key="idx">
                      <td>{{ student.student_id }}</td>
                      <td><strong>{{ student.name }}</strong></td>
                      <td>
                        <span :class="['conf-badge', student.confidence > 0.7 ? 'high' : 'medium']">
                          {{ (student.confidence * 100).toFixed(1) }}%
                        </span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
        
        <div class="stats-summary">
          <div class="stat-item">
            <span class="stat-label">应到</span>
            <span class="stat-number">{{ totalStudents }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">已到</span>
            <span class="stat-number signed">{{ signedCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">未到</span>
            <span class="stat-number absent">{{ totalStudents - signedCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">签到率</span>
            <span class="stat-percent">{{ signRate }}%</span>
          </div>
        </div>
      </div>

      <!-- 签到记录表格 -->
      <div class="sign-records">
        <div class="records-header">
          <h3><i class="fas fa-list-check"></i> 今日签到记录</h3>
          <button class="btn-manual-sign" @click="openManualModal">
            <i class="fas fa-user-plus"></i> 手动签到
          </button>
        </div>
        <div class="record-table">
          <div class="table-header">
            <span>学号</span>
            <span>姓名</span>
            <span>签到时间</span>
            <span>签到方式</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          <div class="table-body">
            <div v-for="(record, idx) in signRecords" :key="idx" class="table-row">
              <span>{{ record.studentId }}</span>
              <span><i class="fas fa-user-graduate"></i> {{ record.name }}</span>
              <span>{{ record.time || "--" }}</span>
              <span>
                <span :class="['sign-method', record.methodClass]">
                  <i :class="record.methodIcon"></i> {{ record.method }}
                </span>
              </span>
              <span>
                <span :class="['status-badge', record.statusClass]">
                  <i :class="record.statusIcon"></i> {{ record.status }}
                </span>
              </span>
              <span>
                <button v-if="!record.time" class="btn-mark" @click="markSign(record)">
                  <i class="fas fa-check"></i> 标记签到
                </button>
                <button v-else class="btn-undo" @click="undoSign(record)">
                  <i class="fas fa-undo-alt"></i> 撤销
                </button>
              </span>
            </div>
          </div>
        </div>
        <div class="table-footer">
          <span class="total-info">共 {{ totalStudents }} 人，已签到 {{ signedCount }} 人</span>
          <button class="btn-export" @click="exportRecords">
            <i class="fas fa-download"></i> 导出签到表
          </button>
        </div>
      </div>
    </div>

    <!-- ========== 学生注册模块 ========== -->
    <div v-if="activeFeature === 'register'" class="feature-panel">
      <div class="control-card register-card">
        <h2 class="panel-title"><i class="fas fa-user-plus"></i> 学生人脸信息注册</h2>
        
        <form @submit.prevent="registerStudent" class="register-form">
          <div class="form-row">
            <div class="form-group">
              <label><i class="fas fa-id-card"></i> 学号</label>
              <input 
                v-model="studentForm.student_id" 
                type="text" 
                placeholder="请输入学号" 
                required 
              />
            </div>
            <div class="form-group">
              <label><i class="fas fa-user"></i> 姓名</label>
              <input 
                v-model="studentForm.name" 
                type="text" 
                placeholder="请输入姓名" 
                required 
              />
            </div>
            <div class="form-group">
              <label><i class="fas fa-school"></i> 班级</label>
              <input 
                v-model="studentForm.class_name" 
                type="text" 
                placeholder="例如：三年级(1)班" 
                required 
              />
            </div>
          </div>

          <div class="photo-upload-section">
            <label class="section-label"><i class="fas fa-camera"></i> 学生人脸照片</label>
            <div class="photo-actions-row">
              <button type="button" class="btn-capture-student" @click="openStudentCamera" :disabled="isStudentCameraOpen">
                <i class="fas fa-video"></i> 拍照录入
              </button>
              <label class="btn-upload-student">
                <i class="fas fa-folder-open"></i> 上传照片
                <input type="file" accept="image/*" style="display: none" @change="onStudentPhotoUpload">
              </label>
            </div>

            <div v-if="isStudentCameraOpen" class="camera-preview-student">
              <video ref="studentVideoRef" autoplay playsinline class="camera-video-student"></video>
              <div class="camera-actions-student">
                <button type="button" class="btn-capture-photo-student" @click="captureStudentPhoto">
                  <i class="fas fa-camera-retro"></i> 拍摄
                </button>
                <button type="button" class="btn-close-camera-student" @click="closeStudentCamera">
                  <i class="fas fa-times"></i> 关闭
                </button>
              </div>
              <canvas ref="studentCanvasRef" style="display: none"></canvas>
            </div>

            <div v-if="studentPhotoData" class="student-photo-preview">
              <img :src="studentPhotoData" alt="学生照片" />
            </div>
          </div>

          <button type="submit" class="btn-register-submit" :disabled="registerLoading || !studentPhotoData">
            <i :class="['fas', registerLoading ? 'fa-spinner fa-spin' : 'fa-user-check']"></i>
            {{ registerLoading ? '注册中...' : '注册学生' }}
          </button>
        </form>

        <!-- 已注册学生列表 -->
        <div class="registered-students-section">
          <h3 class="section-title">
            <i class="fas fa-users"></i> 已注册学生 ({{ faceStudents.length }})
          </h3>
          <div class="students-table-wrap">
            <table class="students-table">
              <thead>
                <tr>
                  <th>学号</th>
                  <th>姓名</th>
                  <th>班级</th>
                  <th>人脸状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(student, idx) in faceStudents" :key="idx">
                  <td>{{ student.student_id }}</td>
                  <td><strong>{{ student.name }}</strong></td>
                  <td>{{ student.class_name }}</td>
                  <td>
                    <span :class="['face-status', student.face_registered ? 'registered' : 'unregistered']">
                      <i :class="student.face_registered ? 'fas fa-check-circle' : 'fas fa-times-circle'"></i>
                      {{ student.face_registered ? '已录入' : '未录入' }}
                    </span>
                  </td>
                </tr>
                <tr v-if="faceStudents.length === 0">
                  <td colspan="4" class="empty-text">暂无注册学生</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 考勤统计模块 ========== -->
    <div v-if="activeFeature === 'statistics'" class="feature-panel">
      <div class="control-card statistics-card">
        <h2 class="panel-title"><i class="fas fa-chart-bar"></i> 考勤统计查询</h2>
        
        <div class="stats-query-form">
          <div class="form-row">
            <div class="form-group">
              <label><i class="fas fa-school"></i> 班级</label>
              <input 
                v-model="statsClassName" 
                type="text" 
                placeholder="例如：三年级(1)班" 
              />
            </div>
            <div class="form-group">
              <label><i class="fas fa-calendar"></i> 日期</label>
              <input 
                v-model="statsDate" 
                type="date" 
              />
            </div>
          </div>
          <button class="btn-stats-query" @click="loadAttendanceStats" :disabled="statsLoading || !statsClassName">
            <i :class="['fas', statsLoading ? 'fa-spinner fa-spin' : 'fa-search']"></i>
            {{ statsLoading ? '查询中...' : '查询考勤' }}
          </button>
        </div>

        <div v-if="attendanceStats" class="stats-result">
          <div class="stats-overview">
            <h3 class="stats-title">{{ attendanceStats.class_name }} - {{ attendanceStats.date }}</h3>
            <div class="stats-grid">
              <div class="stats-box">
                <span class="stats-value">{{ attendanceStats.total_students }}</span>
                <span class="stats-label">班级总人数</span>
              </div>
              <div class="stats-box present">
                <span class="stats-value">{{ attendanceStats.present_count }}</span>
                <span class="stats-label">出勤人数</span>
              </div>
              <div class="stats-box absent">
                <span class="stats-value">{{ attendanceStats.absent_count }}</span>
                <span class="stats-label">缺勤人数</span>
              </div>
              <div class="stats-box rate">
                <span class="stats-value">{{ (attendanceStats.attendance_rate * 100).toFixed(1) }}%</span>
                <span class="stats-label">出勤率</span>
              </div>
            </div>
          </div>

          <div class="stats-detail-grid">
            <div class="detail-column present-col">
              <h4><i class="fas fa-check-circle"></i> 出勤学生</h4>
              <div class="detail-list">
                <div v-for="(student, idx) in attendanceStats.present_students" :key="idx" class="detail-item present-item">
                  <span class="item-name">{{ student.name }}</span>
                  <span class="item-id">({{ student.student_id }})</span>
                  <span class="item-conf">{{ (student.confidence * 100).toFixed(1) }}%</span>
                </div>
                <div v-if="!attendanceStats.present_students?.length" class="empty-detail">暂无出勤记录</div>
              </div>
            </div>
            <div class="detail-column absent-col">
              <h4><i class="fas fa-times-circle"></i> 缺勤学生</h4>
              <div class="detail-list">
                <div v-for="(student, idx) in attendanceStats.absent_students" :key="idx" class="detail-item absent-item">
                  <span class="item-name">{{ student.name }}</span>
                  <span class="item-id">({{ student.student_id }})</span>
                </div>
                <div v-if="!attendanceStats.absent_students?.length" class="empty-detail">暂无缺勤记录</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 手动签到弹窗 -->
    <div
      v-if="showManualModal"
      class="modal-overlay"
      @click.self="showManualModal = false"
    >
      <div class="modal-card">
        <div class="modal-header">
          <h3><i class="fas fa-user-check"></i> 手动签到</h3>
          <button class="close-btn" @click="showManualModal = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <p>输入学生学号或姓名进行签到</p>
          <input
            v-model="manualSearch"
            type="text"
            placeholder="学号 / 姓名"
            class="modal-input"
          />
          <div class="search-results" v-if="manualSearch.length > 0">
            <div
              v-for="student in filteredStudents"
              :key="student.id"
              class="result-item"
            >
              <span>{{ student.id }} - {{ student.name }}</span>
              <button class="btn-sign" @click="manualSign(student)">签到</button>
            </div>
            <div v-if="filteredStudents.length === 0" class="no-result">
              未找到匹配学生
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 人脸识别结果弹窗 -->
    <div v-if="showRecognitionModal && recognitionResult" class="modal-overlay" @click.self="showRecognitionModal = false">
      <div class="modal-card recognition-modal">
        <div class="modal-header">
          <h3><i class="fas fa-user-check"></i> 人脸识别结果</h3>
          <button class="close-btn" @click="showRecognitionModal = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div class="recognition-stats">
            <div class="stat-box">
              <span class="stat-value">{{ recognitionResult.total_detected }}</span>
              <span class="stat-label">检测人数</span>
            </div>
            <div class="stat-box success">
              <span class="stat-value">{{ recognitionResult.total_recognized }}</span>
              <span class="stat-label">识别成功</span>
            </div>
            <div class="stat-box warning">
              <span class="stat-value">{{ recognitionResult.total_detected - recognitionResult.total_recognized }}</span>
              <span class="stat-label">未识别</span>
            </div>
          </div>
          
          <div v-if="recognitionResult.result_image" class="result-image">
            <img :src="recognitionResult.result_image" alt="识别结果" />
          </div>
          
          <div v-if="recognitionResult.recognized_students && recognitionResult.recognized_students.length" class="recognized-list">
            <h4><i class="fas fa-users"></i> 已识别学生</h4>
            <div class="student-grid">
              <div v-for="student in recognitionResult.recognized_students" :key="student.student_id" class="student-card">
                <div class="student-avatar">
                  <i class="fas fa-user-graduate"></i>
                </div>
                <div class="student-info">
                  <span class="student-name">{{ student.name }}</span>
                  <span class="student-id">{{ student.student_id }}</span>
                  <span class="confidence">置信度: {{ (student.confidence * 100).toFixed(1) }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-confirm" @click="showRecognitionModal = false">
            <i class="fas fa-check"></i> 确认
          </button>
        </div>
      </div>
    </div>

    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from "vue";
import {
  fetchSigninClasses,
  fetchSigninRecords,
  markSignin,
  undoSignin,
  uploadSigninPhoto,
  downloadSigninExport,
  recognizeAttendancePhoto,
  registerFaceStudent,
  fetchFaceRecognitionStudents,
  fetchAttendanceStatistics,
} from "@/utils/api";

const activeFeature = ref('recognize');

const classList = ref([]);
const currentClassId = ref("");

function enrichSignRecord(r) {
  const method = r.method && r.method !== "null" ? r.method : "--";
  let methodIcon = "";
  let methodClass = "";
  if (method === "扫码") {
    methodIcon = "fa-qrcode";
    methodClass = "qr";
  } else if (method === "数字人") {
    methodIcon = "fa-robot";
    methodClass = "ai";
  } else if (method === "手动") {
    methodIcon = "fa-pen";
    methodClass = "manual";
  }
  const signed = !!r.time;
  return {
    ...r,
    method,
    methodIcon,
    methodClass,
    status: signed ? "已签到" : "未签到",
    statusIcon: signed ? "fa-check-circle" : "fa-clock",
    statusClass: signed ? "signed" : "absent",
  };
}

const totalStudents = ref(0);
const signedCount = ref(0);
const signRecords = ref([]);

const loadRecords = async () => {
  if (!currentClassId.value) return;
  try {
    const data = await fetchSigninRecords(currentClassId.value);
    totalStudents.value = data.totalStudents ?? 0;
    signedCount.value = data.signedCount ?? 0;
    signRecords.value = (data.records || []).map(enrichSignRecord);
  } catch (e) {
    showToast("error", e.message || "加载签到数据失败", "fa-exclamation-circle");
  }
};

const loadClasses = async () => {
  try {
    const { classes } = await fetchSigninClasses();
    classList.value = Array.isArray(classes) ? classes : [];
    if (
      !currentClassId.value ||
      !classList.value.some((c) => c.classId === currentClassId.value)
    ) {
      currentClassId.value = classList.value[0]?.classId || "";
    } else {
      await loadRecords();
    }
  } catch (e) {
    showToast("error", e.message || "加载班级失败", "fa-exclamation-circle");
  }
};

watch(currentClassId, () => {
  loadRecords();
});

onMounted(loadClasses);

const signRate = computed(() => {
  if (totalStudents.value === 0) return 0;
  return Math.round((signedCount.value / totalStudents.value) * 100);
});

// ---------- 人脸签到相关 ----------
const photoInputRef = ref(null);
const selectedPhotoName = ref("");
const selectedPhotoData = ref("");
const photoUploading = ref(false);
const photoStatusText = ref("");
const recognitionResult = ref(null);
const showRecognitionModal = ref(false);
const uploadDate = ref(new Date().toISOString().split('T')[0]);

const videoRef = ref(null);
const canvasRef = ref(null);
const isCameraOpen = ref(false);
let cameraStream = null;

const triggerPhotoInput = () => {
  photoInputRef.value?.click();
};

const onSelectPhoto = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  selectedPhotoName.value = file.name;
  selectedPhotoData.value = await toBase64(file);
  photoStatusText.value = "照片已选择，点击「开始识别」进行人脸签到";
};

const openCamera = async () => {
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
    if (videoRef.value) {
      videoRef.value.srcObject = cameraStream;
    }
    isCameraOpen.value = true;
    selectedPhotoData.value = "";
    selectedPhotoName.value = "";
  } catch (err) {
    showToast("error", "无法打开摄像头: " + err.message, "fa-exclamation-circle");
  }
};

const closeCamera = () => {
  if (cameraStream) {
    cameraStream.getTracks().forEach(track => track.stop());
    cameraStream = null;
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null;
  }
  isCameraOpen.value = false;
};

const capturePhoto = () => {
  if (!videoRef.value || !canvasRef.value) return;
  const video = videoRef.value;
  const canvas = canvasRef.value;
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  canvas.getContext('2d').drawImage(video, 0, 0);
  selectedPhotoData.value = canvas.toDataURL('image/jpeg', 0.9);
  selectedPhotoName.value = `拍照_${new Date().toLocaleTimeString().replace(/:/g, '-')}.jpg`;
  photoStatusText.value = "已拍摄，点击「开始识别」进行人脸签到";
  closeCamera();
};

const toBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });

const submitPhotoForRecognition = async () => {
  if (!selectedPhotoData.value) return;
  photoUploading.value = true;
  recognitionResult.value = null;
  
  try {
    const currentClassName = classList.value.find(c => c.classId === currentClassId.value)?.className || currentClassId.value;
    
    const data = await recognizeAttendancePhoto({
      class_name: currentClassName,
      photo: selectedPhotoData.value,
      date: uploadDate.value,
    });
    
    if (data.success) {
      recognitionResult.value = data;
      showRecognitionModal.value = true;
      photoStatusText.value = `识别完成：检测到 ${data.total_detected} 人，识别出 ${data.total_recognized} 人`;
      showToast("success", "人脸识别完成，已自动更新签到记录", "fa-check-circle");
      await loadRecords();
    } else {
      photoStatusText.value = data.message || "识别失败";
      showToast("error", data.message || "人脸识别失败", "fa-exclamation-circle");
    }
  } catch (e) {
    photoStatusText.value = e.message || "识别失败";
    
    try {
      const fallbackData = await uploadSigninPhoto({
        classId: currentClassId.value,
        imageName: selectedPhotoName.value,
        imageData: selectedPhotoData.value,
      });
      photoStatusText.value = fallbackData?.message || "照片已上传（人脸识别服务未启动）";
      showToast("info", "照片已上传，人脸识别服务未启动", "fa-info-circle");
    } catch (fallbackError) {
      showToast("error", fallbackError.message || "上传失败", "fa-exclamation-circle");
    }
  } finally {
    photoUploading.value = false;
  }
};

// ---------- 学生注册相关 ----------
const studentForm = reactive({
  student_id: '',
  name: '',
  class_name: '',
});
const studentPhotoData = ref('');
const registerLoading = ref(false);
const faceStudents = ref([]);

const studentVideoRef = ref(null);
const studentCanvasRef = ref(null);
const isStudentCameraOpen = ref(false);
let studentCameraStream = null;

const openStudentCamera = async () => {
  try {
    studentCameraStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    if (studentVideoRef.value) {
      studentVideoRef.value.srcObject = studentCameraStream;
    }
    isStudentCameraOpen.value = true;
  } catch (err) {
    showToast("error", "无法打开摄像头: " + err.message, "fa-exclamation-circle");
  }
};

const closeStudentCamera = () => {
  if (studentCameraStream) {
    studentCameraStream.getTracks().forEach(track => track.stop());
    studentCameraStream = null;
  }
  if (studentVideoRef.value) {
    studentVideoRef.value.srcObject = null;
  }
  isStudentCameraOpen.value = false;
};

const captureStudentPhoto = () => {
  if (!studentVideoRef.value || !studentCanvasRef.value) return;
  const video = studentVideoRef.value;
  const canvas = studentCanvasRef.value;
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  canvas.getContext('2d').drawImage(video, 0, 0);
  studentPhotoData.value = canvas.toDataURL('image/jpeg', 0.9);
  closeStudentCamera();
};

const onStudentPhotoUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  studentPhotoData.value = await toBase64(file);
};

const registerStudent = async () => {
  if (!studentPhotoData.value) {
    showToast("error", "请先拍摄或上传学生照片", "fa-exclamation-circle");
    return;
  }
  
  registerLoading.value = true;
  try {
    const data = await registerFaceStudent({
      ...studentForm,
      photo: studentPhotoData.value,
    });
    
    if (data.success) {
      showToast("success", "学生注册成功！", "fa-check-circle");
      studentForm.student_id = '';
      studentForm.name = '';
      studentForm.class_name = '';
      studentPhotoData.value = '';
      await loadFaceStudents();
    } else {
      showToast("error", data.message || "注册失败", "fa-exclamation-circle");
    }
  } catch (e) {
    showToast("error", e.message || "注册失败", "fa-exclamation-circle");
  } finally {
    registerLoading.value = false;
  }
};

const loadFaceStudents = async () => {
  try {
    const data = await fetchFaceRecognitionStudents();
    if (data.success) {
      faceStudents.value = data.students || [];
    }
  } catch (e) {
    console.error('Failed to load students:', e);
  }
};

// ---------- 考勤统计相关 ----------
const statsClassName = ref('');
const statsDate = ref(new Date().toISOString().split('T')[0]);
const statsLoading = ref(false);
const attendanceStats = ref(null);

const loadAttendanceStats = async () => {
  if (!statsClassName.value) {
    showToast("error", "请输入班级名称", "fa-exclamation-circle");
    return;
  }
  
  statsLoading.value = true;
  try {
    const data = await fetchAttendanceStatistics(statsClassName.value, statsDate.value);
    if (data.success) {
      attendanceStats.value = data;
    } else {
      showToast("error", data.message || "查询失败", "fa-exclamation-circle");
    }
  } catch (e) {
    showToast("error", e.message || "查询失败", "fa-exclamation-circle");
  } finally {
    statsLoading.value = false;
  }
};

// ---------- 手动签到相关 ----------
const showManualModal = ref(false);
const manualSearch = ref("");

const allStudents = computed(() => {
  return signRecords.value.map((r) => ({
    id: r.studentId,
    name: r.name,
    signed: !!r.time,
  }));
});

const filteredStudents = computed(() => {
  if (!manualSearch.value) return [];
  return allStudents.value.filter(
    (s) =>
      (s.id.includes(manualSearch.value) || s.name.includes(manualSearch.value)) &&
      !s.signed
  );
});

const openManualModal = () => {
  manualSearch.value = "";
  showManualModal.value = true;
};

const manualSign = async (student) => {
  try {
    await markSignin({
      classId: currentClassId.value,
      studentId: student.id,
      method: "手动",
    });
    await loadRecords();
    showManualModal.value = false;
    manualSearch.value = "";
    showToast("success", `已为 ${student.name} 签到`, "fa-check-circle");
  } catch (e) {
    showToast("error", e.message || "签到失败", "fa-exclamation-circle");
  }
};

const markSign = async (record) => {
  try {
    await markSignin({
      classId: currentClassId.value,
      studentId: record.studentId,
      method: "手动",
    });
    await loadRecords();
    showToast("success", `已标记 ${record.name} 签到`, "fa-check-circle");
  } catch (e) {
    showToast("error", e.message || "操作失败", "fa-exclamation-circle");
  }
};

const undoSign = async (record) => {
  try {
    await undoSignin({
      classId: currentClassId.value,
      studentId: record.studentId,
    });
    await loadRecords();
    showToast("info", `已撤销 ${record.name} 的签到`, "fa-undo-alt");
  } catch (e) {
    showToast("error", e.message || "撤销失败", "fa-exclamation-circle");
  }
};

// ---------- 导出 ----------
const exportRecords = async () => {
  try {
    const { blob, filename } = await downloadSigninExport(currentClassId.value);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `${currentClassId.value || "班级"}-签到表.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showToast("success", "签到表导出成功", "fa-download");
  } catch (e) {
    showToast("error", e.message || "导出失败", "fa-exclamation-circle");
  }
};

// ---------- Toast 反馈 ----------
const toast = reactive({
  show: false,
  text: "",
  icon: "",
  type: "",
});
let toastTimer = null;
const showToast = (type, text, icon) => {
  if (toastTimer) clearTimeout(toastTimer);
  toast.show = true;
  toast.text = text;
  toast.icon = icon;
  toast.type = type;
  toastTimer = setTimeout(() => {
    toast.show = false;
  }, 3000);
};

onMounted(() => {
  loadFaceStudents();
});
</script>

<style scoped lang="scss">
.sign-in-page {
  padding: 8px 0;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  .title-icon {
    width: 56px;
    height: 56px;
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 28px;
    box-shadow: 0 8px 16px rgba(30, 109, 242, 0.2);
  }
  .title-text {
    flex: 1;
    h1 {
      font-size: 28px;
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 6px;
    }
    .subtitle {
      color: #5e7e9c;
      font-size: 15px;
    }
  }
  .class-selector {
    display: flex;
    align-items: center;
    gap: 12px;
    background: white;
    padding: 8px 20px;
    border-radius: 40px;
    box-shadow: 0 4px 10px rgba(0, 0, 0, 0.02);
    i {
      color: #1e6df2;
      font-size: 18px;
    }
    select {
      border: none;
      background: transparent;
      font-size: 15px;
      font-weight: 600;
      color: #0b2b4a;
      padding: 8px 0;
      outline: none;
      cursor: pointer;
    }
  }
}

.feature-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  background: white;
  padding: 6px;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 20, 40, 0.04);
  
  .tab-btn {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 14px 24px;
    border: none;
    border-radius: 12px;
    font-size: 15px;
    font-weight: 600;
    color: #5e7e9c;
    background: transparent;
    cursor: pointer;
    transition: all 0.25s ease;
    
    i {
      font-size: 18px;
    }
    
    &:hover {
      background: #f0f7ff;
      color: #1e6df2;
    }
    
    &.active {
      background: linear-gradient(135deg, #1e6df2, #4a8cf6);
      color: white;
      box-shadow: 0 4px 14px rgba(30, 109, 242, 0.3);
    }
  }
}

.quick-jump-section {
  margin-bottom: 20px;
  text-align: center;
}

.btn-jump {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  background: linear-gradient(135deg, #2eb85c, #48d68b);
  color: white;
  border-radius: 12px;
  text-decoration: none;
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 6px 20px rgba(46, 184, 92, 0.3);
  transition: all 0.25s ease;
  
  i {
    font-size: 18px;
  }
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 30px rgba(46, 184, 92, 0.4);
  }
}

.feature-panel {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.control-card {
  background: white;
  border-radius: 32px;
  padding: 28px 32px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  margin-bottom: 24px;
  
  &.register-card,
  &.statistics-card {
    max-width: 900px;
  }
}

.panel-title {
  font-size: 22px;
  font-weight: 700;
  color: #0b2b4a;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  
  i {
    color: #1e6df2;
  }
}

.photo-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  flex: 2;
  min-width: 300px;
}
.photo-uploader {
  width: 100%;
  border: 2px dashed #b9d2f4;
  border-radius: 18px;
  padding: 18px;
  background: #f8fbff;
  cursor: pointer;
  i {
    color: #1e6df2;
    font-size: 28px;
    margin-bottom: 8px;
  }
  h3 {
    margin: 0 0 6px;
    color: #0b2b4a;
    font-size: 18px;
  }
  p {
    margin: 0;
    color: #5e7e9c;
    font-size: 13px;
  }
}
.photo-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.class-date-row {
  margin: 12px 0;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-item label {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.date-input {
  padding: 10px 14px;
  border: 2px solid #e0e6ed;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
}
.date-input:focus {
  border-color: #1e6df2;
  box-shadow: 0 0 0 3px rgba(30, 109, 242, 0.1);
}
.photo-capture-btns {
  display: flex;
  gap: 10px;
  width: 100%;
}
.btn-capture, .btn-upload-label {
  flex: 1;
  padding: 10px 16px;
  border-radius: 12px;
  border: none;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  transition: all 0.2s;
}
.btn-capture {
  background: #e8f1ff;
  color: #1e6df2;
  &:hover:not(:disabled) { background: #d0e3ff; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.btn-upload-label {
  background: #e3f2e9;
  color: #2eb85c;
  &:hover { background: #d0ebd5; }
}
.camera-preview {
  width: 100%;
  margin-top: 12px;
  border-radius: 14px;
  overflow: hidden;
  position: relative;
  background: #000;
}
.camera-video {
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  display: block;
}
.camera-actions {
  display: flex;
  gap: 8px;
  padding: 10px;
  background: rgba(0,0,0,0.5);
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  justify-content: center;
}
.btn-capture-photo {
  background: #1e6df2;
  color: white;
  border: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: 600;
  cursor: pointer;
  &:hover { background: #0a4bb0; }
}
.btn-close-camera {
  background: rgba(255,255,255,0.2);
  color: white;
  border: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: 600;
  cursor: pointer;
  &:hover { background: rgba(255,255,255,0.35); }
}
.photo-preview {
  width: 100%;
  margin-top: 12px;
  border-radius: 14px;
  overflow: hidden;
  border: 2px solid #b9d2f4;
  img {
    width: 100%;
    max-height: 360px;
    object-fit: contain;
    display: block;
    background: #f0f0f0;
  }
}
.btn-recognize {
  width: 100%;
  padding: 14px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(30, 109, 242, 0.35);
  }
  &:disabled {
    background: linear-gradient(135deg, #92b6f5, #a8c4fa);
    cursor: not-allowed;
  }
}
.photo-status {
  color: #5e7e9c;
  font-size: 13px;
  
  &.error {
    color: #ff4d4d;
    font-weight: 600;
  }
}

.recognition-result-inline {
  margin-top: 16px;
  border: 1px solid #e2eaf2;
  border-radius: 16px;
  overflow: hidden;
}
.recognition-result-header {
  background: linear-gradient(135deg, #f0f7ff, #e8f1ff);
  padding: 16px;
  h3 {
    font-size: 16px;
    font-weight: 700;
    color: #0b2b4a;
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    i { color: #1e6df2; }
  }
}
.result-stats-row {
  display: flex;
  gap: 12px;
  .rs-item {
    flex: 1;
    background: white;
    border-radius: 12px;
    padding: 10px;
    text-align: center;
    
    &.success { background: #e3f2e9; .rs-value { color: #1e7b4c; } }
    &.warning { background: #fff3e0; .rs-value { color: #b9770e; } }
    
    .rs-value { display: block; font-size: 24px; font-weight: 700; color: #0b2b4a; }
    .rs-label { display: block; font-size: 11px; color: #5e7e9c; margin-top: 2px; }
  }
}
.result-image-inline {
  img {
    width: 100%;
    max-height: 300px;
    object-fit: contain;
    display: block;
    background: #000;
  }
}
.recognized-students-inline {
  h4 {
    font-size: 14px;
    font-weight: 600;
    color: #0b2b4a;
    margin: 0;
    padding: 12px 16px 8px;
    display: flex;
    align-items: center;
    gap: 8px;
    i { color: #1e6df2; }
  }
}
.recognized-table-wrap {
  padding: 0 16px 16px;
  overflow-x: auto;
}
.recognized-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  th, td {
    padding: 10px 14px;
    text-align: left;
    border-bottom: 1px solid #edf2f7;
  }
  th {
    background: #f0f7ff;
    font-weight: 600;
    color: #0b2b4a;
  }
  td { color: #333; }
  .conf-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
    &.high { background: #e3f2e9; color: #1e7b4c; }
    &.medium { background: #fff3e0; color: #b9770e; }
  }
}
.stats-summary {
  flex: 1;
  min-width: 200px;
  background: #f8fcff;
  border-radius: 24px;
  padding: 20px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  .stat-item {
    display: flex;
    flex-direction: column;
    .stat-label {
      font-size: 13px;
      color: #5e7e9c;
      margin-bottom: 6px;
    }
    .stat-number {
      font-size: 30px;
      font-weight: 700;
      color: #0b2b4a;
      line-height: 1;
      &.signed { color: #2eb85c; }
      &.absent { color: #ff4d4d; }
    }
    .stat-percent {
      font-size: 30px;
      font-weight: 700;
      color: #1e6df2;
    }
  }
}

.sign-records {
  background: white;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}
.records-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 10px;
    i { color: #1e6df2; }
  }
  .btn-manual-sign {
    background: #1e6df2;
    border: none;
    color: white;
    padding: 10px 22px;
    border-radius: 40px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    transition: 0.2s;
    &:hover { background: #0a4bb0; }
  }
}
.record-table {
  width: 100%;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid #edf2f7;
  margin-bottom: 20px;
}
.table-header {
  display: grid;
  grid-template-columns: 80px 1.2fr 1fr 1.2fr 1fr 1.2fr;
  background: #f0f7ff;
  padding: 16px 20px;
  font-weight: 700;
  color: #0b2b4a;
  font-size: 14px;
}
.table-row {
  display: grid;
  grid-template-columns: 80px 1.2fr 1fr 1.2fr 1fr 1.2fr;
  padding: 16px 20px;
  border-bottom: 1px solid #edf2f7;
  align-items: center;
  font-size: 14px;
  &:last-child { border-bottom: none; }
  &:hover { background: #fafcff; }
}
.sign-method {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 40px;
  font-size: 13px;
  font-weight: 600;
  &.qr { background: #e8f1ff; color: #1e6df2; }
  &.ai { background: #e6f7e6; color: #2eb85c; }
  &.manual { background: #fff2d6; color: #b9770e; }
}
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 40px;
  font-size: 13px;
  font-weight: 600;
  &.signed { background: #e3f2e9; color: #1e7b4c; }
  &.absent { background: #ffe0e0; color: #cc3b3b; }
}
.btn-mark, .btn-undo {
  padding: 6px 16px;
  border-radius: 40px;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  border: none;
  transition: 0.2s;
}
.btn-mark {
  background: #1e6df2;
  color: white;
  &:hover { background: #0a4bb0; }
}
.btn-undo {
  background: #f0f5fa;
  color: #4a668a;
  &:hover { background: #e2eaf2; }
}
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .total-info { color: #5e7e9c; font-size: 14px; }
  .btn-export {
    background: transparent;
    border: 2px solid #1e6df2;
    color: #1e6df2;
    padding: 10px 24px;
    border-radius: 40px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    transition: 0.2s;
    &:hover { background: #e8f1ff; }
  }
}

.register-form {
  margin-bottom: 32px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
  
  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  
  label {
    font-size: 14px;
    font-weight: 600;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 6px;
    
    i {
      color: #1e6df2;
      font-size: 14px;
    }
  }
  
  input {
    padding: 12px 16px;
    border: 2px solid #e2eaf2;
    border-radius: 12px;
    font-size: 15px;
    outline: none;
    transition: all 0.2s;
    
    &:focus {
      border-color: #1e6df2;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
    }
  }
}

.photo-upload-section {
  margin-bottom: 24px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: #0b2b4a;
  display: block;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  
  i {
    color: #1e6df2;
  }
}

.photo-actions-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.btn-capture-student,
.btn-upload-student {
  padding: 10px 20px;
  border-radius: 12px;
  border: none;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  transition: all 0.2s;
}

.btn-capture-student {
  background: #1e6df2;
  color: white;
  
  &:hover:not(:disabled) { background: #0a4bb0; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.btn-upload-student {
  background: #2eb85c;
  color: white;
  
  &:hover { background: #269e4d; }
}

.camera-preview-student {
  width: 100%;
  max-width: 400px;
  margin-top: 12px;
  border-radius: 14px;
  overflow: hidden;
  position: relative;
  background: #000;
}

.camera-video-student {
  width: 100%;
  max-height: 300px;
  object-fit: cover;
  display: block;
}

.camera-actions-student {
  display: flex;
  gap: 8px;
  padding: 10px;
  background: rgba(0,0,0,0.5);
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  justify-content: center;
}

.btn-capture-photo-student {
  background: #2eb85c;
  color: white;
  border: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: 600;
  cursor: pointer;
  &:hover { background: #269e4d; }
}

.btn-close-camera-student {
  background: rgba(255,255,255,0.2);
  color: white;
  border: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: 600;
  cursor: pointer;
  &:hover { background: rgba(255,255,255,0.35); }
}

.student-photo-preview {
  margin-top: 12px;
  
  img {
    width: 120px;
    height: 120px;
    object-fit: cover;
    border-radius: 12px;
    border: 3px solid #1e6df2;
  }
}

.btn-register-submit {
  width: 100%;
  padding: 14px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(30, 109, 242, 0.35);
  }
  
  &:disabled {
    background: linear-gradient(135deg, #92b6f5, #a8c4fa);
    cursor: not-allowed;
  }
}

.registered-students-section {
  border-top: 2px solid #edf2f7;
  padding-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #0b2b4a;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  
  i {
    color: #1e6df2;
  }
}

.students-table-wrap {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid #edf2f7;
}

.students-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  
  th, td {
    padding: 12px 16px;
    text-align: left;
    border-bottom: 1px solid #edf2f7;
  }
  
  th {
    background: #f0f7ff;
    font-weight: 600;
    color: #0b2b4a;
  }
  
  td { color: #333; }
  
  .empty-text {
    text-align: center;
    color: #5e7e9c;
    padding: 24px;
  }
}

.face-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  
  &.registered {
    background: #e3f2e9;
    color: #1e7b4c;
  }
  
  &.unregistered {
    background: #ffe0e0;
    color: #cc3b3b;
  }
}

.stats-query-form {
  margin-bottom: 24px;
}

.btn-stats-query {
  width: 100%;
  padding: 14px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #2eb85c, #48d68b);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
  
  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(46, 184, 92, 0.35);
  }
  
  &:disabled {
    background: linear-gradient(135deg, #a8e6be, #b8ecc8);
    cursor: not-allowed;
  }
}

.stats-result {
  animation: fadeIn 0.3s ease;
}

.stats-overview {
  background: linear-gradient(135deg, #f0f7ff, #e8f8f0);
  border-radius: 20px;
  padding: 24px;
  margin-bottom: 24px;
}

.stats-title {
  font-size: 20px;
  font-weight: 700;
  color: #0b2b4a;
  margin-bottom: 20px;
  text-align: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  
  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stats-box {
  background: white;
  border-radius: 16px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  
  &.present {
    background: #e3f2e9;
    .stats-value { color: #1e7b4c; }
  }
  
  &.absent {
    background: #ffe0e0;
    .stats-value { color: #cc3b3b; }
  }
  
  &.rate {
    background: #e8f1ff;
    .stats-value { color: #1e6df2; }
  }
  
  .stats-value {
    display: block;
    font-size: 36px;
    font-weight: 700;
    color: #0b2b4a;
    line-height: 1.2;
  }
  
  .stats-label {
    display: block;
    font-size: 13px;
    color: #5e7e9c;
    margin-top: 6px;
  }
}

.stats-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  
  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.detail-column {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #edf2f7;
  
  h4 {
    font-size: 15px;
    font-weight: 700;
    padding: 16px;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  &.present-col h4 {
    color: #1e7b4c;
    background: #e3f2e9;
    border-bottom: 2px solid #c8e6d0;
  }
  
  &.absent-col h4 {
    color: #cc3b3b;
    background: #ffe0e0;
    border-bottom: 2px solid #f5c6c6;
  }
}

.detail-list {
  max-height: 320px;
  overflow-y: auto;
  padding: 8px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
  
  &:last-child {
    border-bottom: none;
  }
  
  .item-name {
    font-weight: 600;
    color: #0b2b4a;
  }
  
  .item-id {
    color: #5e7e9c;
    font-size: 12px;
  }
  
  .item-conf {
    margin-left: auto;
    font-weight: 600;
    font-size: 13px;
  }
  
  &.present-item .item-conf {
    color: #1e7b4c;
  }
  
  &.absent-item {
    color: #888;
  }
}

.empty-detail {
  text-align: center;
  color: #5e7e9c;
  padding: 24px;
  font-size: 14px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(4px);
}
.modal-card {
  width: 480px;
  background: white;
  border-radius: 32px;
  padding: 28px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.2);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  h3 {
    font-size: 20px;
    font-weight: 700;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 10px;
    i { color: #1e6df2; }
  }
  .close-btn {
    background: transparent;
    border: none;
    font-size: 20px;
    color: #5e7e9c;
    cursor: pointer;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    &:hover { background: #f0f5fa; }
  }
}
.modal-body {
  p {
    color: #4a668a;
    margin-bottom: 16px;
  }
  .modal-input {
    width: 100%;
    padding: 16px 20px;
    border: 2px solid #e2eaf2;
    border-radius: 16px;
    font-size: 15px;
    margin-bottom: 20px;
    &:focus {
      border-color: #1e6df2;
      outline: none;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
    }
  }
  .search-results {
    max-height: 240px;
    overflow-y: auto;
    border-top: 1px solid #edf2f7;
    padding-top: 16px;
  }
  .result-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 0;
    border-bottom: 1px solid #edf2f7;
    &:last-child { border-bottom: none; }
    .btn-sign {
      background: #1e6df2;
      color: white;
      border: none;
      padding: 6px 18px;
      border-radius: 40px;
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
      &:hover { background: #0a4bb0; }
    }
  }
  .no-result {
    text-align: center;
    color: #5e7e9c;
    padding: 20px 0;
  }
}
.toast-message {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  padding: 14px 28px;
  border-radius: 60px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.2);
  z-index: 9999;
  animation: slideUpToast 0.3s;
  &.success { background: #2eb85c; color: white; }
  &.info { background: #1e6df2; color: white; }
  &.error { background: #ff4d4f; color: white; }
  i { font-size: 20px; }
}
@keyframes slideUpToast {
  from { opacity: 0; transform: translate(-50%, 20px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}

.recognition-modal {
  width: 700px;
  max-width: 95vw;
  max-height: 90vh;
  overflow-y: auto;
}

.recognition-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  
  .stat-box {
    flex: 1;
    background: #f0f7ff;
    border-radius: 16px;
    padding: 16px;
    text-align: center;
    
    &.success { background: #e3f2e9; }
    &.warning { background: #fff3e0; }
    
    .stat-value {
      display: block;
      font-size: 32px;
      font-weight: 700;
      color: #0b2b4a;
    }
    
    .stat-label {
      font-size: 13px;
      color: #5e7e9c;
    }
  }
}

.result-image {
  margin-bottom: 20px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #edf2f7;
  
  img {
    width: 100%;
    display: block;
  }
}

.recognized-list {
  h4 {
    font-size: 16px;
    color: #0b2b4a;
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    i { color: #1e6df2; }
  }
}

.student-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.student-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f8fcff;
  border-radius: 12px;
  padding: 12px;
  border: 1px solid rgba(30, 109, 242, 0.1);
  
  .student-avatar {
    width: 40px;
    height: 40px;
    background: linear-gradient(135deg, #1e6df2, #4a8cf6);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 16px;
  }
  
  .student-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    
    .student-name { font-weight: 600; color: #0b2b4a; font-size: 14px; }
    .student-id { font-size: 12px; color: #5e7e9c; }
    .confidence { font-size: 11px; color: #2eb85c; }
  }
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #edf2f7;
  margin-top: 16px;
  
  .btn-confirm {
    background: linear-gradient(135deg, #1e6df2, #4a8cf6);
    color: white;
    border: none;
    padding: 10px 24px;
    border-radius: 40px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    &:hover { filter: brightness(1.05); }
  }
}

@media (max-width: 1000px) {
  .control-card {
    flex-direction: column;
    align-items: stretch;
  }
  .table-header,
  .table-row {
    grid-template-columns: 60px 1fr 0.8fr 1fr 0.8fr 1.2fr;
    font-size: 12px;
  }
}
</style>
