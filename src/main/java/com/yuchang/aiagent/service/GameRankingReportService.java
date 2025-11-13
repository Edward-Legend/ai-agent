package com.yuchang.aiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuchang.aiagent.model.GameRankingReport;
import com.yuchang.aiagent.util.ChartGenerateUtil;
import com.yuchang.aiagent.tools.GameRankingTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏榜单报表服务，负责从工具获取数据并生成可视化报表
 */
@Service
@Slf4j
public class GameRankingReportService {

    private final ChartGenerateUtil chartGenerateUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRankingTool gameRankingTool;
    
    // 用于存储正在生成的分析结果，key为reportId
    private final Map<String, CompletableFuture<String>> pendingAnalysis = new ConcurrentHashMap<>();

    public GameRankingReportService(ChartGenerateUtil chartGenerateUtil, GameRankingTool gameRankingTool) {
        this.chartGenerateUtil = chartGenerateUtil;
        this.gameRankingTool = gameRankingTool;
    }

    private static final String DEFAULT_STEAM_DATA = """
            游戏名称,排名,appid,发售日期,价格
            Counter-Strike 2,1,730,2012年8月21日,免费
            Dota 2,2,570,2013年7月9日,免费
            PUBG: BATTLEGROUNDS,3,578080,2017年12月21日,$29.99
            Apex Legends,4,1172470,2019年2月4日,免费
            GTA V,5,271590,2015年4月14日,$29.99
            Team Fortress 2,6,440,2007年10月10日,免费
            ARK: Survival Ascended,7,2399830,2023年10月26日,$39.99
            Cyberpunk 2077,8,1091500,2020年12月10日,$59.99
            Rust,9,252490,2018年2月8日,$39.99
            War Thunder,10,236390,2013年8月15日,免费
            """;

    private static final String DEFAULT_TAPTAP_DATA = """
            榜单,排名,游戏名称,评分
            热门榜,1,绝区零,9.5
            热门榜,2,明日方舟,9.2
            热门榜,3,原神,9.1
            热门榜,4,世界计划 多彩舞台,9.0
            热门榜,5,少女前线2 追放,8.9
            新品榜,1,铃兰之剑,9.4
            新品榜,2,流浪方舟,9.2
            新品榜,3,重返未来1999,9.0
            新品榜,4,鸣潮,8.8
            新品榜,5,星枪旅团,8.7
            热卖榜,1,暗区突围,9.3
            热卖榜,2,幻塔,9.0
            热卖榜,3,星穹铁道,8.9
            热卖榜,4,剑网3指尖江湖,8.7
            热卖榜,5,和平精英,8.5
            """;

    private static final String DEFAULT_3DM_DATA = """
            排名,游戏名称,类型,平台,发售日期
            1,艾尔登法环：黄金树幽影,动作角色扮演,PC/PS5/Xbox Series,2024-06-21
            2,刺客信条：影,动作冒险,PC/PS5/Xbox Series,2024-11-15
            3,荒野大镖客2,开放世界冒险,PC/PS4/Xbox One,2019-11-05
            4,赛博朋克2077：自由幻局,角色扮演,PC/PS5/Xbox Series,2023-09-26
            5,霍格沃茨之遗,动作角色扮演,PC/PS5/Xbox Series/Switch,2023-02-10
            """;


    /**
     * 生成最新的游戏榜单报表集合
     */
    public List<GameRankingReport> generateLatestReports() {
        List<GameRankingReport> reports = new ArrayList<>();
        GameRankingReport steamReport = generateSteamTopGamesReport();
        if (steamReport != null) {
            reports.add(steamReport);
        }
        // 暂时注释掉 TapTap 和 3DM 的图表生成
        // GameRankingReport tapTapReport = generateTapTapBoardReport();
        // if (tapTapReport != null) {
        //     reports.add(tapTapReport);
        // }
        // GameRankingReport threeDmReport = generate3DMHotGamesReport();
        // if (threeDmReport != null) {
        //     reports.add(threeDmReport);
        // }
        return reports;
    }

    /**
     * 生成Steam热门游戏榜单报表
     */
    public GameRankingReport generateSteamTopGamesReport() {
        String json = null;
        try {
            json = gameRankingTool.fetchSteamTopGames();
            if (StringUtils.isBlank(json) || json.startsWith("错误")) {
                log.warn("获取Steam榜单数据失败，将使用默认数据。返回内容：{}", json);
                return buildSteamReport(DEFAULT_STEAM_DATA, true);
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode gamesNode = root.path("games");
            if (!gamesNode.isArray() || gamesNode.isEmpty()) {
                log.warn("Steam榜单数据为空，将使用默认数据。");
                return buildSteamReport(DEFAULT_STEAM_DATA, true);
            }

            // 新的数据格式：游戏名称,排名,appid,发售日期,价格
            StringBuilder csvBuilder = new StringBuilder("游戏名称,排名,appid,发售日期,价格\n");
            for (JsonNode gameNode : gamesNode) {
                String name = gameNode.path("name").asText("");
                String rank = gameNode.path("rank").asText("");
                String appid = gameNode.path("appid").asText("");
                String releaseDate = gameNode.path("releaseDate").asText("");
                String price = gameNode.path("price").asText("");

                if (StringUtils.isAnyBlank(name, rank)) {
                    continue;
                }
                csvBuilder.append(name).append(',')
                        .append(rank).append(',')
                        .append(StringUtils.defaultString(appid)).append(',')
                        .append(StringUtils.defaultString(releaseDate)).append(',')
                        .append(StringUtils.defaultString(price)).append('\n');
            }

            String csvData = csvBuilder.toString();
            if (csvData.trim().split("\n").length <= 1) {
                log.warn("Steam榜单解析结果为空，将使用默认数据。");
                return buildSteamReport(DEFAULT_STEAM_DATA, true);
            }
            return buildSteamReport(csvData, false);
        } catch (Exception e) {
            log.error("生成Steam榜单报表失败，将使用默认数据。", e);
            return buildSteamReport(DEFAULT_STEAM_DATA, true);
        }
    }

    private GameRankingReport buildSteamReport(String csvData, boolean isFallback) {
        // 根据新的数据格式调整分析目标，使用横向柱状图展示排名
        String goal = "分析Steam平台畅销游戏排行榜，使用横向柱状图展示游戏排名，Y轴显示游戏名称（按排名从高到低），X轴显示排名数值，并在图表中标注价格信息。图表需要横向布局，便于展示完整的游戏名称。";
        String chartType = "bar"; // 使用横向柱状图
        
        // 快速生成图表配置（不调用AI）
        ChartGenerateUtil.BiResponse chartResponse = chartGenerateUtil.generateChartByRule(csvData, goal, chartType);
        Map<String, Object> chartConfig = parseChartConfig(chartResponse.genChart());
        
        // 优化图表配置为横向柱状图
        chartConfig = optimizeChartForHorizontalBar(chartConfig, csvData);
        
        // 生成报告ID
        String reportId = UUID.randomUUID().toString();
        
        // 异步生成AI分析结果
        CompletableFuture<String> analysisFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return chartGenerateUtil.generateAnalysisOnly(csvData, goal);
            } catch (Exception e) {
                log.error("生成AI分析结果失败", e);
                return "分析结果生成失败，请稍后重试。";
            }
        });
        
        // 存储异步任务，供后续查询使用
        pendingAnalysis.put(reportId, analysisFuture);
        
        // 先返回包含图表配置的报告，analysisResult设为占位符
        String analysisResult = "正在生成分析结果...";
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceUrl", "https://store.steampowered.com/search/?filter=globaltopsellers&page=1&os=win");
        metadata.put("fallback", isFallback);
        metadata.put("sampleCount", csvData.trim().split("\\r?\\n").length - 1);
        return new GameRankingReport(
                reportId,
                "Steam",
                "Steam畅销游戏排行榜分析",
                goal,
                chartType,
                chartConfig,
                analysisResult,
                csvData,
                nowIso(),
                metadata
        );
    }
    
    /**
     * 获取分析结果（异步）
     * @param reportId 报告ID
     * @return 分析结果，如果还在生成中则返回null
     */
    public String getAnalysisResult(String reportId) {
        CompletableFuture<String> future = pendingAnalysis.get(reportId);
        if (future == null) {
            return null; // 报告不存在或已过期
        }
        if (future.isDone()) {
            try {
                String result = future.get();
                // 获取成功后，延迟清理（5分钟后）
                new Thread(() -> {
                    try {
                        Thread.sleep(5 * 60 * 1000); // 5分钟
                        pendingAnalysis.remove(reportId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                return result;
            } catch (Exception e) {
                log.error("获取分析结果失败", e);
                return "分析结果获取失败";
            }
        }
        return null; // 还在生成中
    }

    /**
     * 优化图表配置为横向柱状图
     */
    private Map<String, Object> optimizeChartForHorizontalBar(Map<String, Object> chartConfig, String csvData) {
        try {
            // 解析CSV数据
            String[] lines = csvData.trim().split("\\r?\\n");
            if (lines.length <= 1) {
                return chartConfig;
            }

            String[] headers = lines[0].split(",");
            List<String> gameNames = new ArrayList<>();
            List<Object> ranks = new ArrayList<>();
            
            // CSV格式：游戏名称,排名,appid,发售日期,价格
            // 第一列是游戏名称，第二列是排名
            for (int i = 1; i < lines.length; i++) {
                String[] values = lines[i].split(",");
                if (values.length >= 2) {
                    try {
                        gameNames.add(values[0].trim());
                        ranks.add(Integer.parseInt(values[1].trim()));
                    } catch (NumberFormatException e) {
                        // 忽略无效行
                    }
                }
            }

            if (gameNames.isEmpty()) {
                return chartConfig;
            }

            // 横向柱状图：排名1应该在顶部，且条形最长
            // 1. 确保数据顺序：如果第一个排名不是1，说明数据可能是倒序的，需要反转
            int firstRank = (Integer) ranks.get(0);
            int lastRank = (Integer) ranks.get(ranks.size() - 1);
            boolean needReverse = firstRank > lastRank; // 如果第一个排名大于最后一个，说明是倒序
            
            if (needReverse) {
                // 反转列表，使排名1在前
                List<String> reversedNames = new ArrayList<>(gameNames);
                List<Object> reversedRanks = new ArrayList<>(ranks);
                java.util.Collections.reverse(reversedNames);
                java.util.Collections.reverse(reversedRanks);
                gameNames = reversedNames;
                ranks = reversedRanks;
            }
            
            // 2. 计算最大排名值，用于生成倒数排名（使排名1的条形最长）
            int maxRank = ranks.stream()
                    .mapToInt(r -> (Integer) r)
                    .max()
                    .orElse(1);
            
            // 3. 生成倒数排名数据：排名1 -> maxRank，排名maxRank -> 1
            // 这样排名1的数值最大，条形最长
            // 数据格式：每个元素是一个对象，包含value（倒数排名）和rank（原始排名）
            List<Map<String, Object>> invertedRanks = new ArrayList<>();
            for (int i = 0; i < ranks.size(); i++) {
                int rankValue = (Integer) ranks.get(i);
                int invertedValue = maxRank + 1 - rankValue; // 倒数排名
                Map<String, Object> dataItem = new HashMap<>();
                dataItem.put("value", invertedValue);
                dataItem.put("rank", rankValue); // 保存原始排名用于标签显示
                invertedRanks.add(dataItem);
            }

            // 更新图表配置为横向柱状图
            Map<String, Object> optimizedConfig = new LinkedHashMap<>(chartConfig);
            
            // 设置简洁的图表标题，移除提示词
            optimizedConfig.put("title", Map.of("text", "Steam畅销游戏排行榜"));
            
            // 设置grid布局，增加左侧空间以显示游戏名称
            Map<String, Object> grid = new HashMap<>();
            grid.put("left", "25%");
            grid.put("right", "10%");
            grid.put("top", "10%");
            grid.put("bottom", "10%");
            optimizedConfig.put("grid", grid);

            // Y轴显示游戏名称（横向柱状图）
            // 使用inverse: true确保排名1在顶部（Y轴从上到下显示）
            Map<String, Object> yAxis = new HashMap<>();
            yAxis.put("type", "category");
            yAxis.put("data", gameNames);
            yAxis.put("inverse", true); // 反转Y轴，使第一个数据在顶部
            yAxis.put("axisLabel", Map.of("interval", 0)); // 显示所有标签
            optimizedConfig.put("yAxis", yAxis);

            // X轴显示倒数排名数值（排名1的数值最大，条形最长）
            Map<String, Object> xAxis = new HashMap<>();
            xAxis.put("type", "value");
            xAxis.put("min", 0);
            xAxis.put("max", maxRank);
            optimizedConfig.put("xAxis", xAxis);

            // 更新series配置
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> seriesList = (List<Map<String, Object>>) optimizedConfig.get("series");
            if (seriesList != null && !seriesList.isEmpty()) {
                Map<String, Object> series = new LinkedHashMap<>(seriesList.get(0));
                // 使用倒数排名数据，但标签显示原始排名
                series.put("data", invertedRanks);
                series.put("type", "bar");
                // 添加标签配置（formatter由前端处理）
                Map<String, Object> label = new HashMap<>();
                label.put("show", true);
                label.put("position", "right");
                series.put("label", label);
                
                optimizedConfig.put("series", List.of(series));
            }

            return optimizedConfig;
        } catch (Exception e) {
            log.error("优化横向柱状图配置失败，使用原始配置", e);
            return chartConfig;
        }
    }

    /**
     * 生成TapTap榜单评分报表（Demo版本，直接返回默认数据）
     */
    public GameRankingReport generateTapTapBoardReport() {
        // Demo版本：直接返回默认数据，不调用真实API
        log.info("生成TapTap榜单报表（Demo模式）");
        return buildTapTapReport(DEFAULT_TAPTAP_DATA, true);
    }

    private GameRankingReport buildTapTapReport(String csvData, boolean isFallback) {
        String goal = "比较TapTap各榜单头部游戏的评分表现";
        String chartType = "bar";
        // Demo版本：使用规则生成图表（不调用AI），AI分析写死
        ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChartByRule(csvData, goal, chartType);
        Map<String, Object> chartConfig = parseChartConfig(response.genChart());
        
        // 设置图表标题
        chartConfig.put("title", Map.of("text", "TapTap榜单评分对比"));
        
        // 写死的AI分析文本
        String analysisResult = """
                TapTap平台各榜单头部游戏评分分析：
                
                1. **热门榜表现**：绝区零以9.5分领跑，明日方舟和原神紧随其后，评分均在9.0以上，显示出强大的用户粘性和口碑。
                
                2. **新品榜亮点**：铃兰之剑作为新作获得9.4分，表现突出；重返未来1999和鸣潮等新游戏也获得了不错的评分，说明TapTap用户对新游戏的接受度较高。
                
                3. **热卖榜特征**：暗区突围以9.3分位居榜首，幻塔和星穹铁道等游戏评分稳定在8.5-9.0区间，显示出良好的商业化表现。
                
                4. **整体趋势**：各榜单头部游戏评分普遍在8.5分以上，说明TapTap平台对游戏质量有较高要求，用户评价体系相对严格。
                """;
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceUrl", "https://www.taptap.com/top");
        metadata.put("fallback", isFallback);
        metadata.put("sampleCount", csvData.trim().split("\\r?\\n").length - 1);
        return new GameRankingReport(
                UUID.randomUUID().toString(),
                "TapTap",
                "TapTap榜单评分对比",
                goal,
                chartType,
                chartConfig,
                analysisResult,
                csvData,
                nowIso(),
                metadata
        );
    }

    /**
     * 生成3DM热门单机游戏榜单报表（Demo版本，直接返回默认数据）
     */
    public GameRankingReport generate3DMHotGamesReport() {
        // Demo版本：直接返回默认数据，不调用真实API
        log.info("生成3DM榜单报表（Demo模式）");
        return build3DMReport(DEFAULT_3DM_DATA, true);
    }

    private GameRankingReport build3DMReport(String csvData, boolean isFallback) {
        String goal = "洞察3DM热门单机游戏的题材与平台分布";
        String chartType = "bar";
        // Demo版本：使用规则生成图表（不调用AI），AI分析写死
        ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChartByRule(csvData, goal, chartType);
        Map<String, Object> chartConfig = parseChartConfig(response.genChart());
        
        // 设置图表标题
        chartConfig.put("title", Map.of("text", "3DM热门单机游戏热度观察"));
        
        // 写死的AI分析文本
        String analysisResult = """
                3DM热门单机游戏榜单分析：
                
                1. **游戏类型分布**：动作角色扮演类游戏占据主导地位，如艾尔登法环、霍格沃茨之遗等，说明玩家对深度剧情和角色成长系统有较高需求。
                
                2. **平台覆盖**：大部分热门游戏都支持多平台（PC/PS5/Xbox Series），跨平台策略成为主流，有助于扩大用户群体。
                
                3. **发售时间**：榜单中既有2024年的新作（如艾尔登法环：黄金树幽影），也有2019年的经典作品（如荒野大镖客2），说明优质游戏具有长久的生命力。
                
                4. **市场趋势**：开放世界和动作冒险类游戏持续受到玩家青睐，这类游戏通常具有较高的可玩性和探索价值，符合当前玩家的游戏偏好。
                """;
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceUrl", "https://www.3dmgame.com/jqremphb.html");
        metadata.put("fallback", isFallback);
        metadata.put("sampleCount", csvData.trim().split("\\r?\\n").length - 1);
        return new GameRankingReport(
                UUID.randomUUID().toString(),
                "3DM",
                "3DM热门单机游戏热度观察",
                goal,
                chartType,
                chartConfig,
                analysisResult,
                csvData,
                nowIso(),
                metadata
        );
    }

    private Map<String, Object> parseChartConfig(String chartJson) {
        try {
            return objectMapper.readValue(chartJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("解析图表配置失败，返回空配置。内容：{}", chartJson, e);
            return Map.of("error", "解析图表配置失败");
        }
    }

    private long parseNumber(String value) {
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        String cleaned = value.replaceAll("[^\\d.]", "");
        if (cleaned.isEmpty()) {
            return 0L;
        }
        try {
            if (cleaned.contains(".")) {
                return Math.round(Double.parseDouble(cleaned));
            }
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            log.debug("无法解析数值：{}，已返回0。", value);
            return 0L;
        }
    }

    private double parseDouble(String value) {
        if (StringUtils.isBlank(value)) {
            return 0D;
        }
        String cleaned = value.replaceAll("[^\\d.]", "");
        if (cleaned.isEmpty()) {
            return 0D;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.debug("无法解析浮点数：{}，已返回0。", value);
            return 0D;
        }
    }

    private String nowIso() {
        return java.time.OffsetDateTime.now().toString();
    }
}

