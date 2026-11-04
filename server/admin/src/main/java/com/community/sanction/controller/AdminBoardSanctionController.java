package com.community.sanction.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.request.BoardSanctionReasonRequest;
import com.community.sanction.dto.response.BoardSanctionDetailView;
import com.community.sanction.dto.response.BoardSanctionListView;
import com.community.sanction.service.AdminBoardSanctionService;
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
 * 관리자 게시판 제재 API(admin-web {@code useBoardSanctionApi.ts}, docs/features/board-sanction-contract.md) — 읽기 {@code sanction.read}, 해제·무효 {@code sanction.manage}
 * (바뀐 상세를 돌려준다). 쓰기는 CSRF, 응답은 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/board-sanctions} → {@code BoardSanctionListView} · 400 {@code fields.q|from|to}</li>
 *   <li>{@code GET /{id}} → {@code BoardSanctionDetail} · 404 {@code BOARD_SANCTION_NOT_FOUND}</li>
 *   <li>{@code POST /{id}/lift { reason }} → 진행 중 쓰기 제한만(409 {@code BOARD_SANCTION_NOT_LIFTABLE}) · 400 {@code fields.reason}(1~200자)</li>
 *   <li>{@code POST /{id}/void { reason }} → VOIDED(409 {@code BOARD_SANCTION_ALREADY_VOIDED}) — 대기 이의제기는 같은 트랜잭션에서 인용</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/board-sanctions")
public class AdminBoardSanctionController {

    private final AdminBoardSanctionService service;

    public AdminBoardSanctionController(AdminBoardSanctionService service) {
        this.service = service;
    }

    /** 목록 — {@code scope}·{@code boardId}·{@code kind}·{@code searchField}·{@code q}·{@code from}·{@code to}·{@code userId}·{@code sort}·{@code page}·{@code size} */
    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<BoardSanctionListView>> list(@RequestParam(required = false) String scope, @RequestParam(required = false) String boardId,
                                                                   @RequestParam(required = false) String kind, @RequestParam(required = false) String searchField,
                                                                   @RequestParam(required = false) String q, @RequestParam(required = false) String from,
                                                                   @RequestParam(required = false) String to, @RequestParam(required = false) String userId,
                                                                   @RequestParam(required = false) String sort, @RequestParam(required = false) String page,
                                                                   @RequestParam(required = false) String size) {
        return noStore(service.list(scope, boardId, kind, searchField, q, from, to, userId, sort, page, size));
    }

    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardSanctionDetailView>> get(@PathVariable String id) {
        return noStore(service.get(id));
    }

    @RequirePermission(Permission.SANCTION_MANAGE)
    @PostMapping("/{id}/lift")
    public ResponseEntity<ApiResponse<BoardSanctionDetailView>> lift(@PathVariable String id, @RequestBody(required = false) BoardSanctionReasonRequest body,
                                                                     AdminActor actor) {
        return noStore(service.lift(actor, id, body));
    }

    @RequirePermission(Permission.SANCTION_MANAGE)
    @PostMapping("/{id}/void")
    public ResponseEntity<ApiResponse<BoardSanctionDetailView>> voidSanction(@PathVariable String id, @RequestBody(required = false) BoardSanctionReasonRequest body,
                                                                             AdminActor actor) {
        return noStore(service.voidSanction(actor, id, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
