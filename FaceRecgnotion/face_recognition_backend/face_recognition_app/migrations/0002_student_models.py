from django.db import migrations, models
import django.utils.timezone


class Migration(migrations.Migration):

    dependencies = [
        ('face_recognition_app', '0001_initial'),
    ]

    operations = [
        migrations.CreateModel(
            name='Student',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('student_id', models.CharField(max_length=50, unique=True, verbose_name='学号')),
                ('name', models.CharField(max_length=100, verbose_name='姓名')),
                ('class_name', models.CharField(max_length=100, verbose_name='班级')),
                ('created_at', models.DateTimeField(default=django.utils.timezone.now, verbose_name='创建时间')),
                ('updated_at', models.DateTimeField(auto_now=True, verbose_name='更新时间')),
            ],
            options={
                'verbose_name': '学生',
                'verbose_name_plural': '学生',
                'ordering': ['class_name', 'student_id'],
            },
        ),
        migrations.CreateModel(
            name='StudentFaceEncoding',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('encoding', models.JSONField(verbose_name='人脸特征向量')),
                ('photo_path', models.CharField(blank=True, max_length=255, null=True, verbose_name='照片路径')),
                ('created_at', models.DateTimeField(default=django.utils.timezone.now, verbose_name='创建时间')),
                ('student', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='face_encodings', to='face_recognition_app.student', verbose_name='学生')),
            ],
            options={
                'verbose_name': '学生人脸特征',
                'verbose_name_plural': '学生人脸特征',
            },
        ),
        migrations.CreateModel(
            name='AttendanceRecord',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('class_name', models.CharField(max_length=100, verbose_name='班级')),
                ('date', models.DateField(verbose_name='日期')),
                ('status', models.CharField(choices=[('present', '出勤'), ('absent', '缺勤'), ('late', '迟到'), ('leave', '请假')], default='present', max_length=20, verbose_name='状态')),
                ('confidence', models.FloatField(default=0.0, verbose_name='识别置信度')),
                ('photo_path', models.CharField(blank=True, max_length=255, null=True, verbose_name='照片路径')),
                ('created_at', models.DateTimeField(default=django.utils.timezone.now, verbose_name='创建时间')),
                ('student', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='attendance_records', to='face_recognition_app.student', verbose_name='学生')),
            ],
            options={
                'verbose_name': '考勤记录',
                'verbose_name_plural': '考勤记录',
                'ordering': ['-date', 'class_name'],
            },
        ),
        migrations.CreateModel(
            name='ClassPhoto',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('photo_id', models.CharField(max_length=100, unique=True, verbose_name='照片ID')),
                ('class_name', models.CharField(max_length=100, verbose_name='班级')),
                ('photo_path', models.CharField(max_length=255, verbose_name='照片路径')),
                ('upload_time', models.DateTimeField(default=django.utils.timezone.now, verbose_name='上传时间')),
                ('total_detected', models.IntegerField(default=0, verbose_name='检测到的人数')),
                ('total_recognized', models.IntegerField(default=0, verbose_name='识别出的人数')),
                ('processed', models.BooleanField(default=False, verbose_name='是否已处理')),
            ],
            options={
                'verbose_name': '班级合影',
                'verbose_name_plural': '班级合影',
                'ordering': ['-upload_time'],
            },
        ),
        migrations.AlterUniqueTogether(
            name='attendancerecord',
            unique_together={('student', 'date')},
        ),
    ]
