import cv2
import numpy as np
from typing import Dict, Tuple, Optional
import time


class ExpressionRecognizer:
    def __init__(self):
        self.expression_history = []
        self.history_length = 10
        
        self.face_cascade = None
        try:
            self.face_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
            )
        except:
            pass
        
        self.expression_features = {
            'happy': {'mouth_up': 0.8, 'cheeks_raised': 0.7, 'eyes_squinted': 0.5},
            'sad': {'mouth_down': 0.7, 'inner_eyebrows_raised': 0.6, 'eyes_narrowed': 0.4},
            'angry': {'eyebrows_lowered': 0.8, 'eyes_widened': 0.5, 'mouth_tight': 0.6},
            'surprised': {'eyes_widened': 0.9, 'jaw_dropped': 0.8, 'eyebrows_raised': 0.7},
            'confused': {'eyebrows_furrowed': 0.6, 'head_tilt': 0.5, 'mouth_parted': 0.4},
            'bored': {'eyes_squinted': 0.5, 'head_down': 0.4, 'mouth_relaxed': 0.6},
            'focused': {'eyes_open': 0.8, 'eyebrows_neutral': 0.7, 'mouth_closed': 0.5},
        }

    def detect_face(self, image: np.ndarray) -> Optional[Tuple[np.ndarray, Dict]]:
        try:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            
            if self.face_cascade is not None:
                faces = self.face_cascade.detectMultiScale(
                    gray,
                    scaleFactor=1.1,
                    minNeighbors=5,
                    minSize=(30, 30)
                )
                
                if len(faces) > 0:
                    x, y, w, h = faces[0]
                    face_region = image[y:y+h, x:x+w]
                    
                    face_metrics = {
                        'x': x, 'y': y, 'width': w, 'height': h,
                        'face_area': w * h
                    }
                    
                    return face_region, face_metrics
            
            return None, None
            
        except Exception as e:
            print(f"Error detecting face: {e}")
            return None, None

    def analyze_facial_features(self, face_image: np.ndarray, face_info: Dict) -> Dict:
        metrics = {}
        
        try:
            if face_image.size == 0 or face_image.shape[0] == 0 or face_image.shape[1] == 0:
                return self._get_default_metrics()
            
            hsv = cv2.cvtColor(face_image, cv2.COLOR_BGR2HSV)
            
            gray_face = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
            
            laplacian_var = cv2.Laplacian(gray_face, cv2.CV_64F).var()
            metrics['sharpness'] = laplacian_var
            
            mean_brightness = np.mean(gray_face)
            metrics['brightness'] = mean_brightness
            
            lower_mouth = int(face_image.shape[0] * 0.6)
            upper_mouth = face_image.shape[0]
            left_mouth = int(face_image.shape[1] * 0.3)
            right_mouth = int(face_image.shape[1] * 0.7)
            
            mouth_region = face_image[lower_mouth:upper_mouth, left_mouth:right_mouth]
            
            if mouth_region.size > 0 and mouth_region.shape[0] > 0 and mouth_region.shape[1] > 0:
                mouth_gray = cv2.cvtColor(mouth_region, cv2.COLOR_BGR2GRAY)
                
                _, mouth_thresh = cv2.threshold(mouth_gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
                
                mouth_pixels = np.sum(mouth_thresh == 255)
                total_mouth_pixels = mouth_thresh.shape[0] * mouth_thresh.shape[1]
                
                if total_mouth_pixels > 0:
                    metrics['mouth_open_ratio'] = mouth_pixels / total_mouth_pixels
                else:
                    metrics['mouth_open_ratio'] = 0.1
                
                edges = cv2.Canny(mouth_gray, 50, 150)
                edge_density = np.sum(edges > 0) / (edges.shape[0] * edges.shape[1])
                metrics['mouth_complexity'] = edge_density
                
                upper_face = face_image[:int(face_image.shape[0]*0.5), :]
                upper_gray = cv2.cvtColor(upper_face, cv2.COLOR_BGR2GRAY)
                
                eye_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_eye.xml')
                eyes = eye_cascade.detectMultiScale(upper_gray, 1.1, 5)
                
                metrics['eye_count'] = len(eyes)
                
                if len(eyes) >= 2:
                    eye_area = sum(exh*ew for (ex, ey, ew, eh) in eyes[:2])
                    face_area_upper = upper_face.shape[0] * upper_face.shape[1]
                    if face_area_upper > 0:
                        metrics['eye_visibility'] = eye_area / face_area_upper
                    else:
                        metrics['eye_visibility'] = 0.05
                else:
                    metrics['eye_visibility'] = 0.03
                
                std_dev = np.std(mouth_gray)
                metrics['mouth_activity'] = std_dev
            else:
                return self._get_default_metrics()
            
            hsv_mean = cv2.mean(hsv)
            metrics['hue_mean'] = hsv_mean[0]
            metrics['saturation_mean'] = hsv_mean[1]
            metrics['value_mean'] = hsv_mean[2]
            
        except Exception as e:
            print(f"Error analyzing features: {e}")
            return self._get_default_metrics()
        
        return metrics

    def _get_default_metrics(self) -> Dict:
        return {
            'sharpness': 100.0,
            'brightness': 128.0,
            'mouth_open_ratio': 0.15,
            'mouth_complexity': 0.02,
            'eye_count': 2,
            'eye_visibility': 0.08,
            'mouth_activity': 30.0,
            'hue_mean': 25.0,
            'saturation_mean': 80.0,
            'value_mean': 180.0
        }

    def recognize_expression(self, image: np.ndarray) -> Tuple[str, float]:
        start_time = time.time()
        
        face_region, face_info = self.detect_face(image)
        
        if face_region is None or face_info is None:
            return 'neutral', 0.35
        
        metrics = self.analyze_facial_features(face_region, face_info)
        
        scores = self._calculate_expression_scores(metrics)
        
        best_expression = max(scores.items(), key=lambda x: x[1])
        expression, confidence = best_expression
        
        self.expression_history.append((expression, confidence))
        if len(self.expression_history) > self.history_length:
            self.expression_history.pop(0)
        
        smoothed_expression, smoothed_confidence = self._smooth_expression()
        
        latency = (time.time() - start_time) * 1000
        
        return smoothed_expression, min(smoothed_confidence, 1.0)

    def _calculate_expression_scores(self, metrics: Dict) -> Dict[str, float]:
        scores = {}
        
        mouth_open = metrics.get('mouth_open_ratio', 0.15)
        eye_vis = metrics.get('eye_visibility', 0.08)
        brightness = metrics.get('brightness', 128)
        saturation = metrics.get('saturation_mean', 80)
        sharpness = metrics.get('sharpness', 100)
        mouth_activity = metrics.get('mouth_activity', 30)
        
        if mouth_open > 0.25 and brightness > 140 and saturation > 90:
            smile_score = min(0.85, 0.4 + mouth_open * 0.8 + (saturation - 70) / 100)
            scores['happy'] = smile_score
        else:
            scores['happy'] = 0.15
        
        if brightness < 110 and mouth_open < 0.12 and eye_vis < 0.06:
            sad_score = min(0.75, 0.3 + (130 - brightness) / 100)
            scores['sad'] = sad_score
        else:
            scores['sad'] = 0.12
        
        if sharpness > 200 and eye_vis > 0.1 and brightness > 120:
            angry_score = min(0.7, 0.35 + sharpness / 500)
            scores['angry'] = angry_score
        else:
            scores['angry'] = 0.1
        
        if eye_vis > 0.12 and mouth_open > 0.2 and brightness > 150:
            surprised_score = min(0.88, 0.4 + eye_vis * 2 + mouth_open)
            scores['surprised'] = surprised_score
        else:
            scores['surprised'] = 0.18
        
        if 0.08 < eye_vis < 0.11 and 0.12 < mouth_open < 0.22 and 115 < brightness < 135:
            confused_score = min(0.72, 0.35 + mouth_activity / 100)
            scores['confused'] = confused_score
        else:
            scores['confused'] = 0.18
        
        if eye_vis < 0.05 and brightness < 105 and mouth_open < 0.1:
            bored_score = min(0.78, 0.4 - eye_vis * 2)
            scores['bored'] = bored_score
        else:
            scores['bored'] = 0.15
        
        if 0.07 < eye_vis < 0.10 and mouth_open < 0.15 and 120 < brightness < 145:
            focused_score = min(0.82, 0.45 + sharpness / 400)
            scores['focused'] = focused_score
        else:
            scores['focused'] = 0.28
        
        max_score = max(scores.values()) if scores else 0
        scores['neutral'] = max(0.15, 0.55 - max_score)
        
        return scores

    def _smooth_expression(self) -> Tuple[str, float]:
        if not self.expression_history:
            return 'neutral', 0.5
        
        expression_counts = {}
        confidence_sum = {}
        
        for expr, conf in self.expression_history:
            if expr not in expression_counts:
                expression_counts[expr] = 0
                confidence_sum[expr] = 0
            expression_counts[expr] += 1
            confidence_sum[expr] += conf
        
        best_expr = max(expression_counts.items(), key=lambda x: x[1])[0]
        avg_conf = confidence_sum[best_expr] / expression_counts[best_expr]
        
        return best_expr, avg_conf

    def draw_result(self, image: np.ndarray, expression: str, confidence: float) -> np.ndarray:
        result_img = image.copy()
        
        face_region, face_info = self.detect_face(image)
        
        if face_info:
            x, y, w, h = face_info['x'], face_info['y'], face_info['width'], face_info['height']
            cv2.rectangle(result_img, (x, y), (x+w, y+h), (0, 255, 0), 2)
        
        label = f"{expression}: {confidence:.2f}"
        cv2.putText(result_img, label, (10, 30), 
                   cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
        
        return result_img
