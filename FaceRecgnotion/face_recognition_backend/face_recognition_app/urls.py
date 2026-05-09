from django.urls import path
from . import views
from . import attendance_views

urlpatterns = [
    path('session/start', views.start_session, name='start_session'),
    path('session/end', views.end_session, name='end_session'),
    path('analyze', views.analyze_frame, name='analyze_frame'),
    path('session/<str:session_id>/status', views.get_session_status, name='get_session_status'),
    path('trigger', views.trigger_knowledge, name='trigger_knowledge'),
    path('session/<str:session_id>/evaluation', views.get_evaluation, name='get_evaluation'),
    
    path('attendance/student/register', attendance_views.register_student, name='register_student'),
    path('attendance/students', attendance_views.list_students, name='list_students'),
    path('attendance/student/<str:student_id>', attendance_views.delete_student, name='delete_student'),
    path('attendance/photo/upload', attendance_views.upload_class_photo, name='upload_class_photo'),
    path('attendance/statistics', attendance_views.get_attendance_statistics, name='get_attendance_statistics'),
    path('attendance/history', attendance_views.get_attendance_history, name='get_attendance_history'),
    path('attendance/photos', attendance_views.get_class_photo_history, name='get_class_photo_history'),
]
