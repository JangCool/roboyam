package com.community.boardstaff.controller;

import com.community.auth.dto.AuthUser;
import com.community.boardstaff.dto.request.BoardSanctionIssueRequest;
import com.community.boardstaff.dto.request.BoardSanctionLiftRequest;
import com.community.boardstaff.dto.response.BoardSanctionIssuedView;
import com.community.boardstaff.dto.response.BoardSanctionPreviewView;
import com.community.boardstaff.dto.response.BoardStaffSanctionPageView;
import com.community.boardstaff.dto.response.BoardStaffSanctionRowView;
import com.community.boardstaff.service.BoardStaffService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
 * 게시판 매니저·스텝 도구(공개 사이트 — user-web {@code useBoardStaffApi.ts}, docs/features/board-sanction-contract.md 「API 후보 > 공개」).
 * 로그인 필요({@code /api/**} 보안 규칙 — 없으면 401), 쓰기는 CSRF, 응답은 {@code Cache-Control: no-store}. 판정은 처리할 때마다 서버가 다시 한다.
 *
 * <ul>
 *   <li>{@code GET /api/board-staff/sanctions/preview?boardId=&userId=} → {@code BoardSanctionPreview} · 403 {@code BOARD_STAFF_FORBIDDEN} ·
 *       409 {@code BOARD_STAFF_TARGET_PROTECTED}</li>
 *   <li>{@code POST /api/board-staff/sanctions} → 201 {@code BoardSanctionIssued} · 400 {@code fields.kind|reasonCode|reasonText|postId|commentId} · 403 ·
 *       409 {@code BOARD_STAFF_TARGET_PROTECTED}·{@code BOARD_STAFF_ALREADY_RESTRICTED}·{@code BOARD_STAFF_DAILY_LIMIT}(+{@code limit}·{@code used}·{@code resetsAt})</li>
 *   <li>{@code GET /api/board-staff/boards/{boardId}/sanctions?scope=ALL|ACTIVE|ENDED&page=} → {@code BoardStaffSanctionPage} · 404 {@code BOARD_STAFF_NOT_FOUND}</li>
 *   <li>{@code POST /api/board-staff/sanctions/{id}/lift} {@code { reason }} → 바뀐 줄 · 400 {@code fields.reason} · 403(매니저 + {@code board.staff-action.restore} 만) ·
 *       404 {@code BOARD_SANCTION_NOT_FOUND} · 409 {@code BOARD_SANCTION_NOT_LIFTABLE}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/board-staff")
public class BoardStaffController {

    private final BoardStaffService service;

    public BoardStaffController(BoardStaffService service) {
        this.service = service;
    }

    @GetMapping("/sanctions/preview")
    public ResponseEntity<ApiResponse<BoardSanctionPreviewView>> preview(@RequestParam(required = false) String boardId, @RequestParam(required = false) String userId,
                                                                         Authentication authentication) {
        return noStore(service.preview(principal(authentication).id(), boardId, userId));
    }

    @PostMapping("/sanctions")
    public ResponseEntity<ApiResponse<BoardSanctionIssuedView>> issue(@RequestBody(required = false) BoardSanctionIssueRequest body, Authentication authentication) {
        BoardSanctionIssuedView issued = service.issue(principal(authentication).id(), body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(issued));
    }

    @GetMapping("/boards/{boardId}/sanctions")
    public ResponseEntity<ApiResponse<BoardStaffSanctionPageView>> list(@PathVariable String boardId, @RequestParam(required = false) String scope,
                                                                        @RequestParam(required = false) String page, Authentication authentication) {
        return noStore(service.list(principal(authentication).id(), boardId, scope, page));
    }

    @PostMapping("/sanctions/{id}/lift")
    public ResponseEntity<ApiResponse<BoardStaffSanctionRowView>> lift(@PathVariable String id, @RequestBody(required = false) BoardSanctionLiftRequest body,
                                                                       Authentication authentication) {
        return noStore(service.lift(principal(authentication).id(), id, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static AuthUser principal(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }
}
