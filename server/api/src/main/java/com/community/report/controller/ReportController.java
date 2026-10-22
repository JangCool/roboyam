package com.community.report.controller;

import com.community.auth.dto.AuthUser;
import com.community.report.dto.request.ReportRequest;
import com.community.report.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신고 — 회원만({@code SecurityConfig} 가 인증을 요구한다, 없으면 401). 성공은 204. 모양은 user-web {@code useReportApi.types.ts} 그대로 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> report(@RequestBody ReportRequest body, Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        service.report(body, userId);
        return ResponseEntity.noContent().build();
    }
}
