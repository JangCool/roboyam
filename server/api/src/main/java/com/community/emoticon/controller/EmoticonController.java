package com.community.emoticon.controller;

import com.community.auth.dto.AuthUser;
import com.community.emoticon.dto.request.EmoticonPurchaseRequest;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.dto.response.EmoticonPurchaseView;
import com.community.emoticon.service.EmoticonAcquireService;
import com.community.emoticon.service.EmoticonPurchaseService;
import com.community.emoticon.service.EmoticonSettingsService;
import com.community.emoticon.service.EmoticonShopService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로보티콘 공개 API(docs/features/emoticon-contract.md S1 — user-web {@code useEmoticonApi} 머리말 그대로). 읽기는 로그인 없이({@code SecurityConfig} 공개 GET), 받기는 로그인(401)·CSRF.
 * <ul>
 *   <li>{@code GET /catalog} — {@code Cache-Control: public, max-age=300} + {@code ETag}(내용의 지문, 같으면 304 — 304 에도 두 머리글이 온다). 로그인·정지와 무관.</li>
 *   <li>{@code GET /rules} — 운영자가 바꾸는 공개 설정이라 {@code no-cache}(브라우저가 쓸 때마다 묻는다).</li>
 *   <li>{@code GET /packs}·{@code /packs/{id}} — 내 보유({@code acquired})가 실려 {@code no-store}.</li>
 *   <li>{@code POST /packs/{id}/acquire} — 무료 팩 받기(멱등).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/emoticons")
public class EmoticonController {

    /** 카탈로그 캐시 머리글(계약 — 그대로의 글자) */
    public static final String CATALOG_CACHE = "public, max-age=300";

    private final EmoticonShopService shop;
    private final EmoticonSettingsService settings;
    private final EmoticonAcquireService acquire;
    private final EmoticonPurchaseService purchases;

    public EmoticonController(EmoticonShopService shop, EmoticonSettingsService settings, EmoticonAcquireService acquire, EmoticonPurchaseService purchases) {
        this.shop = shop;
        this.settings = settings;
        this.acquire = acquire;
        this.purchases = purchases;
    }

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<EmoticonPublicView.Catalog>> catalog() {
        EmoticonShopService.CatalogResult result = shop.catalog();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, CATALOG_CACHE).eTag("\"" + result.etag() + "\"").body(ApiResponse.success(result.catalog()));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<EmoticonPublicView.Rules>> rules() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(ApiResponse.success(settings.rules()));
    }

    /** 상점 목록 — 빈 값·모르는 거르기는 「전체」, 모르는 정렬은 「인기」 */
    @GetMapping("/packs")
    public ResponseEntity<ApiResponse<EmoticonPublicView.PackList>> packs(@RequestParam(required = false) String source, @RequestParam(required = false) String price,
                                                                          @RequestParam(required = false) String sort, @RequestParam(required = false) String q,
                                                                          @RequestParam(required = false) String cursor, Authentication authentication) {
        return noStore(shop.packs(userIdOrNull(authentication), source, price, sort, q, cursor));
    }

    @GetMapping("/packs/{id}")
    public ResponseEntity<ApiResponse<EmoticonPublicView.PackDetail>> pack(@PathVariable String id, Authentication authentication) {
        return noStore(shop.pack(userIdOrNull(authentication), id));
    }

    @PostMapping("/packs/{id}/acquire")
    public ResponseEntity<ApiResponse<EmoticonPublicView.AcquireResult>> acquire(@PathVariable String id, Authentication authentication) {
        return noStore(acquire.acquire(userId(authentication), id).result());
    }

    /**
     * 유료 팩 결제 준비(S3) {@code { agreeRefundNotice: true }} → 결제창 값({@code orderType ROBOTIKON_PACK} · {@code orderRefId} = 구매 번호 · {@code checkout} · {@code customer}).
     * 이후 공용 {@code POST /api/payments/{paymentId}/complete}. 오류 차례는 {@code EmoticonPurchaseService}. 상태를 새로 만들지 않는 결제창 준비라 멱등 키·속도 제한은 두지 않는다
     * (다시 누르면 앞의 결제 대기를 닫고 새로 준비한다).
     */
    @PostMapping("/packs/{id}/purchase")
    public ResponseEntity<ApiResponse<EmoticonPurchaseView.PurchaseStart>> purchase(@PathVariable String id, @RequestBody(required = false) EmoticonPurchaseRequest body,
                                                                                   Authentication authentication) {
        return noStore(purchases.start(userId(authentication), id, body));
    }

    static Long userIdOrNull(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }

    static long userId(Authentication authentication) {
        Long id = userIdOrNull(authentication);
        if (id == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return id;
    }

    static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
