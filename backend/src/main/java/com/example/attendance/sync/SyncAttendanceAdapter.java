package com.example.attendance.sync;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** The only inbound adapter aware of this project's business schema. Never executes peer-provided SQL. */
@Component @RequiredArgsConstructor
public class SyncAttendanceAdapter {
    private final SyncStore store;
    private final SyncProperties config;
    public static class ApplyFailure extends RuntimeException {
        final String status,code;
        ApplyFailure(String status,String code) { super(code);this.status=status;this.code=code; }
    }
    static void waiting(String code) { throw new ApplyFailure("WAITING_DEPENDENCY",code); }
    static void conflict(String code) { throw new ApplyFailure("CONFLICT",code); }
    static String text(JsonNode p,String field) { return p.path(field).asText(); }
    public String studentUuid(String studentNo) {
        return UUID.nameUUIDFromBytes((config.getSchoolId()+"\n"+studentNo).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public String recordUuid(String taskUuid,String studentNo) {
        return UUID.nameUUIDFromBytes((taskUuid+"\n"+studentUuid(studentNo)).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public String catalogUuid(String type,String code) {
        return UUID.nameUUIDFromBytes((config.getSchoolId()+"\n"+type+"\n"+code).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public String payloadHash(String type,JsonNode payload) {
        if(Set.of("STUDENT","CLASS","COURSE").contains(type)) {
            var identity=((com.fasterxml.jackson.databind.node.ObjectNode)payload).deepCopy();identity.remove("name");
            return store.crypto.hash(identity);
        }
        return store.crypto.hash(payload);
    }
    private long id(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public String apply(SyncProtocol.Event event) {
        JsonNode p=event.payload(); String type=event.entityType();
        if(!config.getSchoolId().equals(text(p,"schoolId"))) conflict("SCHOOL_MISMATCH");
        if(type.equals("ATTENDANCE_RECORD")) {
            var task=store.one("SELECT local_id FROM sync_identity_map WHERE entity_type='ATTENDANCE_TASK' AND global_id=?",text(p,"taskUuid"));
            if(task!=null) store.one("SELECT id FROM attendance_task WHERE id=? FOR UPDATE",task.get("local_id"));
        }
        String hash=payloadHash(type,p);
        Map<String,Object> mapped=store.mapping(type,event.entityId());
        if(Set.of("STUDENT","CLASS","COURSE").contains(type)) return reference(event,mapped,hash);
        if(mapped!=null) {
            if(!type.equals("ATTENDANCE_RECORD") && !event.originNodeId().equals(mapped.get("owner_node_id"))) conflict("OWNER_MISMATCH");
            long version=id(mapped,"version");
            if(event.version()<version) return "STALE";
            if(event.version()==version) {
                if(!hash.equals(mapped.get("payload_hash")) || (type.equals("ATTENDANCE_RECORD") && !event.eventId().equals(mapped.get("last_event_id")))) conflict("VERSION_COLLISION");
                return "DUPLICATE";
            }
        }
        if(type.equals("ATTENDANCE_RECORD")) {
            long previous=mapped==null?0:id(mapped,"version");
            if(event.version()!=previous+1) waiting("PREVIOUS_VERSION_MISSING");
            String base=p.path("baseEventId").isNull()?null:text(p,"baseEventId");
            if(!Objects.equals(base,mapped==null?null:mapped.get("last_event_id"))) conflict("BASE_EVENT_CONFLICT");
        }
        return type.equals("ATTENDANCE_TASK")?task(event,mapped,hash):record(event,mapped,hash);
    }
    private String reference(SyncProtocol.Event e,Map<String,Object> mapped,String hash) {
        if(!e.entityType().equals("STUDENT")) {
            boolean isClass=e.entityType().equals("CLASS");String code=text(e.payload(),isClass?"classCode":"courseCode");
            if(!catalogUuid(e.entityType(),code).equals(e.entityId())) conflict("CATALOG_UUID_MISMATCH");
            var row=store.one(isClass?"SELECT id FROM class_info WHERE class_code=? AND deleted=0":"SELECT id FROM course WHERE course_code=? AND deleted=0",code);
            if(row==null) waiting("CATALOG_MAPPING_MISSING");
            return bindReference(e,mapped,row.get("id").toString(),hash);
        }
        String no=text(e.payload(),"studentNo");
        if(!studentUuid(no).equals(e.entityId())) conflict("STUDENT_UUID_MISMATCH");
        var student=store.one("SELECT s.id,c.class_code FROM student s JOIN class_info c ON c.id=s.class_id AND c.deleted=0 WHERE s.student_no=? AND s.deleted=0",no);
        if(student==null) waiting("STUDENT_NOT_FOUND");
        if(!text(e.payload(),"classCode").equals(student.get("class_code"))) conflict("STUDENT_CLASS_MISMATCH");
        return bindReference(e,mapped,student.get("id").toString(),hash);
    }
    private String bindReference(SyncProtocol.Event e,Map<String,Object> mapped,String local,String hash) {
        if(mapped!=null) {
            if(!local.equals(mapped.get("local_id")) || !hash.equals(mapped.get("payload_hash"))) conflict("IDENTITY_COLLISION");
            return "DUPLICATE";
        }
        store.map(e.entityType(),e.entityId(),local,e.originNodeId(),1,hash,e.eventId()); return "APPLIED";
    }
    private String task(SyncProtocol.Event e,Map<String,Object> mapped,String hash) {
        JsonNode p=e.payload();
        if(!e.originNodeId().equals(text(p,"ownerNodeId"))) conflict("OWNER_MISMATCH");
        if(store.mapping("CLASS",catalogUuid("CLASS",text(p,"classCode")))==null || store.mapping("COURSE",catalogUuid("COURSE",text(p,"courseCode")))==null) waiting("CATALOG_MAPPING_MISSING");
        var cc=store.one("SELECT cc.id,t.user_id FROM course_class cc JOIN course c ON c.id=cc.course_id AND c.deleted=0 JOIN class_info ci ON ci.id=cc.class_id AND ci.deleted=0 JOIN teacher t ON t.id=cc.teacher_id AND t.deleted=0 WHERE cc.deleted=0 AND c.course_code=? AND ci.class_code=? AND t.teacher_no=? AND cc.academic_year=? AND cc.semester=?",
                text(p,"courseCode"),text(p,"classCode"),text(p,"teacherNo"),text(p,"academicYear"),text(p,"semester"));
        if(cc==null) waiting("TEACHING_MAPPING_MISSING");
        if(mapped==null) {
            var existing=store.one("SELECT id FROM attendance_task WHERE course_class_id=? AND attendance_date=? FOR UPDATE",cc.get("id"),text(p,"attendanceDate"));
            if(existing!=null) conflict("LOCAL_TASK_UNIQUE_CONFLICT");
            store.db.update("INSERT INTO attendance_task(course_class_id,attendance_date,status,created_by,closed_at) VALUES(?,?,?,?,?)",cc.get("id"),text(p,"attendanceDate"),text(p,"status"),cc.get("user_id"),closedAt(p));
            long local=store.db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            store.map(e.entityType(),e.entityId(),Long.toString(local),e.originNodeId(),e.version(),hash,e.eventId());
        } else {
            var task=store.one("SELECT * FROM attendance_task WHERE id=? AND deleted=0 FOR UPDATE",mapped.get("local_id"));
            if(task==null || id(task,"course_class_id")!=id(cc,"id") || !task.get("attendance_date").toString().equals(text(p,"attendanceDate"))) conflict("TASK_IDENTITY_CHANGED");
            store.db.update("UPDATE attendance_task SET status=?,closed_at=? WHERE id=?",text(p,"status"),closedAt(p),mapped.get("local_id"));
            store.updateMap(e.entityType(),e.entityId(),e.version(),hash,e.eventId());
        }
        return "APPLIED";
    }
    private LocalDateTime closedAt(JsonNode p) {
        return p.path("closedAt").isNull()||p.path("closedAt").isMissingNode()?null:OffsetDateTime.parse(text(p,"closedAt")).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }
    private String record(SyncProtocol.Event e,Map<String,Object> mapped,String hash) {
        JsonNode p=e.payload(); String no=text(p,"studentNo"), taskUuid=text(p,"taskUuid");
        if(!recordUuid(taskUuid,no).equals(e.entityId())) conflict("RECORD_UUID_MISMATCH");
        var studentMap=store.mapping("STUDENT",studentUuid(no));
        var taskMap=store.mapping("ATTENDANCE_TASK",taskUuid);
        if(studentMap==null || taskMap==null) waiting("IDENTITY_MAPPING_MISSING");
        var task=store.one("SELECT at.id,at.attendance_date,cc.course_id,cc.class_id,t.user_id FROM attendance_task at JOIN course_class cc ON cc.id=at.course_class_id AND cc.deleted=0 JOIN teacher t ON t.id=cc.teacher_id AND t.deleted=0 WHERE at.id=? AND at.deleted=0 FOR UPDATE",taskMap.get("local_id"));
        var student=store.one("SELECT id,class_id FROM student WHERE id=? AND deleted=0",studentMap.get("local_id"));
        if(task==null || student==null) waiting("LOCAL_ENTITY_MISSING");
        if(id(student,"class_id")!=id(task,"class_id")) conflict("CLASS_MISMATCH");
        String local=task.get("id")+":"+student.get("id");
        if(mapped!=null && !local.equals(mapped.get("local_id"))) conflict("RECORD_IDENTITY_CHANGED");
        var existing=store.one("SELECT * FROM attendance_record WHERE student_id=? AND course_id=? AND attendance_date=? FOR UPDATE",student.get("id"),task.get("course_id"),task.get("attendance_date"));
        if(existing!=null && (mapped==null || id(existing,"task_id")!=id(task,"id") || ((Number)existing.get("deleted")).intValue()!=0)) conflict("LOCAL_RECORD_UNIQUE_CONFLICT");
        String remark=p.path("remark").isNull()?null:text(p,"remark");
        if(existing==null) store.db.update("INSERT INTO attendance_record(task_id,student_id,course_id,attendance_date,status,recorded_by,remark) VALUES(?,?,?,?,?,?,?)",task.get("id"),student.get("id"),task.get("course_id"),task.get("attendance_date"),text(p,"status"),task.get("user_id"),remark);
        else store.db.update("UPDATE attendance_record SET status=?,remark=? WHERE id=?",text(p,"status"),remark,existing.get("id"));
        // recordedAt is retained in the signed generic event, never written as a local password receipt.
        // Existing local session_id / check_in_time stay untouched by generic record corrections.
        if(mapped==null) store.map(e.entityType(),e.entityId(),local,e.originNodeId(),e.version(),hash,e.eventId());
        else store.updateMap(e.entityType(),e.entityId(),e.version(),hash,e.eventId());
        return "APPLIED";
    }
}
