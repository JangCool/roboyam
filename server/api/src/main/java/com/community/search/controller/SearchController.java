package com.community.search.controller;

import com.community.analytics.collect.VisitCollector;
import com.community.auth.dto.AuthUser;
import com.community.search.dto.response.SearchResponse;
import com.community.search.service.SearchService;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색(공개 사이트) — 모양은 user-web {@code useSearchApi.types.ts}. 로그인 없이 읽는다({@code SecurityConfig}).
 * 쿼리 값은 글자로 받아 서비스가 칸별로 모아 검사한다. 시간 제한을 넘기면 {@code 503 SEARCH_TIMEOUT}.
 *
 * <p>검색어 통계(site-analytics-contract 「2단계 — 검색어」): 검색이 끝난 <b>첫 쪽 응답</b>({@code page = 1}, 커서 더보기·다른 쪽은 아님)만 {@link VisitCollector#countSearch} 로
 * 메모리에 센다(DB 에 쓰지 않는다 — 1분 몰아쓰기). 검사 오류·시간 초과는 세지 않는다. 설정 「검색어」가 꺼져 있거나 봇이거나, 로그인 회원이 뺄 계정(운영자 회원 계정·AI 회원 —
 * 3단계)이면 수집기가 버린다.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;
    private final VisitCollector visits;

    public SearchController(SearchService service, VisitCollector visits) {
        this.service = service;
        this.visits = visits;
    }

    /** {@code q} · {@code scope}(all|title|body|author) · {@code board}(slug) · {@code order}(latest|oldest) · {@code size}(1~50) · {@code page}(1~10) 또는 {@code cursor} */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(@RequestParam(required = false) String q, @RequestParam(required = false) String scope,
                                                              @RequestParam(required = false) String board, @RequestParam(required = false) String order,
                                                              @RequestParam(required = false) String page, @RequestParam(required = false) String cursor,
                                                              @RequestParam(required = false) String size, Authentication authentication,
                                                              HttpServletRequest request) {
        Long viewer = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        SearchResponse result = service.search(q, scope, board, order, page, cursor, size, viewer);
        if (result.page() != null && result.page() == 1) {
            visits.countSearch(q, result.items().isEmpty(), request.getHeader(HttpHeaders.USER_AGENT), viewer); // 뺄 계정(운영자 회원 계정·AI 회원)은 세지 않는다(3단계)
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }
}
