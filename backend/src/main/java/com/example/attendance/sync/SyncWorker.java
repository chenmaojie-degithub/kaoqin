package com.example.attendance.sync;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.example.attendance.sync.SyncProtocol.*;

@Service @EnableScheduling @RequiredArgsConstructor @Slf4j
public class SyncWorker {
    private final SyncProperties config;
    private final SyncStore store;
    private final SyncService service;
    private final AtomicBoolean running=new AtomicBoolean();
    private HttpClient client;
    @PostConstruct void initialize() {
        if(!config.isEnabled()) return;
        store.initialize();
        client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.getTimeoutMs())).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Scheduled(fixedDelayString="${sync.interval-ms:5000}", initialDelayString="${sync.interval-ms:5000}")
    public void scheduled() {
        if(!config.isEnabled()) return;
        try { run("BOTH",false); } catch(Exception e) { log.warn("Sync cycle failed: {}",e.getClass().getSimpleName()); }
    }
    public Map<String,Object> run(String direction,boolean manual) {
        if(!config.isEnabled()) throw com.example.attendance.exception.BusinessException.conflict("同步未启用");
        if(!running.compareAndSet(false,true)) return Map.of("started",false,"reason","BUSY");
        try {
            service.retryPending(); store.publish();
            for(var peer:config.getPeers()) for(String action:List.of("PUSH","PULL")) {
                if(!direction.equals("BOTH") && !direction.equals(action)) continue;
                var state=store.one("SELECT * FROM sync_cursor WHERE peer_node_id=? AND direction=?",peer.nodeId(),action);
                var retry=state.get("retry_at");
                if(!manual && retry instanceof java.sql.Timestamp time && time.toInstant().isAfter(Instant.now())) continue;
                store.db.update("UPDATE sync_cursor SET last_attempt_at=CURRENT_TIMESTAMP(6) WHERE peer_node_id=? AND direction=?",peer.nodeId(),action);
                boolean success=false; String error="NETWORK_FAILURE";
                for(int attempt=0;attempt<config.getMaxAttempts();attempt++) {
                    try {
                        long cursor=((Number)state.get("cursor_value")).longValue();
                        long next=action.equals("PUSH")?push(peer,cursor):pull(peer,cursor);
                        store.db.update("UPDATE sync_cursor SET cursor_value=?,last_success_at=CURRENT_TIMESTAMP(6),last_error=NULL,failures=0,retry_at=NULL WHERE peer_node_id=? AND direction=?",next,peer.nodeId(),action);
                        if(next>cursor) log.info("Sync {} peer={} persisted cursor={}",action,peer.nodeId(),next);
                        success=true; break;
                    } catch(Exception e) {
                        error=e instanceof ProtocolFailure?e.getMessage():"NETWORK_OR_TIMEOUT";
                        if(Thread.currentThread().isInterrupted()) break;
                        // No sleeping in business threads; retry budget here is finite, subsequent cycles use DB backoff.
                    }
                }
                if(!success) {
                    int failures=Math.min(10,((Number)state.get("failures")).intValue()+1);
                    long seconds=Math.min(300,5L << Math.min(failures-1,6));
                    store.db.update("UPDATE sync_cursor SET failures=?,last_error=?,retry_at=? WHERE peer_node_id=? AND direction=?",failures,error,java.sql.Timestamp.from(Instant.now().plusSeconds(seconds)),peer.nodeId(),action);
                    log.warn("Sync {} peer={} failed code={}",action,peer.nodeId(),error);
                } else log.debug("Sync {} peer={} succeeded",action,peer.nodeId());
            }
            service.retryPending();
            return Map.of("started",true);
        } finally { running.set(false); }
    }
    private long push(SyncProperties.Peer peer,long cursor) throws Exception {
        Page page=store.page(cursor,100);
        var envelope=new Push(SyncProtocol.VERSION,config.getNodeId(),page.events().stream().map(Entry::event).toList());
        Ack ack=store.json.treeToValue(exchange(peer,"/sync/push",store.write(envelope)),Ack.class);
        if(ack.protocolVersion()!=SyncProtocol.VERSION || !peer.nodeId().equals(ack.nodeId()) || ack.results()==null || ack.results().size()!=page.events().size()) throw new ProtocolFailure("INVALID_ACK");
        long next=cursor;
        for(int i=0;i<page.events().size();i++) {
            Result result=ack.results().get(i);
            if(!result.eventId().equals(page.events().get(i).event().eventId()) || !result.persisted()) throw new ProtocolFailure("REMOTE_NOT_PERSISTED");
            next=page.events().get(i).cursor();
        }
        return next;
    }
    private long pull(SyncProperties.Peer peer,long cursor) throws Exception {
        Page page=store.json.treeToValue(exchange(peer,"/sync/pull?cursor="+cursor+"&limit=100",null),Page.class);
        if(page.protocolVersion()!=SyncProtocol.VERSION || !peer.nodeId().equals(page.nodeId()) || page.events()==null || page.events().size()>100) throw new ProtocolFailure("INVALID_PAGE");
        long next=cursor;
        for(Entry entry:page.events()) {
            if(entry.cursor()<=next) throw new ProtocolFailure("INVALID_CURSOR");
            service.validate(entry.event());
            var result=service.receiveOne(entry.event(),peer.nodeId());
            if(!result.persisted()) throw new ProtocolFailure("LOCAL_NOT_PERSISTED");
            next=entry.cursor();
        }
        if(next!=page.nextCursor()) throw new ProtocolFailure("INVALID_NEXT_CURSOR");
        return next;
    }
    private JsonNode exchange(SyncProperties.Peer peer,String path,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(peer.url()+path)).timeout(Duration.ofMillis(config.getTimeoutMs()))
                .header("X-Sync-Node",config.getNodeId()).header("Authorization","Sync "+config.getToken());
        var request=body==null?builder.GET().build():builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var future=client.sendAsync(request,info->new LimitedBody());
        HttpResponse<byte[]> response;
        try { response=future.get(config.getTimeoutMs()+200L,TimeUnit.MILLISECONDS); }
        finally { if(!future.isDone()) future.cancel(true); }
        if(response.statusCode()!=200) throw new ProtocolFailure("HTTP_"+response.statusCode());
        var result=store.json.readTree(response.body());
        if(result.path("code").asInt(-1)!=0 || !result.has("data")) throw new ProtocolFailure("INVALID_RESPONSE");
        return result.get("data");
    }
    /** Bound response memory and cancel oversized / slow bodies as well as connection timeouts. */
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        Flow.Subscription subscription; int size;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription=value;delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> data) {
            for(var buffer:data) size+=buffer.remaining();
            if(size>1_048_576) { subscription.cancel();delegate.onError(new IllegalStateException("RESPONSE_TOO_LARGE")); }
            else delegate.onNext(data);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
    private static class ProtocolFailure extends RuntimeException { ProtocolFailure(String message) { super(message); } }
    public Map<String,Object> status() {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("enabled",config.isEnabled());result.put("nodeId",config.getNodeId());result.put("running",running.get());
        result.put("protocolVersion",SyncProtocol.VERSION);
        if(!config.isEnabled()) {result.put("peers",List.of());return result;}
        List<Map<String,Object>> peers=new ArrayList<>();
        for(var peer:config.getPeers()) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("nodeId",peer.nodeId());row.put("url",peer.url());
            var cursors=store.db.queryForList("SELECT direction,cursor_value,last_success_at,last_attempt_at,last_error,failures,retry_at FROM sync_cursor WHERE peer_node_id=?",peer.nodeId());
            row.put("directions",cursors);
            long cursor=cursors.stream().filter(c->"PUSH".equals(c.get("direction"))).mapToLong(c->((Number)c.get("cursor_value")).longValue()).findFirst().orElse(0);
            row.put("pending",store.db.queryForObject("SELECT COUNT(*) FROM sync_event WHERE (published_seq IS NULL OR published_seq>?) AND "+SyncStore.EXPORTABLE+"",Long.class,cursor));peers.add(row);
        }
        result.put("peers",peers);
        result.put("inbox",store.db.queryForList("SELECT status,COUNT(*) AS count FROM sync_inbox GROUP BY status"));
        result.put("recentIssues",store.db.queryForList("SELECT event_id,status,error_code,attempts,updated_at FROM sync_inbox WHERE status IN ('CONFLICT','REJECTED','WAITING_DEPENDENCY') ORDER BY updated_at DESC LIMIT 20"));
        result.put("eventCount",store.db.queryForObject("SELECT COUNT(*) FROM sync_event",Long.class));return result;
    }
}
