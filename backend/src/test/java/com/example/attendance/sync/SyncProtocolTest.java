package com.example.attendance.sync;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncProtocolTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private SyncCrypto crypto;
    private SyncService service;
    @BeforeEach void setup() throws Exception {
        KeyPair a=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var config=new SyncProperties(true,"NODE_A","TEST_SCHOOL","a".repeat(32),Base64.getEncoder().encodeToString(a.getPrivate().getEncoded()),Base64.getEncoder().encodeToString(a.getPublic().getEncoded()),"[]",500,2,json);
        crypto=new SyncCrypto(json,config);
        service=new SyncService(mock(SyncStore.class),config,crypto,mock(SyncAttendanceAdapter.class),mock(org.springframework.transaction.support.TransactionTemplate.class));
    }
    private SyncProtocol.Event event() {
        var payload=json.createObjectNode().put("studentNo","S001").put("schoolId","TEST_SCHOOL").put("name","Fictional Student").put("classCode","CLASS_1");
        return crypto.sign(new SyncProtocol.Event(UUID.randomUUID().toString(),"NODE_A","STUDENT",UUID.randomUUID().toString(),"UPSERT",1,Instant.now().toString(),payload,null));
    }
    @Test void signatureIsStableAcrossJsonObjectOrderingAndDetectsTampering() {
        var e=event(); assertTrue(crypto.verify(e));
        var reverse=json.createObjectNode().put("classCode","CLASS_1").put("name","Fictional Student").put("schoolId","TEST_SCHOOL").put("studentNo","S001");
        assertTrue(crypto.verify(new SyncProtocol.Event(e.eventId(),e.originNodeId(),e.entityType(),e.entityId(),e.operation(),e.version(),e.occurredAt(),reverse,e.signature())));
        reverse.put("studentNo","S002");
        assertFalse(crypto.verify(new SyncProtocol.Event(e.eventId(),e.originNodeId(),e.entityType(),e.entityId(),e.operation(),e.version(),e.occurredAt(),reverse,e.signature())));
    }
    @Test void payloadWhiteListRejectsSecretsBeforePersistence() {
        var e=event(); ((com.fasterxml.jackson.databind.node.ObjectNode)e.payload()).put("signCode","123456");
        assertThrows(IllegalArgumentException.class,()->service.validate(e));
    }
    @Test void uuidVersionAndTimezoneAreRequired() {
        var e=event(); service.validate(e);
        assertThrows(RuntimeException.class,()->service.validate(new SyncProtocol.Event(e.eventId(),e.originNodeId(),e.entityType(),e.entityId(),e.operation(),0,e.occurredAt(),e.payload(),e.signature())));
        assertThrows(RuntimeException.class,()->service.validate(new SyncProtocol.Event(e.eventId(),e.originNodeId(),e.entityType(),e.entityId(),e.operation(),1,"2026-10-14T10:30:00",e.payload(),e.signature())));
        assertFalse(SyncService.uuid("local-id-1"));
    }
    @Test void wrongNodeEnvelopeAndUnsupportedProtocolCannotApplyEvents() {
        assertThrows(IllegalArgumentException.class,()->service.receive(null,"NODE_A"));
        assertThrows(IllegalArgumentException.class,()->service.receive(new SyncProtocol.Push(99,"NODE_A",List.of()),"NODE_A"));
        assertThrows(IllegalArgumentException.class,()->service.receive(new SyncProtocol.Push(SyncProtocol.VERSION,"NODE_B",List.of()),"NODE_A"));
    }
    @Test void globalStudentKeyPreservesLeadingZeroAndSchoolNamespace() {
        var config=mock(SyncProperties.class);when(config.getSchoolId()).thenReturn("SCHOOL_1");
        var adapter=new SyncAttendanceAdapter(mock(SyncStore.class),config);
        assertNotEquals(adapter.studentUuid("001"),adapter.studentUuid("1"));
        var id=adapter.studentUuid("001");when(config.getSchoolId()).thenReturn("SCHOOL_2");
        assertNotEquals(id,adapter.studentUuid("001"));
    }
    @Test void genericRecordHasNoPasswordRequirementsAndRejectsPasswordFields() {
        var payload=json.createObjectNode().put("schoolId","TEST_SCHOOL").put("studentNo","S001")
                .put("taskUuid",UUID.randomUUID().toString()).put("status","NORMAL")
                .putNull("recordedAt").putNull("remark").putNull("baseEventId");
        var event=crypto.sign(new SyncProtocol.Event(UUID.randomUUID().toString(),"NODE_A","ATTENDANCE_RECORD",UUID.randomUUID().toString(),"UPSERT",1,Instant.now().toString(),payload,null));
        assertDoesNotThrow(()->service.validate(event));
        payload.put("method","PASSWORD");assertThrows(IllegalArgumentException.class,()->service.validate(event));
    }
    @Test void publishedJsonExamplesPassTheActualValidator() throws Exception {
        java.nio.file.Path file=java.nio.file.Path.of("../docs/sync-v2-examples.json");
        if(!java.nio.file.Files.exists(file)) file=java.nio.file.Path.of("docs/sync-v2-examples.json");
        JsonNode batch=json.readTree(java.nio.file.Files.readString(file));
        assertEquals(SyncProtocol.VERSION,batch.path("protocolVersion").asInt());
        assertEquals(5,batch.path("events").size());
        for(JsonNode node:batch.path("events")) service.validate(json.treeToValue(node,SyncProtocol.Event.class));
    }
}
