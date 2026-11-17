package com.community.title.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.title.dto.request.AdminTitleCandidateRequest;
import com.community.title.dto.request.AdminTitleGrantRequest;
import com.community.title.dto.request.AdminTitleReasonRequest;
import com.community.title.dto.request.AdminTitleRequest;
import com.community.title.dto.request.AdminTitleSettingsRequest;
import com.community.title.dto.response.AdminMonthlyCandidatesView;
import com.community.title.dto.response.AdminTitleApplicationListView;
import com.community.title.dto.response.AdminTitleGrantPageView;
import com.community.title.dto.response.AdminTitleGrantView;
import com.community.title.dto.response.AdminTitleSettingsView;
import com.community.title.dto.response.AdminTitleView;
import com.community.title.service.AdminTitleAwardService;
import com.community.title.service.AdminTitleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 칭호 관리자 API(member-title-contract 「API 후보」·「구현 결정」 3·7·8) — 읽기 {@code title.read}, 만들기·고치기·설정·수여·회수·후보 {@code title.manage},
 * 신청 승인·거절 {@code title.review}(신청 목록은 {@code title.read} 또는 {@code title.review}). 쓰기는 CSRF, 모든 본문 응답 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/titles} · {@code POST /api/admin/titles} → 201 · {@code PUT /api/admin/titles/{key}} — 400 {@code VALIDATION}(칸 경로 키) · 409 {@code TITLE_NAME_TAKEN}·
 *       {@code TITLE_BUILTIN_LOCKED} · 404 {@code TITLE_NOT_FOUND}</li>
 *   <li>{@code GET·PUT /api/admin/titles/settings} {@code { monthlyMax, honoraryCount, reapplyDays? }}</li>
 *   <li>{@code POST /api/admin/titles/{key}/grants} {@code { memberId, period?, tier?, reason? }} → 201 받은 줄 · 400 {@code fields.memberId|period|tier} · 404 {@code TITLE_NOT_FOUND}·
 *       {@code TITLE_MEMBER_NOT_FOUND} · 409 {@code TITLE_MEMBER_EXCLUDED}·{@code TITLE_ALREADY_GRANTED}·{@code TITLE_MONTHLY_FULL}·{@code TITLE_YEARLY_NOT_ELIGIBLE}</li>
 *   <li>{@code DELETE /api/admin/titles/{key}/grants/{grantId}} {@code { reason }} → 204 · 400 {@code fields.reason} · 404 {@code TITLE_GRANT_NOT_FOUND} · 409 {@code TITLE_GRANT_NOT_ACTIVE}</li>
 *   <li>{@code GET /api/admin/titles/grants?key&memberId&page&size} — 주고 뺀 기록 · {@code GET /api/admin/members/{id}/titles} — 회원 칭호(회수 포함)</li>
 *   <li>{@code GET /api/admin/titles/applications?status=PENDING|APPROVED|REJECTED|ALL&key} · {@code POST …/applications/{id}/approve|reject} {@code { reason }} → 204</li>
 *   <li>{@code GET /api/admin/titles/monthly-candidates?month=YYYY-MM} · {@code POST·DELETE /api/admin/titles/monthly-candidates} {@code { postId, month? }} → 204</li>
 * </ul>
 */
@RestController
public class AdminTitleController {

    private final AdminTitleService titles;
    private final AdminTitleAwardService awards;

    public AdminTitleController(AdminTitleService titles, AdminTitleAwardService awards) {
        this.titles = titles;
        this.awards = awards;
    }

    @RequirePermission(Permission.TITLE_READ)
    @GetMapping("/api/admin/titles")
    public ResponseEntity<ApiResponse<List<AdminTitleView>>> list() {
        return noStore(titles.list());
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @PostMapping("/api/admin/titles")
    public ResponseEntity<ApiResponse<AdminTitleView>> create(@RequestBody(required = false) AdminTitleRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(titles.create(actor, body)));
    }

    /** 고치기 — 본문의 {@code key} 로 찾는다(계약 「PUT /api/admin/titles」) */
    @RequirePermission(Permission.TITLE_MANAGE)
    @PutMapping("/api/admin/titles")
    public ResponseEntity<ApiResponse<AdminTitleView>> updateByBody(@RequestBody(required = false) AdminTitleRequest body, AdminActor actor) {
        return noStore(titles.update(actor, null, body));
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @PutMapping("/api/admin/titles/{key}")
    public ResponseEntity<ApiResponse<AdminTitleView>> update(@PathVariable String key, @RequestBody(required = false) AdminTitleRequest body, AdminActor actor) {
        return noStore(titles.update(actor, key, body));
    }

    @RequirePermission(Permission.TITLE_READ)
    @GetMapping("/api/admin/titles/settings")
    public ResponseEntity<ApiResponse<AdminTitleSettingsView>> settings() {
        return noStore(titles.settings());
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @PutMapping("/api/admin/titles/settings")
    public ResponseEntity<ApiResponse<AdminTitleSettingsView>> saveSettings(@RequestBody(required = false) AdminTitleSettingsRequest body, AdminActor actor) {
        return noStore(titles.saveSettings(actor, body));
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @PostMapping("/api/admin/titles/{key}/grants")
    public ResponseEntity<ApiResponse<AdminTitleGrantView>> grant(@PathVariable String key, @RequestBody(required = false) AdminTitleGrantRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(awards.grant(actor, key, body)));
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @DeleteMapping("/api/admin/titles/{key}/grants/{grantId}")
    public ResponseEntity<Void> revoke(@PathVariable String key, @PathVariable String grantId, @RequestBody(required = false) AdminTitleReasonRequest body,
                                       AdminActor actor) {
        awards.revoke(actor, key, grantId, body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TITLE_READ)
    @GetMapping("/api/admin/titles/grants")
    public ResponseEntity<ApiResponse<AdminTitleGrantPageView>> log(@RequestParam(required = false) String key, @RequestParam(required = false) String memberId,
                                                                   @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return noStore(awards.log(key, memberId, page, size));
    }

    @RequirePermission(Permission.TITLE_READ)
    @GetMapping("/api/admin/members/{id}/titles")
    public ResponseEntity<ApiResponse<List<AdminTitleGrantView>>> memberTitles(@PathVariable String id) {
        return noStore(awards.memberTitles(id));
    }

    @RequirePermission(anyOf = {Permission.TITLE_READ, Permission.TITLE_REVIEW})
    @GetMapping("/api/admin/titles/applications")
    public ResponseEntity<ApiResponse<AdminTitleApplicationListView>> applications(@RequestParam(required = false) String status,
                                                                                    @RequestParam(required = false) String key) {
        return noStore(awards.applications(status, key));
    }

    @RequirePermission(Permission.TITLE_REVIEW)
    @PostMapping("/api/admin/titles/applications/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable String id, @RequestBody(required = false) AdminTitleReasonRequest body, AdminActor actor) {
        awards.approve(actor, id, body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TITLE_REVIEW)
    @PostMapping("/api/admin/titles/applications/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable String id, @RequestBody(required = false) AdminTitleReasonRequest body, AdminActor actor) {
        awards.reject(actor, id, body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TITLE_READ)
    @GetMapping("/api/admin/titles/monthly-candidates")
    public ResponseEntity<ApiResponse<AdminMonthlyCandidatesView>> monthlyCandidates(@RequestParam(required = false) String month) {
        return noStore(awards.monthlyCandidates(month));
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @PostMapping("/api/admin/titles/monthly-candidates")
    public ResponseEntity<Void> addCandidate(@RequestBody(required = false) AdminTitleCandidateRequest body, AdminActor actor) {
        awards.addCandidate(actor, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TITLE_MANAGE)
    @DeleteMapping("/api/admin/titles/monthly-candidates")
    public ResponseEntity<Void> removeCandidate(@RequestBody(required = false) AdminTitleCandidateRequest body, AdminActor actor) {
        awards.removeCandidate(actor, body);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
