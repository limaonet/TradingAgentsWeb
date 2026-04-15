package com.tradingagents.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * LangChain4j 配置类
 * 支持 OpenRouter 聚合 API
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${llm.provider:openrouter}")
    private String provider;

    // OpenRouter 配置
    @Value("${llm.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${llm.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openrouterBaseUrl;

    @Value("${llm.openrouter.chat-model:openai/gpt-4o}")
    private String openrouterChatModel;

    @Value("${llm.openrouter.quick-model:openai/gpt-3.5-turbo}")
    private String openrouterQuickModel;

    /**
     * 深度思考模型（用于研究经理、组合经理等复杂决策）
     */
    @Bean
    @Primary
    public ChatLanguageModel deepThinkingModel() {
        log.info("【LLM】正在初始化深度思考模型，provider={}", provider);
        
        return OpenAiChatModel.builder()
                .apiKey(openrouterApiKey)
                .modelName(openrouterChatModel)
                .baseUrl(openrouterBaseUrl)
                .timeout(Duration.ofSeconds(300))
                .customHeaders(Map.of(
                        "HTTP-Referer", "https://tradingagents.local",
                        "X-Title", "TradingAgents"
                ))
                .build();
    }

    /**
     * 快速思考模型（用于分析师等快速响应场景）
     */
    @Bean
    public ChatLanguageModel quickThinkingModel() {
        log.info("【LLM】正在初始化快速模型，provider={}", provider);
        
        return OpenAiChatModel.builder()
                .apiKey(openrouterApiKey)
                .modelName(openrouterQuickModel)
                .baseUrl(openrouterBaseUrl)
                .timeout(Duration.ofSeconds(60))
                .customHeaders(Map.of(
                        "HTTP-Referer", "https://tradingagents.local",
                        "X-Title", "TradingAgents"
                ))
                .build();
    }
}
