import React, { useState, useRef } from 'react';

const API_BASE_URL = 'http://localhost:8080/api';

interface Student {
  student_id: string;
  name: string;
  class_name: string;
  face_registered: boolean;
  created_at: string;
}

interface RecognizedStudent {
  student_id: string;
  name: string;
  confidence: number;
  location: {
    top: number;
    right: number;
    bottom: number;
    left: number;
  };
}

interface AttendanceResult {
  photo_id: string;
  total_detected: number;
  total_recognized: number;
  recognized_students: RecognizedStudent[];
  result_image: string;
}

const AttendancePage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'register' | 'upload' | 'statistics'>('register');
  const [students, setStudents] = useState<Student[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  
  const [studentForm, setStudentForm] = useState({
    student_id: '',
    name: '',
    class_name: '',
  });
  const [studentPhoto, setStudentPhoto] = useState<string | null>(null);
  const studentVideoRef = useRef<HTMLVideoElement>(null);
  const studentCanvasRef = useRef<HTMLCanvasElement>(null);
  const [isStudentCameraOpen, setIsStudentCameraOpen] = useState(false);
  
  const [uploadClassName, setUploadClassName] = useState('');
  const [uploadDate, setUploadDate] = useState(new Date().toISOString().split('T')[0]);
  const [classPhoto, setClassPhoto] = useState<string | null>(null);
  const [attendanceResult, setAttendanceResult] = useState<AttendanceResult | null>(null);
  const classVideoRef = useRef<HTMLVideoElement>(null);
  const classCanvasRef = useRef<HTMLCanvasElement>(null);
  const [isClassCameraOpen, setIsClassCameraOpen] = useState(false);
  
  const [statsClassName, setStatsClassName] = useState('');
  const [statsDate, setStatsDate] = useState(new Date().toISOString().split('T')[0]);
  const [attendanceStats, setAttendanceStats] = useState<any>(null);

  const showMessage = (type: 'success' | 'error', text: string) => {
    setMessage({ type, text });
    setTimeout(() => setMessage(null), 5000);
  };

  const openStudentCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true });
      if (studentVideoRef.current) {
        studentVideoRef.current.srcObject = stream;
        setIsStudentCameraOpen(true);
      }
    } catch (error) {
      showMessage('error', '无法打开摄像头');
    }
  };

  const captureStudentPhoto = () => {
    if (studentVideoRef.current && studentCanvasRef.current) {
      const video = studentVideoRef.current;
      const canvas = studentCanvasRef.current;
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(video, 0, 0);
        const photoData = canvas.toDataURL('image/jpeg');
        setStudentPhoto(photoData);
        closeStudentCamera();
      }
    }
  };

  const closeStudentCamera = () => {
    if (studentVideoRef.current && studentVideoRef.current.srcObject) {
      const stream = studentVideoRef.current.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      setIsStudentCameraOpen(false);
    }
  };

  const handleStudentPhotoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        setStudentPhoto(event.target?.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const registerStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!studentPhoto) {
      showMessage('error', '请先拍摄或上传学生照片');
      return;
    }
    
    setLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/attendance/student/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...studentForm,
          photo: studentPhoto,
        }),
      });
      
      const data = await response.json();
      
      if (data.success) {
        showMessage('success', '学生注册成功！');
        setStudentForm({ student_id: '', name: '', class_name: '' });
        setStudentPhoto(null);
        loadStudents();
      } else {
        showMessage('error', data.message || '注册失败');
      }
    } catch (error) {
      showMessage('error', '网络错误，请重试');
    } finally {
      setLoading(false);
    }
  };

  const loadStudents = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/attendance/students`);
      const data = await response.json();
      if (data.success) {
        setStudents(data.students);
      }
    } catch (error) {
      console.error('Failed to load students:', error);
    }
  };

  const openClassCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true });
      if (classVideoRef.current) {
        classVideoRef.current.srcObject = stream;
        setIsClassCameraOpen(true);
      }
    } catch (error) {
      showMessage('error', '无法打开摄像头');
    }
  };

  const captureClassPhoto = () => {
    if (classVideoRef.current && classCanvasRef.current) {
      const video = classVideoRef.current;
      const canvas = classCanvasRef.current;
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(video, 0, 0);
        const photoData = canvas.toDataURL('image/jpeg');
        setClassPhoto(photoData);
        closeClassCamera();
      }
    }
  };

  const closeClassCamera = () => {
    if (classVideoRef.current && classVideoRef.current.srcObject) {
      const stream = classVideoRef.current.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      setIsClassCameraOpen(false);
    }
  };

  const handleClassPhotoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        setClassPhoto(event.target?.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const uploadAndAnalyze = async () => {
    if (!classPhoto || !uploadClassName) {
      showMessage('error', '请填写班级信息并上传照片');
      return;
    }
    
    setLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/attendance/photo/upload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          class_name: uploadClassName,
          photo: classPhoto,
          date: uploadDate,
        }),
      });
      
      const data = await response.json();
      
      if (data.success) {
        setAttendanceResult(data);
        showMessage('success', `识别完成！检测到 ${data.total_detected} 人，识别出 ${data.total_recognized} 人`);
      } else {
        showMessage('error', data.message || '识别失败');
      }
    } catch (error) {
      showMessage('error', '网络错误，请重试');
    } finally {
      setLoading(false);
    }
  };

  const loadAttendanceStats = async () => {
    if (!statsClassName) {
      showMessage('error', '请输入班级名称');
      return;
    }
    
    setLoading(true);
    try {
      const response = await fetch(
        `${API_BASE_URL}/attendance/statistics?class_name=${statsClassName}&date=${statsDate}`
      );
      const data = await response.json();
      
      if (data.success) {
        setAttendanceStats(data);
      } else {
        showMessage('error', data.message || '查询失败');
      }
    } catch (error) {
      showMessage('error', '网络错误，请重试');
    } finally {
      setLoading(false);
    }
  };

  React.useEffect(() => {
    loadStudents();
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 p-8">
      <div className="max-w-7xl mx-auto">
        <h1 className="text-4xl font-bold text-center text-gray-800 mb-8">
          📸 学生到班人数统计系统
        </h1>
        
        {message && (
          <div className={`mb-4 p-4 rounded-lg ${message.type === 'success' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
            {message.text}
          </div>
        )}
        
        <div className="bg-white rounded-xl shadow-lg overflow-hidden">
          <div className="flex border-b">
            <button
              onClick={() => setActiveTab('register')}
              className={`flex-1 py-4 px-6 text-center font-medium transition-colors ${
                activeTab === 'register'
                  ? 'bg-blue-500 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              👤 学生注册
            </button>
            <button
              onClick={() => setActiveTab('upload')}
              className={`flex-1 py-4 px-6 text-center font-medium transition-colors ${
                activeTab === 'upload'
                  ? 'bg-blue-500 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              📷 上传合影
            </button>
            <button
              onClick={() => setActiveTab('statistics')}
              className={`flex-1 py-4 px-6 text-center font-medium transition-colors ${
                activeTab === 'statistics'
                  ? 'bg-blue-500 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              📊 考勤统计
            </button>
          </div>
          
          <div className="p-6">
            {activeTab === 'register' && (
              <div className="space-y-6">
                <form onSubmit={registerStudent} className="space-y-4">
                  <div className="grid grid-cols-3 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">学号</label>
                      <input
                        type="text"
                        value={studentForm.student_id}
                        onChange={(e) => setStudentForm({ ...studentForm, student_id: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">姓名</label>
                      <input
                        type="text"
                        value={studentForm.name}
                        onChange={(e) => setStudentForm({ ...studentForm, name: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">班级</label>
                      <input
                        type="text"
                        value={studentForm.class_name}
                        onChange={(e) => setStudentForm({ ...studentForm, class_name: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                        required
                      />
                    </div>
                  </div>
                  
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">学生照片</label>
                    <div className="flex gap-4">
                      <button
                        type="button"
                        onClick={openStudentCamera}
                        className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
                      >
                        📸 拍照
                      </button>
                      <label className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 cursor-pointer">
                        📁 上传照片
                        <input
                          type="file"
                          accept="image/*"
                          onChange={handleStudentPhotoUpload}
                          className="hidden"
                        />
                      </label>
                    </div>
                  </div>
                  
                  {isStudentCameraOpen && (
                    <div className="space-y-4">
                      <video ref={studentVideoRef} autoPlay className="w-full max-w-md rounded-lg" />
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={captureStudentPhoto}
                          className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600"
                        >
                          拍照
                        </button>
                        <button
                          type="button"
                          onClick={closeStudentCamera}
                          className="px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600"
                        >
                          关闭
                        </button>
                      </div>
                      <canvas ref={studentCanvasRef} className="hidden" />
                    </div>
                  )}
                  
                  {studentPhoto && (
                    <div>
                      <img src={studentPhoto} alt="学生照片" className="w-48 h-48 object-cover rounded-lg" />
                    </div>
                  )}
                  
                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:bg-gray-400 font-medium"
                  >
                    {loading ? '注册中...' : '注册学生'}
                  </button>
                </form>
                
                <div>
                  <h3 className="text-lg font-medium text-gray-800 mb-3">已注册学生 ({students.length})</h3>
                  <div className="max-h-64 overflow-y-auto">
                    <table className="w-full">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="px-4 py-2 text-left">学号</th>
                          <th className="px-4 py-2 text-left">姓名</th>
                          <th className="px-4 py-2 text-left">班级</th>
                          <th className="px-4 py-2 text-left">人脸状态</th>
                        </tr>
                      </thead>
                      <tbody>
                        {students.map((student) => (
                          <tr key={student.student_id} className="border-b">
                            <td className="px-4 py-2">{student.student_id}</td>
                            <td className="px-4 py-2">{student.name}</td>
                            <td className="px-4 py-2">{student.class_name}</td>
                            <td className="px-4 py-2">
                              {student.face_registered ? (
                                <span className="text-green-600">✓ 已录入</span>
                              ) : (
                                <span className="text-red-600">✗ 未录入</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}
            
            {activeTab === 'upload' && (
              <div className="space-y-6">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">班级</label>
                    <input
                      type="text"
                      value={uploadClassName}
                      onChange={(e) => setUploadClassName(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                      placeholder="例如：三年级(1)班"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">日期</label>
                    <input
                      type="date"
                      value={uploadDate}
                      onChange={(e) => setUploadDate(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">班级合影照片</label>
                  <div className="flex gap-4">
                    <button
                      type="button"
                      onClick={openClassCamera}
                      className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
                    >
                      📸 拍照
                    </button>
                    <label className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 cursor-pointer">
                      📁 上传照片
                      <input
                        type="file"
                        accept="image/*"
                        onChange={handleClassPhotoUpload}
                        className="hidden"
                      />
                    </label>
                  </div>
                </div>
                
                {isClassCameraOpen && (
                  <div className="space-y-4">
                    <video ref={classVideoRef} autoPlay className="w-full rounded-lg" />
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={captureClassPhoto}
                        className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600"
                      >
                        拍照
                      </button>
                      <button
                        type="button"
                        onClick={closeClassCamera}
                        className="px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600"
                      >
                        关闭
                      </button>
                    </div>
                    <canvas ref={classCanvasRef} className="hidden" />
                  </div>
                )}
                
                {classPhoto && (
                  <div>
                    <img src={classPhoto} alt="班级合影" className="w-full max-w-2xl rounded-lg" />
                  </div>
                )}
                
                <button
                  onClick={uploadAndAnalyze}
                  disabled={loading || !classPhoto || !uploadClassName}
                  className="w-full py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:bg-gray-400 font-medium"
                >
                  {loading ? '识别中...' : '开始识别'}
                </button>
                
                {attendanceResult && (
                  <div className="space-y-4">
                    <div className="bg-blue-50 p-4 rounded-lg">
                      <h3 className="text-lg font-medium text-gray-800 mb-2">识别结果</h3>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-gray-600">检测到人数：</p>
                          <p className="text-2xl font-bold text-blue-600">{attendanceResult.total_detected}</p>
                        </div>
                        <div>
                          <p className="text-gray-600">识别出人数：</p>
                          <p className="text-2xl font-bold text-green-600">{attendanceResult.total_recognized}</p>
                        </div>
                      </div>
                    </div>
                    
                    {attendanceResult.result_image && (
                      <div>
                        <h4 className="text-md font-medium text-gray-800 mb-2">识别结果图</h4>
                        <img src={attendanceResult.result_image} alt="识别结果" className="w-full rounded-lg" />
                      </div>
                    )}
                    
                    <div>
                      <h4 className="text-md font-medium text-gray-800 mb-2">已识别学生名单</h4>
                      <div className="max-h-64 overflow-y-auto">
                        <table className="w-full">
                          <thead className="bg-gray-50">
                            <tr>
                              <th className="px-4 py-2 text-left">学号</th>
                              <th className="px-4 py-2 text-left">姓名</th>
                              <th className="px-4 py-2 text-left">置信度</th>
                            </tr>
                          </thead>
                          <tbody>
                            {attendanceResult.recognized_students.map((student, idx) => (
                              <tr key={idx} className="border-b">
                                <td className="px-4 py-2">{student.student_id}</td>
                                <td className="px-4 py-2">{student.name}</td>
                                <td className="px-4 py-2">
                                  <span className={`font-medium ${student.confidence > 0.7 ? 'text-green-600' : 'text-yellow-600'}`}>
                                    {(student.confidence * 100).toFixed(1)}%
                                  </span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
            
            {activeTab === 'statistics' && (
              <div className="space-y-6">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">班级</label>
                    <input
                      type="text"
                      value={statsClassName}
                      onChange={(e) => setStatsClassName(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                      placeholder="例如：三年级(1)班"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">日期</label>
                    <input
                      type="date"
                      value={statsDate}
                      onChange={(e) => setStatsDate(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
                
                <button
                  onClick={loadAttendanceStats}
                  disabled={loading || !statsClassName}
                  className="w-full py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:bg-gray-400 font-medium"
                >
                  {loading ? '查询中...' : '查询考勤'}
                </button>
                
                {attendanceStats && (
                  <div className="space-y-4">
                    <div className="bg-gradient-to-r from-blue-50 to-green-50 p-6 rounded-lg">
                      <h3 className="text-xl font-bold text-gray-800 mb-4">
                        {attendanceStats.class_name} - {attendanceStats.date}
                      </h3>
                      <div className="grid grid-cols-4 gap-4">
                        <div className="text-center">
                          <p className="text-gray-600 text-sm">班级总人数</p>
                          <p className="text-3xl font-bold text-gray-800">{attendanceStats.total_students}</p>
                        </div>
                        <div className="text-center">
                          <p className="text-gray-600 text-sm">出勤人数</p>
                          <p className="text-3xl font-bold text-green-600">{attendanceStats.present_count}</p>
                        </div>
                        <div className="text-center">
                          <p className="text-gray-600 text-sm">缺勤人数</p>
                          <p className="text-3xl font-bold text-red-600">{attendanceStats.absent_count}</p>
                        </div>
                        <div className="text-center">
                          <p className="text-gray-600 text-sm">出勤率</p>
                          <p className="text-3xl font-bold text-blue-600">
                            {(attendanceStats.attendance_rate * 100).toFixed(1)}%
                          </p>
                        </div>
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <h4 className="text-md font-medium text-gray-800 mb-2">✅ 出勤学生</h4>
                        <div className="max-h-64 overflow-y-auto bg-green-50 rounded-lg p-3">
                          {attendanceStats.present_students.map((student: any, idx: number) => (
                            <div key={idx} className="py-2 border-b border-green-200 last:border-0">
                              <span className="font-medium">{student.name}</span>
                              <span className="text-gray-600 text-sm ml-2">({student.student_id})</span>
                              <span className="text-green-600 text-sm ml-2">
                                {(student.confidence * 100).toFixed(1)}%
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                      
                      <div>
                        <h4 className="text-md font-medium text-gray-800 mb-2">❌ 缺勤学生</h4>
                        <div className="max-h-64 overflow-y-auto bg-red-50 rounded-lg p-3">
                          {attendanceStats.absent_students.map((student: any, idx: number) => (
                            <div key={idx} className="py-2 border-b border-red-200 last:border-0">
                              <span className="font-medium">{student.name}</span>
                              <span className="text-gray-600 text-sm ml-2">({student.student_id})</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default AttendancePage;
