package com.community.pointgame.controller;

import com.community.auth.dto.AuthUser;
import com.community.pointgame.dto.request.LottoDrawRequest;
import com.community.pointgame.dto.response.LottoDrawResultView;
import com.community.pointgame.dto.response.LottoStatusView;
import com.community.pointgame.dto.response.RouletteSpinResultView;
import com.community.pointgame.dto.response.RouletteStatusView;
import com.community.pointgame.service.MemberPointGameService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 게임(공개 사이트 — docs/features/point-games-contract.md 「API 후보」) — 룰렛·로또 번호 뽑기. 로그인 필요(없으면 401), 본인 것만, 쓰기는 CSRF, {@code Cache-Control: no-store}.
 * 적립(무상) 포인트만 쓴다. 규칙은 {@link MemberPointGameService}.
 */
@RestController
@RequestMapping("/api/me/point-games")
public class MyPointGameController {

    private final MemberPointGameService service;

    public MyPointGameController(MemberPointGameService service) {
        this.service = service;
    }

    /** 룰렛 — 값·하루 횟수·오늘 돌린 횟수·상품표·적립 잔액·다시 차는 시각. 사업자 403 {@code BUSINESS_ACCOUNT_RESTRICTED} */
    @GetMapping("/roulette")
    public ResponseEntity<ApiResponse<RouletteStatusView>> roulette(Authentication authentication) {
        return noStore(service.roulette(userId(authentication)));
    }

    /** 한 번 돌린다 — 409 {@code POINT_GAME_DAILY_LIMIT} · 409 {@code POINT_INSUFFICIENT} · 403 {@code ACCOUNT_RESTRICTED}·{@code BUSINESS_ACCOUNT_RESTRICTED}·{@code MEMBER_RESTRICTED} */
    @PostMapping("/roulette/spins")
    public ResponseEntity<ApiResponse<RouletteSpinResultView>> spin(Authentication authentication) {
        return noStore(service.spin(userId(authentication)));
    }

    /** 로또 — 값·보관 한도·내 번호(최신이 위)·당첨 확인 링크 */
    @GetMapping("/lotto")
    public ResponseEntity<ApiResponse<LottoStatusView>> lotto(Authentication authentication) {
        return noStore(service.lotto(userId(authentication)));
    }

    /** 번호 뽑기 — 400 {@code VALIDATION}({@code fields.sets|mode|fixed|excluded}) · 409 {@code LOTTO_STORAGE_FULL} · 409 {@code POINT_INSUFFICIENT} · 403 (룰렛과 같다) */
    @PostMapping("/lotto/draws")
    public ResponseEntity<ApiResponse<LottoDrawResultView>> draw(@RequestBody(required = false) LottoDrawRequest body, Authentication authentication) {
        return noStore(service.draw(userId(authentication), body));
    }

    /** 내 번호 한 줄 지우기 — 204(이미 없어도 204), 남의 줄 404 {@code LOTTO_DRAW_NOT_FOUND} */
    @DeleteMapping("/lotto/draws/{id}")
    public ResponseEntity<Void> removeDraw(@PathVariable String id, Authentication authentication) {
        service.removeDraw(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static long userId(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }
}
