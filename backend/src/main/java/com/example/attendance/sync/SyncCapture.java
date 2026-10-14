package com.example.attendance.sync;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Converts local business results to the common protocol inside the original business transaction. */
@Component @RequiredArgsConstructor
public class SyncCapture {
    private final SyncStore store;
    private final SyncProperties config;
    private final SyncCrypto crypto;
    private final SyncAttendanceAdapter adapter;
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void changed(SyncProtocol.Changed change) {
        if(!config.isEnabled()) return;
        if(change.type().equals("ATTENDANCE_TASK")) {store.assertOwner(change.taskId());task(change.taskId(),true);}
        else record(change.taskId(),change.studentId());
    }
    private String instant(Object value) {
        if(value==null) return null;
        LocalDateTime local=value instanceof Timestamp timestamp?timestamp.toLocalDateTime():(LocalDateTime)value;
        return local.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString();
    }
    private void emit(String type,String global,String local,ObjectNode payload) {
        var map=store.localMapping(type,local);
        long version=map==null?1:((Number)map.get("version")).longValue()+1;
        if(type.equals("ATTENDANCE_RECORD")) payload.put("baseEventId",map==null?null:(String)map.get("last_event_id"));
        var event=crypto.sign(new SyncProtocol.Event(UUID.randomUUID().toString(),config.getNodeId(),type,global,"UPSERT",version,Instant.now().toString(),payload,null));
        store.append(event);String hash=adapter.payloadHash(type,payload);
        if(map==null) store.map(type,global,local,config.getNodeId(),version,hash,event.eventId());
        else store.updateMap(type,global,version,hash,event.eventId());
    }
    private void reference(String type,String global,String local,ObjectNode payload) {
        if(store.localMapping(type,local)==null) emit(type,global,local,payload);
    }
    private void classReference(String id,String code,String name) {
        reference("CLASS",adapter.catalogUuid("CLASS",code),id,store.json.createObjectNode().put("schoolId",config.getSchoolId()).put("classCode",code).put("name",name));
    }
    private String task(Long id,boolean update) {
        var mapped=store.localMapping("ATTENDANCE_TASK",id.toString());
        var row=store.one("SELECT at.*,c.course_code,c.name AS course_name,c.id AS course_id,ci.class_code,ci.name AS class_name,ci.id AS class_id,t.teacher_no,cc.academic_year,cc.semester FROM attendance_task at JOIN course_class cc ON cc.id=at.course_class_id JOIN course c ON c.id=cc.course_id JOIN class_info ci ON ci.id=cc.class_id JOIN teacher t ON t.id=cc.teacher_id WHERE at.id=? AND at.deleted=0",id);
        if(row==null) throw new IllegalStateException("TASK_CAPTURE_MISSING");
        classReference(row.get("class_id").toString(),(String)row.get("class_code"),(String)row.get("class_name"));
        String code=(String)row.get("course_code");
        reference("COURSE",adapter.catalogUuid("COURSE",code),row.get("course_id").toString(),store.json.createObjectNode().put("schoolId",config.getSchoolId()).put("courseCode",code).put("name",(String)row.get("course_name")));
        if(mapped!=null && !update) return (String)mapped.get("global_id");
        String global=mapped==null?UUID.randomUUID().toString():(String)mapped.get("global_id");
        var p=store.json.createObjectNode().put("schoolId",config.getSchoolId()).put("ownerNodeId",config.getNodeId())
                .put("courseCode",code).put("classCode",(String)row.get("class_code"))
                .put("teacherNo",(String)row.get("teacher_no")).put("academicYear",(String)row.get("academic_year"))
                .put("semester",(String)row.get("semester")).put("attendanceDate",row.get("attendance_date").toString())
                .put("status",(String)row.get("status")).put("closedAt",instant(row.get("closed_at")));
        emit("ATTENDANCE_TASK",global,id.toString(),p);return global;
    }
    private void record(Long taskId,Long studentId) {
        var student=store.one("SELECT s.student_no,s.name,c.id AS class_id,c.class_code,c.name AS class_name FROM student s JOIN class_info c ON c.id=s.class_id WHERE s.id=? AND s.deleted=0",studentId);
        String no=(String)Objects.requireNonNull(student).get("student_no");
        classReference(student.get("class_id").toString(),(String)student.get("class_code"),(String)student.get("class_name"));
        reference("STUDENT",adapter.studentUuid(no),studentId.toString(),store.json.createObjectNode().put("schoolId",config.getSchoolId()).put("studentNo",no).put("name",(String)student.get("name")).put("classCode",(String)student.get("class_code")));
        String task=task(taskId,false);
        var row=store.one("SELECT * FROM attendance_record WHERE task_id=? AND student_id=? AND deleted=0",taskId,studentId);
        // Password validation stays entirely local. Only the resulting status/time is exported.
        Object recordedAt=row==null?null:row.get("check_in_time");
        if(recordedAt==null && row!=null) recordedAt=row.get("updated_at");
        String time=recordedAt==null?Instant.now().toString():instant(recordedAt);
        String remark=row==null?null:(row.get("session_id")!=null?"考勤结果":(String)row.get("remark"));
        var p=store.json.createObjectNode().put("schoolId",config.getSchoolId()).put("studentNo",no).put("taskUuid",task)
                .put("status",row==null?"NORMAL":(String)row.get("status")).put("recordedAt",time).put("remark",remark);
        emit("ATTENDANCE_RECORD",adapter.recordUuid(task,no),taskId+":"+studentId,p);
    }
}