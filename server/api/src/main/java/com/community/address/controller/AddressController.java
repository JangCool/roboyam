package com.community.address.controller;

import com.community.address.dto.response.AddressSearchConfigResponse;
import com.community.address.dto.response.AddressSearchPageResponse;
import com.community.address.service.AddressSearchPolicy;
import com.community.address.service.AddressSearchRateLimiter;
import com.community.address.service.AddressSearchService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 화면 우편번호 찾기(docs/features/address-search-contract.md) — 로그인 없이 부른다({@code SecurityConfig}). 설정은 비밀값이 없고, 검색은 IP 한도를 센다.
 */
@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final AddressSearchService service;
    private final AddressSearchRateLimiter limiter;
    private final ClientIpResolver ipResolver;

    public AddressController(AddressSearchService service, AddressSearchRateLimiter limiter, ClientIpResolver ipResolver) {
        this.service = service;
        this.limiter = limiter;
        this.ipResolver = ipResolver;
    }

    /** 제공처·다음 스크립트 주소 — 운영자가 바꾸면 다음 요청부터 따라오게 브라우저 사본을 쓰지 않는다 */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AddressSearchConfigResponse>> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(ApiResponse.success(service.config()));
    }

    /** 400 {@code VALIDATION}({@code fields.keyword}) · 429 {@code ADDRESS_SEARCH_RATE_LIMITED} · 503 {@code ADDRESS_DB_NOT_LOADED} */
    @GetMapping("/search")
    public ApiResponse<AddressSearchPageResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "" + AddressSearchPolicy.PAGE_SIZE_DEFAULT) int size,
                                                         HttpServletRequest request) {
        limiter.acquire(ipResolver.resolve(request));
        return ApiResponse.success(service.search(keyword, page, size));
    }
}
