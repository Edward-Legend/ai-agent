package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.app.GameAnalyzeApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GameRankingControllerTest {

    @Resource
    private ChatModel dashscopeChatModel;

    @Autowired
    private GameAnalyzeApp gameAnalyzeApp;


    @Test
    void getAllTools() {}

    @Test
    void doChatWithTools() {
        Flux<String> s = gameAnalyzeApp.doChatWithTools("现在收入最高的游戏是什么", "1212");
        System.out.println(s.blockLast());
        Assertions.assertNotNull(s.blockLast());
    }
}