package com.community.hotdeal.controller;

import com.community.auth.dto.AuthUser;
import com.community.hotdeal.dto.response.DealLinkCheckView;
import com.community.hotdeal.dto.response.DealPrefillView;
import com.community.hotdeal.dto.response.DealSameRefView;
import com.community.hotdeal.service.DealGoService;
import com.community.hotdeal.service.DealLinkService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 핫딜 — 모양은 user-web {@code useHotdealApi.types.ts} 그대로. 접근 규칙은 {@code SecurityConfig}: 판정·미리 채움은 로그인 필요(없으면 401), 구매하기 {@code /go} 와 같은 딜
 * ({@code /api/posts/{id}/deal/same}) 은 로그인 없이. 보는 사람마다 다를 수 있어 모두 {@code no-store}.
 */
@RestController
public class DealController {

    private final DealLinkService links;
    private final DealGoService go;
    private final ClientIpResolver ipResolver;

    public DealController(DealLinkService links, DealGoService go, ClientIpResolver ipResolver) {
        this.links = links;
        this.go = go;
        this.ipResolver = ipResolver;
    }

    /** 링크 판정 + OG + 같은 딜 — 회원 분당 10회(429 DEAL_LINK_CHECK_RATE_LIMIT) */
    @GetMapping("/api/deals/link-check")
    public ResponseEntity<ApiResponse<DealLinkCheckView>> linkCheck(@RequestParam(required = false) String url, @RequestParam(required = false) String board,
                                                                    Authentication authentication) {
        return noStore(ApiResponse.success(links.linkCheck(url, board, userId(authentication))));
    }

    /** 끝난 딜에서 새 딜 올리기 — 링크·몰·이미지·제목 */
    @GetMapping("/api/deals/prefill")
    public ResponseEntity<ApiResponse<DealPrefillView>> prefill(@RequestParam(required = false) String from, Authentication authentication) {
        return noStore(ApiResponse.success(links.prefill(from, userId(authentication))));
    }

    /** 이 글과 같은 딜(공개·진행 중, 이 글 제외) */
    @GetMapping("/api/posts/{id}/deal/same")
    public ResponseEntity<ApiResponse<List<DealSameRefView>>> same(@PathVariable String id) {
        return noStore(ApiResponse.success(links.same(id)));
    }

    /** 구매하기 — 302(서버 경유, 클릭 시점의 제휴 방식으로 바꾼다). 본문 없음 */
    @GetMapping("/api/deals/{postId}/go")
    public ResponseEntity<Void> go(@PathVariable String postId, Authentication authentication, HttpServletRequest request) {
        String target = go.target(postId, new DealGoService.Visitor(userId(authentication), ipResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT)));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(URI.create(target).toASCIIString())).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").build();
    }

    static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }

    static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
