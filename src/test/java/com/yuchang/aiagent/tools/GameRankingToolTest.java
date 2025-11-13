package com.yuchang.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class GameRankingToolTest {

    private static final int STEAM_TOP_LIMIT = 25;

    private static final String STEAM_STATS_URL = "https://store.steampowered.com/stats/";
//    private static final String STEAM_CHARTS_TOPSELLING_URL = "https://store.steampowered.com/charts/topselling/CN";
    private static final String STEAM_CHARTS_TOPSELLING_URL = "https://store.steampowered.com/search/?filter=globaltopsellers&page=1&os=win";

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

    /**
     * 爬取Steam热门游戏排行榜
     */
    @Test
    public void fetchSteamTopGames() {
        try {
            // 使用jsoup连接Steam畅销榜页面
            Document doc = Jsoup.connect(STEAM_CHARTS_TOPSELLING_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();


//            System.out.println(doc.html());

            log.debug("成功获取Steam畅销榜页面HTML");

            List<Map<String, String>> gameList = new ArrayList<>();

            // 方法3: 尝试从链接中提取（查找所有包含/app/的链接）
            {
                Elements links = doc.select("a[href*='/app/']");
                int rank = 1;
                for (Element link : links) {
                    if (gameList.size() >= STEAM_TOP_LIMIT) break;

                    String href = link.attr("href");
                    String appId = extractAppIdFromUrl(href);
                    if (StrUtil.isBlank(appId)) continue;

                    // 检查是否已存在
                    boolean exists = gameList.stream()
                            .anyMatch(g -> appId.equals(g.get("appid")));
                    if (exists) continue;

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
            }

            if (gameList.isEmpty()) {
                log.warn("Steam畅销榜页面无法解析到有效的游戏数据，HTML结构可能已变更");
                // 输出部分HTML用于调试
                log.debug("页面标题: {}", doc.title());
                log.debug("页面部分HTML: {}", doc.body() != null ? doc.body().html().substring(0, Math.min(500, doc.body().html().length())) : "无body");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("source", "Steam Charts (Top Selling CN)");
            result.put("url", STEAM_CHARTS_TOPSELLING_URL);
            result.put("count", gameList.size());
            result.put("games", gameList);

            System.out.println(JSONUtil.toJsonPrettyStr(result));
        } catch (Exception e) {
            log.error("通过Steam畅销榜页面获取热门游戏失败", e);
        }
    }

    @Test
    public void trySteamChartsApi() {
        // Steam使用的protobuf API端点
        // 参数说明：origin=来源URL，input_protobuf_encoded=protobuf编码的请求参数
        // 这里使用中国区的参数：cc=CN (chinese)
        String protobufApiUrl = "https://api.steampowered.com/IStoreTopSellersService/GetWeeklyTopSellers/v1" +
                "?origin=https%3A%2F%2Fstore.steampowered.com" +
                "&input_protobuf_encoded=CgJVUxIQCghzY2hpbmVzZRoCVVMgARoQCAEQARgBKAEwAUAUSAFQATAU";

        try {
            log.debug("尝试调用Steam Protobuf API");
            HttpResponse response = HttpRequest.get(protobufApiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .header("Referer", "https://store.steampowered.com/")
                    .timeout(10000)
                    .execute();

            if (response.isOk()) {
                // 获取二进制响应数据
                byte[] responseBytes = response.bodyBytes();
                if (responseBytes != null && responseBytes.length > 0) {
                    log.debug("收到protobuf响应，大小: {} bytes", responseBytes.length);

                    // 调试：输出可读的字符串部分
                    String debugStr = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                    // 只输出包含可打印字符的部分
                    StringBuilder readable = new StringBuilder();
                    for (char c : debugStr.toCharArray()) {
                        if (c >= 32 && c < 127 || c == '\n' || c == '\r' || c == '\t') {
                            readable.append(c);
                        } else if (c > 127) {
                            readable.append(c); // 保留UTF-8字符
                        } else {
                            readable.append('.');
                        }
                    }
                    // 输出前2000个字符用于调试
                    String preview = readable.toString();
                    if (preview.length() > 2000) {
                        preview = preview.substring(0, 2000) + "...";
                    }
                    System.out.println("=== Protobuf响应可读部分预览 ===");
                    System.out.println(preview);
                    System.out.println("=== 结束预览 ===\n");

                    // 尝试从protobuf二进制数据中提取游戏信息
                    String result = parseProtobufResponse(responseBytes);
                    if (StrUtil.isNotBlank(result)) {
                        System.out.println(result);
                    }
                }
            } else {
                log.debug("API调用失败，HTTP状态码: {}", response.getStatus());
            }
        } catch (Exception e) {
            log.debug("调用Steam Protobuf API失败: {}", e.getMessage());
        }
    }

    private String parseProtobufResponse(byte[] responseBytes) {
        try {
            // 将二进制数据转换为字符串，protobuf中的字符串字段会以UTF-8编码存储
            String responseStr = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);

            log.debug("Protobuf响应转换为字符串后长度: {} 字符", responseStr.length());

            List<Map<String, String>> gameList = new ArrayList<>();

            // 方法1: 提取app/数字格式的appid（如 app/1808500 或 app/1808500/ARC_Raiders）
            // 优先匹配带游戏名称的格式：app/数字/游戏名
            Pattern appIdWithNamePattern = Pattern.compile("app/(\\d{4,})/([A-Za-z0-9_]+)");
            Matcher appIdWithNameMatcher = appIdWithNamePattern.matcher(responseStr);

            Map<String, String> appIdToNameMap = new HashMap<>(); // appid -> 游戏名称映射

            while (appIdWithNameMatcher.find()) {
                String appId = appIdWithNameMatcher.group(1);
                String nameInPath = appIdWithNameMatcher.group(2);
                // 将下划线替换为空格，如 ARC_Raiders -> ARC Raiders
                String gameName = nameInPath.replace("_", " ");
                appIdToNameMap.put(appId, gameName);
            }

            // 提取所有appid（包括不带名称的）
            Pattern appIdPattern = Pattern.compile("app/(\\d{4,})");
            Matcher appIdMatcher = appIdPattern.matcher(responseStr);
            List<String> appIds = new ArrayList<>();
            while (appIdMatcher.find() && appIds.size() < STEAM_TOP_LIMIT * 2) {
                String appId = appIdMatcher.group(1);
                if (!appIds.contains(appId)) {
                    appIds.add(appId);
                }
            }

            log.debug("从protobuf响应中提取到 {} 个appid，其中 {} 个有名称映射",
                    appIds.size(), appIdToNameMap.size());

            // 方法2: 提取游戏名称（查找可读的ASCII字符串）
            // 改进：使用更精确的正则表达式，避免匹配到二进制数据中的其他字段
            // 游戏名称通常：
            // 1. 以大写字母开头
            // 2. 包含字母、数字、空格、连字符、冒号等
            // 3. 不包含控制字符或特殊二进制字符
            // 4. 长度在合理范围内
            
            // 先尝试提取完整的字符串字段（protobuf字符串字段通常以长度前缀开始）
            // 更简单的方法：查找连续的字母数字和空格组合
            Pattern namePattern = Pattern.compile("([A-Z][A-Za-z0-9\\s&:.'-]{1,50}[A-Za-z0-9])");
            Matcher nameMatcher = namePattern.matcher(responseStr);

            // 收集所有可能的游戏名称，并记录它们在响应中的位置
            List<NamePosition> namePositions = new ArrayList<>();
            while (nameMatcher.find() && namePositions.size() < 300) {
                String name = nameMatcher.group(1).trim();
                
                // 清理名称：去除末尾的控制字符和无效字符
                name = cleanGameName(name);
                
                // 过滤掉明显不是游戏名称的字符串
                if (isValidGameName(name)) {
                    namePositions.add(new NamePosition(name, nameMatcher.start()));
                }
            }

            log.debug("从protobuf响应中提取到 {} 个可能的游戏名称", namePositions.size());

            // 方法3: 提取价格信息（$符号后的数字）
            Pattern pricePattern = Pattern.compile("\\$([0-9]+\\.?[0-9]*)");
            Matcher priceMatcher = pricePattern.matcher(responseStr);
            Map<Integer, String> positionToPrice = new HashMap<>(); // 位置 -> 价格
            while (priceMatcher.find()) {
                String price = "$" + priceMatcher.group(1);
                positionToPrice.put(priceMatcher.start(), price);
            }

            // 方法4: 智能匹配appid和游戏名称
            // 对于每个appid，在其附近（前后500字符）查找最可能的游戏名称
            int rank = 1;
            for (String appId : appIds) {
                if (gameList.size() >= STEAM_TOP_LIMIT) break;

                Map<String, String> game = new HashMap<>();
                game.put("rank", String.valueOf(rank++));
                game.put("appid", appId);

                // 优先使用从路径中提取的名称
                if (appIdToNameMap.containsKey(appId)) {
                    game.put("name", appIdToNameMap.get(appId));
                } else {
                    // 在appid附近查找游戏名称
                    int appIdIndex = responseStr.indexOf("app/" + appId);
                    if (appIdIndex >= 0) {
                        // 在appid前后各500字符范围内查找游戏名称
                        int searchStart = Math.max(0, appIdIndex - 500);
                        int searchEnd = Math.min(responseStr.length(), appIdIndex + 500);

                        // 查找在这个范围内最接近appid的游戏名称
                        String bestName = findClosestGameName(appIdIndex, namePositions, searchStart, searchEnd);
                        if (StrUtil.isNotBlank(bestName)) {
                            game.put("name", bestName);
                        }

                        // 查找价格信息
                        String price = findClosestPrice(appIdIndex, positionToPrice, searchStart, searchEnd);
                        if (StrUtil.isNotBlank(price)) {
                            game.put("price", price);
                        }
                    }
                }

                // 如果仍然没有名称，尝试使用名称列表中的下一个
                if (!game.containsKey("name") || StrUtil.isBlank(game.get("name"))) {
                    int nameIndex = gameList.size();
                    if (nameIndex < namePositions.size()) {
                        game.put("name", namePositions.get(nameIndex).name);
                    } else {
                        game.put("name", "Unknown Game " + appId);
                    }
                }

                gameList.add(game);
            }

            if (!gameList.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("source", "Steam Protobuf API");
                result.put("url", "https://api.steampowered.com/IStoreTopSellersService/GetWeeklyTopSellers/v1");
                result.put("count", gameList.size());
                result.put("games", gameList);
                log.info("成功从protobuf响应中提取到 {} 个游戏", gameList.size());
                return JSONUtil.toJsonPrettyStr(result);
            }

        } catch (Exception e) {
            log.error("解析protobuf响应失败", e);
        }

        return null;
    }

    /**
     * 查找最接近appid位置的价格信息
     */
    private String findClosestPrice(int appIdPosition, Map<Integer, String> positionToPrice,
                                    int searchStart, int searchEnd) {
        String bestPrice = null;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Integer, String> entry : positionToPrice.entrySet()) {
            int pricePos = entry.getKey();
            if (pricePos >= searchStart && pricePos <= searchEnd) {
                int distance = Math.abs(pricePos - appIdPosition);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestPrice = entry.getValue();
                }
            }
        }

        return bestPrice;
    }

    /**
     * 清理游戏名称，去除末尾的无效字符
     */
    private String cleanGameName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        // 去除末尾的控制字符、特殊字符和单个字母
        // 例如："ARC RaidersH" -> "ARC Raiders"
        name = name.trim();
        
        // 如果末尾是单个大写字母（可能是protobuf字段标识），去除它
        if (name.length() > 1 && name.matches(".*[A-Za-z0-9][A-Z]$")) {
            // 检查倒数第二个字符是否是字母或数字
            char lastChar = name.charAt(name.length() - 1);
            char secondLastChar = name.charAt(name.length() - 2);
            
            // 如果最后一个字符是单个大写字母，且前面是空格或字母，可能是无效字符
            if (Character.isUpperCase(lastChar) && 
                (Character.isWhitespace(secondLastChar) || Character.isLetter(secondLastChar))) {
                // 检查是否是常见的无效后缀
                if (name.endsWith("H") || name.endsWith("X") || name.endsWith("P") || 
                    name.endsWith("C") || name.endsWith("D") || name.endsWith("F")) {
                    // 如果去除后长度仍然合理，则去除
                    String candidate = name.substring(0, name.length() - 1).trim();
                    if (candidate.length() >= 3) {
                        name = candidate;
                    }
                }
            }
        }
        
        // 去除末尾的其他控制字符（ASCII < 32）
        while (name.length() > 0 && name.charAt(name.length() - 1) < 32) {
            name = name.substring(0, name.length() - 1).trim();
        }
        
        // 去除开头的无效字符
        while (name.length() > 0 && name.charAt(0) < 32) {
            name = name.substring(1).trim();
        }
        
        return name.trim();
    }
    
    /**
     * 判断字符串是否是有效的游戏名称
     */
    private boolean isValidGameName(String name) {
        if (name == null || name.length() < 3 || name.length() > 60) {
            return false;
        }
        
        // 检查是否包含控制字符（ASCII < 32，除了空格、制表符、换行符）
        for (char c : name.toCharArray()) {
            if (c < 32 && c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }

        // 过滤掉明显不是游戏名称的字符串
        String[] invalidPatterns = {
                "http", "www", "Steam", "Store", "Windows", "Mac", "Linux",
                "Android", "iOS", "PlayStation", "Xbox", "Nintendo",
                "video", "webm", "mp4", "jpg", "png", "gif", "webp",
                "movie", "trailer", "microtrailer", "dash", "hls"
        };

        String lowerName = name.toLowerCase();
        for (String pattern : invalidPatterns) {
            if (lowerName.contains(pattern)) {
                return false;
            }
        }

        // 过滤单个大写字母或短缩写
        if (name.matches("^[A-Z]{1,3}$")) {
            return false;
        }

        // 过滤纯数字
        if (name.matches("^\\d+$")) {
            return false;
        }
        
        // 过滤只包含空格和数字的字符串（如 " 6H"）
        if (name.trim().matches("^\\s*\\d+[A-Z]?$")) {
            return false;
        }
        
        // 过滤单个字符或只有空格的字符串
        if (name.trim().length() < 3) {
            return false;
        }

        // 过滤看起来像文件路径的字符串
        if (name.contains("/") || name.contains("\\")) {
            return false;
        }
        
        // 过滤末尾是单个大写字母的字符串（可能是protobuf字段标识）
        // 但允许正常的游戏名称（如 "PUBG"）
        if (name.length() > 3 && name.matches(".*\\s[A-Z]$")) {
            // 如果最后一个字符是单个大写字母，且前面是空格，可能是无效的
            String withoutLast = name.substring(0, name.length() - 1).trim();
            if (withoutLast.length() < 3) {
                return false;
            }
        }

        return true;
    }

    /**
     * 游戏名称及其在响应中的位置
     */
    private static class NamePosition {
        String name;
        int position;

        NamePosition(String name, int position) {
            this.name = name;
            this.position = position;
        }
    }

    /**
     * 查找最接近appid位置的游戏名称
     */
    private String findClosestGameName(int appIdPosition, List<NamePosition> namePositions,
                                       int searchStart, int searchEnd) {
        String bestName = null;
        int minDistance = Integer.MAX_VALUE;

        for (NamePosition np : namePositions) {
            if (np.position >= searchStart && np.position <= searchEnd) {
                int distance = Math.abs(np.position - appIdPosition);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestName = np.name;
                }
            }
        }

        return bestName;
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