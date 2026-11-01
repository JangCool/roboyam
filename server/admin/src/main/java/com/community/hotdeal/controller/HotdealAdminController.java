package com.community.hotdeal.controller;

import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealAdminView;
import com.community.hotdeal.service.HotdealAdminQueryService;
import com.community.hotdeal.service.HotdealModerationService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
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
 * 관리자 핫딜 글(admin-web {@code useHotdealApi}, hotdeal-contract 「API 후보」 관리자) — 조회 {@code hotdeal.read}, 종료·재개 {@code hotdeal.manage}
 * (게시판 매니저·스텝은 회원이라 관리자 앱에 들어오지 않는다 — 판정은 shared {@code DealModeration}). 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/hotdeals")
public class HotdealAdminController {

    private final HotdealAdminQueryService queries;
    private final HotdealModerationService moderation;

    public HotdealAdminController(HotdealAdminQueryService queries, HotdealModerationService moderation) {
        this.queries = queries;
        this.moderation = moderation;
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/boards")
    public ResponseEntity<ApiResponse<HotdealAdminView.BoardOptions>> boards() {
        return ok(queries.boards());
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<HotdealAdminView.Page>> list(@RequestParam(required = false) String view, @RequestParam(required = false) String board,
                                                                   @RequestParam(required = false) String q, @RequestParam(required = false) String createdFrom,
                                                                   @RequestParam(required = false) String createdTo,
                                                                   @RequestParam(required = false) String blockedActive, @RequestParam(required = false) String page,
                                                                   @RequestParam(required = false) String size) {
        return ok(queries.list(view, board, q, createdFrom, createdTo, blockedActive, page, size));
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<HotdealAdminView.Detail>> detail(@PathVariable String postId) {
        return ok(queries.detail(postId));
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/{postId}/revisions")
    public ResponseEntity<ApiResponse<HotdealAdminView.RevisionList>> revisions(@PathVariable String postId) {
        return ok(queries.revisions(postId));
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @PostMapping("/{postId}/end")
    public ResponseEntity<Void> end(@PathVariable String postId, @RequestBody(required = false) HotdealAdminRequest.Action body, AdminActor actor) {
        moderation.end(actor, postId, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @PostMapping("/{postId}/reopen")
    public ResponseEntity<Void> reopen(@PathVariable String postId, @RequestBody(required = false) HotdealAdminRequest.Action body, AdminActor actor) {
        moderation.reopen(actor, postId, body);
        return ResponseEntity.noContent().build();
    }

    static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
