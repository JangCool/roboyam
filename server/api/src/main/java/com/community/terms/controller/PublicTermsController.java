package com.community.terms.controller;

import com.community.terms.dto.response.PublicTermsPageResponse;
import com.community.terms.dto.response.PublicTermsVersionResponse;
import com.community.terms.dto.response.TermsSummaryResponse;
import com.community.terms.service.PublicTermsService;
import com.community.zconfiguration.response.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 약관 API(docs/features/terms-contract.md) — 로그인 없이 부른다. 경로의 {@code {kind}} 는 {@code terms}·{@code privacy}(모르면 404).
 * 캐시 규칙은 {@code /api/site/info} 와 같다 — 브라우저는 쓸 때마다 묻고({@code Cache-Control: no-cache}), {@code ETag}(본문 내용의 지문)가 같으면 {@code 304} 로 답한다.
 */
@RestController
@RequestMapping("/api/terms")
public class PublicTermsController {

    private final PublicTermsService service;

    public PublicTermsController(PublicTermsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<TermsSummaryResponse>> summary() {
        return revalidated(service.summary());
    }

    @GetMapping("/{kind}")
    public ResponseEntity<ApiResponse<PublicTermsPageResponse>> page(@PathVariable String kind) {
        return revalidated(service.page(kind));
    }

    @GetMapping("/{kind}/versions/{version}")
    public ResponseEntity<ApiResponse<PublicTermsVersionResponse>> version(@PathVariable String kind, @PathVariable int version) {
        return revalidated(service.version(kind, version));
    }

    /** 응답 본문은 레코드라 {@code toString} 이 칸 값을 모두 싣는다 — 그 지문이 {@code ETag}(공통 래퍼의 시각·요청 ID 는 넣지 않는다) */
    private static <T> ResponseEntity<ApiResponse<T>> revalidated(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(fingerprint(String.valueOf(body))).body(ApiResponse.success(body));
    }

    private static String fingerprint(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
