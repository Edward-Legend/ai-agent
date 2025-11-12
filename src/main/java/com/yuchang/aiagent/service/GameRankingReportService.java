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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 游戏榜单报表服务，负责从工具获取数据并生成可视化报表
 */
@Service
@Slf4j
public class GameRankingReportService {

    private final ChartGenerateUtil chartGenerateUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRankingTool gameRankingTool;

    private static final String DEFAULT_STEAM_DATA = """
            游戏名称,当前玩家数,峰值玩家数,排名
            Counter-Strike 2,1120000,1450000,1
            Dota 2,650000,920000,2
            PUBG: BATTLEGROUNDS,420000,560000,3
            Apex Legends,310000,480000,4
            GTA V,210000,360000,5
            Team Fortress 2,190000,250000,6
            ARK: Survival Ascended,175000,220000,7
            Cyberpunk 2077,165000,230000,8
            Rust,150000,210000,9
            War Thunder,135000,200000,10
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

    public GameRankingReportService(ChartGenerateUtil chartGenerateUtil) {
        this.chartGenerateUtil = chartGenerateUtil;
        this.gameRankingTool = new GameRankingTool();
    }

    /**
     * 生成最新的游戏榜单报表集合
     */
    public List<GameRankingReport> generateLatestReports() {
        List<GameRankingReport> reports = new ArrayList<>();
        GameRankingReport steamReport = generateSteamTopGamesReport();
        if (steamReport != null) {
            reports.add(steamReport);
        }
        GameRankingReport tapTapReport = generateTapTapBoardReport();
        if (tapTapReport != null) {
            reports.add(tapTapReport);
        }
        GameRankingReport threeDmReport = generate3DMHotGamesReport();
        if (threeDmReport != null) {
            reports.add(threeDmReport);
        }
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

            StringBuilder csvBuilder = new StringBuilder("游戏名称,当前玩家数,峰值玩家数,排名\n");
            for (JsonNode gameNode : gamesNode) {
                String name = gameNode.path("name").asText("");
                long currentPlayers = parseNumber(gameNode.path("currentPlayers").asText(""));
                long peakPlayers = parseNumber(gameNode.path("peakPlayers").asText(""));
                String rank = gameNode.path("rank").asText("");

                if (StringUtils.isAnyBlank(name, rank) || currentPlayers <= 0 || peakPlayers <= 0) {
                    continue;
                }
                csvBuilder.append(name).append(',')
                        .append(currentPlayers).append(',')
                        .append(peakPlayers).append(',')
                        .append(rank).append('\n');
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
        String goal = "分析Steam平台前十热门游戏的实时在线与峰值玩家表现";
        String chartType = "bar";
        ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(csvData, goal, chartType);
        Map<String, Object> chartConfig = parseChartConfig(response.genChart());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceUrl", "https://store.steampowered.com/stats/");
        metadata.put("fallback", isFallback);
        metadata.put("sampleCount", csvData.trim().split("\\r?\\n").length - 1);
        return new GameRankingReport(
                UUID.randomUUID().toString(),
                "Steam",
                "Steam热门游戏在线玩家分析",
                goal,
                chartType,
                chartConfig,
                response.genResult(),
                csvData,
                nowIso(),
                metadata
        );
    }

    /**
     * 生成TapTap榜单评分报表
     */
    public GameRankingReport generateTapTapBoardReport() {
        String json = null;
        try {
            json = gameRankingTool.fetchTapTapTopBoards();
            if (StringUtils.isBlank(json) || json.startsWith("错误")) {
                log.warn("获取TapTap榜单数据失败，将使用默认数据。返回内容：{}", json);
                return buildTapTapReport(DEFAULT_TAPTAP_DATA, true);
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode gamesNode = root.path("games");
            if (!gamesNode.isArray() || gamesNode.isEmpty()) {
                log.warn("TapTap榜单数据为空，将使用默认数据。");
                return buildTapTapReport(DEFAULT_TAPTAP_DATA, true);
            }

            Map<String, Integer> boardCount = new HashMap<>();
            StringBuilder csvBuilder = new StringBuilder("榜单,排名,游戏名称,评分\n");
            for (JsonNode gameNode : gamesNode) {
                String board = gameNode.path("board").asText("");
                String rank = gameNode.path("rank").asText("");
                String name = gameNode.path("name").asText("");
                double score = parseDouble(gameNode.path("score").asText(""));

                if (StringUtils.isAnyBlank(board, rank, name) || score <= 0) {
                    continue;
                }

                // 每个榜单最多取前5名
                int count = boardCount.getOrDefault(board, 0);
                if (count >= 5) {
                    continue;
                }
                boardCount.put(board, count + 1);

                csvBuilder.append(board).append(',')
                        .append(rank).append(',')
                        .append(name).append(',')
                        .append(score).append('\n');
            }

            String csvData = csvBuilder.toString();
            if (csvData.trim().split("\n").length <= 1) {
                log.warn("TapTap榜单解析结果为空，将使用默认数据。");
                return buildTapTapReport(DEFAULT_TAPTAP_DATA, true);
            }
            return buildTapTapReport(csvData, false);
        } catch (Exception e) {
            log.error("生成TapTap榜单报表失败，将使用默认数据。", e);
            return buildTapTapReport(DEFAULT_TAPTAP_DATA, true);
        }
    }

    private GameRankingReport buildTapTapReport(String csvData, boolean isFallback) {
        String goal = "比较TapTap各榜单头部游戏的评分表现";
        String chartType = "bar";
        ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(csvData, goal, chartType);
        Map<String, Object> chartConfig = parseChartConfig(response.genChart());
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
                response.genResult(),
                csvData,
                nowIso(),
                metadata
        );
    }

    /**
     * 生成3DM热门单机游戏榜单报表
     */
    public GameRankingReport generate3DMHotGamesReport() {
        String json = null;
        try {
            json = gameRankingTool.fetch3DMGameRanking();
            if (StringUtils.isBlank(json) || json.startsWith("错误")) {
                log.warn("获取3DM榜单数据失败，将使用默认数据。返回内容：{}", json);
                return build3DMReport(DEFAULT_3DM_DATA, true);
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode gamesNode = root.path("games");
            if (!gamesNode.isArray() || gamesNode.isEmpty()) {
                log.warn("3DM榜单数据为空，将使用默认数据。");
                return build3DMReport(DEFAULT_3DM_DATA, true);
            }

            StringBuilder csvBuilder = new StringBuilder("排名,游戏名称,类型,平台,发售日期\n");
            int rank = 1;
            for (JsonNode gameNode : gamesNode) {
                String name = gameNode.path("name").asText("");
                String type = gameNode.path("type").asText("");
                String platform = gameNode.path("platform").asText("");
                String releaseDate = gameNode.path("releaseDate").asText("");

                if (StringUtils.isBlank(name)) {
                    continue;
                }

                csvBuilder.append(rank).append(',')
                        .append(name).append(',')
                        .append(StringUtils.defaultString(type)).append(',')
                        .append(StringUtils.defaultString(platform)).append(',')
                        .append(StringUtils.defaultString(releaseDate))
                        .append('\n');
                rank++;

                if (rank > 15) {
                    break;
                }
            }

            String csvData = csvBuilder.toString();
            if (csvData.trim().split("\\r?\\n").length <= 1) {
                log.warn("3DM榜单解析结果为空，将使用默认数据。");
                return build3DMReport(DEFAULT_3DM_DATA, true);
            }
            return build3DMReport(csvData, false);
        } catch (Exception e) {
            log.error("生成3DM榜单报表失败，将使用默认数据。", e);
            return build3DMReport(DEFAULT_3DM_DATA, true);
        }
    }

    private GameRankingReport build3DMReport(String csvData, boolean isFallback) {
        String goal = "洞察3DM热门单机游戏的题材与平台分布";
        String chartType = "bar";
        ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(csvData, goal, chartType);
        Map<String, Object> chartConfig = parseChartConfig(response.genChart());
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
                response.genResult(),
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

