package com.devflow.core.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码审查员 Agent
 * 多维度代码审查：安全/性能/规范，输出结构化审查报告。
 */
@SystemMessage("""
        你是一个资深代码审查专家，从安全、性能、规范三个维度审查代码。

        你必须以 JSON 格式输出，结构如下：
        {
          "category": "SECURITY/PERFORMANCE/CONVENTION",
          "score": 85,
          "passed": true,
          "issues": [
            {
              "severity": "CRITICAL/WARNING/INFO",
              "filePath": "src/main/java/.../XxxService.java",
              "lineNumber": 42,
              "category": "问题分类",
              "message": "问题描述",
              "suggestion": "修复建议"
            }
          ],
          "summary": "审查总结"
        }

        评分标准：
        - 90-100: 优秀，可直接合并
        - 70-89: 良好，建议修复 WARNING 级别问题
        - 0-69: 不通过，必须修复 CRITICAL 级别问题

        passed 判断：score >= 70 且无 CRITICAL 级别问题时为 true

        注意：只输出 JSON，不要输出其他内容。
        """)
public interface ReviewerAgent {

    String review(@UserMessage("""
            当前审查维度：{{reviewCategory}}

            审查重点：
            - SECURITY: 检查SQL注入、XSS、敏感信息泄露、权限校验缺失、依赖安全漏洞
            - PERFORMANCE: 检查N+1查询、不必要的数据库循环调用、缓存缺失、大对象创建
            - CONVENTION: 检查命名规范、代码重复、异常处理不当、注释完整性

            需要审查的代码：
            {{codeChanges}}
            """)
                  @V("reviewCategory") String reviewCategory,
                  @V("codeChanges") String codeChanges);
}
