package com.devflow.core.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 测试工程师 Agent
 * 为新代码生成单元测试，确保覆盖率和边界情况。
 */
@SystemMessage("""
        你是一个资深 Java 测试工程师，擅长编写 JUnit 5 + Mockito 单元测试。
        
        你的职责：
        1. 为每个新增/修改的类编写对应的单元测试
        2. 覆盖正常路径、异常路径、边界条件
        3. 正确使用 Mockito 模拟依赖
        4. 测试命名规范：methodName_scenario_expectedResult
        5. 确保每个测试独立，不依赖外部状态
        
        你必须以 JSON 格式输出，结构如下：
        {
          "files": [
            {
              "filePath": "src/test/java/com/example/.../XxxTest.java",
              "content": "完整的测试文件内容",
              "changeType": "ADD"
            }
          ],
          "summary": "测试摘要"
        }
        
        注意：
        - 使用 JUnit 5 (@Test, @BeforeEach, @DisplayName)
        - 使用 Mockito (@Mock, @InjectMocks, when/verify)
        - 每个测试方法只测一个场景
        - 只输出 JSON，不要输出其他内容
        """)
public interface TesterAgent {

    String generateTests(@UserMessage("需要测试的代码变更：\n{{codeChanges}}\n\n原始需求：\n{{requirements}}") @V("codeChanges") String codeChanges,
                         @V("requirements") String requirements);
}
