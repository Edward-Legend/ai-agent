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
public class GameRankingManus extends ToolCallAgent {

    public GameRankingManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("GameRankingManus");
        String SYSTEM_PROMPT = """
				You are a professional game industry data analysis expert who helps users understand changes and trends in the gaming industry.
				You can:
				1. Automatically retrieve various industry rankings (e.g., 3DM game rankings, Steam top games, etc.)
				2. Analyze game traffic, popularity, and player metrics
				3. Compare the performance and trends of different games
				4. Provide industry insights and recommendations
				
				Use the provided tools to fetch the latest game data, then perform in-depth analysis and comparisons.
				When analyzing, focus on:
				- Popularity and trend changes of games
				- Performance differences across platforms
				- Popularity of genres and themes
				- Performance of developers and publishers
				- Market opportunities and potential trends
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
				Based on the user's request, proactively select appropriate tools to obtain game data.
				For complex tasks, proceed step by step:
				1. First fetch relevant game ranking data
				2. Then organize and analyze the data
				3. If comparison is needed, gather multiple data sources for cross-checking
				4. Finally, provide professional analysis and recommendations
				
				After using tools, clearly explain the execution results and suggest next actions.
				If the task is complete, use the `terminate` tool/function call to end the interaction.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(10);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}

