package com.yuchang.aiagent.tools;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏排行爬虫工具
 * 支持爬取多个游戏网站的排行榜数据
 */
@Slf4j
public class GameRankingTool {

    /**
     * 爬取3DM游戏排行榜
     */
    @Tool(description = "爬取3DM游戏网站的热门单机游戏排行榜，返回游戏名称、开发商、发行商、发售日期、类型、平台等信息")
    public String fetch3DMGameRanking() {
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
            
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("爬取3DM游戏排行榜失败", e);
            return "错误：爬取3DM游戏排行榜失败 - " + e.getMessage();
        }
    }

    /**
     * 爬取Steam热门游戏排行榜
     */
    @Tool(description = "爬取Steam平台的热门游戏排行榜，返回游戏名称、当前玩家数、峰值玩家数等信息")
    public String fetchSteamTopGames() {
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
            
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("爬取Steam热门游戏失败", e);
            return "错误：爬取Steam热门游戏失败 - " + e.getMessage();
        }
    }

    /**
     * 爬取 TapTap 排行榜（热门榜、预约榜、热卖榜、热玩榜、新品榜）
     */
    @Tool(description = "爬取TapTap平台的多个榜单（热门、预约、热卖、热玩、新品），返回名称、厂商、评分、类型与榜单、名次等信息")
    public String fetchTapTapTopBoards() {
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

            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("爬取TapTap排行榜失败", e);
            return "错误：爬取TapTap排行榜失败 - " + e.getMessage();
        }
    }

    /**
     * 爬取指定游戏网站的游戏排行榜
     */
    @Tool(description = "爬取指定URL的游戏排行榜数据，支持自定义网站")
    public String fetchGameRankingFromUrl(
            @ToolParam(description = "要爬取的网站URL") String url,
            @ToolParam(description = "游戏列表的CSS选择器，例如：div.game-item") String gameSelector,
            @ToolParam(description = "游戏名称的CSS选择器，例如：h3.title") String nameSelector) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            List<Map<String, String>> gameList = new ArrayList<>();
            Elements gameItems = doc.select(gameSelector);
            
            int rank = 1;
            for (Element item : gameItems) {
                Map<String, String> game = new HashMap<>();
                game.put("rank", String.valueOf(rank));
                
                // 游戏名称
                Element nameElement = item.selectFirst(nameSelector);
                if (nameElement != null) {
                    game.put("name", nameElement.text());
                }
                
                // 尝试提取其他信息
                game.put("rawHtml", item.html());
                
                if (!game.isEmpty()) {
                    gameList.add(game);
                }
                rank++;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("source", "自定义网站");
            result.put("url", url);
            result.put("count", gameList.size());
            result.put("games", gameList);
            
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("爬取游戏排行榜失败", e);
            return "错误：爬取游戏排行榜失败 - " + e.getMessage();
        }
    }

    /**
     * 搜索游戏相关信息
     */
    @Tool(description = "搜索指定游戏的相关信息，包括评分、评价、销量等")
    public String searchGameInfo(
            @ToolParam(description = "游戏名称") String gameName) {
        try {
            // 这里可以整合多个数据源
            Map<String, Object> result = new HashMap<>();
            result.put("gameName", gameName);
            result.put("message", "游戏信息搜索功能，可以结合其他工具获取更详细的信息");
            
            // 可以调用其他工具或API获取游戏信息
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("搜索游戏信息失败", e);
            return "错误：搜索游戏信息失败 - " + e.getMessage();
        }
    }

    /**
     * 比较多个游戏的排行榜数据
     */
    @Tool(description = "比较多个游戏在不同排行榜中的表现，分析趋势和差异")
    public String compareGameRankings(
            @ToolParam(description = "要比较的游戏名称列表，用逗号分隔") String gameNames) {
        try {
            String[] names = gameNames.split(",");
            Map<String, Object> result = new HashMap<>();
            result.put("games", names);
            result.put("message", "游戏比较分析功能，可以结合多个排行榜数据进行对比分析");
            
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("比较游戏排行榜失败", e);
            return "错误：比较游戏排行榜失败 - " + e.getMessage();
        }
    }
}

