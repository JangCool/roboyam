package com.community.boardstaff.controller;

import com.community.auth.dto.AuthUser;
import com.community.boardstaff.dto.request.BoardSanctionIssueRequest;
import com.community.boardstaff.dto.request.BoardSanctionLiftRequest;
import com.community.boardstaff.dto.request.BoardStaffActionRestoreRequest;
import com.community.boardstaff.dto.request.BoardStaffCommentDeleteRequest;
import com.community.boardstaff.dto.request.BoardStaffPostActionRequest;
import com.community.boardstaff.dto.response.BoardStaffActionDoneView;
import com.community.boardstaff.dto.response.BoardStaffActionPageView;
import com.community.boardstaff.dto.response.BoardStaffActionRowView;
import com.community.boardstaff.dto.response.BoardSanctionIssuedView;
import com.community.boardstaff.dto.response.BoardSanctionPreviewView;
import com.community.boardstaff.dto.response.BoardStaffSanctionPageView;
import com.community.boardstaff.dto.response.BoardStaffSanctionRowView;
import com.community.boardstaff.service.BoardStaffService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
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
 *       409 {@code BOARD_STAFF_TARGET_PROTECTED}(익명 대상 {@code anonymousPostId|anonymousCommentId} 이거나 익명 글·댓글의 스텝 처리면 작성자 보호는 까닭을 가린 409
 *       {@code ANONYMOUS_TARGET_UNAVAILABLE} — 부여·스텝 처리도 같다)</li>
 *   <li>{@code POST /api/board-staff/sanctions} → 201 {@code BoardSanctionIssued} · 400 {@code fields.kind|reasonCode|reasonText|postId|commentId} · 403 ·
 *       409 {@code BOARD_STAFF_TARGET_PROTECTED}·{@code BOARD_STAFF_ALREADY_RESTRICTED}·{@code BOARD_STAFF_DAILY_LIMIT}(+{@code limit}·{@code used}·{@code resetsAt})</li>
 *   <li>{@code GET /api/board-staff/boards/{boardId}/sanctions?scope=ALL|ACTIVE|ENDED&page=} → {@code BoardStaffSanctionPage} · 404 {@code BOARD_STAFF_NOT_FOUND}</li>
 *   <li>{@code POST /api/board-staff/sanctions/{id}/lift} {@code { reason }} → 바뀐 줄 · 400 {@code fields.reason} · 403(매니저 + {@code board.staff-action.restore} 만) ·
 *       404 {@code BOARD_SANCTION_NOT_FOUND} · 409 {@code BOARD_SANCTION_NOT_LIFTABLE}</li>
 *   <li>스텝 처리(board-staff-action-contract): {@code POST /posts/{postId}/hide|delete { reasonCode }} · {@code POST /comments/{commentId}/delete { postId, reasonCode }}
 *       → 200 {@code { quota, resetsAt }} · 404 {@code POST_NOT_FOUND}·{@code COMMENT_NOT_FOUND} → 403 {@code BOARD_STAFF_FORBIDDEN} → 400 {@code fields.reasonCode} →
 *       409 {@code BOARD_STAFF_TARGET_PROTECTED} → 409 {@code POST_STATE_INVALID}·{@code COMMENT_STATE_INVALID} → 409 {@code BOARD_STAFF_DAILY_LIMIT}(+{@code kind}·{@code limit}·{@code used}·{@code resetsAt})</li>
 *   <li>{@code GET /boards/{boardId}/actions?kind=ALL|POST|COMMENT&page=}(1부터, 20개) → {@code BoardStaffActionPage} · 404 {@code BOARD_STAFF_NOT_FOUND}</li>
 *   <li>{@code POST /actions/{id}/restore { reason }} → 바뀐 줄 · 400 {@code fields.reason} · 403(매니저 + {@code board.staff-action.restore} 만) · 404 {@code BOARD_STAFF_ACTION_NOT_FOUND}
 *       · 409 {@code BOARD_STAFF_ACTION_RESTORED}·{@code POST_STATE_INVALID}·{@code POST_PURGED}·{@code COMMENT_STATE_INVALID}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/board-staff")
public class BoardStaffController {

    private final BoardStaffService service;
    private final ClientIpResolver clientIps;

    public BoardStaffController(BoardStaffService service, ClientIpResolver clientIps) {
        this.service = service;
        this.clientIps = clientIps;
    }

    @GetMapping("/sanctions/preview")
    public ResponseEntity<ApiResponse<BoardSanctionPreviewView>> preview(@RequestParam(required = false) String boardId, @RequestParam(required = false) String userId,
                                                                         @RequestParam(required = false) String anonymousPostId,
                                                                         @RequestParam(required = false) String anonymousCommentId,
                                                                         Authentication authentication) {
        return noStore(service.preview(principal(authentication).id(), boardId, userId, anonymousPostId, anonymousCommentId));
    }

    @PostMapping("/sanctions")
    public ResponseEntity<ApiResponse<BoardSanctionIssuedView>> issue(@RequestBody(required = false) BoardSanctionIssueRequest body, Authentication authentication,
                                                                 HttpServletRequest request) {
        BoardSanctionIssuedView issued = service.issue(principal(authentication).id(), body, clientIps.resolve(request));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(issued));
    }

    @GetMapping("/boards/{boardId}/sanctions")
    public ResponseEntity<ApiResponse<BoardStaffSanctionPageView>> list(@PathVariable String boardId, @RequestParam(required = false) String scope,
                                                                        @RequestParam(required = false) String page, Authentication authentication) {
        return noStore(service.list(principal(authentication).id(), boardId, scope, page));
    }

    @PostMapping("/sanctions/{id}/lift")
    public ResponseEntity<ApiResponse<BoardStaffSanctionRowView>> lift(@PathVariable String id, @RequestBody(required = false) BoardSanctionLiftRequest body,
                                                                       Authentication authentication, HttpServletRequest request) {
        return noStore(service.lift(principal(authentication).id(), id, body, clientIps.resolve(request)));
    }

    // ── 스텝 처리(docs/features/board-staff-action-contract.md 「API > 공개」) ──

    @PostMapping("/posts/{postId}/hide")
    public ResponseEntity<ApiResponse<BoardStaffActionDoneView>> hidePost(@PathVariable String postId, @RequestBody(required = false) BoardStaffPostActionRequest body,
                                                                         Authentication authentication, HttpServletRequest request) {
        return noStore(service.hidePost(principal(authentication).id(), postId, body, clientIps.resolve(request)));
    }

    @PostMapping("/posts/{postId}/delete")
    public ResponseEntity<ApiResponse<BoardStaffActionDoneView>> deletePost(@PathVariable String postId, @RequestBody(required = false) BoardStaffPostActionRequest body,
                                                                           Authentication authentication, HttpServletRequest request) {
        return noStore(service.deletePost(principal(authentication).id(), postId, body, clientIps.resolve(request)));
    }

    @PostMapping("/comments/{commentId}/delete")
    public ResponseEntity<ApiResponse<BoardStaffActionDoneView>> deleteComment(@PathVariable String commentId,
                                                                              @RequestBody(required = false) BoardStaffCommentDeleteRequest body,
                                                                              Authentication authentication, HttpServletRequest request) {
        return noStore(service.deleteComment(principal(authentication).id(), commentId, body, clientIps.resolve(request)));
    }

    @GetMapping("/boards/{boardId}/actions")
    public ResponseEntity<ApiResponse<BoardStaffActionPageView>> actions(@PathVariable String boardId, @RequestParam(required = false) String kind,
                                                                        @RequestParam(required = false) String page, Authentication authentication) {
        return noStore(service.actions(principal(authentication).id(), boardId, kind, page));
    }

    @PostMapping("/actions/{id}/restore")
    public ResponseEntity<ApiResponse<BoardStaffActionRowView>> restore(@PathVariable String id, @RequestBody(required = false) BoardStaffActionRestoreRequest body,
                                                                       Authentication authentication, HttpServletRequest request) {
        return noStore(service.restore(principal(authentication).id(), id, body, clientIps.resolve(request)));
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
