package com.community.home.controller;

import com.community.home.dto.response.PopularListResponse;
import com.community.home.service.LatestFeedService;
import com.community.home.service.PopularService;
import com.community.post.dto.response.PostFeedPageView;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 최신글 전체·인기글 전체(공개 사이트) — 모양은 user-web {@code useHomeApi.types.ts}. 로그인 없이 읽는다({@code SecurityConfig}).
 * 쿼리 값은 글자로 받아 서비스가 칸별로 모아 검사한다(숫자가 아닌 값도 {@code 400 VALIDATION fields.*}).
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final LatestFeedService latest;
    private final PopularService popular;

    public HomeController(LatestFeedService latest, PopularService popular) {
        this.latest = latest;
        this.popular = popular;
    }

    /** 최신글 — {@code size}(1~50, 기본 20) · {@code cursor}(이전 응답의 nextCursor) */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<PostFeedPageView>> latest(@RequestParam(required = false) String size, @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(latest.latest(size, cursor)));
    }

    /** 인기글 — {@code period}(REALTIME 기본 · YESTERDAY · WEEK, 대소문자 무시) · {@code page}(1부터) · {@code size}(1~50, 기본 20) */
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PopularListResponse>> popular(@RequestParam(required = false) String period, @RequestParam(required = false) String page,
                                                                   @RequestParam(required = false) String size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(popular.popular(period, page, size)));
    }
}
