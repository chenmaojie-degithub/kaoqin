package com.example.attendance.sync;

import com.example.attendance.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/sync/admin") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class SyncAdminController {
    private final SyncWorker worker;
    @GetMapping("/status") public ApiResponse<Map<String,Object>> status() { return ApiResponse.ok(worker.status()); }
    @PostMapping("/push") public ApiResponse<Map<String,Object>> push() { return ApiResponse.ok(worker.run("PUSH",true)); }
    @PostMapping("/pull") public ApiResponse<Map<String,Object>> pull() { return ApiResponse.ok(worker.run("PULL",true)); }
}
