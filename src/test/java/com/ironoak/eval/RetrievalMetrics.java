package com.ironoak.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranking metrics over a ranked list of relevance grades (0 = not relevant,
 * 1 = supports, 2 = answers). Pure functions, so the same input always gives
 * the same score.
 */
final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /**
     * Share of the labelled passages found in the top k. Unlabelled passages that
     * match the evidence count for the other metrics but not here, because the
     * denominator is the labelled set.
     */
    static double recallAtK(List<String> rankedKeys,
            Map<String, Integer> labelled,
            int k) {

        if (labelled.isEmpty()) {
            return 0.0;
        }
        Set<String> found = new HashSet<>(top(rankedKeys, k));
        found.retainAll(labelled.keySet());
        return (double) found.size() / labelled.size();
    }

    /** Relevant passages in the top k divided by k. */
    static double precisionAtK(List<Integer> grades, int k) {
        long relevant = top(grades, k).stream().filter(g -> g > 0).count();
        return (double) relevant / k;
    }

    /** 1 when any relevant passage is in the top k. */
    static double hitAtK(List<Integer> grades, int k) {
        return top(grades, k).stream().anyMatch(g -> g > 0) ? 1.0 : 0.0;
    }

    /** 1 / rank of the first relevant passage, 0 when there is none. */
    static double reciprocalRank(List<Integer> grades) {

        for (int i = 0; i < grades.size(); i++) {
            if (grades.get(i) > 0) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Graded NDCG@k with gain 2^grade - 1. The ideal ranking uses the labelled
     * grades, so finding unlabelled evidence matches can only help.
     */
    static double ndcgAtK(List<Integer> grades,
            List<Integer> labelledGrades,
            int k) {

        double ideal = dcg(labelledGrades.stream().sorted((a, b) -> b - a).toList(), k);
        return ideal == 0.0 ? 0.0 : Math.min(1.0, dcg(grades, k) / ideal);
    }

    /**
     * Average precision over the whole list (RAGAS-style context precision):
     * mean of precision@i at every relevant position i.
     */
    static double averagePrecision(List<Integer> grades) {

        double sum = 0.0;
        int relevant = 0;
        for (int i = 0; i < grades.size(); i++) {
            if (grades.get(i) > 0) {
                relevant++;
                sum += (double) relevant / (i + 1);
            }
        }
        return relevant == 0 ? 0.0 : sum / relevant;
    }

    private static double dcg(List<Integer> grades, int k) {

        double sum = 0.0;
        List<Integer> head = top(grades, k);
        for (int i = 0; i < head.size(); i++) {
            sum += (Math.pow(2, head.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return sum;
    }

    private static <T> List<T> top(List<T> values, int k) {
        return values.subList(0, Math.min(k, values.size()));
    }
}
