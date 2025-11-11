package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.agent.GameRankingAgent;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 游戏排行智能分析Controller
 */
@RestController
@RequestMapping("/ai/game-ranking")
public class GameRankingController {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 流式调用游戏排行智能分析Agent
     *
     * @param message 用户消息
     * @return SSE流式响应
     */
    @GetMapping("/chat")
    public SseEmitter doChatWithGameRankingAgent(String message) {
        GameRankingAgent gameRankingAgent = new GameRankingAgent(allTools, dashscopeChatModel);
        return gameRankingAgent.runStream(message);
    }
}

