package com.example.attendance.sync;

import com.example.attendance.AttendanceApplication;
import com.example.attendance.entity.User;
import com.example.attendance.security.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.attendance.sync.SyncProtocol.*;

/** Opt in only against a separate loopback MySQL test instance, never the normal 3306 business service. */
@EnabledIfEnvironmentVariable(named="SYNC_IT_DB_PORT",matches="\\d+")
class SyncThreeNodeTest {
    static final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    static final List<Node> nodes=new ArrayList<>();
    static String port;
    static class Node {
        String id,database,token,priv,pub; int index; ConfigurableApplicationContext app;
        JdbcTemplate db() {return app.getBean(JdbcTemplate.class);}
        String url() {return "http://127.0.0.1:"+(8081+index)+"/api";}
        SyncWorker worker() {return app.getBean(SyncWorker.class);}
        String jwt(String role) {
            var row=db().queryForMap("SELECT * FROM user WHERE role=? LIMIT 1",role);
            User user=new User(); user.setId(((Number)row.get("id")).longValue());user.setUsername((String)row.get("username"));user.setRole(role);user.setStatus(1);
            return app.getBean(JwtService.class).create(new LoginUser(user));
        }
        void start() throws Exception {
            List<SyncProperties.Peer> peers=nodes.stream().filter(n->n!=this).map(n->new SyncProperties.Peer(n.id,n.url(),n.token,n.pub)).toList();
            app=new SpringApplicationBuilder(AttendanceApplication.class).run(
                    "--spring.config.import=optional:classpath:/no-local-config.properties", "--server.address=127.0.0.1", "--server.port="+(8081+index),
                    "--spring.datasource.url=jdbc:mysql://127.0.0.1:"+port+"/"+database+"?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                    "--spring.datasource.username=root","--spring.datasource.password=", "--attendance.jwt.secret="+(id+"-test-jwt-").repeat(8),
                    "--sync.enabled=true","--sync.node-id="+id,"--sync.school-id=TEST_SCHOOL","--sync.token="+token,
                    "--sync.private-key="+priv,"--sync.public-key="+pub,"--sync.peers-json="+json.writeValueAsString(peers),
                    "--sync.interval-ms=5000","--sync.timeout-ms=600","--sync.max-attempts=2","--spring.datasource.hikari.maximum-pool-size=5",
                    "--logging.level.root=WARN");
        }
    }
    @BeforeAll static void setup() throws Exception {
        port=System.getenv("SYNC_IT_DB_PORT"); assertNotEquals("3306",port,"Never use the business MySQL instance");
        Path root=Path.of("..").toAbsolutePath().normalize();
        if(!Files.exists(root.resolve("database/schema.sql"))) root=Path.of("").toAbsolutePath();
        String schema=Files.readString(root.resolve("database/schema.sql")); schema=schema.substring(schema.indexOf("CREATE TABLE `user`"));
        String runId=UUID.randomUUID().toString().substring(0,8);
        for(int i=0;i<3;i++) {
            Node n=new Node();n.index=i;n.id="NODE_"+(char)('A'+i);n.database="attendance_sync_test_"+runId+"_"+(char)('a'+i);n.token=UUID.randomUUID().toString()+UUID.randomUUID();
            KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();n.priv=Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());n.pub=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());nodes.add(n);
            try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:"+port+"/?useSSL=false&allowPublicKeyRetrieval=true","root","")) {
                connection.createStatement().execute("CREATE DATABASE "+n.database+" CHARACTER SET utf8mb4");
                connection.setCatalog(n.database);
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(schema.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                new ResourceDatabasePopulator(new FileSystemResource(root.resolve("database/migrate_sync.sql"))).populate(connection);
                if(i==0) connection.createStatement().execute("ALTER TABLE sync_identity_map DROP COLUMN last_event_id");
                new ResourceDatabasePopulator(new FileSystemResource(root.resolve("database/migrate_sync_v2.sql"))).populate(connection);
                new ResourceDatabasePopulator(new FileSystemResource(root.resolve("database/migrate_sync_v2.sql"))).populate(connection);
                // Deliberately different local IDs on all three databases.
                connection.createStatement().execute("ALTER TABLE user AUTO_INCREMENT="+(1+i*100));
                connection.createStatement().execute("ALTER TABLE student AUTO_INCREMENT="+(11+i*100));
                connection.createStatement().execute("ALTER TABLE teacher AUTO_INCREMENT="+(21+i*100));
                connection.createStatement().execute("ALTER TABLE course AUTO_INCREMENT="+(31+i*100));
                connection.createStatement().execute("ALTER TABLE class_info AUTO_INCREMENT="+(41+i*100));
                connection.createStatement().execute("ALTER TABLE course_class AUTO_INCREMENT="+(51+i*100));
                connection.createStatement().execute("ALTER TABLE attendance_task AUTO_INCREMENT="+(61+i*100));
                String hash=new BCryptPasswordEncoder().encode("SyncTest!2026");
                try(var insert=connection.prepareStatement("INSERT INTO user(username,password,real_name,role) VALUES(?,?,?,?)")) {
                    for(String role:List.of("ADMIN","TEACHER","STUDENT")) {insert.setString(1,"sync_"+role.toLowerCase());insert.setString(2,hash);insert.setString(3,"Fictional "+role);insert.setString(4,role);insert.executeUpdate();}
                }
                connection.createStatement().execute("INSERT INTO teacher(user_id,teacher_no,name) SELECT id,'T001','Fictional Teacher' FROM user WHERE role='TEACHER'");
                connection.createStatement().execute("INSERT INTO class_info(class_code,name,grade) VALUES('CLASS_1','Fictional Class','2026')");
                connection.createStatement().execute("INSERT INTO student(user_id,student_no,name,class_id) SELECT u.id,'S001','Fictional Student',c.id FROM user u CROSS JOIN class_info c WHERE u.role='STUDENT'");
                for(String code:List.of("COURSE_A","COURSE_B","COURSE_C")) connection.createStatement().execute("INSERT INTO course(course_code,name) VALUES('"+code+"','Fictional Course')");
                connection.createStatement().execute("INSERT INTO course_class(course_id,class_id,teacher_id,academic_year,semester) SELECT c.id,ci.id,t.id,'2026-2027','第一学期' FROM course c CROSS JOIN class_info ci CROSS JOIN teacher t");
            }
        }
        for(Node n:nodes) n.start();
    }
    @AfterAll static void close() {for(Node n:nodes) if(n.app!=null) n.app.close();}
    static HttpResponse<String> request(Node target,String method,String path,String auth,String source,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(target.url()+path)).timeout(Duration.ofSeconds(15));
        if(auth!=null) builder.header("Authorization",auth);if(source!=null) builder.header("X-Sync-Node",source);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String text?text:json.writeValueAsString(body))).header("Content-Type","application/json");
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode business(Node n,String role,String method,String path,Object body) throws Exception {
        var response=request(n,method,path,"Bearer "+n.jwt(role),null,body); assertEquals(200,response.statusCode(),response.body());return json.readTree(response.body()).path("data");
    }
    static long createTask(Node n,String code,LocalDate date) throws Exception {
        long cc=n.db().queryForObject("SELECT cc.id FROM course_class cc JOIN course c ON c.id=cc.course_id WHERE c.course_code=?",Long.class,code);
        return business(n,"TEACHER","POST","/attendance/tasks",Map.of("courseClassId",cc,"attendanceDate",date.toString())).path("id").asLong();
    }
    static void signed(Node n,long task) throws Exception {
        var session=business(n,"TEACHER","POST","/attendance/sessions",Map.of("taskId",task));
        business(n,"STUDENT","POST","/attendance/check-in",Map.of("sessionId",session.path("sessionId").asLong(),"signCode",session.path("signCode").asText()));
    }
    static void manual(Node n,long task) throws Exception {
        long student=n.db().queryForObject("SELECT id FROM student WHERE student_no='S001'",Long.class);
        business(n,"TEACHER","PUT","/attendance/tasks/"+task+"/records",List.of(Map.of("studentId",student,"status","ABSENT")));
    }
    static long count(Node n,String table) {return n.db().queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    static void cycles() {for(int round=0;round<3;round++) for(Node n:nodes) if(n.app.isActive()) n.worker().run("BOTH",true);}
    static Event event(Node source,String type,String entity,long version,ObjectNode payload) {
        return source.app.getBean(SyncCrypto.class).sign(new Event(UUID.randomUUID().toString(),source.id,type,entity,"UPSERT",version,Instant.now().toString(),payload,null));
    }
    static JsonNode push(Node source,Node target,Event... events) throws Exception {
        var response=request(target,"POST","/sync/push","Sync "+source.token,source.id,new Push(SyncProtocol.VERSION,source.id,List.of(events)));
        assertEquals(200,response.statusCode(),response.body());return json.readTree(response.body()).path("data").path("results");
    }
    static ObjectNode manualPayload(String student,String task) {
        return json.createObjectNode().put("schoolId","TEST_SCHOOL").put("studentNo",student).put("taskUuid",task).put("status","ABSENT").putNull("recordedAt").putNull("remark").putNull("baseEventId");
    }
    static ObjectNode studentPayload(String no) {
        return json.createObjectNode().put("schoolId","TEST_SCHOOL").put("studentNo",no).put("classCode","CLASS_1").put("name","Fictional Student");
    }
    static String statusForCourse(Node n,String course) {
        return n.db().queryForObject("SELECT r.status FROM attendance_record r JOIN attendance_task t ON t.id=r.task_id JOIN course_class cc ON cc.id=t.course_class_id JOIN course c ON c.id=cc.course_id WHERE c.course_code=? AND t.attendance_date=? LIMIT 1",String.class,course,LocalDate.now());
    }
    static void addStudent(Node n,String no) {
        n.db().update("INSERT INTO user(username,password,real_name,role) VALUES(?,?,?,'STUDENT')",no,new BCryptPasswordEncoder().encode("SyncTest!2026"),"Fictional Student");
        n.db().update("INSERT INTO student(user_id,student_no,name,class_id) SELECT u.id,?,'Fictional Student',c.id FROM user u CROSS JOIN class_info c WHERE u.username=?",no,no);
    }
    @Test void realThreeNodeHttpMysqlJourney() throws Exception {
        Node a=nodes.get(0),b=nodes.get(1),c=nodes.get(2);
        assertEquals(401,request(a,"GET","/sync/pull?cursor=0",null,null,null).statusCode());
        assertEquals(401,request(a,"GET","/sync/pull","Bearer "+a.jwt("ADMIN"),null,null).statusCode());
        assertEquals(401,request(a,"GET","/sync/admin/status","Sync "+b.token,b.id,null).statusCode());
        assertEquals(403,request(a,"GET","/sync/admin/status","Bearer "+a.jwt("TEACHER"),null,null).statusCode());
        assertEquals(400,request(a,"POST","/sync/push","Sync "+b.token,b.id,new Push(SyncProtocol.VERSION,a.id,List.of())).statusCode());
        assertEquals(400,request(a,"POST","/sync/push","Sync "+b.token,b.id,new Push(1,b.id,List.of())).statusCode());
        assertEquals(413,request(a,"POST","/sync/push","Sync "+b.token,b.id,"x".repeat(1_048_577)).statusCode());
        long ta=createTask(a,"COURSE_A",LocalDate.now());signed(a,ta);
        long tb=createTask(b,"COURSE_B",LocalDate.now());manual(b,tb);
        long tc=createTask(c,"COURSE_C",LocalDate.now());manual(c,tc);
        for(Node n:nodes) n.worker().run("PUSH",true);cycles();
        for(Node n:nodes) assertEquals(3,count(n,"attendance_record"));
        assertEquals(1,count(a,"attendance_session"));assertEquals(0,count(b,"attendance_session"));assertEquals(0,count(c,"attendance_session"));
        var firstPage=a.app.getBean(SyncStore.class).page(0,100);
        assertEquals(Set.of("CLASS","COURSE","STUDENT","ATTENDANCE_TASK","ATTENDANCE_RECORD"),new HashSet<>(firstPage.events().stream().map(e->e.event().entityType()).toList()));
        for(Entry entry:firstPage.events()) {
            String body=json.writeValueAsString(entry.event());
            for(String field:List.of("signCode","sessionUuid","openedAt","expiresAt","PASSWORD","checkedInAt")) assertFalse(body.contains(field));
        }
        long head=count(a,"sync_event");cycles();for(Node n:nodes) assertEquals(head,count(n,"sync_event"),"Forwarding must not create events");
        assertNotEquals(a.db().queryForObject("SELECT id FROM student LIMIT 1",Long.class),b.db().queryForObject("SELECT id FROM student LIMIT 1",Long.class));
        var ownRows=business(a,"STUDENT","GET","/student-portal/today-check-ins",null);
        assertEquals(1,java.util.stream.StreamSupport.stream(ownRows.spliterator(),false).filter(r->r.path("checkedIn").asBoolean()).count());
        for(JsonNode row:business(b,"STUDENT","GET","/student-portal/today-check-ins",null)) assertFalse(row.path("checkedIn").asBoolean(),"Generic results cannot certify local password check-in");
        String taskUuid=a.db().queryForObject("SELECT global_id FROM sync_identity_map WHERE entity_type='ATTENDANCE_TASK' AND local_id=?",String.class,Long.toString(ta));
        long remoteTask=b.db().queryForObject("SELECT local_id FROM sync_identity_map WHERE entity_type='ATTENDANCE_TASK' AND global_id=?",Long.class,taskUuid);
        assertEquals(409,request(b,"POST","/attendance/sessions","Bearer "+b.jwt("TEACHER"),null,Map.of("taskId",remoteTask)).statusCode());
        var adapter=a.app.getBean(SyncAttendanceAdapter.class);
        String recordUuid=adapter.recordUuid(taskUuid,"S001");
        // B uses ordinary teacher attendance, without password APIs, to revise A's common record.
        manual(b,remoteTask);
        long until=System.nanoTime()+Duration.ofSeconds(25).toNanos();
        while(System.nanoTime()<until && !nodes.stream().allMatch(n->"ABSENT".equals(statusForCourse(n,"COURSE_A")))) Thread.sleep(200);
        for(Node n:nodes) assertEquals("ABSENT",statusForCourse(n,"COURSE_A"));
        assertNotNull(a.db().queryForObject("SELECT check_in_time FROM attendance_record WHERE task_id=?",LocalDateTime.class,ta));
        assertNotNull(a.db().queryForObject("SELECT session_id FROM attendance_record WHERE task_id=?",Long.class,ta));
        String base=a.db().queryForObject("SELECT last_event_id FROM sync_identity_map WHERE entity_type='ATTENDANCE_RECORD' AND global_id=?",String.class,recordUuid);
        // C, a non-password producer, submits a normal protocol update against the observed baseline.
        var p=manualPayload("S001",taskUuid).put("status","LATE").put("baseEventId",base).put("recordedAt",Instant.now().toString());
        Event correction=event(c,"ATTENDANCE_RECORD",recordUuid,3,p);
        assertEquals("APPLIED",push(c,a,correction).get(0).path("status").asText());cycles();
        for(Node n:nodes) assertEquals("LATE",statusForCourse(n,"COURSE_A"));
        Event left=event(b,"ATTENDANCE_RECORD",recordUuid,4,manualPayload("S001",taskUuid).put("status","NORMAL").put("baseEventId",correction.eventId()));
        Event right=event(c,"ATTENDANCE_RECORD",recordUuid,4,manualPayload("S001",taskUuid).put("status","ABSENT").put("baseEventId",correction.eventId()));
        var f1=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return push(b,a,left).get(0).path("status").asText();}catch(Exception e){throw new RuntimeException(e);}});
        var f2=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return push(c,a,right).get(0).path("status").asText();}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals(Set.of("APPLIED","CONFLICT"),Set.of(f1.get(),f2.get()));
        assertEquals(3,count(a,"attendance_record"));assertNotNull(a.db().queryForObject("SELECT session_id FROM attendance_record WHERE task_id=?",Long.class,ta));
        Event first=firstPage.events().stream().map(Entry::event).filter(e->e.entityType().equals("ATTENDANCE_RECORD")).findFirst().orElseThrow();
        assertEquals("DUPLICATE",push(a,b,first).get(0).path("status").asText());
        Event relay=event(a,"STUDENT",adapter.studentUuid("S001"),1,studentPayload("S001"));push(a,b,relay);
        Event fromB=b.app.getBean(SyncStore.class).page(0,100).events().stream().map(Entry::event).filter(e->e.eventId().equals(relay.eventId())).findFirst().orElseThrow();
        assertEquals(a.id,fromB.originNodeId());push(b,c,fromB);assertEquals("DUPLICATE",push(c,a,fromB).get(0).path("status").asText());
        var bad=(ObjectNode)first.payload().deepCopy();bad.put("status","ABSENT");
        Event tamper=new Event(UUID.randomUUID().toString(),first.originNodeId(),first.entityType(),first.entityId(),first.operation(),first.version()+1,first.occurredAt(),bad,first.signature());
        assertEquals("REJECTED",push(a,b,tamper).get(0).path("status").asText());
        Event identity=event(a,"STUDENT",adapter.studentUuid("S_MISSING"),1,studentPayload("S_MISSING"));
        Event pending=event(a,"ATTENDANCE_RECORD",adapter.recordUuid(taskUuid,"S_MISSING"),1,manualPayload("S_MISSING",taskUuid));
        var result=push(a,b,identity,pending);for(JsonNode row:result){assertTrue(row.path("persisted").asBoolean());assertEquals("WAITING_DEPENDENCY",row.path("status").asText());}
        addStudent(b,"S_MISSING");b.app.getBean(SyncService.class).retryPending();b.app.getBean(SyncService.class).retryPending();
        assertEquals("APPLIED",b.db().queryForObject("SELECT status FROM sync_inbox WHERE event_id=?",String.class,pending.eventId()));
        String missingTask=UUID.randomUUID().toString();
        Event noTask=event(a,"ATTENDANCE_RECORD",adapter.recordUuid(missingTask,"S001"),1,manualPayload("S001",missingTask));
        assertEquals("WAITING_DEPENDENCY",push(a,b,noTask).get(0).path("status").asText());
        var taskPayload=(ObjectNode)firstPage.events().stream().map(Entry::event).filter(e->e.entityId().equals(taskUuid)).findFirst().orElseThrow().payload().deepCopy();
        taskPayload.put("attendanceDate",LocalDate.now().plusDays(4).toString());
        push(a,b,event(a,"ATTENDANCE_TASK",missingTask,1,taskPayload));b.app.getBean(SyncService.class).retryPending();
        assertEquals("APPLIED",b.db().queryForObject("SELECT status FROM sync_inbox WHERE event_id=?",String.class,noTask.eventId()));
        addStudent(b,"S_CONFLICT");push(a,b,event(a,"STUDENT",adapter.studentUuid("S_CONFLICT"),1,studentPayload("S_CONFLICT")));
        b.db().update("INSERT INTO attendance_record(task_id,student_id,course_id,attendance_date,status,recorded_by) SELECT at.id,s.id,cc.course_id,at.attendance_date,'NORMAL',u.id FROM attendance_task at JOIN course_class cc ON cc.id=at.course_class_id JOIN student s ON s.student_no='S_CONFLICT' JOIN user u ON u.role='TEACHER' WHERE at.id=?",remoteTask);
        Event collision=event(a,"ATTENDANCE_RECORD",adapter.recordUuid(taskUuid,"S_CONFLICT"),1,manualPayload("S_CONFLICT",taskUuid));
        assertEquals("CONFLICT",push(a,b,collision).get(0).path("status").asText());
        assertEquals("NORMAL",b.db().queryForObject("SELECT r.status FROM attendance_record r JOIN student s ON s.id=r.student_id WHERE s.student_no='S_CONFLICT'",String.class));
        // Legacy v1 password events remain archived, not exported or silently interpreted by v2.
        Event legacy=event(a,"ATTENDANCE_RECORD",UUID.randomUUID().toString(),1,json.createObjectNode().put("schoolId","TEST_SCHOOL").put("method","PASSWORD"));
        b.app.getBean(SyncStore.class).append(legacy);
        b.db().update("INSERT INTO sync_inbox(event_id,event_hash,event_json,sender_node_id,status) VALUES(?,?,?,?,'WAITING_DEPENDENCY')",legacy.eventId(),"0".repeat(64),json.writeValueAsString(legacy),a.id);
        b.app.getBean(SyncService.class).retryPending();
        assertEquals("LEGACY_PROTOCOL_REVIEW",b.db().queryForObject("SELECT error_code FROM sync_inbox WHERE event_id=?",String.class,legacy.eventId()));
        long taskCount=count(a,"attendance_task");
        a.db().execute("CREATE TRIGGER test_sync_outbox_failure BEFORE INSERT ON sync_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test failure'");
        try {
            long cc=a.db().queryForObject("SELECT MIN(id) FROM course_class",Long.class);
            assertEquals(500,request(a,"POST","/attendance/tasks","Bearer "+a.jwt("TEACHER"),null,Map.of("courseClassId",cc,"attendanceDate",LocalDate.now().plusDays(9).toString())).statusCode());
            assertEquals(taskCount,count(a,"attendance_task"));
        } finally {a.db().execute("DROP TRIGGER test_sync_outbox_failure");}
        c.app.close();long outageTask=createTask(a,"COURSE_A",LocalDate.now().plusDays(1));manual(a,outageTask);a.worker().run("PUSH",true);
        assertNotNull(a.db().queryForObject("SELECT last_error FROM sync_cursor WHERE peer_node_id='NODE_C' AND direction='PUSH'",String.class));
        c.start();cycles();
        assertTrue(c.db().queryForObject("SELECT COUNT(*) FROM attendance_record r JOIN attendance_task t ON t.id=r.task_id WHERE t.attendance_date=?",Long.class,LocalDate.now().plusDays(1))>0);
        long auto=createTask(b,"COURSE_B",LocalDate.now().plusDays(2));manual(b,auto);
        until=System.nanoTime()+Duration.ofSeconds(25).toNanos();boolean complete=false;
        while(System.nanoTime()<until) {
            complete=nodes.stream().allMatch(n->n.db().queryForObject("SELECT COUNT(*) FROM attendance_record r JOIN attendance_task t ON t.id=r.task_id WHERE t.attendance_date=?",Long.class,LocalDate.now().plusDays(2))==1);
            if(complete)break;Thread.sleep(200);
        }
        assertTrue(complete,"Automatic sync must handle ordinary records without password APIs");
        List<Event> many=new ArrayList<>();for(int i=0;i<105;i++) many.add(event(a,"STUDENT",adapter.studentUuid("S001"),1,studentPayload("S001")));
        push(a,b,many.subList(0,100).toArray(Event[]::new));push(a,b,many.subList(100,105).toArray(Event[]::new));
        long cursor=0;Set<String> paged=new HashSet<>();boolean more;
        do {Page next=b.app.getBean(SyncStore.class).page(cursor,100);more=next.hasMore();for(Entry entry:next.events()){assertTrue(entry.cursor()>cursor);assertTrue(paged.add(entry.event().eventId()));cursor=entry.cursor();}assertEquals(cursor,next.nextCursor());} while(more);
        assertFalse(paged.contains(legacy.eventId()));assertEquals(b.db().queryForObject("SELECT COUNT(*) FROM sync_event WHERE "+SyncStore.EXPORTABLE,Long.class),paged.size());
        var page=request(a,"GET","/sync/pull?cursor=0&limit=1","Sync "+b.token,b.id,null);assertTrue(json.readTree(page.body()).path("data").path("hasMore").asBoolean());
        String admin=business(a,"ADMIN","GET","/sync/admin/status",null).toString();assertFalse(admin.contains(a.token));assertFalse(admin.contains(a.priv));
        System.out.println("SYNC_V2_IT_PASS: A password result -> generic JSON; B/C ordinary writes and cross-node corrections; causal conflict protection; five entity mappings; auto Push/Pull; legacy isolation; retries; no local password receipt fabricated");
    }
}