package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.agent.GameRankingManus;
import com.yuchang.aiagent.app.GameAnalyzeApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

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

    @Resource
    private GameAnalyzeApp gameAnalyzeApp;

    /**
     * 流式调用游戏排行智能分析Agent
     *
     * @param message 用户消息
     * @return SSE流式响应
     */
    @GetMapping("/chat")
    public SseEmitter doChatWithGameRankingAgent(String message) {
        GameRankingManus gameRankingManus = new GameRankingManus(allTools, dashscopeChatModel);
        return gameRankingManus.runStream(message);
    }

    /**
     * SSE 流式调用 AI 应用
     *
     * @param message
     * @param chatId
     * @return
     */
//    @GetMapping(value = "/game/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithTools(String message, String chatId) {
        return gameAnalyzeApp.doChatWithTools(message, chatId);
    }

    /**
     * SSE 流式调用 AI 应用
     *
     * @param message
     * @param chatId
     * @return
     */
//    @GetMapping(value = "/love_app/chat/server_sent_event")
    @GetMapping(value = "/game/chat/sse")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return gameAnalyzeApp.doChatWithTools(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}

