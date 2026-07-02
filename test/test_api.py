"""
DevFlow Agent API 自动化测试脚本
纯标准库实现，无需 pip install
用法: python test/test_api.py [--url URL] [--skip-health] [--cleanup]
"""
import json
import time
import argparse
import sys
import base64
import urllib.request
import urllib.error
import ssl

BASE_URL = "http://localhost:8080"
API_USERNAME = "admin"
API_PASSWORD = "devflow2024"

POLL_INTERVAL_SEC = 2
POLL_MAX_WAIT_SEC = 60
REQUEST_TIMEOUT_SEC = 10


class DevFlowAPITester:
    def __init__(self, base_url=BASE_URL):
        self.base_url = base_url
        self.auth_header = self._build_auth_header()
        self.timeout = REQUEST_TIMEOUT_SEC
        self.project_id = None
        self.task_id = None
        self.created_ids = []

    def _build_auth_header(self):
        credentials = f"{API_USERNAME}:{API_PASSWORD}"
        encoded = base64.b64encode(credentials.encode()).decode()
        return f"Basic {encoded}"

    def _request(self, method, path, body=None):
        """统一 HTTP 请求，带超时和 Basic Auth"""
        url = f"{self.base_url}{path}"
        data = json.dumps(body).encode("utf-8") if body else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        req.add_header("Authorization", self.auth_header)
        # 忽略自签名证书（本地开发）
        ctx = ssl.create_default_context()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=ctx) as resp:
                return resp.status, resp.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8")
        except Exception as e:
            return 0, str(e)

    def _get(self, path):
        return self._request("GET", path)

    def _post(self, path, body):
        return self._request("POST", path, body)

    def _delete(self, path):
        return self._request("DELETE", path)

    def print_result(self, test_name, success, message=""):
        status = "PASS" if success else "FAIL"
        print(f"[{status}] {test_name}")
        if message:
            print(f"     {message}")
        return success

    def poll_task_status(self, expected_status, timeout_sec=POLL_MAX_WAIT_SEC):
        elapsed = 0
        while elapsed < timeout_sec:
            try:
                code, body = self._get(f"/api/tasks/{self.task_id}")
                if code == 200:
                    current = json.loads(body)["data"]["status"]
                    if current == expected_status:
                        return True, current
                else:
                    return False, f"HTTP {code}"
            except Exception as e:
                return False, str(e)
            time.sleep(POLL_INTERVAL_SEC)
            elapsed += POLL_INTERVAL_SEC
        try:
            code, body = self._get(f"/api/tasks/{self.task_id}")
            if code == 200:
                current = json.loads(body)["data"]["status"]
                return False, f"超时: 当前状态={current}, 期望={expected_status}"
        except Exception:
            pass
        return False, f"超时: 等待{timeout_sec}秒后仍不是{expected_status}"

    # --- Test Cases ---

    def test_health_check(self):
        try:
            code, _ = self._get("/actuator/health")
            if code == 200:
                return self.print_result("健康检查", True)
            else:
                return self.print_result("健康检查", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("健康检查", False, f"服务未启动: {str(e)}")

    def test_create_project(self):
        payload = {
            "name": "自动化测试项目",
            "repoUrl": "https://github.com/devflow/test-repo",
            "language": "Java",
            "framework": "Spring Boot",
            "description": "自动化测试项目（可安全删除）"
        }
        try:
            code, body = self._post("/api/projects", payload)
            if code == 200:
                data = json.loads(body)
                self.project_id = data["data"]["id"]
                self.created_ids.append(("project", self.project_id))
                return self.print_result("创建项目", True, f"项目ID: {self.project_id}")
            else:
                return self.print_result("创建项目", False, f"状态码: {code}, 响应: {body[:200]}")
        except Exception as e:
            return self.print_result("创建项目", False, f"异常: {str(e)}")

    def test_get_project(self):
        if not self.project_id:
            return self.print_result("获取项目详情", False, "前置条件失败: 项目ID为空")
        try:
            code, body = self._get(f"/api/projects/{self.project_id}")
            if code == 200:
                data = json.loads(body)
                return self.print_result("获取项目详情", True, f"项目名称: {data['data']['name']}")
            else:
                return self.print_result("获取项目详情", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("获取项目详情", False, f"异常: {str(e)}")

    def test_list_projects(self):
        try:
            code, body = self._get("/api/projects")
            if code == 200:
                data = json.loads(body)
                projects = data.get("data", [])
                count = len(projects) if isinstance(projects, list) else 0
                return self.print_result("获取项目列表", True, f"项目数量: {count}")
            else:
                return self.print_result("获取项目列表", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("获取项目列表", False, f"异常: {str(e)}")

    def test_create_task_validation(self):
        payload = {"projectId": None, "issueNumber": None, "issueTitle": ""}
        try:
            code, body = self._post("/api/tasks", payload)
            if code == 400:
                return self.print_result("任务创建参数校验", True, "正确返回400")
            else:
                return self.print_result("任务创建参数校验", False, f"期望400，实际: {code}")
        except Exception as e:
            return self.print_result("任务创建参数校验", False, f"异常: {str(e)}")

    def test_create_task(self):
        if not self.project_id:
            return self.print_result("创建任务", False, "前置条件失败: 项目ID为空")
        payload = {
            "projectId": self.project_id,
            "issueNumber": 1,
            "issueTitle": "测试功能：用户登录",
            "issueBody": "实现登录接口和JWT认证"
        }
        try:
            code, body = self._post("/api/tasks", payload)
            if code == 200:
                data = json.loads(body)
                self.task_id = data["data"]["id"]
                self.created_ids.append(("task", self.task_id))
                return self.print_result("创建任务", True, f"任务ID: {self.task_id}")
            else:
                return self.print_result("创建任务", False, f"状态码: {code}, 响应: {body[:200]}")
        except Exception as e:
            return self.print_result("创建任务", False, f"异常: {str(e)}")

    def test_get_task(self):
        if not self.task_id:
            return self.print_result("获取任务详情", False, "前置条件失败: 任务ID为空")
        try:
            code, body = self._get(f"/api/tasks/{self.task_id}")
            if code == 200:
                data = json.loads(body)
                return self.print_result("获取任务详情", True, f"状态: {data['data']['status']}")
            else:
                return self.print_result("获取任务详情", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("获取任务详情", False, f"异常: {str(e)}")

    def test_get_task_progress(self):
        if not self.task_id:
            return self.print_result("获取任务进度", False, "前置条件失败: 任务ID为空")
        try:
            code, body = self._get(f"/api/tasks/{self.task_id}/progress")
            if code == 200:
                data = json.loads(body)
                return self.print_result("获取任务进度", True, f"阶段: {data['data']['currentPhase']}")
            else:
                return self.print_result("获取任务进度", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("获取任务进度", False, f"异常: {str(e)}")

    def test_list_tasks(self):
        try:
            code, body = self._get("/api/tasks")
            if code == 200:
                data = json.loads(body)
                tasks = data.get("data", [])
                count = len(tasks) if isinstance(tasks, list) else 0
                return self.print_result("获取任务列表", True, f"任务数量: {count}")
            else:
                return self.print_result("获取任务列表", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("获取任务列表", False, f"异常: {str(e)}")

    def test_approve_task(self):
        if not self.task_id:
            return self.print_result("审批任务", False, "前置条件失败: 任务ID为空")

        print(f"     等待任务进入PAUSED状态（最多{POLL_MAX_WAIT_SEC}秒）...")
        ok, detail = self.poll_task_status("PAUSED", timeout_sec=POLL_MAX_WAIT_SEC)
        if not ok:
            return self.print_result("审批任务", False,
                                     f"任务未进入PAUSED状态: {detail}（工作流可能未运行或已完成）")

        payload = {"approver": "test_user", "comment": "架构方案通过", "action": "APPROVED"}
        try:
            code, body = self._post(f"/api/tasks/{self.task_id}/approve", payload)
            if code == 200:
                return self.print_result("审批任务", True, "审批成功")
            else:
                return self.print_result("审批任务", False, f"状态码: {code}, 响应: {body[:200]}")
        except Exception as e:
            return self.print_result("审批任务", False, f"异常: {str(e)}")

    def test_approve_validation(self):
        if not self.task_id:
            return self.print_result("审批参数校验", False, "前置条件失败: 任务ID为空")
        payload = {"approver": "test_user", "comment": "", "action": ""}
        try:
            code, _ = self._post(f"/api/tasks/{self.task_id}/approve", payload)
            if code == 400:
                return self.print_result("审批参数校验", True, "空action正确返回400")
            else:
                return self.print_result("审批参数校验", False, f"期望400，实际: {code}")
        except Exception as e:
            return self.print_result("审批参数校验", False, f"异常: {str(e)}")

    def test_update_project(self):
        """验证 githubToken 更新不被丢失（修复 #2）"""
        if not self.project_id:
            return self.print_result("更新项目(含Token)", False, "前置条件失败: 项目ID为空")
        payload = {
            "name": "自动化测试项目(已更新)",
            "repoUrl": "https://github.com/devflow/test-repo",
            "language": "Java",
            "framework": "Spring Boot",
            "description": "更新后的描述",
            "githubToken": "ghp_updated_test_token"
        }
        try:
            code, body = self._post(f"/api/projects/{self.project_id}", payload)
            if code == 200:
                # 再 GET 验证 githubToken 是否正确更新
                _, get_body = self._get(f"/api/projects/{self.project_id}")
                data = json.loads(get_body)["data"]
                updated_token = data.get("githubToken", "")
                if updated_token == "ghp_updated_test_token":
                    return self.print_result("更新项目(含Token)", True, "githubToken正确更新")
                else:
                    return self.print_result("更新项目(含Token)", False,
                                             f"githubToken未更新: 期望=ghp_updated_test_token, 实际={updated_token}")
            else:
                return self.print_result("更新项目(含Token)", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("更新项目(含Token)", False, f"异常: {str(e)}")

    def test_get_dashboard_stats(self):
        try:
            code, body = self._get("/api/dashboard/stats")
            if code == 200:
                data = json.loads(body)["data"]
                return self.print_result("仪表盘统计", True, f"总任务: {data.get('totalTasks', 0)}")
            else:
                return self.print_result("仪表盘统计", False, f"状态码: {code}")
        except Exception as e:
            return self.print_result("仪表盘统计", False, f"异常: {str(e)}")

    def test_get_nonexistent_project_404(self):
        """验证不存在的项目返回 404 而非 500（修复 #3）"""
        try:
            code, body = self._get("/api/projects/99999")
            data = json.loads(body)
            if code == 404 and data.get("code") == 404:
                return self.print_result("不存在项目返回404", True, f"code={data['code']}")
            else:
                return self.print_result("不存在项目返回404", False,
                                         f"HTTP {code}, body code={data.get('code')}")
        except Exception as e:
            return self.print_result("不存在项目返回404", False, f"异常: {str(e)}")

    def test_get_nonexistent_task_404(self):
        """验证不存在的任务返回 404 而非 500（修复 #3）"""
        try:
            code, body = self._get("/api/tasks/99999")
            data = json.loads(body)
            if code == 404 and data.get("code") == 404:
                return self.print_result("不存在任务返回404", True, f"code={data['code']}")
            else:
                return self.print_result("不存在任务返回404", False,
                                         f"HTTP {code}, body code={data.get('code')}")
        except Exception as e:
            return self.print_result("不存在任务返回404", False, f"异常: {str(e)}")

    def cleanup(self):
        if not self.created_ids:
            return
        print("\n" + "-" * 40)
        print("清理测试数据...")
        for resource_type, resource_id in reversed(self.created_ids):
            try:
                if resource_type == "task":
                    code, _ = self._delete(f"/api/tasks/{resource_id}")
                elif resource_type == "project":
                    code, _ = self._delete(f"/api/projects/{resource_id}")
                else:
                    continue
                status = "OK" if code == 200 else f"HTTP {code}"
                print(f"  [{status}] 删除{resource_type} {resource_id}")
            except Exception as e:
                print(f"  [ERROR] 删除{resource_type} {resource_id}: {e}")

    def run_all_tests(self, skip_health=False, cleanup_after=False):
        print("\n" + "=" * 60)
        print("DevFlow Agent API 自动化测试")
        print("=" * 60 + "\n")

        if not skip_health:
            if not self.test_health_check():
                print("\n[ABORT] 服务未启动，请先启动服务再运行测试！")
                sys.exit(1)

        results = []
        results.append(self.test_list_projects())
        results.append(self.test_create_task_validation())
        results.append(self.test_get_dashboard_stats())
        results.append(self.test_get_nonexistent_project_404())
        results.append(self.test_get_nonexistent_task_404())

        project_ok = self.test_create_project()
        results.append(project_ok)
        if project_ok:
            results.append(self.test_get_project())
            results.append(self.test_update_project())
            task_ok = self.test_create_task()
            results.append(task_ok)
        else:
            print("\n[SKIP] 项目创建失败，跳过任务相关测试")
            results.extend([False] * 8)

        if self.task_id:
            results.append(self.test_get_task())
            results.append(self.test_get_task_progress())
            results.append(self.test_list_tasks())
            results.append(self.test_approve_task())
            results.append(self.test_approve_validation())

        print("\n" + "=" * 60)
        passed = sum(results)
        total = len(results)
        print(f"测试结果: {passed}/{total} 通过")
        if passed == total:
            print("All tests passed!")
        else:
            print(f"{total - passed} tests failed")
        print("=" * 60)

        if cleanup_after:
            self.cleanup()

        return passed == total


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="DevFlow Agent API 自动化测试")
    parser.add_argument("--url", default=BASE_URL, help="API基础URL")
    parser.add_argument("--skip-health", action="store_true", help="跳过健康检查")
    parser.add_argument("--cleanup", action="store_true", help="测试完成后清理测试数据")
    parser.add_argument("--poll-timeout", type=int, default=POLL_MAX_WAIT_SEC,
                        help=f"轮询超时（秒），默认{POLL_MAX_WAIT_SEC}")
    args = parser.parse_args()

    POLL_MAX_WAIT_SEC = args.poll_timeout
    tester = DevFlowAPITester(args.url)
    success = tester.run_all_tests(args.skip_health, args.cleanup)
    sys.exit(0 if success else 1)
