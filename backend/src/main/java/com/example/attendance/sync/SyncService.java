package com.example.attendance.sync;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static com.example.attendance.sync.SyncProtocol.*;

@Service @RequiredArgsConstructor
public class SyncService {
    private final SyncStore store;
    private final SyncProperties config;
    private final SyncCrypto crypto;
    private final SyncAttendanceAdapter adapter;
    private final TransactionTemplate tx;
    private static final Set<String> TYPES=Set.of("STUDENT","CLASS","COURSE","ATTENDANCE_TASK","ATTENDANCE_RECORD");
    public void validate(Event e) {
        if(e==null || !uuid(e.eventId()) || !uuid(e.entityId()) || e.originNodeId()==null || !e.originNodeId().matches("[A-Za-z0-9_-]{1,64}")
                || !TYPES.contains(e.entityType()==null?"":e.entityType()) || !"UPSERT".equals(e.operation()) || e.version()<1 || e.version()>1_000_000_000
                || e.occurredAt()==null || e.signature()==null || e.signature().length()>100 || e.payload()==null || !e.payload().isObject()) throw new IllegalArgumentException("INVALID_EVENT");
        OffsetDateTime.parse(e.occurredAt());
        Set<String> required=new HashSet<>(Set.of("schoolId"));
        switch(e.entityType()) {
            case "STUDENT" -> required.addAll(Set.of("studentNo","name","classCode"));
            case "CLASS" -> required.addAll(Set.of("classCode","name"));
            case "COURSE" -> required.addAll(Set.of("courseCode","name"));
            case "ATTENDANCE_TASK" -> required.addAll(Set.of("ownerNodeId","courseCode","classCode","teacherNo","academicYear","semester","attendanceDate","status","closedAt"));
            case "ATTENDANCE_RECORD" -> required.addAll(Set.of("studentNo","taskUuid","status","recordedAt","remark","baseEventId"));
        }
        Set<String> actual=new HashSet<>();e.payload().fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(required)) throw new IllegalArgumentException("PAYLOAD_FIELDS");
        for(String field:required) {
            JsonNode value=e.payload().get(field);
            int max=field.equals("remark")?500:100;
            if(!value.isNull() && (!value.isTextual() || (value.textValue().isBlank() && !field.equals("remark")) || value.textValue().length()>max)) throw new IllegalArgumentException("PAYLOAD_VALUE");
            if(value.isNull() && !Set.of("closedAt","recordedAt","remark","baseEventId").contains(field)) throw new IllegalArgumentException("PAYLOAD_NULL");
        }
        if(Set.of("STUDENT","CLASS","COURSE").contains(e.entityType()) && e.version()!=1) throw new IllegalArgumentException("REFERENCE_VERSION");
        if(e.payload().has("studentNo") && !e.payload().path("studentNo").asText().matches("[A-Za-z0-9_-]{1,30}")) throw new IllegalArgumentException("STUDENT_NO");
        for(String code:List.of("classCode","courseCode","teacherNo")) if(e.payload().has(code) && !e.payload().path(code).asText().matches("[A-Za-z0-9_-]{1,30}")) throw new IllegalArgumentException("BUSINESS_CODE");
        if(e.entityType().equals("ATTENDANCE_TASK")) {
            LocalDate.parse(e.payload().path("attendanceDate").asText());
            if(!Set.of("OPEN","CLOSED").contains(e.payload().path("status").asText())) throw new IllegalArgumentException("TASK_STATUS");
            boolean closed=e.payload().path("status").asText().equals("CLOSED");
            if(closed==e.payload().path("closedAt").isNull()) throw new IllegalArgumentException("CLOSED_AT");
            if(closed) OffsetDateTime.parse(e.payload().path("closedAt").asText());
        }
        if(e.entityType().equals("ATTENDANCE_RECORD")) {
            if(!uuid(e.payload().path("taskUuid").asText()) || !Set.of("NORMAL","ABSENT","LATE","EARLY_LEAVE","LEAVE").contains(e.payload().path("status").asText())) throw new IllegalArgumentException("RECORD_VALUE");
            if(!e.payload().path("recordedAt").isNull()) OffsetDateTime.parse(e.payload().path("recordedAt").asText());
            if(!e.payload().path("baseEventId").isNull() && !uuid(e.payload().path("baseEventId").asText())) throw new IllegalArgumentException("BASE_EVENT_ID");
        }
    }
    static boolean uuid(String s) {
        if(s==null || !s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return false;
        try { UUID.fromString(s); return true; } catch(Exception e) { return false; }
    }
    public Ack receive(Push push,String authenticatedNode) {
        if(push==null || push.protocolVersion()!=SyncProtocol.VERSION || !authenticatedNode.equals(push.nodeId()) || push.events()==null || push.events().size()>100) throw new IllegalArgumentException("INVALID_ENVELOPE");
        // Validate the whole envelope before storing any untrusted payload; unknown fields never enter the journal.
        push.events().forEach(this::validate);
        List<Result> results=new ArrayList<>();
        for(Event event:push.events()) results.add(receiveOne(event,authenticatedNode));
        return new Ack(SyncProtocol.VERSION,config.getNodeId(),results,Instant.now().toString());
    }
    Result receiveOne(Event event,String sender) {
        try { return tx.execute(status->{
            String hash=crypto.hash(store.json.valueToTree(event));
            var local=store.one("SELECT event_hash FROM sync_event WHERE event_id=?",event.eventId());
            if(local!=null) return new Result(event.eventId(),hash.equals(local.get("event_hash"))?"DUPLICATE":"CONFLICT",hash.equals(local.get("event_hash")),hash.equals(local.get("event_hash"))?null:"EVENT_ID_COLLISION");
            store.db.update("INSERT IGNORE INTO sync_inbox(event_id,event_hash,event_json,sender_node_id,status) VALUES(?,?,?,?, 'RECEIVED')",event.eventId(),hash,store.write(event),sender);
            var inbox=store.one("SELECT * FROM sync_inbox WHERE event_id=? FOR UPDATE",event.eventId());
            if(!hash.equals(inbox.get("event_hash"))) return new Result(event.eventId(),"CONFLICT",false,"EVENT_ID_COLLISION");
            if(Set.of("APPLIED","DUPLICATE","STALE").contains(inbox.get("status"))) return new Result(event.eventId(),"DUPLICATE",true,null);
            if(Set.of("CONFLICT","REJECTED").contains(inbox.get("status"))) return new Result(event.eventId(),(String)inbox.get("status"),true,(String)inbox.get("error_code"));
            String result="REJECTED",error="INVALID_SIGNATURE";
            if(crypto.verify(event)) {
                Object savepoint=status.createSavepoint();
                try { result=adapter.apply(event); error=null; store.append(event); }
                catch(SyncAttendanceAdapter.ApplyFailure e) { status.rollbackToSavepoint(savepoint); result=e.status;error=e.code; }
                catch(DataIntegrityViolationException e) { status.rollbackToSavepoint(savepoint); result="CONFLICT"; error="DATABASE_UNIQUE_CONFLICT"; }
                finally { status.releaseSavepoint(savepoint); }
            }
            store.db.update("UPDATE sync_inbox SET status=?,error_code=?,attempts=attempts+1 WHERE event_id=?",result,error,event.eventId());
            return new Result(event.eventId(),result,true,error);
        }); } catch(Exception e) { return new Result(event.eventId(),"FAILED",false,"PERSISTENCE_FAILURE"); }
    }
    private void retryStored(Map<String,Object> row) {
        Event event=store.read((String)row.get("event_json"));
        try {validate(event);}
        catch(RuntimeException invalid) {
            store.db.update("UPDATE sync_inbox SET status='REJECTED',error_code='LEGACY_PROTOCOL_REVIEW' WHERE event_id=?",event.eventId());
            return;
        }
        receiveOne(event,(String)row.get("sender_node_id"));
    }
    public void retryPending() {
        for(var row:store.db.queryForList("SELECT event_json,sender_node_id FROM sync_inbox WHERE status='WAITING_DEPENDENCY' ORDER BY updated_at LIMIT 100"))
            retryStored(row);
    }
}
