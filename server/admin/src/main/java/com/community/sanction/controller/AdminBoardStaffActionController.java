package com.community.sanction.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.request.BoardStaffActionRestoreReasonRequest;
import com.community.sanction.dto.response.AdminBoardStaffActionListView;
import com.community.sanction.dto.response.AdminBoardStaffActionView;
import com.community.sanction.service.AdminBoardStaffActionService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 「스텝 처리 감독」 API(admin-web {@code useBoardStaffActionApi.ts}, docs/features/board-staff-action-contract.md 「API > 관리자」) — 읽기 {@code keeper-action.read},
 * 되돌리기 {@code keeper-action.restore}(바뀐 줄을 돌려준다). 쓰기는 CSRF, 응답은 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/board-staff-actions?board=&kind=&restored=&q=&from=&to=&page=&size=} → {@code BoardStaffActionListView}({@code page} 0부터) · 400 {@code fields.from|to}</li>
 *   <li>{@code GET /{id}} → {@code AdminBoardStaffAction} · 404 {@code BOARD_STAFF_ACTION_NOT_FOUND}</li>
 *   <li>{@code POST /{id}/restore { reason }} → {@code AdminBoardStaffAction} · 400 {@code fields.reason}(1~200자) · 409 {@code BOARD_STAFF_ACTION_RESTORED}·{@code POST_STATE_INVALID}·
 *       {@code POST_PURGED}·{@code COMMENT_STATE_INVALID}. 감사 {@code BOARD_STAFF_ACTION_RESTORE}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/board-staff-actions")
public class AdminBoardStaffActionController {

    private final AdminBoardStaffActionService service;

    public AdminBoardStaffActionController(AdminBoardStaffActionService service) {
        this.service = service;
    }

    @RequirePermission(Permission.KEEPER_ACTION_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AdminBoardStaffActionListView>> list(@RequestParam(required = false) String board, @RequestParam(required = false) String kind,
                                                                           @RequestParam(required = false) String restored, @RequestParam(required = false) String q,
                                                                           @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                           @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(service.list(board, kind, restored, q, from, to, page, size));
    }

    @RequirePermission(Permission.KEEPER_ACTION_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminBoardStaffActionView>> get(@PathVariable String id) {
        return noStore(service.get(id));
    }

    @RequirePermission(Permission.KEEPER_ACTION_RESTORE)
    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<AdminBoardStaffActionView>> restore(@PathVariable String id,
                                                                          @RequestBody(required = false) BoardStaffActionRestoreReasonRequest body, AdminActor actor) {
        return noStore(service.restore(actor, id, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
