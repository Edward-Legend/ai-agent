package com.yuchang.aiagent.model;

import java.time.Instant;
import java.util.Map;

/**
 * 游戏行业榜单分析报告
 */
public record GameRankingReport(
        String id,
        String source,
        String title,
        String goal,
        String chartType,
        Map<String, Object> chartConfig,
        String analysisResult,
        String rawData,
        String generatedAt,
        Map<String, Object> metadata
) {
}

