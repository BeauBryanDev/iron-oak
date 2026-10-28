package com.ironoak.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * LLM-as-judge for the generation metrics: Claude Haiku over the Anthropic
 * Messages API at temperature 0, plus OpenAI embeddings for answer relevancy.
 * Every response is cached under eval/cache/ by a hash of its request, so
 * scoring the same raw.json twice gives the same numbers and costs nothing.
 */
final class HaikuJudge {

    static final String DEFAULT_MODEL = "claude-haiku-5-5";
    static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private static final Path CACHE = Path.of("eval", "cache");
    private static final int PASSAGE_CHARS = 2500;

    /** What the judge decided for one case. */
    record Verdict(List<Boolean> claims, List<Boolean> facts, List<String> questions) {
    }

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String anthropicKey;
    private final String openAiKey;
    private final String model;

    HaikuJudge(ObjectMapper json,
            String anthropicKey,
            String openAiKey,
            String model) {

        this.json = json;
        this.anthropicKey = anthropicKey;
        this.openAiKey = openAiKey;
        this.model = model;
    }

    /** null when the Anthropic or OpenAI key is missing: judge metrics then stay pending. */
    static HaikuJudge fromEnv(ObjectMapper json) {

        String anthropic = EvalEnv.get("ANTHROPIC_API_KEY");
        String openAi = EvalEnv.get("OPENAI_API_KEY");
        if (anthropic == null || openAi == null) {
            return null;
        }
        String model = System.getProperty("piper.eval.judge-model", DEFAULT_MODEL);
        return new HaikuJudge(json, anthropic, openAi, model);
    }

    String model() {
        return model;
    }

    Verdict judge(String question,
            String answer,
            List<RawRun.Passage> contexts,
            String referenceAnswer,
            List<List<String>> keyFacts) throws IOException, InterruptedException {

        StringBuilder prompt = new StringBuilder();
        prompt.append("QUESTION:\n").append(question).append("\n\n");
        prompt.append("CONTEXT PASSAGES (what the assistant retrieved):\n");
        if (contexts.isEmpty()) {
            prompt.append("(none: the assistant retrieved nothing)\n");
        }
        for (int i = 0; i < contexts.size(); i++) {
            RawRun.Passage p = contexts.get(i);
            String text = p.text().length() > PASSAGE_CHARS ? p.text().substring(0, PASSAGE_CHARS) : p.text();
            prompt.append("[").append(i + 1).append("] ").append(text).append("\n\n");
        }
        prompt.append("ASSISTANT ANSWER:\n").append(answer).append("\n\n");
        prompt.append("REFERENCE ANSWER:\n").append(referenceAnswer).append("\n\n");
        prompt.append("KEY FACTS:\n");
        for (int i = 0; i < keyFacts.size(); i++) {
            prompt.append(i + 1).append(". ").append(String.join(" / ", keyFacts.get(i))).append("\n");
        }
        prompt.append("""

                Tasks:
                1. "claims": split the ASSISTANT ANSWER into atomic factual claims. Skip greetings, offers of \
                help and questions back to the customer. For each claim set "supported" to true only if the \
                CONTEXT PASSAGES state it or directly imply it; otherwise false.
                2. "facts": for each KEY FACT, in order, true if the ASSISTANT ANSWER states it (same meaning; \
                any wording or units), otherwise false. Use the REFERENCE ANSWER to understand each fact.
                3. "questions": write 3 different questions for which the ASSISTANT ANSWER would be a good answer.
                Reply with JSON only, exactly this shape:
                {"claims":[{"claim":"...","supported":true}],"facts":[true],"questions":["...","...","..."]}""");

        JsonNode result = parseJsonObject(askClaude(prompt.toString()));
        List<Boolean> claims = new ArrayList<>();
        result.path("claims").forEach(c -> claims.add(c.path("supported").asBoolean(false)));
        List<Boolean> facts = new ArrayList<>();
        result.path("facts").forEach(f -> facts.add(f.asBoolean(false)));
        List<String> questions = new ArrayList<>();
        result.path("questions").forEach(q -> questions.add(q.asText()));
        return new Verdict(claims, facts, questions);
    }

    /** Mean cosine similarity between the question and the questions generated from the answer. */
    Double answerRelevancy(String question, List<String> generated) throws IOException, InterruptedException {

        if (generated.isEmpty()) {
            return null;
        }
        List<String> texts = new ArrayList<>();
        texts.add(question);
        texts.addAll(generated);
        List<double[]> vectors = embed(texts);
        double sum = 0.0;
        for (int i = 1; i < vectors.size(); i++) {
            sum += cosine(vectors.get(0), vectors.get(i));
        }
        return sum / (vectors.size() - 1);
    }

    private String askClaude(String prompt) throws IOException, InterruptedException {

        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 2000);
        body.put("temperature", 0);
        body.put("system", "You are a strict, consistent evaluator of a retrieval-augmented shop assistant. "
                + "Reply with JSON only.");
        body.putArray("messages").addObject().put("role", "user").put("content", prompt);
        String request = json.writeValueAsString(body);

        Path cached = CACHE.resolve("judge").resolve(sha256(request) + ".json");
        if (Files.exists(cached)) {
            return json.readTree(cached.toFile()).path("text").asText();
        }
        HttpRequest call = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("x-api-key", anthropicKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request))
                .build();
        JsonNode response = send(call);
        String text = response.path("content").path(0).path("text").asText();
        write(cached, json.createObjectNode().put("model", model).put("text", text));
        return text;
    }

    private List<double[]> embed(List<String> texts) throws IOException, InterruptedException {

        ObjectNode body = json.createObjectNode();
        body.put("model", EMBEDDING_MODEL);
        texts.forEach(body.putArray("input")::add);
        String request = json.writeValueAsString(body);

        Path cached = CACHE.resolve("embeddings").resolve(sha256(request) + ".json");
        JsonNode response;
        if (Files.exists(cached)) {
            response = json.readTree(cached.toFile());
        } else {
            HttpRequest call = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request))
                    .build();
            response = send(call);
            write(cached, response);
        }
        List<double[]> vectors = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            double[] vector = new double[item.path("embedding").size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = item.path("embedding").get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private JsonNode send(HttpRequest call) throws IOException, InterruptedException {

        HttpResponse<String> response = http.send(call, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " from " + call.uri() + ": " + response.body());
        }
        return json.readTree(response.body());
    }

    private JsonNode parseJsonObject(String text) throws IOException {

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IOException("The judge did not return JSON: " + text);
        }
        return json.readTree(text.substring(start, end + 1));
    }

    private void write(Path file, JsonNode value) throws IOException {
        Files.createDirectories(file.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }

    private static double cosine(double[] a, double[] b) {

        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0.0 : dot / Math.sqrt(na * nb);
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
