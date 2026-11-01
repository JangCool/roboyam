package com.community.hotdeal.controller;

import com.community.auth.dto.AuthUser;
import com.community.hotdeal.dto.request.DealEndRequestInput;
import com.community.hotdeal.dto.request.DealModerateInput;
import com.community.hotdeal.dto.response.DealLinkCheckView;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.service.DealStateService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final DealStateService states;

    public DealController(DealLinkService links, DealGoService go, ClientIpResolver ipResolver, DealStateService states) {
        this.states = states;
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

    /** 작성자 종료(ACTIVE·FLAGGED → ENDED AUTHOR) — 새 딜 */
    @PostMapping("/api/posts/{id}/deal/end")
    public ResponseEntity<ApiResponse<PostDealView>> end(@PathVariable String id, Authentication authentication) {
        return noStore(ApiResponse.success(states.end(id, userId(authentication))));
    }

    /** 작성자 되돌리기(D2 — 작성자 종료 뒤 24시간 안 한 번) */
    @PostMapping("/api/posts/{id}/deal/reopen")
    public ResponseEntity<ApiResponse<PostDealView>> reopen(@PathVariable String id, Authentication authentication) {
        return noStore(ApiResponse.success(states.reopen(id, userId(authentication))));
    }

    /** 「아직 진행 중」(FLAGGED → ACTIVE, keepMaxCount 까지) */
    @PostMapping("/api/posts/{id}/deal/keep")
    public ResponseEntity<ApiResponse<PostDealView>> keep(@PathVariable String id, Authentication authentication) {
        return noStore(ApiResponse.success(states.keep(id, userId(authentication))));
    }

    /** 종료 요청 {reason} */
    @PostMapping("/api/posts/{id}/deal/end-requests")
    public ResponseEntity<ApiResponse<PostDealView>> endRequest(@PathVariable String id, @RequestBody(required = false) DealEndRequestInput body,
                                                               Authentication authentication) {
        return noStore(ApiResponse.success(states.request(id, userId(authentication), body == null ? null : body.reason())));
    }

    /** 게시판 매니저·스텝 종료 {reason}(1~200자 필수) — 그 글 게시판의 매니저·스텝만(403 DEAL_MODERATE_FORBIDDEN) */
    @PostMapping("/api/posts/{id}/deal/moderate/end")
    public ResponseEntity<ApiResponse<PostDealView>> moderateEnd(@PathVariable String id, @RequestBody(required = false) DealModerateInput body,
                                                                Authentication authentication, HttpServletRequest request) {
        return noStore(ApiResponse.success(states.moderateEnd(id, userId(authentication), body == null ? null : body.reason(), ipResolver.resolve(request))));
    }

    /** 게시판 매니저·스텝 재개 {reason?}(0~200자) — 운영진 재개와 같은 리셋 */
    @PostMapping("/api/posts/{id}/deal/moderate/reopen")
    public ResponseEntity<ApiResponse<PostDealView>> moderateReopen(@PathVariable String id, @RequestBody(required = false) DealModerateInput body,
                                                                   Authentication authentication, HttpServletRequest request) {
        return noStore(ApiResponse.success(states.moderateReopen(id, userId(authentication), body == null ? null : body.reason(), ipResolver.resolve(request))));
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
