package com.community.post.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.post.dto.request.AdminPostBulkRequest;
import com.community.post.dto.request.AdminPostHideRequest;
import com.community.post.dto.request.AdminPostMoveRequest;
import com.community.post.dto.request.AdminPostReasonRequest;
import com.community.post.dto.response.AdminPostBoardOptionsView;
import com.community.post.dto.response.AdminPostBulkResultView;
import com.community.post.dto.response.AdminPostDetailView;
import com.community.post.dto.response.AdminPostListView;
import com.community.post.service.AdminPostModerationService;
import com.community.post.service.AdminPostQueryService;
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
 * 게시글 관리 API(관리자 앱) — 읽기 {@code post.read}, 조치 {@code post.manage}(복구는 {@code post.restore}, 일괄 삭제는 {@code post.bulk-delete} 도 — 서비스가 확인), 쓰기는 CSRF.
 * 조치는 본문 없는 204, 모든 본문 응답은 {@code Cache-Control: no-store}. 규칙은 {@link AdminPostQueryService}·{@link AdminPostModerationService},
 * 계약은 docs/features/post-admin-contract.md. 글의 찬반 토론은 {@code /api/admin/debates/{postId}} 를 그대로 쓴다.
 */
@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final AdminPostQueryService queries;
    private final AdminPostModerationService moderation;

    public AdminPostController(AdminPostQueryService queries, AdminPostModerationService moderation) {
        this.queries = queries;
        this.moderation = moderation;
    }

    /**
     * 목록 — {@code searchField}(TITLE·AUTHOR·ID)·{@code q}·{@code createdFrom}·{@code createdTo}(UTC ISO 순간, 양 끝 포함)·{@code board}(게시판 ID)·
     * {@code prefix}(말머리 ID)·{@code state}(ALL·PUBLIC·HIDDEN·DELETED)·{@code sort}(newest·oldest)·{@code page}(0부터)·{@code size}(기본 20, 최대 50)
     */
    @RequirePermission(Permission.POST_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AdminPostListView>> list(@RequestParam(required = false) String searchField, @RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String createdFrom, @RequestParam(required = false) String createdTo,
                                                               @RequestParam(required = false) String board, @RequestParam(required = false) String prefix,
                                                               @RequestParam(required = false) String state, @RequestParam(required = false) String sort,
                                                               @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(queries.list(searchField, q, createdFrom, createdTo, board, prefix, state, sort, page, size));
    }

    /** 게시판 고르기(거르기·이동) — 켜진 게시판·말머리 */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/board-options")
    public ResponseEntity<ApiResponse<AdminPostBoardOptionsView>> boardOptions() {
        return noStore(queries.boardOptions());
    }

    /** 글 상세 — 글 한 줄 · 본문 · 조치 기록. 없는 글 404 POST_NOT_FOUND */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminPostDetailView>> detail(@PathVariable String id) {
        return noStore(queries.detail(id));
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/hide")
    public ResponseEntity<Void> hide(@PathVariable String id, @RequestBody(required = false) AdminPostHideRequest body, AdminActor actor) {
        moderation.hide(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/unhide")
    public ResponseEntity<Void> unhide(@PathVariable String id, @RequestBody(required = false) AdminPostReasonRequest body, AdminActor actor) {
        moderation.unhide(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestBody(required = false) AdminPostReasonRequest body, AdminActor actor) {
        moderation.delete(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    /** 복구 — {@code post.restore}. 원문 파기 409 POST_PURGED */
    @RequirePermission(Permission.POST_RESTORE)
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable String id, @RequestBody(required = false) AdminPostReasonRequest body, AdminActor actor) {
        moderation.restore(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/move")
    public ResponseEntity<Void> move(@PathVariable String id, @RequestBody(required = false) AdminPostMoveRequest body, AdminActor actor) {
        moderation.move(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/notice")
    public ResponseEntity<Void> notice(@PathVariable String id, @RequestBody(required = false) AdminPostReasonRequest body, AdminActor actor) {
        moderation.notice(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> pin(@PathVariable String id, @RequestBody(required = false) AdminPostReasonRequest body, AdminActor actor) {
        moderation.pin(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/unpin")
    public ResponseEntity<Void> unpin(@PathVariable String id, @RequestBody(required = false) AdminPostReasonRequest body, AdminActor actor) {
        moderation.unpin(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    /** 일괄 숨김·해제·삭제 — 글마다 따로(200 {@code { done, failed }}). DELETE 는 {@code post.bulk-delete} 도 */
    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<AdminPostBulkResultView>> bulk(@RequestBody(required = false) AdminPostBulkRequest body, AdminActor actor) {
        return noStore(moderation.bulk(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
