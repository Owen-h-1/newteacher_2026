import cv2
import numpy as np
import base64
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
from django.utils import timezone
import json
import uuid
from .models import LearningSession, ExpressionRecord, LearningEvaluation, KnowledgeTrigger
from .expression_engine import ExpressionRecognizer
from .learning_state_mapper import LearningStateMapper

expression_recognizer = ExpressionRecognizer()
session_mappers = {}


@csrf_exempt
@require_http_methods(["POST"])
def start_session(request):
    try:
        session_id = str(uuid.uuid4())
        session = LearningSession.objects.create(
            session_id=session_id,
            start_time=timezone.now()
        )
        session_mappers[session_id] = LearningStateMapper()
        
        return JsonResponse({
            'success': True,
            'session_id': session_id,
            'message': '会话创建成功'
        })
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["POST"])
def end_session(request):
    try:
        data = json.loads(request.body)
        session_id = data.get('session_id')
        
        if not session_id:
            return JsonResponse({
                'success': False,
                'message': '缺少session_id'
            }, status=400)
        
        session = LearningSession.objects.get(session_id=session_id)
        session.end_time = timezone.now()
        session.is_active = False
        session.save()
        
        if session_id in session_mappers:
            del session_mappers[session_id]
        
        return JsonResponse({
            'success': True,
            'message': '会话已结束'
        })
    except LearningSession.DoesNotExist:
        return JsonResponse({
            'success': False,
            'message': '会话不存在'
        }, status=404)
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["POST"])
def analyze_frame(request):
    try:
        data = json.loads(request.body)
        session_id = data.get('session_id')
        image_data = data.get('image')
        
        if not session_id or not image_data:
            return JsonResponse({
                'success': False,
                'message': '缺少session_id或image数据'
            }, status=400)
        
        try:
            session = LearningSession.objects.get(session_id=session_id)
        except LearningSession.DoesNotExist:
            return JsonResponse({
                'success': False,
                'message': '会话不存在'
            }, status=404)
        
        if session_id not in session_mappers:
            session_mappers[session_id] = LearningStateMapper()
        
        mapper = session_mappers[session_id]
        
        img_data = base64.b64decode(image_data.split(',')[1])
        nparr = np.frombuffer(img_data, np.uint8)
        frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        expression, confidence = expression_recognizer.recognize_expression(frame)
        
        mapper.update_expression(expression, confidence)
        
        ExpressionRecord.objects.create(
            session=session,
            expression=expression,
            confidence=confidence
        )
        
        state_data = mapper.get_current_state()
        intervention = mapper.should_trigger_intervention()
        engagement_score = mapper.calculate_engagement_score()
        
        result = {
            'success': True,
            'expression': expression,
            'confidence': confidence,
            'learning_state': state_data,
            'engagement_score': engagement_score,
            'intervention': intervention
        }
        
        if intervention:
            KnowledgeTrigger.objects.create(
                session=session,
                trigger_expression=expression,
                suggested_topic=intervention['suggestions'][0] if intervention['suggestions'] else '',
                status='pending'
            )
        
        return JsonResponse(result)
        
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["GET"])
def get_session_status(request, session_id):
    try:
        session = LearningSession.objects.get(session_id=session_id)
        
        recent_expressions = ExpressionRecord.objects.filter(session=session)[:20]
        expressions_data = [{
            'expression': expr.expression,
            'confidence': expr.confidence,
            'timestamp': expr.timestamp.isoformat()
        } for expr in recent_expressions]
        
        if session_id in session_mappers:
            mapper = session_mappers[session_id]
            state_data = mapper.get_current_state()
            engagement_score = mapper.calculate_engagement_score()
        else:
            state_data = {'state': 'neutral', 'confidence': 0.5, 'description': '状态稳定'}
            engagement_score = 0.5
        
        pending_triggers = KnowledgeTrigger.objects.filter(
            session=session,
            status='pending'
        )
        triggers_data = [{
            'id': trigger.id,
            'trigger_expression': trigger.trigger_expression,
            'suggested_topic': trigger.suggested_topic,
            'created_at': trigger.created_at.isoformat()
        } for trigger in pending_triggers]
        
        return JsonResponse({
            'success': True,
            'session_id': session_id,
            'is_active': session.is_active,
            'start_time': session.start_time.isoformat(),
            'learning_state': state_data,
            'engagement_score': engagement_score,
            'recent_expressions': expressions_data,
            'pending_triggers': triggers_data
        })
        
    except LearningSession.DoesNotExist:
        return JsonResponse({
            'success': False,
            'message': '会话不存在'
        }, status=404)
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["POST"])
def trigger_knowledge(request):
    try:
        data = json.loads(request.body)
        trigger_id = data.get('trigger_id')
        action = data.get('action', 'trigger')
        
        trigger = KnowledgeTrigger.objects.get(id=trigger_id)
        
        if action == 'trigger':
            trigger.status = 'triggered'
            trigger.triggered_at = timezone.now()
        else:
            trigger.status = 'dismissed'
        
        trigger.save()
        
        return JsonResponse({
            'success': True,
            'message': f'触发已{action}',
            'trigger': {
                'id': trigger.id,
                'status': trigger.status,
                'suggested_topic': trigger.suggested_topic
            }
        })
        
    except KnowledgeTrigger.DoesNotExist:
        return JsonResponse({
            'success': False,
            'message': '触发不存在'
        }, status=404)
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)


@csrf_exempt
@require_http_methods(["GET"])
def get_evaluation(request, session_id):
    try:
        session = LearningSession.objects.get(session_id=session_id)
        
        all_expressions = ExpressionRecord.objects.filter(session=session)
        
        expression_counts = {}
        for expr in all_expressions:
            if expr.expression not in expression_counts:
                expression_counts[expr.expression] = 0
            expression_counts[expr.expression] += 1
        
        avg_engagement = 0.5
        if session_id in session_mappers:
            avg_engagement = session_mappers[session_id].calculate_engagement_score()
        
        total_duration = 0
        if session.end_time:
            total_duration = (session.end_time - session.start_time).total_seconds()
        
        return JsonResponse({
            'success': True,
            'session_id': session_id,
            'total_duration': total_duration,
            'expression_distribution': expression_counts,
            'avg_engagement': avg_engagement,
            'total_records': all_expressions.count()
        })
        
    except LearningSession.DoesNotExist:
        return JsonResponse({
            'success': False,
            'message': '会话不存在'
        }, status=404)
    except Exception as e:
        return JsonResponse({
            'success': False,
            'message': str(e)
        }, status=500)
