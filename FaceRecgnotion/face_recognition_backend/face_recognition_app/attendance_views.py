import cv2
import numpy as np
import base64
import json
import uuid
import os
from datetime import date
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
from django.core.files.storage import default_storage
from django.conf import settings
from .models import (
    Student, StudentFaceEncoding, AttendanceRecord, ClassPhoto
)
from .face_recognition_engine import FaceRecognitionEngine

face_engine = FaceRecognitionEngine()


@csrf_exempt
@require_http_methods(["POST"])
def register_student(request):
    """
    注册学生并录入人脸特征
    参数: student_id, name, class_name, photo (base64)
    """
    try:
        data = json.loads(request.body)
        student_id = data.get('student_id')
        name = data.get('name')
        class_name = data.get('class_name')
        photo_data = data.get('photo')
        
        if not all([student_id, name, class_name, photo_data]):
            return JsonResponse({
                'success': False,
                'message': '缺少必要参数'
            }, status=400)
        
        if Student.objects.filter(student_id=student_id).exists():
            return JsonResponse({
                'success': False,
                'message': '该学号已存在'
            }, status=400)
        
        student = Student.objects.create(
            student_id=student_id,
            name=name,
            class_name=class_name
        )
        
        try:
            if ',' in photo_data:
                photo_data = photo_data.split(',')[1]
            
            img_data = base64.b64decode(photo_data)
            nparr = np.frombuffer(img_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            
            if image is None:
                raise ValueError("无法解码图像")
            
            face_encoding, face_location = face_engine.extract_face_encoding(image)
            
            if face_encoding is None:
                student.delete()
                return JsonResponse({
                    'success': False,
                    'message': '未检测到人脸，请确保照片清晰且包含正面人脸'
                }, status=400)
            
            encoding_list = face_encoding.tolist()
            
            photo_filename = f"{student_id}_{uuid.uuid4().hex[:8]}.jpg"
            photo_path = os.path.join('student_photos', photo_filename)
            
            StudentFaceEncoding.objects.create(
                student=student,
                encoding=encoding_list,
                photo_path=photo_path
            )
            
            return JsonResponse({
                'success': True,
                'message': '学生注册成功',
                'student': {
                    'student_id': student.student_id,
                    'name': student.name,
                    'class_name': student.class_name
                }
            })
            
        except Exception as e:
            student.delete()
            return JsonResponse({
                'success': False,
                'message': f'人脸特征提取失败: {str(e)}'
            }, status=500)
            
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["GET"])
def list_students(request):
    """获取学生列表"""
    try:
        class_name = request.GET.get('class_name')
        
        if class_name:
            students = Student.objects.filter(class_name=class_name)
        else:
            students = Student.objects.all()
        
        students_data = []
        for student in students:
            face_count = student.face_encodings.count()
            students_data.append({
                'student_id': student.student_id,
                'name': student.name,
                'class_name': student.class_name,
                'face_registered': face_count > 0,
                'created_at': student.created_at.isoformat()
            })
        
        return JsonResponse({
            'success': True,
            'students': students_data,
            'total': len(students_data)
        })
        
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["DELETE"])
def delete_student(request, student_id):
    """删除学生"""
    try:
        student = Student.objects.get(student_id=student_id)
        student.delete()
        
        return JsonResponse({
            'success': True,
            'message': '学生已删除'
        })
        
    except Student.DoesNotExist:
        return JsonResponse({
            'success': False,
            'message': '学生不存在'
        }, status=404)
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["POST"])
def upload_class_photo(request):
    """
    上传班级合影并识别学生
    参数: class_name, photo (base64), date (可选，默认今天)
    """
    try:
        data = json.loads(request.body)
        class_name = data.get('class_name')
        photo_data = data.get('photo')
        photo_date = data.get('date', str(date.today()))
        
        if not all([class_name, photo_data]):
            return JsonResponse({
                'success': False,
                'message': '缺少必要参数'
            }, status=400)
        
        try:
            if ',' in photo_data:
                photo_data = photo_data.split(',')[1]
            
            img_data = base64.b64decode(photo_data)
            nparr = np.frombuffer(img_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            
            if image is None:
                raise ValueError("无法解码图像")
            
            students = Student.objects.filter(class_name=class_name)
            students_data = []
            for student in students:
                face_encodings = student.face_encodings.all()
                if face_encodings:
                    students_data.append({
                        'student_id': student.student_id,
                        'name': student.name,
                        'encoding': face_encodings[0].encoding
                    })
            
            if not students_data:
                return JsonResponse({
                    'success': False,
                    'message': f'班级 {class_name} 尚未注册学生或学生未录入人脸特征'
                }, status=400)
            
            face_engine.load_known_faces(students_data)
            
            recognition_result = face_engine.recognize_faces_in_photo(image)
            
            photo_id = str(uuid.uuid4())
            photo_filename = f"{class_name}_{photo_date}_{photo_id[:8]}.jpg"
            photo_path = os.path.join('class_photos', photo_filename)
            
            class_photo = ClassPhoto.objects.create(
                photo_id=photo_id,
                class_name=class_name,
                photo_path=photo_path,
                total_detected=int(recognition_result['total_detected']),
                total_recognized=int(recognition_result['total_recognized'])
            )
            
            recognized_student_ids = set()
            for student_info in recognition_result['recognized_students']:
                student = Student.objects.get(student_id=student_info['student_id'])
                
                AttendanceRecord.objects.update_or_create(
                    student=student,
                    date=photo_date,
                    defaults={
                        'class_name': class_name,
                        'status': 'present',
                        'confidence': float(student_info['confidence']),
                        'photo_path': photo_path
                    }
                )
                recognized_student_ids.add(student.student_id)
            
            all_class_students = Student.objects.filter(class_name=class_name)
            for student in all_class_students:
                if student.student_id not in recognized_student_ids:
                    AttendanceRecord.objects.update_or_create(
                        student=student,
                        date=photo_date,
                        defaults={
                            'class_name': class_name,
                            'status': 'absent',
                            'confidence': 0.0,
                            'photo_path': photo_path
                        }
                    )
            
            result_image = face_engine.draw_recognition_results(image, recognition_result)
            _, buffer = cv2.imencode('.jpg', result_image)
            result_image_base64 = base64.b64encode(buffer).decode('utf-8')
            
            recognized_students_json = []
            for student_info in recognition_result['recognized_students']:
                recognized_students_json.append({
                    'student_id': str(student_info['student_id']),
                    'name': str(student_info['name']),
                    'confidence': float(student_info['confidence']),
                    'location': {
                        'top': int(student_info['location']['top']),
                        'right': int(student_info['location']['right']),
                        'bottom': int(student_info['location']['bottom']),
                        'left': int(student_info['location']['left'])
                    }
                })
            
            return JsonResponse({
                'success': True,
                'message': '照片处理成功',
                'photo_id': photo_id,
                'total_detected': int(recognition_result['total_detected']),
                'total_recognized': int(recognition_result['total_recognized']),
                'recognized_students': recognized_students_json,
                'result_image': f"data:image/jpeg;base64,{result_image_base64}"
            })
            
        except Exception as e:
            return JsonResponse({
                'success': False,
                'message': f'照片处理失败: {str(e)}'
            }, status=500)
            
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["GET"])
def get_attendance_statistics(request):
    """
    获取考勤统计
    参数: class_name, date (可选)
    """
    try:
        class_name = request.GET.get('class_name')
        query_date = request.GET.get('date', str(date.today()))
        
        if not class_name:
            return JsonResponse({
                'success': False,
                'message': '缺少班级参数'
            }, status=400)
        
        attendance_records = AttendanceRecord.objects.filter(
            class_name=class_name,
            date=query_date
        ).select_related('student')
        
        present_students = []
        absent_students = []
        
        for record in attendance_records:
            student_info = {
                'student_id': record.student.student_id,
                'name': record.student.name,
                'status': record.get_status_display(),
                'confidence': record.confidence
            }
            
            if record.status == 'present':
                present_students.append(student_info)
            else:
                absent_students.append(student_info)
        
        total_students = Student.objects.filter(class_name=class_name).count()
        
        return JsonResponse({
            'success': True,
            'date': query_date,
            'class_name': class_name,
            'total_students': total_students,
            'present_count': len(present_students),
            'absent_count': len(absent_students),
            'attendance_rate': len(present_students) / total_students if total_students > 0 else 0,
            'present_students': present_students,
            'absent_students': absent_students
        })
        
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["GET"])
def get_attendance_history(request):
    """
    获取考勤历史记录
    参数: class_name, start_date, end_date
    """
    try:
        class_name = request.GET.get('class_name')
        start_date = request.GET.get('start_date')
        end_date = request.GET.get('end_date')
        
        if not class_name:
            return JsonResponse({
                'success': False,
                'message': '缺少班级参数'
            }, status=400)
        
        records = AttendanceRecord.objects.filter(class_name=class_name)
        
        if start_date:
            records = records.filter(date__gte=start_date)
        if end_date:
            records = records.filter(date__lte=end_date)
        
        records = records.order_by('-date', 'student__student_id')
        
        history_data = []
        for record in records:
            history_data.append({
                'date': str(record.date),
                'student_id': record.student.student_id,
                'name': record.student.name,
                'status': record.get_status_display(),
                'confidence': record.confidence
            })
        
        return JsonResponse({
            'success': True,
            'class_name': class_name,
            'records': history_data,
            'total': len(history_data)
        })
        
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["GET"])
def get_class_photo_history(request):
    """获取班级合影历史"""
    try:
        class_name = request.GET.get('class_name')
        
        photos = ClassPhoto.objects.all()
        if class_name:
            photos = photos.filter(class_name=class_name)
        
        photos = photos.order_by('-upload_time')[:20]
        
        photos_data = []
        for photo in photos:
            photos_data.append({
                'photo_id': photo.photo_id,
                'class_name': photo.class_name,
                'upload_time': photo.upload_time.isoformat(),
                'total_detected': photo.total_detected,
                'total_recognized': photo.total_recognized,
                'processed': photo.processed
            })
        
        return JsonResponse({
            'success': True,
            'photos': photos_data
        })
        
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)
