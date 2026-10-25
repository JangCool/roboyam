package com.community.dm.controller;

import com.community.auth.dto.AuthUser;
import com.community.dm.dto.request.DmSendRequest;
import com.community.dm.dto.request.DmSettingsRequest;
import com.community.dm.dto.response.DmCanSendView;
import com.community.dm.dto.response.DmRecipientListView;
import com.community.dm.dto.response.DmRoomView;
import com.community.dm.dto.response.DmSendResultView;
import com.community.dm.dto.response.DmSettingsView;
import com.community.dm.dto.response.DmThreadListView;
import com.community.dm.dto.response.DmUnreadCountView;
import com.community.dm.dto.response.MyDmView;
import com.community.dm.service.DmService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쪽지 API(공개 사이트) — user-web {@code useDmApi.ts} 의 주소·모양 그대로. 로그인 필요(401, {@code SecurityConfig} 의 {@code /api/**}), 내 쪽지만.
 * 개인 정보라 {@code Cache-Control: no-store}, 쓰기는 CSRF 헤더가 필요하다. 규칙·오류는 {@link DmService}.
 */
@RestController
@RequestMapping("/api")
public class DmController {

    private final DmService service;

    public DmController(DmService service) {
        this.service = service;
    }

    @GetMapping("/me/dm")
    public ResponseEntity<ApiResponse<MyDmView>> me(Authentication authentication) {
        return ok(service.me(userId(authentication)));
    }

    @GetMapping("/dm/unread-count")
    public ResponseEntity<ApiResponse<DmUnreadCountView>> unreadCount(Authentication authentication) {
        return ok(new DmUnreadCountView(service.unreadCount(userId(authentication))));
    }

    @GetMapping("/me/dm/settings")
    public ResponseEntity<ApiResponse<DmSettingsView>> settings(Authentication authentication) {
        return ok(service.settings(userId(authentication)));
    }

    @PutMapping("/me/dm/settings")
    public ResponseEntity<ApiResponse<DmSettingsView>> updateSettings(@RequestBody(required = false) DmSettingsRequest body, Authentication authentication) {
        return ok(service.updateSettings(userId(authentication), body == null ? null : body.acceptDm()));
    }

    /** 쪽지함 — {@code size}(기본 20, 1~50)·{@code cursor} */
    @GetMapping("/dm/threads")
    public ResponseEntity<ApiResponse<DmThreadListView>> threads(@RequestParam(required = false) Integer size, @RequestParam(required = false) String cursor,
                                                                 Authentication authentication) {
        return ok(service.threads(userId(authentication), size, cursor));
    }

    /** 대화방 — {@code size}(기본 30, 1~50)·{@code cursor}(더 오래된 쪽). 읽음 처리는 하지 않는다 */
    @GetMapping("/dm/threads/{id}")
    public ResponseEntity<ApiResponse<DmRoomView>> thread(@PathVariable String id, @RequestParam(required = false) Integer size,
                                                          @RequestParam(required = false) String cursor, Authentication authentication) {
        return ok(service.room(userId(authentication), id, size, cursor));
    }

    @PostMapping("/dm/threads/{id}/read")
    public ResponseEntity<Void> read(@PathVariable String id, Authentication authentication) {
        service.markRead(userId(authentication), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/dm/threads/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id, Authentication authentication) {
        service.remove(userId(authentication), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/dm/recipients")
    public ResponseEntity<ApiResponse<DmRecipientListView>> recipients(@RequestParam(required = false) String q, Authentication authentication) {
        return ok(service.recipients(userId(authentication), q));
    }

    @GetMapping("/users/{id}/dm")
    public ResponseEntity<ApiResponse<DmCanSendView>> canSend(@PathVariable String id, Authentication authentication) {
        return ok(service.canSend(userId(authentication), id));
    }

    @PostMapping("/users/{id}/dm")
    public ResponseEntity<ApiResponse<DmSendResultView>> send(@PathVariable String id, @RequestBody(required = false) DmSendRequest body,
                                                              Authentication authentication) {
        DmSendResultView result = service.send(userId(authentication), id, body == null ? null : body.body());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    /** 로그인한 회원 — 보안 설정이 막지만 혹시 빠져도 401 로 닫는다 */
    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
