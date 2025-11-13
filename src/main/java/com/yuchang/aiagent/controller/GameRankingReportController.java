package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.model.GameRankingReport;
import com.yuchang.aiagent.service.GameRankingReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import java.util.List;

/**
 * 游戏榜单报表接口
 */
@RestController
@RequestMapping("/ai/game-ranking/report")
public class GameRankingReportController {

    private final GameRankingReportService gameRankingReportService;

    public GameRankingReportController(GameRankingReportService gameRankingReportService) {
        this.gameRankingReportService = gameRankingReportService;
    }

    /**
     * 获取最新的游戏行业榜单报表列表
     */
    @GetMapping("/latest")
    public ResponseEntity<List<GameRankingReport>> latestReports() {
        return ResponseEntity.ok(gameRankingReportService.generateLatestReports());
    }

    /**
     * 获取Steam热门游戏榜单报表
     */
    @GetMapping("/steam")
    public ResponseEntity<GameRankingReport> steamReport() {
        return ResponseEntity.ok(gameRankingReportService.generateSteamTopGamesReport());
    }

    /**
     * 获取TapTap榜单评分报表
     */
    @GetMapping("/taptap")
    public ResponseEntity<GameRankingReport> tapTapReport() {
        return ResponseEntity.ok(gameRankingReportService.generateTapTapBoardReport());
    }

    @GetMapping("/3dm")
    public ResponseEntity<GameRankingReport> threeDmReport() {
        return ResponseEntity.ok(gameRankingReportService.generate3DMHotGamesReport());
    }
    
    /**
     * 获取分析结果（异步）
     * @param reportId 报告ID
     * @return 分析结果，如果还在生成中则返回null
     */
    @GetMapping("/analysis/{reportId}")
    public ResponseEntity<Map<String, Object>> getAnalysisResult(@PathVariable String reportId) {
        String analysisResult = gameRankingReportService.getAnalysisResult(reportId);
        Map<String, Object> response = new HashMap<>();
        if (analysisResult == null) {
            response.put("status", "pending");
            response.put("message", "分析结果还在生成中，请稍后再试");
        } else {
            response.put("status", "completed");
            response.put("analysisResult", analysisResult);
        }
        return ResponseEntity.ok(response);
    }
}

