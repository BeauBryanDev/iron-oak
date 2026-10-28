package com.ironoak.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-scores a saved run without calling Piper (no Spring, no database): use it
 * once ANTHROPIC_API_KEY is in .env to fill in the judge metrics, or after a
 * scoring change. Running it twice must give an identical metrics.json.
 *
 *   ./mvnw test -Dtest=JudgeRescoreTest -Dpiper.eval=true -Dpiper.eval.label=baseline
 */
@Tag("eval")
@EnabledIfSystemProperty(named = "piper.eval", matches = "true")
class JudgeRescoreTest {

    @Test
    void rescore() throws Exception {

        ObjectMapper json = new ObjectMapper();
        String label = System.getProperty("piper.eval.label", "baseline");
        Path dir = Path.of("eval", "results", label);
        RawRun raw = json.readValue(dir.resolve("raw.json").toFile(), RawRun.class);
        GoldenSet golden = GoldenSet.load(json, raw.placeholders());

        EvalScorer scorer = new EvalScorer(json, ChunkIndex.load(json), HaikuJudge.fromEnv(json));
        Map<String, Object> metrics = scorer.write(dir, raw, golden);

        assertThat(metrics).containsKeys("overall", "cases");
    }
}
