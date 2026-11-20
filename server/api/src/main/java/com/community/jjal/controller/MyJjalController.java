package com.community.jjal.controller;

import com.community.jjal.dto.request.JjalCollectRequest;
import com.community.jjal.dto.request.JjalOrderRequest;
import com.community.jjal.dto.response.JjalSaveResultView;
import com.community.jjal.dto.response.MyJjalListView;
import com.community.jjal.service.MyJjalService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 짤 보관함(docs/features/my-jjal-contract.md 「화면 확정 결과」) — 회원만({@code SecurityConfig} 의 {@code /api/**} 인증 — 비회원 401), 쓰기는 CSRF 헤더 필요. 응답 {@code no-store}.
 * <ul>
 *   <li>{@code GET /api/me/jjals} → {@code { items, count, limit, introAcknowledged }}(배열 순서 = 회원이 정한 순서)</li>
 *   <li>{@code POST /api/me/jjals}(multipart {@code file}) · {@code POST /api/me/jjals/collect { jjalId, postId, commentId? }} → {@code 201 { item, count, limit }}(새 짤은 맨 앞)</li>
 *   <li>{@code DELETE /api/me/jjals/{id}} · {@code PUT /api/me/jjals/order { ids }} · {@code POST /api/me/jjals/intro-ack} → 204</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/jjals")
public class MyJjalController {

    private final MyJjalService service;

    public MyJjalController(MyJjalService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyJjalListView>> list(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.list(JjalController.userId(authentication))));
    }

    /** 올리기 — 칸이 빠진 요청도 서비스가 칸 오류로 거절하도록 {@code required = false} */
    @PostMapping
    public ResponseEntity<ApiResponse<JjalSaveResultView>> upload(@RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        return created(service.upload(JjalController.userId(authentication), file));
    }

    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<JjalSaveResultView>> collect(@RequestBody(required = false) JjalCollectRequest body, Authentication authentication) {
        return created(service.collect(JjalController.userId(authentication), body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id, Authentication authentication) {
        service.remove(JjalController.userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public ResponseEntity<Void> order(@RequestBody(required = false) JjalOrderRequest body, Authentication authentication) {
        service.order(JjalController.userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/intro-ack")
    public ResponseEntity<Void> acknowledgeIntro(Authentication authentication) {
        service.acknowledgeIntro(JjalController.userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<ApiResponse<JjalSaveResultView>> created(JjalSaveResultView body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
