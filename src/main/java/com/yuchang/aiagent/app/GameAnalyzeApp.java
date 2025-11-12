package com.yuchang.aiagent.app;

import com.yuchang.aiagent.advisor.MyLoggerAdvisor;
import com.yuchang.aiagent.rag.QueryRewriter;
import com.yuchang.aiagent.tools.GameRankingTool;
import com.yuchang.aiagent.tools.WebSearchTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class GameAnalyzeApp {

    private final ChatClient chatClient;

    @Value("${search-api.api-key}")
    private String searchApiKey;

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    private static final String SYSTEM_PROMPT = "你是一名专业的游戏行业数据分析师，擅长从多维度解读市场变化。请严格遵守以下规则：\n" +
            "\n" +
            "# 角色职责\n" +
            "- 主要目标：基于用户查询，检索最新游戏榜单、流量、收入数据，并进行可视化比较（如增长率、份额变化）。\n" +
            "- 限制：仅回答与游戏行业相关的问题，若数据不足需明确说明来源局限性。\n" +
            "- 工具调用：你应当灵活使用工具调用，当工具返回结果为空时，应当继续使用Search工具" +
            "\n" +
            "# 数据处理原则\n" +
            "1. **指标标准化**：统一将收入转换为万美元、下载量转换为万次，日期格式为YYYY-MM-DD。\n" +
            "2. **比较逻辑**：计算环比增长率（(本期-上期)/上期）、市场份额（单个游戏收入/总收入）。\n" +
            "3. **洞察生成**：突出异常值（如排名跃升>5位）、趋势变化（连续增长/下降）。";

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
     * AI 恋爱报告功能（支持调用工具）
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
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
//                .toolCallbacks(allTools)
                .tools(new GameRankingTool(), new WebSearchTool(searchApiKey))
                .stream()
                .content();
    }


}
