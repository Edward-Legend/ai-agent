package com.yuchang.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            String apiResult = fetchSteamTopGamesFromApi();
            if (apiResult != null) {
                return apiResult;
            }
        } catch (Exception e) {
            log.warn("通过Steam官方接口获取热门游戏失败，将尝试网页备选方案", e);
        }

        try {
            String htmlResult = fetchSteamTopGamesFromHtml();
            if (htmlResult != null) {
                return htmlResult;
            }
        } catch (Exception e) {
            log.warn("通过Steam网页解析热门游戏失败", e);
        }

        try {
            String steamChartsResult = fetchSteamTopGamesFromSteamCharts();
            if (steamChartsResult != null) {
                return steamChartsResult;
            }
        } catch (Exception e) {
            log.warn("通过SteamCharts解析热门游戏失败", e);
        }

        return "错误：爬取Steam热门游戏失败 - 数据源不可用";
    }

    private static final String STEAM_TOP_GAMES_API = "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/?count=25";
    private static final String STEAM_STATS_URL = "https://store.steampowered.com/stats/";
    private static final int STEAM_TOP_LIMIT = 20;

    private String fetchSteamTopGamesFromApi() {
        try {
            HttpResponse response = HttpRequest.get(STEAM_TOP_GAMES_API)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .execute();

            if (!response.isOk()) {
                log.warn("Steam热门游戏接口返回异常：HTTP {}", response.getStatus());
                return null;
            }

            String body = response.body();
            if (StrUtil.isBlank(body)) {
                log.warn("Steam热门游戏接口返回为空");
                return null;
            }

            JSONObject root = JSONUtil.parseObj(body);
            JSONObject resp = root.getJSONObject("response");
            if (resp == null) {
                log.warn("Steam热门游戏接口缺少response节点");
                return null;
            }

            JSONArray ranks = resp.getJSONArray("ranks");
            if (ranks == null || ranks.isEmpty()) {
                log.warn("Steam热门游戏接口未返回排行榜数据");
                return null;
            }

            List<Map<String, String>> gameList = new ArrayList<>();
            for (int i = 0; i < ranks.size() && gameList.size() < STEAM_TOP_LIMIT; i++) {
                JSONObject rankObj = ranks.getJSONObject(i);
                if (rankObj == null) {
                    continue;
                }

                String name = rankObj.getStr("name");
                if (StrUtil.isBlank(name)) {
                    continue;
                }

                Map<String, String> game = new HashMap<>();
                game.put("rank", String.valueOf(rankObj.getInt("rank", i + 1)));
                game.put("name", name);

                long currentPlayers = firstPositiveNumber(rankObj, "concurrent_in_game", "current_players", "players", "avg_players");
                long peakPlayers = firstPositiveNumber(rankObj, "peak_in_game", "peak_players", "24h_peak_players", "last_24h_peak_players");

                if (currentPlayers > 0) {
                    game.put("currentPlayers", String.valueOf(currentPlayers));
                }
                if (peakPlayers > 0) {
                    game.put("peakPlayers", String.valueOf(peakPlayers));
                }

                String appId = rankObj.getStr("appid");
                if (StrUtil.isNotBlank(appId)) {
                    game.put("appid", appId);
                }

                if (game.containsKey("currentPlayers") || game.containsKey("peakPlayers")) {
                    gameList.add(game);
                }
            }

            if (gameList.isEmpty()) {
                log.warn("Steam热门游戏接口数据无法解析有效的榜单信息");
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "Steam API");
            result.put("url", STEAM_TOP_GAMES_API);
            result.put("count", gameList.size());
            result.put("games", gameList);

            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("通过Steam接口获取热门游戏失败", e);
            return null;
        }
    }

    private String fetchSteamTopGamesFromHtml() {
        try {
            Document doc = Jsoup.connect(STEAM_STATS_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            String html = doc.outerHtml();
            Pattern pattern = Pattern.compile("var\\s+g_rgTopPlayedApps\\s*=\\s*(\\[.*?\\]);", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);
            if (!matcher.find()) {
                log.warn("Steam统计页面未找到g_rgTopPlayedApps数据脚本");
                return null;
            }

            String jsonArrayStr = matcher.group(1);
            JSONArray array = JSONUtil.parseArray(jsonArrayStr);
            if (array == null || array.isEmpty()) {
                log.warn("Steam统计页面g_rgTopPlayedApps数据为空");
                return null;
            }

            List<Map<String, String>> gameList = new ArrayList<>();
            for (int i = 0; i < array.size() && gameList.size() < STEAM_TOP_LIMIT; i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj == null) {
                    continue;
                }

                String name = obj.getStr("name");
                if (StrUtil.isBlank(name)) {
                    continue;
                }

                Map<String, String> game = new HashMap<>();
                game.put("rank", String.valueOf(i + 1));
                game.put("name", name);

                long current = parseLong(obj.getStr("current"));
                long peak = parseLong(obj.getStr("peak"));

                if (current > 0) {
                    game.put("currentPlayers", String.valueOf(current));
                }
                if (peak > 0) {
                    game.put("peakPlayers", String.valueOf(peak));
                }

                String appId = obj.getStr("appid");
                if (StrUtil.isNotBlank(appId)) {
                    game.put("appid", appId);
                }

                if (game.containsKey("currentPlayers") || game.containsKey("peakPlayers")) {
                    gameList.add(game);
                }
            }

            if (gameList.isEmpty()) {
                log.warn("Steam统计页面脚本数据未解析到有效榜单");
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "Steam");
            result.put("url", STEAM_STATS_URL);
            result.put("count", gameList.size());
            result.put("games", gameList);

            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("爬取Steam热门游戏失败", e);
            return null;
        }
    }

    private String fetchSteamTopGamesFromSteamCharts() {
        final String url = "https://steamcharts.com/top/p.1";
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            Element table = doc.selectFirst("table.common-table");
            if (table == null) {
                log.warn("SteamCharts页面缺少排行榜表格");
                return null;
            }

            Elements rows = table.select("tbody tr");
            if (rows == null || rows.isEmpty()) {
                log.warn("SteamCharts排行榜无有效数据行");
                return null;
            }

            List<Map<String, String>> gameList = new ArrayList<>();
            for (Element row : rows) {
                if (gameList.size() >= STEAM_TOP_LIMIT) {
                    break;
                }

                Elements cols = row.select("td");
                if (cols.size() < 5) {
                    continue;
                }

                String rankText = cols.get(0).text();
                String name = cols.get(1).text();
                String current = cols.get(2).text();
                String peak24h = cols.get(3).text();
                String peakAll = cols.get(4).text();

                if (StrUtil.isBlank(name)) {
                    continue;
                }

                Map<String, String> game = new HashMap<>();
                if (StrUtil.isNotBlank(rankText)) {
                    game.put("rank", rankText.replace("#", "").trim());
                } else {
                    game.put("rank", String.valueOf(gameList.size() + 1));
                }
                game.put("name", name);

                long currentPlayers = parseLong(current);
                long peakPlayers = parseLong(peak24h);
                long allTimePeak = parseLong(peakAll);

                if (currentPlayers > 0) {
                    game.put("currentPlayers", String.valueOf(currentPlayers));
                }
                if (peakPlayers > 0) {
                    game.put("peakPlayers", String.valueOf(peakPlayers));
                } else if (allTimePeak > 0) {
                    game.put("peakPlayers", String.valueOf(allTimePeak));
                }

                Element appLink = cols.get(1).selectFirst("a");
                if (appLink != null) {
                    String href = appLink.attr("href");
                    String appId = extractAppIdFromUrl(href);
                    if (StrUtil.isNotBlank(appId)) {
                        game.put("appid", appId);
                    }
                }

                gameList.add(game);
            }

            if (gameList.isEmpty()) {
                log.warn("SteamCharts页面未解析到有效数据");
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "SteamCharts");
            result.put("url", url);
            result.put("count", gameList.size());
            result.put("games", gameList);

            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            log.error("爬取SteamCharts热门游戏失败", e);
            return null;
        }
    }

    private String extractAppIdFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        Pattern pattern = Pattern.compile("/app/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private long firstPositiveNumber(JSONObject jsonObject, String... keys) {
        if (jsonObject == null || keys == null) {
            return -1;
        }
        for (String key : keys) {
            if (StrUtil.isBlank(key) || !jsonObject.containsKey(key)) {
                continue;
            }
            String value = jsonObject.getStr(key);
            long parsed = parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        }
        return -1;
    }

    private long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return -1;
        }
        String normalized = StrUtil.removeAll(value, ',').trim();
        if (StrUtil.isBlank(normalized)) {
            return -1;
        }
        try {
            if (normalized.contains(".")) {
                return Math.round(Double.parseDouble(normalized));
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return -1;
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




}

