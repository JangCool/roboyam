package com.community.jjal.controller;

import com.community.auth.dto.AuthUser;
import com.community.jjal.dto.response.JjalLookupView;
import com.community.jjal.service.JjalLookupService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글·댓글 속 짤의 표시 정보 {@code GET /api/jjals?ids=a,b&postId=} — 비회원도({@code SecurityConfig} 허용 경로). {@code postId}(선택)는 담기 가능 판정의 맥락 글.
 * 응답 {@code no-store}(내려감·담기 가능이 바뀔 수 있다).
 */
@RestController
@RequestMapping("/api/jjals")
public class JjalController {

    private final JjalLookupService service;

    public JjalController(JjalLookupService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<JjalLookupView>> lookup(@RequestParam(value = "ids", required = false) String ids,
                                                              @RequestParam(value = "postId", required = false) String postId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.lookup(ids, postId, userId(authentication))));
    }

    /** 로그인한 회원 ID(아니면 null) */
    static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
