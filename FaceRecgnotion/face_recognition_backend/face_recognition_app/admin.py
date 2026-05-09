from django.contrib import admin
from .models import LearningSession, ExpressionRecord, LearningEvaluation, KnowledgeTrigger


@admin.register(LearningSession)
class LearningSessionAdmin(admin.ModelAdmin):
    list_display = ['session_id', 'start_time', 'end_time', 'is_active']
    list_filter = ['is_active', 'start_time']
    search_fields = ['session_id']
    readonly_fields = ['session_id', 'start_time']


@admin.register(ExpressionRecord)
class ExpressionRecordAdmin(admin.ModelAdmin):
    list_display = ['session', 'expression', 'confidence', 'timestamp']
    list_filter = ['expression', 'timestamp']
    search_fields = ['session__session_id']
    readonly_fields = ['timestamp']


@admin.register(LearningEvaluation)
class LearningEvaluationAdmin(admin.ModelAdmin):
    list_display = ['session', 'evaluation_type', 'score', 'timestamp']
    list_filter = ['evaluation_type', 'timestamp']
    search_fields = ['session__session_id']


@admin.register(KnowledgeTrigger)
class KnowledgeTriggerAdmin(admin.ModelAdmin):
    list_display = ['session', 'trigger_expression', 'suggested_topic', 'status', 'created_at']
    list_filter = ['status', 'created_at', 'trigger_expression']
    search_fields = ['session__session_id', 'suggested_topic']
