package com.community.sanction.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.request.BoardSanctionReviewCloseRequest;
import com.community.sanction.dto.response.BoardSanctionReviewDetailView;
import com.community.sanction.dto.response.BoardSanctionReviewListView;
import com.community.sanction.dto.response.BoardSanctionReviewSummaryView;
import com.community.sanction.service.AdminBoardSanctionReviewService;
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
 * 관리자 게시판 제재 반복 검토 API(admin-web {@code useBoardSanctionReviewApi.ts}, docs/features/board-sanction-contract.md 결정 3·9) — 목록·요약·상세 {@code sanction.read},
 * 닫기 {@code sanction.manage}(바뀐 상세). 쓰기는 CSRF, 응답은 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/board-sanction-reviews?status=OPEN|CLOSED&page=&size=} → {@code BoardSanctionReviewListView}(상태 생략 = OPEN)</li>
 *   <li>{@code GET /summary} → {@code { open }}</li>
 *   <li>{@code GET /{id}} → {@code BoardSanctionReviewDetail} · 404 {@code BOARD_SANCTION_REVIEW_NOT_FOUND}</li>
 *   <li>{@code POST /{id}/close} → 바뀐 상세 · 400 {@code fields.result|memo|siteSanctionId|voidSanctionIds} · 409 {@code BOARD_SANCTION_REVIEW_CLOSED}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/board-sanction-reviews")
public class AdminBoardSanctionReviewController {

    private final AdminBoardSanctionReviewService service;

    public AdminBoardSanctionReviewController(AdminBoardSanctionReviewService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<BoardSanctionReviewListView>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String page,
                                                                         @RequestParam(required = false) String size) {
        return noStore(service.list(status, page, size));
    }

    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BoardSanctionReviewSummaryView>> summary() {
        return noStore(service.summary());
    }

    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardSanctionReviewDetailView>> detail(@PathVariable String id) {
        return noStore(service.detail(id));
    }

    @RequirePermission(Permission.SANCTION_MANAGE)
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<BoardSanctionReviewDetailView>> close(@PathVariable String id, @RequestBody(required = false) BoardSanctionReviewCloseRequest body,
                                                                            AdminActor actor) {
        return noStore(service.close(actor, id, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
