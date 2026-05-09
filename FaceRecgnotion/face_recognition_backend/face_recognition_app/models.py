from django.db import models
from django.utils import timezone


class Student(models.Model):
    student_id = models.CharField(max_length=50, unique=True, verbose_name='学号')
    name = models.CharField(max_length=100, verbose_name='姓名')
    class_name = models.CharField(max_length=100, verbose_name='班级')
    created_at = models.DateTimeField(default=timezone.now, verbose_name='创建时间')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='更新时间')
    
    class Meta:
        verbose_name = '学生'
        verbose_name_plural = '学生'
        ordering = ['class_name', 'student_id']
    
    def __str__(self):
        return f"{self.student_id} - {self.name} ({self.class_name})"


class StudentFaceEncoding(models.Model):
    student = models.ForeignKey(Student, on_delete=models.CASCADE, related_name='face_encodings', verbose_name='学生')
    encoding = models.JSONField(verbose_name='人脸特征向量')
    photo_path = models.CharField(max_length=255, null=True, blank=True, verbose_name='照片路径')
    created_at = models.DateTimeField(default=timezone.now, verbose_name='创建时间')
    
    class Meta:
        verbose_name = '学生人脸特征'
        verbose_name_plural = '学生人脸特征'
    
    def __str__(self):
        return f"{self.student.name} 的人脸特征"


class AttendanceRecord(models.Model):
    STATUS_CHOICES = [
        ('present', '出勤'),
        ('absent', '缺勤'),
        ('late', '迟到'),
        ('leave', '请假'),
    ]
    
    student = models.ForeignKey(Student, on_delete=models.CASCADE, related_name='attendance_records', verbose_name='学生')
    class_name = models.CharField(max_length=100, verbose_name='班级')
    date = models.DateField(verbose_name='日期')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='present', verbose_name='状态')
    confidence = models.FloatField(default=0.0, verbose_name='识别置信度')
    photo_path = models.CharField(max_length=255, null=True, blank=True, verbose_name='照片路径')
    created_at = models.DateTimeField(default=timezone.now, verbose_name='创建时间')
    
    class Meta:
        verbose_name = '考勤记录'
        verbose_name_plural = '考勤记录'
        ordering = ['-date', 'class_name']
        unique_together = ['student', 'date']
    
    def __str__(self):
        return f"{self.student.name} - {self.date} - {self.get_status_display()}"


class ClassPhoto(models.Model):
    photo_id = models.CharField(max_length=100, unique=True, verbose_name='照片ID')
    class_name = models.CharField(max_length=100, verbose_name='班级')
    photo_path = models.CharField(max_length=255, verbose_name='照片路径')
    upload_time = models.DateTimeField(default=timezone.now, verbose_name='上传时间')
    total_detected = models.IntegerField(default=0, verbose_name='检测到的人数')
    total_recognized = models.IntegerField(default=0, verbose_name='识别出的人数')
    processed = models.BooleanField(default=False, verbose_name='是否已处理')
    
    class Meta:
        verbose_name = '班级合影'
        verbose_name_plural = '班级合影'
        ordering = ['-upload_time']
    
    def __str__(self):
        return f"{self.class_name} - {self.upload_time.strftime('%Y-%m-%d %H:%M')}"


class LearningSession(models.Model):
    session_id = models.CharField(max_length=100, unique=True)
    start_time = models.DateTimeField(default=timezone.now)
    end_time = models.DateTimeField(null=True, blank=True)
    is_active = models.BooleanField(default=True)

    def __str__(self):
        return f"Session {self.session_id} - {'Active' if self.is_active else 'Ended'}"


class ExpressionRecord(models.Model):
    EXPRESSION_CHOICES = [
        ('happy', '开心'),
        ('sad', '悲伤'),
        ('angry', '愤怒'),
        ('surprised', '惊讶'),
        ('neutral', '中性'),
        ('confused', '困惑'),
        ('bored', '无聊'),
        ('focused', '专注'),
    ]

    session = models.ForeignKey(LearningSession, on_delete=models.CASCADE, related_name='expressions')
    expression = models.CharField(max_length=20, choices=EXPRESSION_CHOICES)
    confidence = models.FloatField()
    timestamp = models.DateTimeField(default=timezone.now)
    frame_data = models.TextField(null=True, blank=True)

    class Meta:
        ordering = ['-timestamp']

    def __str__(self):
        return f"{self.expression} ({self.confidence:.2f}) at {self.timestamp}"


class LearningEvaluation(models.Model):
    session = models.ForeignKey(LearningSession, on_delete=models.CASCADE, related_name='evaluations')
    evaluation_type = models.CharField(max_length=50)
    score = models.FloatField()
    details = models.JSONField(default=dict)
    timestamp = models.DateTimeField(default=timezone.now)

    def __str__(self):
        return f"{self.evaluation_type}: {self.score}"


class KnowledgeTrigger(models.Model):
    TRIGGER_STATUS = [
        ('pending', '待触发'),
        ('triggered', '已触发'),
        ('dismissed', '已忽略'),
    ]

    session = models.ForeignKey(LearningSession, on_delete=models.CASCADE, related_name='triggers')
    trigger_expression = models.CharField(max_length=20)
    suggested_topic = models.CharField(max_length=200)
    status = models.CharField(max_length=20, choices=TRIGGER_STATUS, default='pending')
    created_at = models.DateTimeField(default=timezone.now)
    triggered_at = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"{self.suggested_topic} - {self.status}"
