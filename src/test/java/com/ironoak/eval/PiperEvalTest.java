package com.ironoak.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.TestOrders;
import com.ironoak.agent.PiperAgentConfig;
import com.ironoak.agent.PiperAssistant;
import com.ironoak.domain.enums.KnowledgeNamespace;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.dto.request.CreateOrderRequest.Item;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.repository.ProductRepository;
import com.ironoak.services.ChatOrchestrationService;
import com.ironoak.services.KnowledgeSearchService;
import com.ironoak.services.OrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * The Piper agent eval: runs every golden case against the real chat model,
 * Zilliz and a Testcontainers database, records the tool calls and the passages
 * Piper retrieved, runs the retriever alone on each RAG question, then scores.
 *
 * Skipped unless -Dpiper.eval=true. Run:
 *   ./mvnw test -Dtest=PiperEvalTest -Dpiper.eval=true -Dpiper.eval.label=baseline
 * Optional: -Dpiper.eval.cases=rag-mach-01,tool-order-01 (subset),
 * -Dpiper.eval.temperature / -Dpiper.eval.seed, -Dpiper.eval.judge-model.
 * Output: eval/results/{label}/raw.json, metrics.json, report.md.
 */
@Tag("eval")
@EnabledIfSystemProperty(named = "piper.eval", matches = "true")
@SpringBootTest(properties = {
        "spring.ai.openai.chat.options.temperature=${piper.eval.temperature:0}",
        "spring.ai.openai.chat.options.seed=${piper.eval.seed:42}"
})
@Testcontainers
class PiperEvalTest {

    private static final int RETRIEVER_TOP_K = 10;
    private static final String BUYER_EMAIL = "eval.buyer@example.com";
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Bogota");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoSpyBean
    private KnowledgeSearchService knowledge;
    @Autowired
    private ToolCallbackProvider piperTools;
    @Autowired
    private ChatClient.Builder chatClientBuilder;
    @Autowired
    private PiperAgentConfig promptConfig;
    @Autowired
    private ChatOrchestrationService chats;
    @Autowired
    private OrderService orders;
    @Autowired
    private ProductRepository products;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String chatModel;
    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;
    @Value("${spring.ai.openai.chat.options.temperature:#{null}}")
    private Double temperature;
    @Value("${spring.ai.openai.chat.options.seed:#{null}}")
    private Integer seed;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runGoldenSet() throws Exception {

        String label = System.getProperty("piper.eval.label", "baseline");
        Map<String, String> placeholders = fixtures();
        GoldenSet golden = GoldenSet.load(json, placeholders);
        Set<String> only = selectedCases();

        List<RawRun.ToolCall> toolLog = new CopyOnWriteArrayList<>();
        List<RawRun.Search> searchLog = new CopyOnWriteArrayList<>();
        AtomicBoolean recording = new AtomicBoolean(false);
        recordSearches(searchLog, recording);

        ToolCallback[] tools = Arrays.stream(piperTools.getToolCallbacks())
                .map(tool -> new RecordingToolCallback(tool, toolLog))
                .toArray(ToolCallback[]::new);
        ChatClient piper = chatClientBuilder
                .defaultSystem(promptConfig.systemPrompt())
                .defaultToolCallbacks(tools)
                .build();

        String startedAt = OffsetDateTime.now().toString();
        List<RawRun.RawCase> cases = new ArrayList<>();
        for (GoldenSet.GoldenCase gc : golden.cases()) {
            if (!only.isEmpty() && !only.contains(gc.id())) {
                continue;
            }
            toolLog.clear();
            searchLog.clear();
            recording.set(true);
            Answer answer = ask(piper, gc);
            recording.set(false);

            List<RawRun.Passage> alone = gc.isRag()
                    ? passages(knowledge.searchDocuments(gc.question(),
                            List.of(KnowledgeNamespace.values()),
                            RETRIEVER_TOP_K))
                    : List.of();
            cases.add(new RawRun.RawCase(gc.id(),
                    gc.category(),
                    gc.question(),
                    answer.text(),
                    answer.error(),
                    answer.latencyMs(),
                    answer.promptTokens(),
                    answer.completionTokens(),
                    List.copyOf(toolLog),
                    List.copyOf(searchLog),
                    alone));
        }

        RawRun raw = new RawRun(label,
                startedAt,
                EvalEnv.gitCommit(),
                chatModel,
                embeddingModel,
                knowledge.hybridEnabled() ? "hybrid (dense + BM25, RRF)" : "dense",
                temperature,
                seed,
                placeholders,
                cases);
        Path dir = Path.of("eval", "results", label);
        Files.createDirectories(dir);
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("raw.json").toFile(), raw);

        EvalScorer scorer = new EvalScorer(json, ChunkIndex.load(json), HaikuJudge.fromEnv(json));
        scorer.write(dir, raw, golden);

        assertThat(cases).isNotEmpty();
    }

    private record Answer(String text, String error, long latencyMs, Integer promptTokens, Integer completionTokens) {
    }

    /** One case, sent exactly the way PiperAssistant.reply sends a chat message. */
    private Answer ask(ChatClient piper, GoldenSet.GoldenCase gc) {

        List<Message> history = gc.history().stream()
                .map(turn -> "ASSISTANT".equals(turn.role())
                        ? (Message) new AssistantMessage(turn.content())
                        : new UserMessage(turn.content()))
                .toList();
        Long sessionId = chats.startSession().sessionId();
        long start = System.nanoTime();
        try {
            ChatResponse response = piper.prompt()
                    .messages(history)
                    .user(PiperAssistant.withCurrentTime(gc.question()))
                    .toolContext(Map.of(PiperAssistant.CHAT_SESSION_ID, sessionId))
                    .call()
                    .chatResponse();
            Usage usage = response.getMetadata().getUsage();
            return new Answer(response.getResult().getOutput().getText(),
                    null,
                    millisSince(start),
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens());
        } catch (RuntimeException e) {
            return new Answer(null, e.getClass().getSimpleName() + ": " + e.getMessage(), millisSince(start), null, null);
        }
    }

    /** A web order to look up, and the dates the golden set refers to. */
    private Map<String, String> fixtures() {

        Long productId = products.findBySku("IO-AICO-001").orElseThrow().getId();
        OrderResponse order = orders.create(TestOrders.web("Eval Buyer",
                        BUYER_EMAIL,
                        List.of(new Item(OrderItemType.PRODUCT, productId, 1, null))),
                OrderChannel.WEB_CHECKOUT);

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("ORDER_NUMBER", order.orderNumber());
        placeholders.put("ORDER_EMAIL", BUYER_EMAIL);
        placeholders.put("FUTURE_DATE", LocalDate.now(STORE_ZONE).plusDays(7).toString());
        return placeholders;
    }

    /** Every knowledge search while a case runs goes into the log, with its arguments. */
    @SuppressWarnings("unchecked")
    private void recordSearches(List<RawRun.Search> searchLog, AtomicBoolean recording) {

        doAnswer(invocation -> {
            List<Document> documents = (List<Document>) invocation.callRealMethod();
            if (recording.get()) {
                List<KnowledgeNamespace> namespaces = invocation.getArgument(1);
                searchLog.add(new RawRun.Search(invocation.getArgument(0),
                        namespaces.stream().map(KnowledgeNamespace::value).toList(),
                        invocation.getArgument(2),
                        passages(documents)));
            }
            return documents;
        }).when(knowledge).searchDocuments(anyString(), anyList(), anyInt());
    }

    private static List<RawRun.Passage> passages(List<Document> documents) {

        return documents.stream().map(doc -> {
            Map<String, Object> meta = doc.getMetadata();
            Object page = meta.get("page");
            return new RawRun.Passage(meta.get("source") + "#" + meta.get("chunk_index"),
                    doc.getId(),
                    String.valueOf(meta.get("namespace")),
                    String.valueOf(meta.get("doc_title")),
                    meta.get("section") == null ? null : meta.get("section").toString(),
                    page instanceof Number n ? n.intValue() : null,
                    doc.getScore() == null ? 0.0 : doc.getScore(),
                    doc.getText());
        }).toList();
    }

    private static Set<String> selectedCases() {
        String value = System.getProperty("piper.eval.cases", "");
        return value.isBlank() ? Set.of() : Set.of(value.split(","));
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
