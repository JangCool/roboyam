package com.community.highlight.controller;

import com.community.highlight.dto.request.HighlightOrderRequest;
import com.community.highlight.dto.request.HighlightSaveRequest;
import com.community.highlight.dto.request.HighlightVisibleRequest;
import com.community.highlight.dto.response.HighlightCandidatesView;
import com.community.highlight.dto.response.HighlightItemView;
import com.community.highlight.dto.response.HighlightListView;
import com.community.highlight.dto.response.HighlightPlaceView;
import com.community.highlight.service.HighlightAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.post.dto.response.AdminPostRefView;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 하이라이트 관리 API(관리자 앱) — 목록·글 찾기·후보는 {@code highlight.read}, 나머지(만들기·고치기·순서·노출·지우기)는 {@code highlight.manage}(CSRF).
 * 규칙은 {@link HighlightAdminService}. 토론 의견 관리는 글 번호 기준 {@code /api/admin/debates/{postId}/…} 로 옮겼다(docs/features/post-debate-contract.md). 대표 이미지는 {@code POST /api/admin/files}(purpose HIGHLIGHT_IMAGE)로 먼저 올린다.
 * 목록·글 찾기·후보는 쿼리 {@code place}, 만들기·순서는 본문 {@code place} 로 한 곳을 고른다(비우면 home — tips-contract 「서버 구현 결과」).
 * 모든 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/highlights")
public class HighlightController {

    private final HighlightAdminService service;

    public HighlightController(HighlightAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<HighlightListView>> list(@RequestParam(required = false) String place) {
        return noStore(service.list(place));
    }

    /** 하이라이트를 놓을 수 있는 곳 {@code [{key, name, kind}]} — 홈 · 팁·강좌·사용기 · 가젯 페이지 */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/places")
    public ResponseEntity<ApiResponse<List<HighlightPlaceView>>> places() {
        return noStore(service.places());
    }

    /** 대상 글 찾기 — {@code q} 제목 일부(2자 이상) 또는 글 번호(숫자 15자리 이상), 그 {@code place} 에 아직 없는 글. 최대 10개 */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/post-search")
    public ResponseEntity<ApiResponse<List<AdminPostRefView>>> search(@RequestParam(required = false) String q, @RequestParam(required = false) String place) {
        return noStore(service.search(q, place));
    }

    /** 후보 — {@code days} 0~365(0 = 전체, 기본 7) · {@code minLikes} 1~1000(기본 3) · {@code boardId}(선택) */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<HighlightCandidatesView>> candidates(@RequestParam(required = false) String days,
                                                                          @RequestParam(required = false) String minLikes,
                                                                          @RequestParam(required = false) String boardId,
                                                                          @RequestParam(required = false) String place) {
        return noStore(service.candidates(days, minLikes, boardId, place));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<HighlightItemView>> create(@RequestBody(required = false) HighlightSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 순서 바꾸기(204) — 경로가 {@code /{id}} 보다 먼저 맞는다 */
    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PutMapping("/order")
    public ResponseEntity<Void> order(@RequestBody(required = false) HighlightOrderRequest body, AdminActor actor) {
        service.order(actor, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HighlightItemView>> update(@PathVariable String id, @RequestBody(required = false) HighlightSaveRequest body,
                                                                 AdminActor actor) {
        return noStore(service.update(actor, id, body));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PutMapping("/{id}/visible")
    public ResponseEntity<ApiResponse<HighlightItemView>> visible(@PathVariable String id, @RequestBody(required = false) HighlightVisibleRequest body,
                                                                  AdminActor actor) {
        return noStore(service.visible(actor, id, body));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
