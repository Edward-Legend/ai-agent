package com.yuchang.aiagent.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuchang.aiagent.advisor.MyLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 图表生成工具类
 * 用于基于数据生成可视化图表和分析结论
 */
@Component
@Slf4j
public class ChartGenerateUtil {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 构建系统提示词
    private static final String CHARTS_SYSTEM_PROMPT = "你是一个数据分析师，接下来我会提供原始数据，请根据用户需求生成对应的图表配置和分析结果。\n" +
            "\n" +
            "## 注意事项\n" +
            "1. 仅根据提供的数据进行分析，不要添加额外的数据。\n" +
            "2. 严格按照指定的JSON格式输出，不要输出任何格式说明和解释。\n" +
            "3. 分析结果要简洁明了，不超过300字。\n" +
            "4. 请使用ECharts的配置格式，确保返回的JSON可以直接用于ECharts。\n" +
            "5. 图表标题、坐标轴、图例等都要设置中文。\n" +
            "6. 请生成一个默认有良好视觉效果的主题。\n" +
            "\n" +
            "## 输出格式\n" +
            "```json\n" +
            "{\n" +
            "  \"chart\": {ECharts配置对象},\n" +
            "  \"result\": \"分析结果字符串\"\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "请确保输出是一个有效的JSON，并且只包含chart和result两个字段。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public ChartGenerateUtil(ChatModel dashscopeChatModel) {

        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(CHARTS_SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * 生成图表
     *
     * @param data      原始数据（CSV格式）
     * @param goal      分析目标
     * @param chartType 图表类型
     * @return 包含图表配置和分析结果的响应
     */
    public BiResponse generateChart(String data, String goal, String chartType) {
        String userPrompt = buildUserPrompt(goal, chartType, data);

        if (chatClient != null) {
            try {
                ChatResponse chatResponse = chatClient
                        .prompt()
                        .system(CHARTS_SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .chatResponse();
                String content = chatResponse.getResult().getOutput().getText();
                return parseAIResponse(content);
            } catch (Exception e) {
                log.error("调用AI生成图表失败，将使用默认图表逻辑。", e);
            }
        } else {
            log.info("没有可用的ChatClient，使用默认图表生成逻辑。");
        }

        return buildFallbackChart(data, goal, chartType);
    }

    /**
     * 解析AI返回结果
     */
    private BiResponse parseAIResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.has("chart") || !root.has("result")) {
                throw new IllegalArgumentException("AI返回结果缺少chart或result字段");
            }
            String chartConfig = objectMapper.writeValueAsString(root.get("chart"));
            String analysisResult = root.get("result").asText();
            return new BiResponse(chartConfig, analysisResult);
        } catch (Exception e) {
            throw new RuntimeException("解析AI响应失败", e);
        }
    }

    /**
     * 构建用户提示
     */
    private String buildUserPrompt(String goal, String chartType, String data) {
        StringBuilder prompt = new StringBuilder("分析需求：\n");
        prompt.append(StringUtils.defaultIfBlank(goal, "请提供数据分析结论。"));
        if (StringUtils.isNotBlank(chartType)) {
            prompt.append(" 请使用").append(chartType).append("图表。");
        }
        prompt.append("\n\n原始数据:\n").append(data);
        return prompt.toString();
    }

    /**
     * 当AI不可用或解析失败时，提供基础图表
     */
    private BiResponse buildFallbackChart(String data, String goal, String chartType) {
        try {
            List<String[]> rows = parseCsv(data);
            if (rows.size() <= 1) {
                return defaultChart(goal);
            }

            String[] headers = rows.get(0);
            int categoryIndex = 0;
            int valueIndex = findNumericColumnIndex(rows, headers);

            List<String> categories = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length == 0) {
                    continue;
                }
                categories.add(row.length > categoryIndex ? row[categoryIndex] : "记录" + i);
                double value = 1D;
                if (valueIndex != -1 && row.length > valueIndex) {
                    value = parseDouble(row[valueIndex], 1D);
                } else if (row.length > categoryIndex && NumberUtils.isParsable(row[categoryIndex])) {
                    value = parseDouble(row[categoryIndex], 1D);
                }
                values.add(value);
            }

            String seriesName = (valueIndex != -1 && valueIndex < headers.length) ? headers[valueIndex] : "统计值";
            String resolvedChartType = StringUtils.defaultIfBlank(chartType, "bar");

            Map<String, Object> chartConfig = new LinkedHashMap<>();
            chartConfig.put("title", Map.of("text", StringUtils.defaultIfBlank(goal, "数据分析结果")));
            chartConfig.put("tooltip", new HashMap<>());
            chartConfig.put("xAxis", Map.of("type", "category", "data", categories));
            chartConfig.put("yAxis", Map.of("type", "value"));

            Map<String, Object> series = new LinkedHashMap<>();
            series.put("name", seriesName);
            series.put("type", resolvedChartType);
            series.put("data", values);
            chartConfig.put("series", List.of(series));

            String chartJson = objectMapper.writeValueAsString(chartConfig);
            String analysisResult = buildFallbackAnalysisResult(goal, categories, values, seriesName);
            return new BiResponse(chartJson, analysisResult);
        } catch (Exception e) {
            log.error("构建默认图表失败，将返回基础模板。", e);
            return defaultChart(goal);
        }
    }

    private String buildFallbackAnalysisResult(String goal, List<String> categories, List<Double> values, String seriesName) {
        if (categories.isEmpty() || values.isEmpty()) {
            return "未从数据中解析出有效记录，请检查数据格式。";
        }
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        int maxIndex = values.indexOf(max);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        return String.format(
                "%s共解析出%d条记录，%s在%s中最高（%.2f），平均值为%.2f。",
                StringUtils.defaultIfBlank(goal, "根据提供的数据"),
                categories.size(),
                categories.get(maxIndex),
                seriesName,
                max,
                avg
        );
    }

    private int findNumericColumnIndex(List<String[]> rows, String[] headers) {
        for (int col = 1; col < headers.length; col++) {
            boolean numeric = true;
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                String[] row = rows.get(rowIndex);
                if (row.length <= col) {
                    continue;
                }
                String value = row[col];
                if (!NumberUtils.isParsable(value)) {
                    numeric = false;
                    break;
                }
            }
            if (numeric) {
                return col;
            }
        }
        return -1;
    }

    private double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private List<String[]> parseCsv(String data) {
        List<String[]> rows = new ArrayList<>();
        if (StringUtils.isBlank(data)) {
            return rows;
        }
        String[] lines = data.split("\\r?\\n");
        for (String line : lines) {
            if (StringUtils.isBlank(line)) {
                continue;
            }
            rows.add(line.split("\\s*,\\s*"));
        }
        return rows;
    }

    private BiResponse defaultChart(String goal) {
        Map<String, Object> chartConfig = new LinkedHashMap<>();
        chartConfig.put("title", Map.of("text", StringUtils.defaultIfBlank(goal, "数据分析结果")));
        chartConfig.put("tooltip", new HashMap<>());
        chartConfig.put("series", List.of(Map.of(
                "name", "默认系列",
                "type", "bar",
                "data", List.of(1, 2, 3, 4, 5)
        )));
        try {
            String chartJson = objectMapper.writeValueAsString(chartConfig);
            return new BiResponse(chartJson, "AI 服务不可用，已返回基础示例图表。");
        } catch (Exception e) {
            throw new RuntimeException("构建默认图表失败", e);
        }
    }

    /**
     * Excel文件转CSV
     * @param multipartFile Excel文件
     * @return CSV格式的数据
     */
    public String excelToCsv(MultipartFile multipartFile) {
        StringBuilder csvContent = new StringBuilder();

        try (InputStream inputStream = multipartFile.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            // 获取第一个sheet
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null) {
                throw new RuntimeException("Excel文件中没有找到工作表");
            }

            boolean headerProcessed = false;
            List<String> headers = new ArrayList<>();

            // 遍历所有行
            for (Row row : sheet) {
                if (row == null || row.getLastCellNum() <= 0) {
                    continue;
                }

                StringBuilder line = new StringBuilder();

                // 处理表头
                if (!headerProcessed) {
                    headerProcessed = true;
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i);
                        String cellValue = getCellValueAsString(cell);
                        if (StringUtils.isNotEmpty(cellValue)) {
                            headers.add(cellValue);
                            if (line.length() > 0) {
                                line.append(",");
                            }
                            line.append(cellValue);
                        }
                    }
                } else {
                    // 处理数据行
                    for (int i = 0; i < headers.size(); i++) {
                        Cell cell = row.getCell(i);
                        if (line.length() > 0) {
                            line.append(",");
                        }
                        String cellValue = getCellValueAsString(cell);
                        line.append(cellValue);
                    }
                }

                if (line.length() > 0) {
                    csvContent.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            log.error("表格处理错误", e);
            throw new RuntimeException("表格处理错误", e);
        }

        return csvContent.toString();
    }

    /**
     * 获取单元格值并转换为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 处理数字和日期
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 避免科学计数法
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }

    /**
     * 校验文件
     * @param multipartFile 上传的文件
     */
    public void validateFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();

        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        if (size > ONE_MB) {
            throw new RuntimeException("文件超过 1M");
        }

        // 校验文件后缀，使用Java标准库实现
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new RuntimeException("文件后缀非法，仅支持.xlsx文件");
        }
    }

    /**
     * 图表生成响应类
     */
    public record BiResponse(String genChart, String genResult) {
    }
}