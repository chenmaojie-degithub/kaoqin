package com.example.attendance.sync;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;

@Component
public class SyncCrypto {
    private final ObjectMapper json;
    private final SyncProperties config;
    public SyncCrypto(ObjectMapper json, SyncProperties config) {
        this.json=json; this.config=config;
        if (config.isEnabled()) {
            String probe="sync-key-pair-check";
            if (!verifyBytes(probe.getBytes(StandardCharsets.UTF_8),signBytes(probe.getBytes(StandardCharsets.UTF_8)),config.getPublicKey()))
                throw new IllegalArgumentException("Sync signing key pair does not match");
            config.getPeers().forEach(p->publicKey(p.publicKey()));
        }
    }
    public byte[] canonical(JsonNode node) {
        try { return json.writeValueAsBytes(sorted(node)); }
        catch (Exception e) { throw new IllegalArgumentException("INVALID_JSON"); }
    }
    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            var result=json.createObjectNode(); List<String> names=new ArrayList<>(); node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names); names.forEach(n->result.set(n,sorted(node.get(n)))); return result;
        }
        if (node.isArray()) { var result=json.createArrayNode(); node.forEach(n->result.add(sorted(n))); return result; }
        return node;
    }
    private byte[] signingBytes(SyncProtocol.Event event) {
        var node=json.valueToTree(event); ((com.fasterxml.jackson.databind.node.ObjectNode)node).remove("signature");
        return canonical(node);
    }
    public SyncProtocol.Event sign(SyncProtocol.Event event) {
        return new SyncProtocol.Event(event.eventId(),event.originNodeId(),event.entityType(),event.entityId(),event.operation(),event.version(),event.occurredAt(),event.payload(),signBytes(signingBytes(event)));
    }
    public boolean verify(SyncProtocol.Event event) {
        String key;
        try { key=event.originNodeId().equals(config.getNodeId())?config.getPublicKey():config.peer(event.originNodeId()).publicKey(); }
        catch (Exception e) { return false; }
        return verifyBytes(signingBytes(event),event.signature(),key);
    }
    private String signBytes(byte[] bytes) {
        try {
            PrivateKey key=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(config.getPrivateKey())));
            Signature signature=Signature.getInstance("Ed25519"); signature.initSign(key); signature.update(bytes);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) { throw new IllegalArgumentException("Invalid sync private key"); }
    }
    private PublicKey publicKey(String encoded) {
        try { return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded))); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid sync public key"); }
    }
    private boolean verifyBytes(byte[] bytes,String encoded,String key) {
        try { Signature signature=Signature.getInstance("Ed25519"); signature.initVerify(publicKey(key)); signature.update(bytes); return signature.verify(Base64.getDecoder().decode(encoded)); }
        catch (Exception e) { return false; }
    }
    public String hash(JsonNode node) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(node))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
