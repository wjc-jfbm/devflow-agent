package com.devflow.core;

import com.devflow.core.agent.*;
import com.devflow.core.orchestration.SupervisorAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public RequirementsAgent requirementsAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(RequirementsAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public ArchitectAgent architectAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(ArchitectAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public CoderAgent coderAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(CoderAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public TesterAgent testerAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(TesterAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public ReviewerAgent reviewerAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(ReviewerAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(ChatLanguageModel chatModel) {
        return new SupervisorAgent(chatModel);
    }
}
