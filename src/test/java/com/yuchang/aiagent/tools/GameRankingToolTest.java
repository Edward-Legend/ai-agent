package com.yuchang.aiagent.tools;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
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


    /**
     * 爬取Steam热门游戏排行榜
     */
    @Test
    public void fetchSteamTopGames() {
        try {
            // Steam API或网页爬取
            String url = "https://store.steampowered.com/stats/";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            List<Map<String, String>> gameList = new ArrayList<>();
            Elements gameRows = doc.select("tr.player_count_row");

            int rank = 1;
            for (Element row : gameRows) {
                if (rank > 20) {
                    break; // 只取前20名
                }

                Map<String, String> game = new HashMap<>();
                game.put("rank", String.valueOf(rank));

                // 游戏名称
                Element nameElement = row.selectFirst("a.gameLink");
                if (nameElement != null) {
                    game.put("name", nameElement.text());
                }

                // 当前玩家数
                Elements playerCounts = row.select("span.currentServers");
                if (playerCounts.size() > 0) {
                    game.put("currentPlayers", playerCounts.get(0).text());
                }

                // 峰值玩家数
                Elements peakCounts = row.select("span.peakServers");
                if (peakCounts.size() > 0) {
                    game.put("peakPlayers", peakCounts.get(0).text());
                }

                if (!game.isEmpty()) {
                    gameList.add(game);
                }
                rank++;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "Steam");
            result.put("url", url);
            result.put("count", gameList.size());
            result.put("games", gameList);

            System.out.println(JSONUtil.toJsonPrettyStr(result));
        } catch (Exception e) {
            log.error("爬取Steam热门游戏失败", e);
            System.out.println("错误：爬取Steam热门游戏失败 - " + e.getMessage());
        }
    }

    @Test
    void fetchTapTapTopBoards() {
        try {
            Map<String, String> boardToUrl = new HashMap<>();
            boardToUrl.put("热门榜", "https://www.taptap.com/top/download");
            boardToUrl.put("新品榜", "https://www.taptap.com/top/new");
            boardToUrl.put("预约榜", "https://www.taptap.com/top/reserve");
            boardToUrl.put("热卖榜", "https://www.taptap.com/top/sell");
            boardToUrl.put("热玩榜", "https://www.taptap.com/top/played");

            List<Map<String, String>> gameList = new ArrayList<>();

            for (Map.Entry<String, String> entry : boardToUrl.entrySet()) {
                String boardName = entry.getKey();
                String url = entry.getValue();

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get();

                // 每一个游戏条目的中部信息容器
                Elements items = doc.select("div.top-card-middle");
                int rank = 1;
                for (Element item : items) {
                    Map<String, String> game = new HashMap<>();
                    game.put("board", boardName);
                    game.put("rank", String.valueOf(rank));

                    Element nameEl = item.selectFirst("a h4");
                    if (nameEl != null) {
                        game.put("name", nameEl.text());
                    }

                    Element developerEl = item.selectFirst("p a");
                    if (developerEl != null) {
                        game.put("developer", developerEl.text());
                    }

                    Element scoreEl = item.selectFirst("div.card-middle-score p");
                    if (scoreEl != null) {
                        game.put("score", scoreEl.text());
                    }

                    Elements typeEls = item.select("div.card-middle-category a");
                    if (typeEls != null && !typeEls.isEmpty()) {
                        List<String> types = new ArrayList<>();
                        for (Element t : typeEls) {
                            types.add(t.text());
                        }
                        game.put("type", String.join(",", types));
                    }

                    if (game.containsKey("name")) {
                        gameList.add(game);
                        rank++;
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "TapTap");
            result.put("urls", boardToUrl);
            result.put("count", gameList.size());
            result.put("games", gameList);

            System.out.println(JSONUtil.toJsonPrettyStr(result));
        } catch (Exception e) {
            log.error("爬取TapTap排行榜失败", e);

        }
    }
}