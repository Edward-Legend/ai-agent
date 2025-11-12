package com.yuchang.aiagent.controller;

import com.yuchang.aiagent.util.ChartGenerateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 游戏图表生成Controller
 * 处理游戏数据的图表生成请求
 */
@RestController
@RequestMapping("/ai/game-ranking/chart")
public class GameChartController {

    private final ChartGenerateUtil chartGenerateUtil;

    @Autowired
    public GameChartController(ChartGenerateUtil chartGenerateUtil) {
        this.chartGenerateUtil = chartGenerateUtil;
    }

    /**
     * 生成游戏数据图表
     * @param file 上传的Excel文件
     * @param goal 分析目标
     * @param chartType 图表类型（可选）
     * @return 图表配置和分析结果
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateChart(
            @RequestParam("file") MultipartFile file,
            @RequestParam("goal") String goal,
            @RequestParam(value = "chartType", required = false) String chartType) {
        try {
            // 校验文件
            chartGenerateUtil.validateFile(file);

            // 转换Excel为CSV
            String csvData = chartGenerateUtil.excelToCsv(file);

            // 生成图表，修正参数顺序
            ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(csvData, goal, chartType);
            // 使用正确的方法名
            return ResponseEntity.ok(Map.of("chart", response.genChart(), "result", response.genResult()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("生成图表失败: " + e.getMessage());
        }
    }

    /**
     * 基于游戏榜单数据生成图表
     * 此接口用于直接基于已有数据生成图表，无需文件上传
     * @param data CSV格式的游戏数据
     * @param goal 分析目标
     * @param chartType 图表类型（可选）
     * @return 图表配置和分析结果
     */
    @PostMapping("/generate-from-data")
    public ResponseEntity<?> generateChartFromData(
            @RequestParam("data") String data,
            @RequestParam("goal") String goal,
            @RequestParam(value = "chartType", required = false) String chartType) {
        try {
            if (data == null || data.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("数据不能为空");
            }

            if (goal == null || goal.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("分析目标不能为空");
            }

            // 生成图表，修正参数顺序
            ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(data, goal, chartType);

            // 使用正确的方法名
            return ResponseEntity.ok(Map.of("chart", response.genChart(), "result", response.genResult()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("生成图表失败: " + e.getMessage());
        }
    }
}