package com.community.search.controller;

import com.community.search.dto.response.SearchResponse;
import com.community.search.service.SearchService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색(공개 사이트) — 모양은 user-web {@code useSearchApi.types.ts}. 로그인 없이 읽는다({@code SecurityConfig}).
 * 쿼리 값은 글자로 받아 서비스가 칸별로 모아 검사한다. 시간 제한을 넘기면 {@code 503 SEARCH_TIMEOUT}.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    /** {@code q} · {@code scope}(all|title|body|author) · {@code board}(slug) · {@code order}(latest|oldest) · {@code size}(1~50) · {@code page}(1~10) 또는 {@code cursor} */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(@RequestParam(required = false) String q, @RequestParam(required = false) String scope,
                                                              @RequestParam(required = false) String board, @RequestParam(required = false) String order,
                                                              @RequestParam(required = false) String page, @RequestParam(required = false) String cursor,
                                                              @RequestParam(required = false) String size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.search(q, scope, board, order, page, cursor, size)));
    }
}
