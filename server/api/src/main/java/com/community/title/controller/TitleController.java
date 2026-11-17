package com.community.title.controller;

import com.community.auth.dto.AuthUser;
import com.community.title.dto.request.RepresentativeTitleRequest;
import com.community.title.dto.response.HallOfFameView;
import com.community.title.dto.response.MyTitlesView;
import com.community.title.dto.response.TitleCatalogItemView;
import com.community.title.service.MyTitleService;
import com.community.title.service.PublicTitleService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 칭호 공개 API(member-title-contract 「API 후보」·「구현 결정」 4·7).
 *
 * <ul>
 *   <li>{@code GET /api/titles} — 칭호 도감(누구나) · {@code GET /api/hall-of-fame} — 명예의 전당(누구나)</li>
 *   <li>{@code GET /api/me/titles} — 내 칭호(로그인 401) · {@code PUT /api/me/titles/representative} {@code { key | null, hidden }} → 바뀐 내 칭호 · 409 {@code TITLE_NOT_OWNED}</li>
 *   <li>{@code POST /api/me/titles/{key}/applications} → 201 신청 · 404 {@code TITLE_NOT_FOUND} · 409 {@code TITLE_NOT_APPLICABLE}·{@code TITLE_MEMBER_EXCLUDED}·
 *       {@code TITLE_ALREADY_GRANTED}·{@code TITLE_APPLICATION_PENDING}·{@code TITLE_REAPPLY_TOO_SOON}·{@code TITLE_CONDITIONS_UNMET}</li>
 *   <li>{@code GET /api/me/titles/unseen} → 축하 창에 띄울 받은 칭호만(가벼운 API — 점검 D14) · {@code POST /api/me/titles/seen} → 204 — 축하 창을 닫았다(지금 보지 않은 받은 칭호 전부)</li>
 * </ul>
 * 모든 응답 {@code Cache-Control: no-store}(내 값·보유자 수가 바뀐다).
 */
@RestController
public class TitleController {

    private final PublicTitleService publicTitles;
    private final MyTitleService myTitles;

    public TitleController(PublicTitleService publicTitles, MyTitleService myTitles) {
        this.publicTitles = publicTitles;
        this.myTitles = myTitles;
    }

    @GetMapping("/api/titles")
    public ResponseEntity<ApiResponse<List<TitleCatalogItemView>>> catalog() {
        return noStore(publicTitles.catalog());
    }

    @GetMapping("/api/hall-of-fame")
    public ResponseEntity<ApiResponse<HallOfFameView>> hallOfFame() {
        return noStore(publicTitles.hallOfFame());
    }

    @GetMapping("/api/me/titles")
    public ResponseEntity<ApiResponse<MyTitlesView>> mine(Authentication authentication) {
        return noStore(myTitles.mine(userId(authentication)));
    }

    /** 축하 창용 가벼운 목록(점검 D14) — 보지 않은 받은 칭호 {@code [{ key, label, labelEn, icon, tier, chip, ratio }]}(없으면 빈 목록) */
    @GetMapping("/api/me/titles/unseen")
    public ResponseEntity<ApiResponse<List<MyTitlesView.UnseenView>>> unseen(Authentication authentication) {
        return noStore(myTitles.unseen(userId(authentication)));
    }

    @PutMapping("/api/me/titles/representative")
    public ResponseEntity<ApiResponse<MyTitlesView>> representative(@RequestBody(required = false) RepresentativeTitleRequest body, Authentication authentication) {
        return noStore(myTitles.representative(userId(authentication), body));
    }

    @PostMapping("/api/me/titles/{key}/applications")
    public ResponseEntity<ApiResponse<MyTitlesView.ApplicationView>> apply(@PathVariable String key, Authentication authentication) {
        MyTitlesView.ApplicationView created = myTitles.apply(userId(authentication), key);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(created));
    }

    @PostMapping("/api/me/titles/seen")
    public ResponseEntity<Void> seen(Authentication authentication) {
        myTitles.seen(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static long userId(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
