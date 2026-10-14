package com.example.attendance.sync;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class SyncProtocol {
    public static final int VERSION = 2;
    private SyncProtocol() {}
    public record Event(String eventId, String originNodeId, String entityType, String entityId,
                        String operation, long version, String occurredAt, JsonNode payload, String signature) {}
    public record Push(int protocolVersion, String nodeId, List<Event> events) {}
    public record Result(String eventId, String status, boolean persisted, String errorCode) {}
    public record Ack(int protocolVersion, String nodeId, List<Result> results, String serverTime) {}
    public record Entry(long cursor, Event event) {}
    public record Page(int protocolVersion, String nodeId, List<Entry> events, long nextCursor, boolean hasMore) {}
    public record Changed(String type, Long taskId, Long studentId) {}
}
