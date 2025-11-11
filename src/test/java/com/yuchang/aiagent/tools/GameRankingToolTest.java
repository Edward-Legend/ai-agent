package com.yuchang.aiagent.tools;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class GameRankingToolTest {

    @Test
    void fetch3DMGameRanking() {
        try {
            String url = "https://www.3dmgame.com/jqremphb.html";

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            List<Map<String, String>> gameList = new ArrayList<>();
            Elements gameItems = doc.select("div.phlist");

            for (Element item : gameItems) {
                Map<String, String> game = new HashMap<>();

                // 游戏名称
                Element nameElement = item.selectFirst("div a");
                if (nameElement != null) {
                    game.put("name", nameElement.text());
                }

                // 游戏信息列表
                Elements infoList = item.select("li");
                for (int i = 0; i < infoList.size(); i++) {
                    Element li = infoList.get(i);
                    String text = li.text();
                    if (i == 0 && text.startsWith("开发：")) {
                        game.put("developer", text.substring(3));
                    } else if (i == 1 && text.startsWith("发行：")) {
                        game.put("publisher", text.substring(3));
                    } else if (i == 2 && text.startsWith("发售：")) {
                        game.put("releaseDate", text.substring(3));
                    } else if (i == 3 && text.startsWith("类型：")) {
                        game.put("type", text.substring(3));
                    } else if (i == 4 && text.startsWith("平台：")) {
                        game.put("platform", text.substring(3));
                    }
                }

                if (!game.isEmpty()) {
                    gameList.add(game);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "3DM游戏网");
            result.put("url", url);
            result.put("count", gameList.size());
            result.put("games", gameList);

            System.out.println(JSONUtil.toJsonPrettyStr(result));
        } catch (Exception e) {
            log.error("爬取3DM游戏排行榜失败", e);
            System.out.println("错误：爬取3DM游戏排行榜失败 - " + e.getMessage());
        }

    }
}