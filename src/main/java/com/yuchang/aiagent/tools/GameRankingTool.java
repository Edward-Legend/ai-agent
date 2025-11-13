package com.yuchang.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

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
@Component
public class GameRankingTool {

    private final Cache<String, String> steamRankingCache;

    public GameRankingTool(@Qualifier("steamRankingCache") Cache<String, String> steamRankingCache) {
        this.steamRankingCache = steamRankingCache;
    }

    private static final String STEAM_TOP_GAMES_API = "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/?count=25";
    private static final String STEAM_STATS_URL = "https://store.steampowered.com/stats/";
    private static final String STEAM_CHARTS_TOPSELLING_URL = "https://store.steampowered.com/search/?filter=globaltopsellers&page=1&os=win";
    private static final int STEAM_TOP_LIMIT = 25;

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
     * 使用 Caffeine 缓存，缓存时间 30 分钟
     */
    @Tool(description = "爬取Steam平台的热门游戏排行榜，返回游戏名称、appid、发售日期、价格等信息")
    public String fetchSteamTopGames() {
        // 使用固定的缓存键
        String cacheKey = "steam_top_games";
        
        // 先从缓存中获取
        String cachedResult = steamRankingCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            log.debug("从缓存中获取Steam排行榜数据");
            return cachedResult;
        }
        
        log.debug("缓存未命中，开始爬取Steam排行榜数据");
        
        try {
            // 使用jsoup连接Steam畅销榜页面
            Document doc = Jsoup.connect(STEAM_CHARTS_TOPSELLING_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();

            log.debug("成功获取Steam畅销榜页面HTML");

            List<Map<String, String>> gameList = new ArrayList<>();

            // 从链接中提取（查找所有包含/app/的链接）
            Elements links = doc.select("a[href*='/app/']");
            int rank = 1;
            for (Element link : links) {
                if (gameList.size() >= STEAM_TOP_LIMIT) {
                    break;
                }

                String href = link.attr("href");
                String appId = extractAppIdFromUrl(href);
                if (StrUtil.isBlank(appId)) {
                    continue;
                }

                // 检查是否已存在
                boolean exists = gameList.stream()
                        .anyMatch(g -> appId.equals(g.get("appid")));
                if (exists) {
                    continue;
                }

                String name = link.text().trim();
                if (StrUtil.isBlank(name)) {
                    // 尝试从父元素获取名称
                    Element nameEl = link.selectFirst("span, div, h3, h4");
                    if (nameEl != null) {
                        name = nameEl.text().trim();
                    }
                }

                if (StrUtil.isNotBlank(name)) {
                    // 解析游戏名称，分离日期和价格
                    Map<String, String> parsedInfo = parseGameName(name);
                    
                    Map<String, String> game = new HashMap<>();
                    game.put("rank", String.valueOf(rank));
                    game.put("name", parsedInfo.get("name"));
                    game.put("appid", appId);
                    
                    // 如果有日期，添加日期字段
                    if (StrUtil.isNotBlank(parsedInfo.get("date"))) {
                        game.put("releaseDate", parsedInfo.get("date"));
                    }
                    
                    // 如果有价格，添加价格字段
                    if (StrUtil.isNotBlank(parsedInfo.get("price"))) {
                        game.put("price", parsedInfo.get("price"));
                    }
                    
                    gameList.add(game);
                    rank++;
                }
            }

            if (gameList.isEmpty()) {
                log.warn("Steam畅销榜页面无法解析到有效的游戏数据，HTML结构可能已变更");
                // 输出部分HTML用于调试
                log.debug("页面标题: {}", doc.title());
                log.debug("页面部分HTML: {}", doc.body() != null ? doc.body().html().substring(0, Math.min(500, doc.body().html().length())) : "无body");
                return "错误：Steam畅销榜页面无法解析到有效的游戏数据";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "Steam Charts (Top Selling CN)");
            result.put("url", STEAM_CHARTS_TOPSELLING_URL);
            result.put("count", gameList.size());
            result.put("games", gameList);

            String jsonResult = JSONUtil.toJsonPrettyStr(result);
            
            // 将结果存入缓存
            steamRankingCache.put(cacheKey, jsonResult);
            log.debug("Steam排行榜数据已存入缓存");
            
            return jsonResult;
        } catch (Exception e) {
            log.error("通过Steam畅销榜页面获取热门游戏失败", e);
            return "错误：爬取Steam热门游戏排行榜失败 - " + e.getMessage();
        }
    }

    /**
     * 从JSON对象中解析游戏信息
     */
    private Map<String, String> parseGameFromJson(JSONObject gameObj, int rank) {
        Map<String, String> game = new HashMap<>();
        game.put("rank", String.valueOf(rank));
        
        String name = gameObj.getStr("name");
        if (StrUtil.isBlank(name)) {
            name = gameObj.getStr("title");
        }
        if (StrUtil.isNotBlank(name)) {
            game.put("name", name);
        }
        
        String appId = gameObj.getStr("appid");
        if (StrUtil.isBlank(appId)) {
            appId = gameObj.getStr("app_id");
        }
        if (StrUtil.isNotBlank(appId)) {
            game.put("appid", appId);
        }
        
        // 尝试获取价格信息
        String price = gameObj.getStr("price");
        if (StrUtil.isBlank(price)) {
            price = gameObj.getStr("final_price");
        }
        if (StrUtil.isNotBlank(price)) {
            game.put("price", price);
        }
        
        // 尝试获取折扣信息
        String discount = gameObj.getStr("discount");
        if (StrUtil.isNotBlank(discount)) {
            game.put("discount", discount);
        }
        
        return game;
    }
    
    /**
     * 从HTML元素中解析游戏信息
     */
    private Map<String, String> parseGameFromElement(Element element, int rank) {
        Map<String, String> game = new HashMap<>();
        game.put("rank", String.valueOf(rank));
        
        // 尝试多种方式获取游戏名称
        Element nameEl = element.selectFirst("a[href*='/app/'], .game_name, .chart_row_name, [class*='name'], [class*='Name']");
        if (nameEl == null) {
            nameEl = element.selectFirst("a, h3, h4, span.title, div.title");
        }
        
        if (nameEl != null) {
            String name = nameEl.text().trim();
            if (StrUtil.isBlank(name)) {
                name = nameEl.attr("title");
            }
            if (StrUtil.isNotBlank(name)) {
                game.put("name", name);
            }
            
            // 从链接中提取appid
            String href = nameEl.attr("href");
            if (StrUtil.isBlank(href)) {
                Element link = nameEl.selectFirst("a");
                if (link != null) {
                    href = link.attr("href");
                }
            }
            String appId = extractAppIdFromUrl(href);
            if (StrUtil.isNotBlank(appId)) {
                game.put("appid", appId);
            }
        }
        
        // 尝试获取价格信息
        Element priceEl = element.selectFirst(".price, [class*='price'], [class*='Price']");
        if (priceEl != null) {
            String price = priceEl.text().trim();
            if (StrUtil.isNotBlank(price)) {
                game.put("price", price);
            }
        }
        
        // 尝试获取折扣信息
        Element discountEl = element.selectFirst(".discount, [class*='discount'], [class*='Discount']");
        if (discountEl != null) {
            String discount = discountEl.text().trim();
            if (StrUtil.isNotBlank(discount)) {
                game.put("discount", discount);
            }
        }
        
        return game;
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

    /**
     * 解析游戏名称，分离日期和价格
     * 例如："Counter-Strike 2 2012 年 8 月 21 日 免费" -> name: "Counter-Strike 2", date: "2012 年 8 月 21 日", price: "免费"
     * 例如："ARC Raiders 2025 年 10 月 30 日 $39.99" -> name: "ARC Raiders", date: "2025 年 10 月 30 日", price: "$39.99"
     */
    private Map<String, String> parseGameName(String fullName) {
        Map<String, String> result = new HashMap<>();
        
        if (StrUtil.isBlank(fullName)) {
            result.put("name", "");
            result.put("date", "");
            result.put("price", "");
            return result;
        }
        
        String name = fullName.trim();
        String date = null;
        String price = null;
        
        // 匹配日期格式：年份 年 月份 月 日期 日（如 "2012 年 8 月 21 日"）
        Pattern datePattern = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
        Matcher dateMatcher = datePattern.matcher(name);
        
        // 匹配价格格式：可能是 "免费"、"$数字"、"¥数字"、"€数字" 等
        Pattern pricePattern = Pattern.compile("([$¥€£]\\s*\\d+(\\.\\d{1,2})?|\\d+(\\.\\d{1,2})?\\s*[$¥€£]|免费)");
        
        // 先查找日期和价格的位置
        int dateStart = -1;
        int priceStart = -1;
        
        if (dateMatcher.find()) {
            dateStart = dateMatcher.start();
            date = dateMatcher.group(0);
        }
        
        Matcher priceMatcher = pricePattern.matcher(name);
        if (priceMatcher.find()) {
            priceStart = priceMatcher.start();
            price = priceMatcher.group(0).trim();
        }
        
        // 根据日期和价格的位置，提取游戏名称
        // 通常格式是：游戏名 + 日期 + 价格，或者游戏名 + 价格
        if (dateStart >= 0 && priceStart >= 0) {
            // 两者都存在，取较小的位置作为游戏名称的结束位置
            int endPos = Math.min(dateStart, priceStart);
            name = name.substring(0, endPos).trim();
        } else if (dateStart >= 0) {
            // 只有日期
            name = name.substring(0, dateStart).trim();
        } else if (priceStart >= 0) {
            // 只有价格
            name = name.substring(0, priceStart).trim();
        }
        // 如果都没有，name保持原样
        
        result.put("name", name);
        result.put("date", date != null ? date : "");
        result.put("price", price != null ? price : "");
        
        return result;
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

