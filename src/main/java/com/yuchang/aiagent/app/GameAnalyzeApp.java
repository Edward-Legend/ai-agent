package com.yuchang.aiagent.app;

import com.alibaba.cloud.ai.toolcalling.crawler.CrawlerService;
import com.yuchang.aiagent.advisor.MyLoggerAdvisor;
import com.yuchang.aiagent.util.ChartGenerateUtil;
import com.yuchang.aiagent.tools.GameRankingTool;
import com.yuchang.aiagent.tools.WebSearchTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class GameAnalyzeApp {

    private final ChatClient chatClient;

    @Autowired
    @Qualifier("jinaFunction")
    private CrawlerService crawlerService;

    @Value("${search-api.api-key}")
    private String searchApiKey;

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChartGenerateUtil chartGenerateUtil;

    @Resource
    private GameRankingTool gameRankingTool;

    private static final String SYSTEM_PROMPT = "你是一名专业的游戏行业数据分析师，擅长从多维度解读市场变化。请严格遵守以下规则：\n" +
            "\n" +
            "# 角色职责\n" +
            "- 主要目标：基于用户查询，检索最新游戏榜单、流量、收入数据，并进行可视化比较（如增长率、份额变化）。\n" +
            "- 限制：仅回答与游戏行业相关的问题\n" +
            "- 工具调用：你应当灵活使用工具调用，当工具返回结果为空时，应当继续使用Search工具。\n" +
            "- 图表生成：当用户需要对游戏数据进行可视化分析时，你可以将数据整理为CSV格式并使用图表生成工具创建可视化报表。\n" +
            "\n" +
            "# 数据处理原则\n" +
            "1. **指标标准化**：统一将收入转换为万美元、下载量转换为万次，日期格式为YYYY-MM-DD。\n" +
            "2. **比较逻辑**：计算环比增长率（(本期-上期)/上期）、市场份额（单个游戏收入/总收入）。\n" +
            "3. **洞察生成**：突出异常值（如排名跃升>5位）、趋势变化（连续增长/下降）。\n" +
            "4. **数据可视化**：根据数据特点选择合适的图表类型，如折线图展示趋势、柱状图比较大小、饼图展示占比等。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public GameAnalyzeApp(ChatModel dashscopeChatModel) {
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
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
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    record RecommendationeReport(String title, List<String> suggestions) {

    }

    /**
     * 游戏分析报告，包含图表和分析结果
     */
    public record GameAnalysisReport(String title, String chartConfig, String analysisResult, String chartType) {
    }

    /**
     * AI 报告功能（结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public RecommendationeReport doChatWithReport(String message, String chatId) {
        RecommendationeReport recommendationeReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要推荐岗位，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(RecommendationeReport.class);
        log.info("loveReport: {}", recommendationeReport);
        return recommendationeReport;
    }

    /**
     * 生成游戏数据分析报表
     * @param gameData CSV格式的游戏数据
     * @param analysisGoal 分析目标
     * @param chartType 图表类型（可选）
     * @return 游戏分析报告
     */
    public GameAnalysisReport generateGameAnalysisReport(String gameData, String analysisGoal, String chartType) {
        try {
            // 使用图表生成工具生成图表和分析结果
            ChartGenerateUtil.BiResponse response = chartGenerateUtil.generateChart(gameData, analysisGoal, chartType);

            // 构造分析报告
            String title = "游戏行业数据分析报告 - " + analysisGoal;
            return new GameAnalysisReport(title, response.genChart(), response.genResult(), chartType != null ? chartType : "自动选择");
        } catch (Exception e) {
            log.error("生成游戏分析报告失败", e);
            throw new RuntimeException("生成游戏分析报告失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从对话中分析游戏数据并生成报表
     * @param message 用户消息
     * @param chatId 对话ID
     * @return 游戏分析报告
     */
    public GameAnalysisReport doChatWithGameAnalysisReport(String message, String chatId) {
        // 首先通过对话提取数据和分析需求
        String enhancedSystemPrompt = SYSTEM_PROMPT + "\n当用户询问关于游戏数据分析的问题时，请将数据整理为CSV格式，并明确分析目标和合适的图表类型。";
        
        String extractedData = chatClient
                .prompt()
                .system(enhancedSystemPrompt)
                .user("请从以下问题中提取需要分析的数据(CSV格式)、分析目标和建议的图表类型:\n" + message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
        
        log.info("提取的游戏数据和分析需求: {}", extractedData);
        
        // 解析提取的数据
        // 这里简单解析，实际应用中可能需要更复杂的解析逻辑
        String gameData = extractSection(extractedData, "CSV数据:", "分析目标:");
        String analysisGoal = extractSection(extractedData, "分析目标:", "图表类型:");
        String chartType = extractSection(extractedData, "图表类型:", "");
        
        if (gameData == null || analysisGoal == null) {
            throw new RuntimeException("无法从对话中提取有效的游戏数据和分析目标");
        }
        
        // 生成分析报告
        return generateGameAnalysisReport(gameData, analysisGoal, chartType);
    }
    
    /**
     * 从文本中提取特定部分
     */
    private String extractSection(String text, String startMarker, String endMarker) {
        int startIndex = text.indexOf(startMarker);
        if (startIndex == -1) {
            return null;
        }
        startIndex += startMarker.length();
        
        int endIndex = -1;
        if (!endMarker.isEmpty()) {
            endIndex = text.indexOf(endMarker, startIndex);
            if (endIndex == -1) {
                return null;
            }
        }
        
        return endIndex == -1 ? text.substring(startIndex).trim() : text.substring(startIndex, endIndex).trim();
    }



    /**
     * AI 调用工具
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithTools(String message, String chatId) {
//        ChatResponse chatResponse = chatClient
//                .prompt()
//                .user(message)
//                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
//                // 开启日志，便于观察效果
//                .advisors(new MyLoggerAdvisor())
//                .toolCallbacks(allTools)
//                .call()
//                .chatResponse();
//        String content = chatResponse.getResult().getOutput().getText();
//        log.info("content: {}", content);
//        return content;

        return chatClient
                .prompt()
                .user(message)
//                .toolNames("crawler", "baiduSearch")
//                .toolNames("jinaFunction")  // jina爬虫工具,很坑,不知道名字
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))

//                .toolCallbacks(allTools)
                .tools(gameRankingTool, new WebSearchTool(searchApiKey))
//                .tools(crawlerService)

                .stream()
                .content();
    }


}
