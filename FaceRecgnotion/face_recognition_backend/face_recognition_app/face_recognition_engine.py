import cv2
import numpy as np
import pickle
import os
from typing import List, Dict, Tuple, Optional
from django.conf import settings


class FaceRecognitionEngine:
    def __init__(self):
        self.known_face_encodings = []
        self.known_face_ids = []
        self.known_face_names = []
        self.recognizer = cv2.face.LBPHFaceRecognizer_create()
        self.face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        self.trained = False
        self.confidence_threshold = 100
        
    def load_known_faces(self, students_data: List[Dict]):
        """
        加载已知学生的人脸特征
        students_data: [{'student_id': str, 'name': str, 'encoding': List[float]}, ...]
        """
        self.known_face_encodings = []
        self.known_face_ids = []
        self.known_face_names = []
        
        faces = []
        labels = []
        label_map = {}
        label_counter = 0
        
        for student in students_data:
            if 'encoding' in student and student['encoding']:
                encoding = np.array(student['encoding'], dtype=np.uint8).reshape(100, 100)
                faces.append(encoding)
                
                if student['student_id'] not in label_map:
                    label_map[student['student_id']] = label_counter
                    label_counter += 1
                
                labels.append(label_map[student['student_id']])
                self.known_face_ids.append(student['student_id'])
                self.known_face_names.append(student['name'])
        
        if faces:
            self.recognizer.train(faces, np.array(labels))
            self.trained = True
            self.label_map = label_map
            self.reverse_label_map = {v: k for k, v in label_map.items()}
        
        print(f"已加载 {len(self.known_face_ids)} 个学生的人脸特征")
    
    def extract_face_encoding(self, image: np.ndarray) -> Tuple[Optional[np.ndarray], Optional[Dict]]:
        """
        从图像中提取人脸特征
        返回: (face_encoding, face_location)
        """
        try:
            if image is None or image.size == 0:
                return None, None
            
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            
            faces = self.face_cascade.detectMultiScale(gray, 1.3, 5)
            
            if len(faces) == 0:
                return None, None
            
            x, y, w, h = faces[0]
            face_roi = gray[y:y+h, x:x+w]
            face_roi = cv2.resize(face_roi, (100, 100))
            
            face_location = {
                'top': y,
                'right': x + w,
                'bottom': y + h,
                'left': x
            }
            
            return face_roi, face_location
            
        except Exception as e:
            print(f"提取人脸特征失败: {e}")
            return None, None
    
    def detect_faces_in_image(self, image: np.ndarray) -> List[Dict]:
        """
        检测图像中的所有人脸
        返回: [{'encoding': np.ndarray, 'location': Dict, 'bbox': Tuple}, ...]
        """
        try:
            if image is None or image.size == 0:
                return []
            
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            
            faces = self.face_cascade.detectMultiScale(gray, 1.3, 5)
            
            results = []
            for (x, y, w, h) in faces:
                face_roi = gray[y:y+h, x:x+w]
                face_roi = cv2.resize(face_roi, (100, 100))
                
                results.append({
                    'encoding': face_roi,
                    'location': {
                        'top': y,
                        'right': x + w,
                        'bottom': y + h,
                        'left': x
                    },
                    'bbox': (x, y, x + w, y + h)
                })
            
            return results
            
        except Exception as e:
            print(f"检测人脸失败: {e}")
            return []
    
    def recognize_face(self, face_encoding: np.ndarray) -> Tuple[Optional[str], Optional[str], float]:
        """
        识别单个人脸
        返回: (student_id, student_name, confidence)
        """
        if not self.trained:
            return None, None, 0.0
        
        try:
            label, confidence = self.recognizer.predict(face_encoding)
            
            if confidence < self.confidence_threshold:
                student_id = self.reverse_label_map.get(label)
                if student_id:
                    idx = self.known_face_ids.index(student_id)
                    student_name = self.known_face_names[idx]
                    normalized_confidence = 1.0 - (confidence / self.confidence_threshold)
                    return student_id, student_name, normalized_confidence
            
            return None, None, 0.0
            
        except Exception as e:
            print(f"识别人脸失败: {e}")
            return None, None, 0.0
    
    def recognize_faces_in_photo(self, image: np.ndarray) -> Dict:
        """
        识别照片中的所有学生
        返回: {
            'total_detected': int,
            'total_recognized': int,
            'recognized_students': [{'student_id': str, 'name': str, 'confidence': float, 'location': Dict}, ...],
            'unrecognized_faces': [{'location': Dict}, ...]
        }
        """
        detected_faces = self.detect_faces_in_image(image)
        
        result = {
            'total_detected': len(detected_faces),
            'total_recognized': 0,
            'recognized_students': [],
            'unrecognized_faces': []
        }
        
        for face_data in detected_faces:
            student_id, student_name, confidence = self.recognize_face(face_data['encoding'])
            
            if student_id:
                result['recognized_students'].append({
                    'student_id': student_id,
                    'name': student_name,
                    'confidence': float(confidence),
                    'location': face_data['location']
                })
                result['total_recognized'] += 1
            else:
                result['unrecognized_faces'].append({
                    'location': face_data['location']
                })
        
        return result
    
    def draw_recognition_results(self, image: np.ndarray, recognition_result: Dict) -> np.ndarray:
        """
        在图像上绘制识别结果
        """
        result_image = image.copy()
        
        for student in recognition_result['recognized_students']:
            location = student['location']
            top, right, bottom, left = location['top'], location['right'], location['bottom'], location['left']
            
            cv2.rectangle(result_image, (left, top), (right, bottom), (0, 255, 0), 2)
            
            label = f"{student['name']} ({student['confidence']:.2f})"
            cv2.putText(result_image, label, (left, top - 10),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
        
        for face in recognition_result['unrecognized_faces']:
            location = face['location']
            top, right, bottom, left = location['top'], location['right'], location['bottom'], location['left']
            
            cv2.rectangle(result_image, (left, top), (right, bottom), (0, 0, 255), 2)
            cv2.putText(result_image, "Unknown", (left, top - 10),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
        
        summary = f"Detected: {recognition_result['total_detected']} | Recognized: {recognition_result['total_recognized']}"
        cv2.putText(result_image, summary, (10, 30),
                   cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        
        return result_image
    
    def save_encoding_to_file(self, encoding: np.ndarray, filepath: str):
        """保存人脸特征到文件"""
        try:
            with open(filepath, 'wb') as f:
                pickle.dump(encoding, f)
        except Exception as e:
            print(f"保存特征失败: {e}")
    
    def load_encoding_from_file(self, filepath: str) -> Optional[np.ndarray]:
        """从文件加载人脸特征"""
        try:
            with open(filepath, 'rb') as f:
                return pickle.load(f)
        except Exception as e:
            print(f"加载特征失败: {e}")
            return None
