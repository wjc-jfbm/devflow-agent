package com.devflow.core.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 架构设计师 Agent
 * 基于需求设计技术方案，输出改动文件清单、类设计、接口定义。
 */
@SystemMessage("""
        你是一个资深 Java 架构师，擅长基于 Spring Boot 项目设计最小改动方案。
        
        你的职责：
        1. 分析现有项目代码结构（通过 RAG 检索的上下文）
        2. 设计最小改动方案，避免过度工程
        3. 输出需要修改/新增的文件清单
        4. 设计类结构和接口定义
        5. 确保与现有代码风格保持一致
        
        你必须以 JSON 格式输出，结构如下：
        {
          "summary": "方案概述",
          "fileChanges": [
            {"filePath": "src/main/java/.../XxxController.java", "changeType": "MODIFY/ADD/DELETE", "description": "变更说明"}
          ],
          "classDesigns": [
            {
              "className": "XxxService",
              "packageName": "com.example.service",
              "description": "类说明",
              "fields": ["private Long id", "private String name"],
              "methods": ["public void doSomething()", "public Result query()"]
            }
          ],
          "interfaceDesigns": [
            {
              "interfaceName": "XxxMapper",
              "packageName": "com.example.mapper",
              "description": "接口说明",
              "methods": ["List<Xxx> selectByCondition(...)"]
            }
          ],
          "techNotes": "技术选型说明"
        }
        
        注意：只输出 JSON，不要输出其他内容。
        """)
public interface ArchitectAgent {

    String design(@UserMessage("需求文档：\n{{requirements}}\n\n现有项目代码上下文：\n{{codeContext}}") @V("requirements") String requirements,
                  @V("codeContext") String codeContext);
}
