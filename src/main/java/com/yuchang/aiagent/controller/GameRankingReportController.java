package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.model.GameRankingReport;
import com.yuchang.aiagent.service.GameRankingReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

