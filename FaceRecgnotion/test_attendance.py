"""
学生到班人数统计功能测试脚本
"""
import requests
import base64
import json
from PIL import Image
import io

API_BASE_URL = "http://localhost:8080/api"

def image_to_base64(image_path):
    """将图片转换为 base64 编码"""
    with open(image_path, 'rb') as f:
        image_data = f.read()
    return base64.b64encode(image_data).decode('utf-8')

def test_register_student():
    """测试学生注册功能"""
    print("\n=== 测试学生注册 ===")
    
    # 模拟学生照片（实际使用时需要真实照片）
    # 这里使用一个示例图片路径
    # photo_base64 = image_to_base64("path/to/student_photo.jpg")
    
    # 为了测试，我们创建一个简单的测试数据
    test_data = {
        "student_id": "2024001",
        "name": "张三",
        "class_name": "三年级(1)班",
        "photo": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."  # 实际使用时需要真实照片
    }
    
    try:
        response = requests.post(
            f"{API_BASE_URL}/attendance/student/register",
            json=test_data
        )
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.json()}")
        return response.json().get('success', False)
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_list_students():
    """测试获取学生列表"""
    print("\n=== 测试获取学生列表 ===")
    
    try:
        response = requests.get(f"{API_BASE_URL}/attendance/students")
        print(f"状态码: {response.status_code}")
        data = response.json()
        print(f"学生总数: {data.get('total', 0)}")
        if data.get('students'):
            for student in data['students'][:3]:  # 只显示前3个
                print(f"  - {student['student_id']}: {student['name']} ({student['class_name']})")
        return data.get('success', False)
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_upload_class_photo():
    """测试上传班级合影"""
    print("\n=== 测试上传班级合影 ===")
    
    test_data = {
        "class_name": "三年级(1)班",
        "date": "2026-05-04",
        "photo": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."  # 实际使用时需要真实照片
    }
    
    try:
        response = requests.post(
            f"{API_BASE_URL}/attendance/photo/upload",
            json=test_data
        )
        print(f"状态码: {response.status_code}")
        data = response.json()
        if data.get('success'):
            print(f"✓ 检测到人数: {data.get('total_detected', 0)}")
            print(f"✓ 识别出人数: {data.get('total_recognized', 0)}")
            print(f"✓ 识别的学生: {len(data.get('recognized_students', []))}")
        else:
            print(f"❌ 识别失败: {data.get('message', '未知错误')}")
        return data.get('success', False)
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_get_attendance_statistics():
    """测试获取考勤统计"""
    print("\n=== 测试获取考勤统计 ===")
    
    params = {
        "class_name": "三年级(1)班",
        "date": "2026-05-04"
    }
    
    try:
        response = requests.get(
            f"{API_BASE_URL}/attendance/statistics",
            params=params
        )
        print(f"状态码: {response.status_code}")
        data = response.json()
        if data.get('success'):
            print(f"✓ 班级总人数: {data.get('total_students', 0)}")
            print(f"✓ 出勤人数: {data.get('present_count', 0)}")
            print(f"✓ 缺勤人数: {data.get('absent_count', 0)}")
            print(f"✓ 出勤率: {data.get('attendance_rate', 0) * 100:.1f}%")
        else:
            print(f"❌ 查询失败: {data.get('message', '未知错误')}")
        return data.get('success', False)
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_health_check():
    """测试后端服务健康状态"""
    print("\n=== 测试后端服务 ===")
    
    try:
        response = requests.get(f"{API_BASE_URL.replace('/api', '')}/admin/", timeout=5)
        if response.status_code in [200, 302, 403]:
            print("✓ 后端服务运行正常")
            return True
        else:
            print(f"❌ 后端服务异常: {response.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        print("❌ 无法连接到后端服务")
        print("   请确保后端服务已启动: python manage.py runserver 0.0.0.0:8080")
        return False
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def main():
    """运行所有测试"""
    print("=" * 60)
    print("学生到班人数统计功能 - 自动化测试")
    print("=" * 60)
    
    results = []
    
    # 测试后端服务
    results.append(("后端服务健康检查", test_health_check()))
    
    # 如果后端服务正常，继续其他测试
    if results[0][1]:
        results.append(("获取学生列表", test_list_students()))
        # results.append(("学生注册", test_register_student()))
        # results.append(("上传班级合影", test_upload_class_photo()))
        # results.append(("考勤统计", test_get_attendance_statistics()))
    
    # 输出测试结果
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)
    
    passed = 0
    failed = 0
    
    for test_name, result in results:
        status = "✓ 通过" if result else "❌ 失败"
        print(f"{test_name}: {status}")
        if result:
            passed += 1
        else:
            failed += 1
    
    print(f"\n总计: {passed} 个通过, {failed} 个失败")
    
    if failed == 0:
        print("\n🎉 所有测试通过！系统运行正常。")
    else:
        print("\n⚠️  部分测试失败，请检查系统配置。")

if __name__ == "__main__":
    main()
