package com.example.attendance.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Component
public class SyncStore {
    static final String EXPORTABLE = "(entity_type IN ('STUDENT','CLASS','COURSE','ATTENDANCE_TASK') OR (entity_type='ATTENDANCE_RECORD' AND JSON_CONTAINS_PATH(event_json,'one','$.payload.baseEventId')=1 AND JSON_CONTAINS_PATH(event_json,'one','$.payload.method')=0))";
    final JdbcTemplate db;
    final ObjectMapper json;
    final SyncCrypto crypto;
    final SyncProperties config;
    private final TransactionTemplate tx;

    public SyncStore(JdbcTemplate template,ObjectMapper json,SyncCrypto crypto,SyncProperties config,TransactionTemplate tx) {
        this.db=new JdbcTemplate(Objects.requireNonNull(template.getDataSource()));
        this.db.setQueryTimeout(5);
        this.json=json;this.crypto=crypto;this.config=config;this.tx=tx;
    }

    Map<String,Object> one(String sql,Object... args) {
        var rows=db.queryForList(sql,args); return rows.isEmpty()?null:rows.get(0);
    }
    Map<String,Object> mapping(String type,String id) { return one("SELECT * FROM sync_identity_map WHERE entity_type=? AND global_id=? FOR UPDATE",type,id); }
    Map<String,Object> localMapping(String type,String local) { return one("SELECT * FROM sync_identity_map WHERE entity_type=? AND local_id=? FOR UPDATE",type,local); }
    void map(String type,String id,String local,String owner,long version,String hash,String eventId) {
        db.update("INSERT INTO sync_identity_map(entity_type,global_id,local_id,owner_node_id,version,payload_hash,last_event_id) VALUES(?,?,?,?,?,?,?)",type,id,local,owner,version,hash,eventId);
    }
    void updateMap(String type,String id,long version,String hash,String eventId) {
        db.update("UPDATE sync_identity_map SET version=?,payload_hash=?,last_event_id=? WHERE entity_type=? AND global_id=?",version,hash,eventId,type,id);
    }
    String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    SyncProtocol.Event read(String value) { try { return json.readValue(value,SyncProtocol.Event.class); } catch(Exception e) { throw new IllegalStateException("CORRUPT_EVENT"); } }
    void append(SyncProtocol.Event event) {
        db.update("INSERT INTO sync_event(event_id,origin_node_id,entity_type,entity_id,version,event_json,event_hash) VALUES(?,?,?,?,?,?,?)",
                event.eventId(),event.originNodeId(),event.entityType(),event.entityId(),event.version(),write(event),crypto.hash(json.valueToTree(event)));
    }
    public void initialize() {
        if (!config.isEnabled()) return;
        tx.executeWithoutResult(s->{
            var state=one("SELECT * FROM sync_state WHERE id=1 FOR UPDATE");
            if (state==null || (state.get("node_id")!=null && !config.getNodeId().equals(state.get("node_id"))))
                throw new IllegalStateException("Sync database node identity mismatch / migration missing");
            db.update("UPDATE sync_state SET node_id=? WHERE id=1",config.getNodeId());
            db.queryForList("SELECT last_event_id FROM sync_identity_map LIMIT 0");
            for (var peer:config.getPeers()) for (String direction:List.of("PUSH","PULL"))
                db.update("INSERT IGNORE INTO sync_cursor(peer_node_id,direction) VALUES(?,?)",peer.nodeId(),direction);
        });
    }
    public void publish() {
        if (!config.isEnabled()) return;
        tx.executeWithoutResult(s->{
            long seq=((Number)one("SELECT last_published_seq FROM sync_state WHERE id=1 FOR UPDATE").get("last_published_seq")).longValue();
            for (var row:db.queryForList("SELECT journal_id FROM sync_event WHERE published_seq IS NULL ORDER BY journal_id LIMIT 500 FOR UPDATE"))
                db.update("UPDATE sync_event SET published_seq=? WHERE journal_id=?",++seq,row.get("journal_id"));
            db.update("UPDATE sync_state SET last_published_seq=? WHERE id=1",seq);
        });
    }
    public SyncProtocol.Page page(long cursor,int limit) {
        if (cursor<0 || limit<1 || limit>100) throw new IllegalArgumentException("INVALID_PAGE");
        publish();
        long head=db.queryForObject("SELECT last_published_seq FROM sync_state WHERE id=1",Long.class);
        if(cursor>head) throw new IllegalArgumentException("CURSOR_AHEAD_OF_LOG");
        // Preserve original v1 logs/signatures, but never expose password-specific payloads to v2 peers.
        var rows=db.queryForList("SELECT published_seq,event_json FROM sync_event WHERE published_seq>? AND "+EXPORTABLE+" ORDER BY published_seq LIMIT ?",cursor,limit+1);
        boolean more=rows.size()>limit;
        List<SyncProtocol.Entry> entries=new ArrayList<>();
        for(var row:rows.subList(0,Math.min(limit,rows.size()))) entries.add(new SyncProtocol.Entry(((Number)row.get("published_seq")).longValue(),read((String)row.get("event_json"))));
        return new SyncProtocol.Page(SyncProtocol.VERSION,config.getNodeId(),entries,entries.isEmpty()?cursor:entries.get(entries.size()-1).cursor(),more);
    }
    public void assertOwner(Long taskId) {
        if(!config.isEnabled()) return;
        var rows=db.queryForList("SELECT owner_node_id FROM sync_identity_map WHERE entity_type='ATTENDANCE_TASK' AND local_id=?",taskId.toString());
        if(!rows.isEmpty() && !config.getNodeId().equals(rows.get(0).get("owner_node_id")))
            throw com.example.attendance.exception.BusinessException.conflict("该任务由其他节点管理，请到所属节点开启或修改签到");
    }
}
