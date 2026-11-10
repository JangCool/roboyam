package com.community.sanction.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.request.SanctionCreateRequest;
import com.community.sanction.dto.request.SanctionLiftRequest;
import com.community.sanction.dto.response.SanctionLineView;
import com.community.sanction.dto.response.SanctionListView;
import com.community.sanction.dto.response.SanctionPreviewView;
import com.community.sanction.dto.response.SanctionTargetView;
import com.community.sanction.service.AdminSanctionService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 제재 API(admin-web {@code useSanctionApi.ts}) — 읽기 {@code sanction.read}, 부여·해제 입구 {@code sanction.manage} 또는 {@code sanction.ban}(단계별 권한은 서비스),
 * 쓰기는 CSRF, 모든 본문 응답은 {@code Cache-Control: no-store}. 계약 docs/features/member-sanction-contract.md.
 */
@RestController
@RequestMapping("/api/admin/sanctions")
public class AdminSanctionController {

    private final AdminSanctionService service;

    public AdminSanctionController(AdminSanctionService service) {
        this.service = service;
    }

    /** 목록 — {@code scope}·{@code kind}·{@code searchField}·{@code q}·{@code from}·{@code to}·{@code userId}·{@code sort}·{@code page}·{@code size} */
    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<SanctionListView>> list(@RequestParam(required = false) String scope, @RequestParam(required = false) String kind,
                                                              @RequestParam(required = false) String searchField, @RequestParam(required = false) String q,
                                                              @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                              @RequestParam(required = false) String userId, @RequestParam(required = false) String sort,
                                                              @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(service.list(scope, kind, searchField, q, from, to, userId, sort, page, size));
    }

    /** 부여 대화창의 회원 찾기 — 닉네임 2자 이상, 최대 10명 */
    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<List<SanctionTargetView>>> targets(@RequestParam(required = false) String q) {
        return noStore(service.targets(q));
    }

    /** 회원의 제재 현황 — 없음·탈퇴 404 {@code SANCTION_TARGET_NOT_FOUND} */
    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<SanctionPreviewView>> preview(@RequestParam(required = false) String userId,
                                                                    @RequestParam(required = false) String anonymousPostId,
                                                                    @RequestParam(required = false) String anonymousCommentId) {
        return noStore(service.preview(userId, anonymousPostId, anonymousCommentId)); // 익명 글·댓글 작성자(V184)면 대상 봉인
    }

    /** 제재 하나 — 없으면 404 {@code SANCTION_NOT_FOUND} */
    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SanctionLineView>> get(@PathVariable String id) {
        return noStore(service.get(id));
    }

    /** 부여 → 201 */
    @RequirePermission(anyOf = {Permission.SANCTION_MANAGE, Permission.SANCTION_BAN})
    @PostMapping
    public ResponseEntity<ApiResponse<SanctionLineView>> create(@RequestBody(required = false) SanctionCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 진행 중 정지·영구 정지 해제 → 바뀐 줄 */
    @RequirePermission(anyOf = {Permission.SANCTION_MANAGE, Permission.SANCTION_BAN})
    @PostMapping("/{id}/lift")
    public ResponseEntity<ApiResponse<SanctionLineView>> lift(@PathVariable String id, @RequestBody(required = false) SanctionLiftRequest body, AdminActor actor) {
        return noStore(service.lift(actor, id, body));
    }

    /** 회원의 진행 중 정지·영구 정지 해제(회원 상세 — 봉인 정지는 ID 를 주지 않으므로 회원 기준) → 바뀐 줄 · 409 {@code SANCTION_NOT_LIFTABLE} */
    @RequirePermission(anyOf = {Permission.SANCTION_MANAGE, Permission.SANCTION_BAN})
    @PostMapping("/members/{userId}/lift-active")
    public ResponseEntity<ApiResponse<SanctionLineView>> liftActive(@PathVariable String userId, @RequestBody(required = false) SanctionLiftRequest body, AdminActor actor) {
        return noStore(service.liftActive(actor, userId, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
