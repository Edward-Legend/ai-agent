package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.app.GameAnalyzeApp;
import com.yuchang.aiagent.app.GameAnalyzeApp.GameAnalysisReport;
import com.yuchang.aiagent.util.ChartGenerateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 游戏数据图表整合测试控制器
 * 用于测试和验证游戏数据图表功能与AI对话功能的整合
 */
@RestController
@RequestMapping("/api/game-chart-integration")
@Slf4j
public class GameChartIntegrationController {

    private final GameAnalyzeApp gameAnalyzeApp;
    private final ChartGenerateUtil chartGenerateUtil;

    public GameChartIntegrationController(GameAnalyzeApp gameAnalyzeApp, ChartGenerateUtil chartGenerateUtil) {
        this.gameAnalyzeApp = gameAnalyzeApp;
        this.chartGenerateUtil = chartGenerateUtil;
    }

    /**
     * 测试基本的图表生成功能
     */
    @PostMapping("/test-chart-generation")
    public ResponseEntity<Map<String, Object>> testChartGeneration(@RequestBody Map<String, String> request) {
        try {
            String gameData = request.getOrDefault("gameData", 
                    "游戏名称,下载量,收入(万美元),排名\n" +
                    "王者荣耀,1000,500,1\n" +
                    "和平精英,800,400,2\n" +
                    "原神,700,350,3\n" +
                    "穿越火线,600,250,4\n" +
                    "第五人格,500,200,5");
            String analysisGoal = request.getOrDefault("analysisGoal", "分析游戏下载量和收入数据");
            String chartType = request.getOrDefault("chartType", "bar");

            ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(gameData, analysisGoal, chartType);
            
            Map<String, Object> result = new HashMap<>();
            result.put("chartConfig", response.genChart());
            result.put("analysisResult", response.genResult());
            result.put("success", true);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试图表生成失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 测试通过对话生成游戏分析报告
     */
    @PostMapping("/test-conversation-analysis")
    public ResponseEntity<Map<String, Object>> testConversationAnalysis(@RequestBody Map<String, String> request) {
        try {
            String userMessage = request.getOrDefault("message", "分析最近一周排名前五的游戏收入数据，生成柱状图");
            String chatId = UUID.randomUUID().toString();
            
            GameAnalysisReport report = gameAnalyzeApp.doChatWithGameAnalysisReport(userMessage, chatId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("title", report.title());
            result.put("chartConfig", report.chartConfig());
            result.put("analysisResult", report.analysisResult());
            result.put("chartType", report.chartType());
            result.put("success", true);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试对话分析失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 测试文件上传和图表生成功能
     */
    @PostMapping("/test-file-upload")
    public ResponseEntity<Map<String, Object>> testFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("analysisGoal") String analysisGoal,
            @RequestParam(value = "chartType", required = false) String chartType) {
        try {
            // 验证文件
            chartGenerateUtil.validateFile(file);
            
            // Excel转CSV
            String csvData = chartGenerateUtil.excelToCsv(file);
            
            // 生成图表
            ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(csvData, analysisGoal, chartType);
            
            Map<String, Object> result = new HashMap<>();
            result.put("chartConfig", response.genChart());
            result.put("analysisResult", response.genResult());
            result.put("success", true);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试文件上传失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 验证AI对话功能是否正常工作（确保原有功能未受影响）
     */
    @PostMapping("/validate-original-chat")
    public ResponseEntity<Map<String, Object>> validateOriginalChat(@RequestBody Map<String, String> request) {
        try {
            String userMessage = request.getOrDefault("message", "介绍一下当前最受欢迎的游戏类型");
            String chatId = UUID.randomUUID().toString();
            
            // 测试原有对话功能
            String response = gameAnalyzeApp.doChat(userMessage, chatId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("response", response);
            result.put("success", true);
            result.put("message", "原有AI对话功能验证成功");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("验证原有对话功能失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "原有AI对话功能验证失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}