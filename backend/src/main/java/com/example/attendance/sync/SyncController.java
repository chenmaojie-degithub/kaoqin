package com.example.attendance.sync;

import com.example.attendance.common.ApiResponse;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/sync") @RequiredArgsConstructor
public class SyncController {
    private final SyncService service;
    private final SyncStore store;
    private final ObjectMapper json;
    @PostMapping("/push")
    public ApiResponse<SyncProtocol.Ack> push(@RequestBody String body,Authentication authentication) {
        SyncProtocol.Push push;
        try { push=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(body,SyncProtocol.Push.class); }
        catch(Exception e) { throw new IllegalArgumentException("INVALID_JSON"); }
        return ApiResponse.ok(service.receive(push,authentication.getName()));
    }
    @GetMapping("/pull")
    public ApiResponse<SyncProtocol.Page> pull(@RequestParam(defaultValue="0") long cursor,@RequestParam(defaultValue="100") int limit) { return ApiResponse.ok(store.page(cursor,limit)); }
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error(40000,"同步协议或字段无效")); }
}
