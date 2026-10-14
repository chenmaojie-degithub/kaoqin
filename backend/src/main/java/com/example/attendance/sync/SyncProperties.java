package com.example.attendance.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component @Getter
public class SyncProperties {
    private final boolean enabled;
    private final String nodeId, schoolId, token, privateKey, publicKey;
    private final int timeoutMs, maxAttempts;
    private final List<Peer> peers;
    public record Peer(String nodeId, String url, String token, String publicKey) {}
    public SyncProperties(@Value("${sync.enabled:false}") boolean enabled,
                          @Value("${sync.node-id:}") String nodeId, @Value("${sync.school-id:}") String schoolId,
                          @Value("${sync.token:}") String token, @Value("${sync.private-key:}") String privateKey,
                          @Value("${sync.public-key:}") String publicKey,
                          @Value("${sync.peers-json:[]}") String peersJson,
                          @Value("${sync.timeout-ms:2000}") int timeoutMs,
                          @Value("${sync.max-attempts:2}") int maxAttempts, ObjectMapper mapper) {
        this.enabled=enabled; this.nodeId=nodeId; this.schoolId=schoolId; this.token=token;
        this.privateKey=privateKey; this.publicKey=publicKey; this.timeoutMs=timeoutMs; this.maxAttempts=maxAttempts;
        try { peers=List.copyOf(mapper.readValue(peersJson, new TypeReference<List<Peer>>() {})); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid SYNC_PEERS_JSON"); }
        if (!enabled) return;
        if (!nodeId.matches("[A-Za-z0-9_-]{1,64}") || !schoolId.matches("[A-Za-z0-9_-]{1,64}")
                || token.length()<32 || privateKey.isBlank() || publicKey.isBlank()
                || timeoutMs<100 || timeoutMs>10000 || maxAttempts<1 || maxAttempts>3 || peers.size()>2)
            throw new IllegalArgumentException("Invalid sync configuration");
        Set<String> ids=new HashSet<>(); ids.add(nodeId);
        for (Peer peer : peers) {
            URI uri;
            try { uri=URI.create(peer.url()); } catch (Exception e) { throw new IllegalArgumentException("Invalid peer URL"); }
            if (peer.nodeId()==null || !peer.nodeId().matches("[A-Za-z0-9_-]{1,64}") || !ids.add(peer.nodeId())
                    || peer.token()==null || peer.token().length()<32 || peer.publicKey()==null || peer.publicKey().isBlank()
                    || !Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null
                    || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                    || !"/api".equals(uri.getPath())) throw new IllegalArgumentException("Invalid peer configuration");
        }
    }
    public Peer peer(String id) { return peers.stream().filter(p->p.nodeId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("UNKNOWN_NODE")); }
}
