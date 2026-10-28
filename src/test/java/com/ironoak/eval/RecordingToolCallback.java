package com.ironoak.eval;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

/**
 * Wraps one of Piper's tools: runs it unchanged and records the name, the JSON
 * arguments the model sent, the result and any error into the shared log.
 */
final class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final List<RawRun.ToolCall> log;

    RecordingToolCallback(ToolCallback delegate, List<RawRun.ToolCall> log) {
        this.delegate = delegate;
        this.log = log;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {

        long start = System.nanoTime();
        String name = delegate.getToolDefinition().name();
        try {
            String output = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            log.add(new RawRun.ToolCall(name, toolInput, cut(output), null, millisSince(start)));
            return output;
        } catch (RuntimeException e) {
            log.add(new RawRun.ToolCall(name, toolInput, null, e.getMessage(), millisSince(start)));
            throw e;
        }
    }

    private static String cut(String output) {
        return output == null || output.length() <= RawRun.ToolCall.OUTPUT_LIMIT
                ? output
                : output.substring(0, RawRun.ToolCall.OUTPUT_LIMIT) + "…";
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
