package com.yuchang.aiagent.agent;

import com.yuchang.aiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 游戏排行智能分析AI Agent
 * 专门用于分析游戏行业的各种榜单、流量、数据，并提供比较和分析功能
 */
@Component
public class GameRankingAgent extends ToolCallAgent {

    public GameRankingAgent(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("GameRankingAgent");
        String SYSTEM_PROMPT = """
                你是一个专业的游戏行业数据分析专家，专门帮助用户了解游戏行业的变化和趋势。
                你可以：
                1. 自动检索游戏行业的各种榜单（如3DM游戏排行榜、Steam热门游戏等）
                2. 分析游戏的流量、热度、玩家数据
                3. 比较不同游戏的表现和趋势
                4. 提供游戏行业洞察和建议
                
                请使用提供的工具来获取最新的游戏数据，然后进行深入分析和比较。
                在分析时，要关注：
                - 游戏的热度和趋势变化
                - 不同平台的表现差异
                - 游戏类型和题材的流行度
                - 开发商和发行商的表现
                - 市场机会和潜在趋势
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户的需求，主动选择合适的工具来获取游戏数据。
                对于复杂任务，可以分步骤进行：
                1. 首先获取相关的游戏排行榜数据
                2. 然后对数据进行整理和分析
                3. 如果用户需要比较，可以获取多个数据源进行对比
                4. 最后提供专业的分析和建议
                
                在使用工具后，要清楚地解释执行结果，并建议下一步行动。
                如果任务完成，可以使用 `terminate` 工具来结束交互。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}

