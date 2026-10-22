package com.community.board.controller;

import com.community.board.dto.request.BoardActiveRequest;
import com.community.board.dto.request.BoardCreateRequest;
import com.community.board.dto.request.BoardOrderRequest;
import com.community.board.dto.request.BoardSaveRequest;
import com.community.board.dto.request.BoardStaffAppointRequest;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.dto.response.BoardListItemView;
import com.community.board.dto.response.BoardListResponse;
import com.community.board.dto.response.BoardStaffCandidateView;
import com.community.board.dto.response.BoardStaffMemberView;
import com.community.board.service.BoardService;
import com.community.board.service.BoardStaffService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 관리 API (docs/features/board-contract.md, admin-web {@code useBoardApi.ts} 머리말). 조회 {@code board.read}, 만들기·켜고 끄기·순서 {@code board.manage},
 * 저장은 바뀐 부분의 권한(서비스가 본다), 삭제 {@code board.delete}, 매니저·스텝 {@code board.staff.manage}. 기본 설정은 {@link BoardDefaultsController}.
 */
@RestController
@RequestMapping("/api/admin/boards")
public class BoardController {

    private final BoardService service;
    private final BoardStaffService staff;

    public BoardController(BoardService service, BoardStaffService staff) {
        this.service = service;
        this.staff = staff;
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping
    public ApiResponse<BoardListResponse> list() {
        return ApiResponse.success(service.list());
    }

    @RequirePermission(Permission.BOARD_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<BoardListItemView>> create(@RequestBody BoardCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 화면 주소가 slug 라 둔 조회 — {@code GET /{id}} 와 같은 응답 */
    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/by-slug/{slug}")
    public ApiResponse<BoardDetailView> bySlug(@PathVariable String slug) {
        return ApiResponse.success(service.bySlug(slug));
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/{boardId}")
    public ApiResponse<BoardDetailView> detail(@PathVariable String boardId) {
        return ApiResponse.success(service.byId(boardId));
    }

    /** 기본 정보 + 정책 + 말머리 한 트랜잭션 — 바뀐 부분의 권한은 서비스가 본다 */
    @RequirePermission(anyOf = {Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS, Permission.BOARD_STAFF_LIMIT})
    @PutMapping("/{boardId}")
    public ApiResponse<BoardDetailView> save(@PathVariable String boardId, @RequestBody BoardSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, boardId, body));
    }

    @RequirePermission(Permission.BOARD_MANAGE)
    @PatchMapping("/{boardId}/active")
    public ApiResponse<BoardListItemView> setActive(@PathVariable String boardId, @RequestBody BoardActiveRequest body, AdminActor actor) {
        return ApiResponse.success(service.setActive(actor, boardId, body));
    }

    @RequirePermission(Permission.BOARD_MANAGE)
    @PutMapping("/order")
    public ApiResponse<BoardListResponse> order(@RequestBody BoardOrderRequest body, AdminActor actor) {
        return ApiResponse.success(service.order(actor, body));
    }

    /** 글이 있으면 409 {@code BOARD_HAS_POSTS} */
    @RequirePermission(Permission.BOARD_DELETE)
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> delete(@PathVariable String boardId, AdminActor actor) {
        service.delete(actor, boardId);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/{boardId}/staff")
    public ApiResponse<List<BoardStaffMemberView>> staff(@PathVariable String boardId) {
        return ApiResponse.success(staff.list(boardId));
    }

    @RequirePermission(Permission.BOARD_STAFF_MANAGE)
    @PostMapping("/{boardId}/staff")
    public ApiResponse<List<BoardStaffMemberView>> appoint(@PathVariable String boardId, @RequestBody BoardStaffAppointRequest body, AdminActor actor) {
        return ApiResponse.success(staff.appoint(actor, boardId, body));
    }

    @RequirePermission(Permission.BOARD_STAFF_MANAGE)
    @DeleteMapping("/{boardId}/staff/{memberId}")
    public ApiResponse<List<BoardStaffMemberView>> removeStaff(@PathVariable String boardId, @PathVariable String memberId, AdminActor actor) {
        return ApiResponse.success(staff.remove(actor, boardId, memberId));
    }

    @RequirePermission(Permission.BOARD_STAFF_MANAGE)
    @GetMapping("/staff-candidates")
    public ApiResponse<List<BoardStaffCandidateView>> candidates(@RequestParam(required = false) String boardId, @RequestParam(required = false) String q) {
        return ApiResponse.success(staff.candidates(boardId, q));
    }
}
