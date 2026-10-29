package com.community.home.controller;

import com.community.auth.dto.AuthUser;
import com.community.home.dto.response.HomeGadgetsResponse;
import com.community.home.dto.response.PopularListResponse;
import com.community.home.service.HomeGadgetService;
import com.community.home.service.LatestFeedService;
import com.community.home.service.PopularService;
import com.community.post.dto.response.PostFeedPageView;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 최신글 전체·인기글 전체·홈 가젯(공개 사이트) — 모양은 user-web {@code useHomeApi.types.ts}·{@code useHomeGadgetApi.types.ts}. 로그인 없이 읽는다({@code SecurityConfig}).
 * 쿼리 값은 글자로 받아 서비스가 칸별로 모아 검사한다(숫자가 아닌 값도 {@code 400 VALIDATION fields.*}).
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final LatestFeedService latest;
    private final PopularService popular;
    private final HomeGadgetService gadgets;

    public HomeController(LatestFeedService latest, PopularService popular, HomeGadgetService gadgets) {
        this.latest = latest;
        this.popular = popular;
        this.gadgets = gadgets;
    }

    /**
     * 지금 사이트 테마의 홈 구성 + 가젯마다 데이터. {@code theme} 은 관리자 테마 화면 미리보기만 싣는다 — 그 테마의 구성(저장한 것, 없으면 기본 배치)을 준다.
     * 모르는 테마 값은 무시하고 사이트 테마로 준다(공개 데이터만 담겨 권한은 따로 보지 않는다).
     */
    @GetMapping("/gadgets")
    public ResponseEntity<ApiResponse<HomeGadgetsResponse>> gadgets(@RequestParam(required = false) String theme, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(gadgets.gadgets(theme, viewerId(authentication))));
    }

    /** 최신글 — {@code size}(1~50, 기본 20) · {@code cursor}(이전 응답의 nextCursor) */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<PostFeedPageView>> latest(@RequestParam(required = false) String size, @RequestParam(required = false) String cursor,
                                                                Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(latest.latest(size, cursor, viewerId(authentication))));
    }

    /** 인기글 — {@code period}(REALTIME 기본 · YESTERDAY · WEEK, 대소문자 무시) · {@code page}(1부터) · {@code size}(1~50, 기본 20) */
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PopularListResponse>> popular(@RequestParam(required = false) String period, @RequestParam(required = false) String page,
                                                                   @RequestParam(required = false) String size, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(popular.popular(period, page, size, viewerId(authentication))));
    }

    /** 로그인한 회원(비로그인 null) — 줄의 차단·메모를 싣는 기준 */
    private static Long viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
