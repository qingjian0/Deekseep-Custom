package com.dsmod.probe.agentcore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Android- and Hook-independent contracts shared by the Agent Core, API Gateway and host adapter.
 *
 * <p>Keep this package free of Android, Xposed, HTTP-server and DeepSeek obfuscated types.</p>
 */
public final class AgentCoreContract {
    private AgentCoreContract() {}

    public enum EventType {
        STARTED, TEXT_DELTA, REASONING_DELTA, TOOL_PROPOSED, COMPLETED, FAILED
    }

    public enum ToolDecision {
        ALLOW, REQUIRE_CONFIRMATION, DENY
    }

    public static final class Request {
        public final String requestId;
        public final String conversationScope;
        public final String requestedModel;
        public final String input;
        public final boolean stream;
        public final long deadlineEpochMs;
        public final Map<String, String> metadata;

        public Request(String requestId, String conversationScope, String requestedModel,
                       String input, boolean stream, long deadlineEpochMs,
                       Map<String, String> metadata) {
            this.requestId = required(requestId, "requestId", 128);
            this.conversationScope = required(conversationScope, "conversationScope", 256);
            this.requestedModel = required(requestedModel, "requestedModel", 128);
            this.input = input == null ? "" : input;
            if (this.input.length() > 1_000_000) {
                throw new IllegalArgumentException("input exceeds 1,000,000 characters");
            }
            if (deadlineEpochMs <= 0L) {
                throw new IllegalArgumentException("deadlineEpochMs must be positive");
            }
            this.stream = stream;
            this.deadlineEpochMs = deadlineEpochMs;
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            if (metadata != null) {
                if (metadata.size() > 32) throw new IllegalArgumentException("too many metadata entries");
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    String key = required(entry.getKey(), "metadata key", 64);
                    String value = entry.getValue() == null ? "" : entry.getValue();
                    if (value.length() > 2048) {
                        throw new IllegalArgumentException("metadata value too long: " + key);
                    }
                    copy.put(key, value);
                }
            }
            this.metadata = Collections.unmodifiableMap(copy);
        }
    }

    public static final class Event {
        public final EventType type;
        public final String requestId;
        public final String text;
        public final String detail;
        public final Map<String, String> attributes;

        public Event(EventType type, String requestId, String text, String detail,
                     Map<String, String> attributes) {
            if (type == null) throw new IllegalArgumentException("event type is required");
            this.type = type;
            this.requestId = required(requestId, "requestId", 128);
            this.text = text == null ? "" : text;
            this.detail = detail == null ? "" : detail;
            if (this.text.length() > 64 * 1024 || this.detail.length() > 2048) {
                throw new IllegalArgumentException("event text or detail exceeds its limit");
            }
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            if (attributes != null) {
                if (attributes.size() > 32) throw new IllegalArgumentException("too many event attributes");
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    String key = required(entry.getKey(), "attribute key", 64);
                    String value = entry.getValue() == null ? "" : entry.getValue();
                    if (value.length() > 2048) throw new IllegalArgumentException("event attribute too long: " + key);
                    copy.put(key, value);
                }
            }
            this.attributes = Collections.unmodifiableMap(copy);
        }
    }

    public static final class ToolCall {
        public final String callId;
        public final String name;
        public final String argumentsJson;
        public final String conversationScope;

        public ToolCall(String callId, String name, String argumentsJson,
                        String conversationScope) {
            this.callId = required(callId, "callId", 128);
            this.name = required(name, "tool name", 128);
            this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
            if (this.argumentsJson.length() > 64 * 1024) {
                throw new IllegalArgumentException("tool arguments exceed 64 KiB");
            }
            this.conversationScope = required(conversationScope, "conversationScope", 256);
        }
    }

    public interface EventSink {
        /** Return false to stop upstream collection. */
        boolean onEvent(Event event) throws Exception;
        boolean isCancelled();
    }

    public interface Cancellation {
        boolean cancel();
        boolean isCancelled();
    }

    public interface ModelAdapter {
        String adapterId();
        boolean isAvailable();
        void complete(Request request, EventSink sink, Cancellation cancellation)
                throws Exception;
    }

    public interface ToolPolicy {
        ToolDecision evaluate(ToolCall call);
    }

    public interface ToolExecutor {
        String execute(ToolCall call, Cancellation cancellation) throws Exception;
    }

    public interface RunRepository {
        boolean create(Request request);
        boolean transition(String requestId, AgentRunStateMachine.State next, String detail);
        AgentRunStateMachine.State stateOf(String requestId);
    }

    private static String required(String value, String label, int maxLength) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
        String clean = value.trim();
        if (clean.length() == 0 || clean.length() > maxLength) {
            throw new IllegalArgumentException(label + " is empty or too long");
        }
        return clean;
    }
}
