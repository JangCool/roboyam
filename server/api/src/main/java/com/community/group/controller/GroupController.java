package com.community.group.controller;

import com.community.auth.dto.AuthUser;
import com.community.group.dto.request.GroupChatLinksRequest;
import com.community.group.dto.request.GroupCreateRequest;
import com.community.group.dto.request.GroupDissolveRequest;
import com.community.group.dto.request.GroupJoinRequest;
import com.community.group.dto.request.GroupLeaderRequest;
import com.community.group.dto.request.GroupReasonRequest;
import com.community.group.dto.request.GroupRecruitingRequest;
import com.community.group.dto.request.GroupUpdateRequest;
import com.community.group.dto.response.GroupApplicationsView;
import com.community.group.dto.response.GroupCategoriesView;
import com.community.group.dto.response.GroupCreateResultView;
import com.community.group.dto.response.GroupCreationRulesView;
import com.community.group.dto.response.GroupDetailView;
import com.community.group.dto.response.GroupJoinResultView;
import com.community.group.dto.response.GroupListPageView;
import com.community.group.dto.response.GroupMemberCountView;
import com.community.group.dto.response.GroupMemberPageView;
import com.community.group.service.GroupCreateService;
import com.community.group.service.GroupLeaderService;
import com.community.group.service.GroupMembershipService;
import com.community.group.service.GroupQueryService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * 소모임(공개 사이트) — community docs/api-groups.md 1~5절의 경로 그대로(5-1 매니저는 3단계). 읽기(목록·분류·개설 규칙·상세)는 로그인 없이, 나머지는 회원만
 * ({@code SecurityConfig} — 없으면 401). 응답은 {@code Cache-Control: no-store}(보는 사람·실시간 수에 따라 달라진다).
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupQueryService queries;
    private final GroupMembershipService membership;
    private final GroupCreateService creates;
    private final GroupLeaderService leaders;

    public GroupController(GroupQueryService queries, GroupMembershipService membership, GroupCreateService creates, GroupLeaderService leaders) {
        this.queries = queries;
        this.membership = membership;
        this.creates = creates;
        this.leaders = leaders;
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<GroupListPageView>> list(@RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                                                               @RequestParam(required = false) String q, @RequestParam(required = false) String page,
                                                               @RequestParam(required = false) String size, @RequestParam(required = false) String exclude,
                                                               Authentication authentication) {
        return noStore(queries.list(category, sort, q, page, size, exclude, userId(authentication)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<GroupCategoriesView>> categories() {
        return noStore(queries.categories());
    }

    @GetMapping("/creation-rules")
    public ResponseEntity<ApiResponse<GroupCreationRulesView>> creationRules(Authentication authentication) {
        return noStore(queries.creationRules(userId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupDetailView>> detail(@PathVariable String id, Authentication authentication) {
        return noStore(queries.detail(id, userId(authentication)));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<GroupMemberPageView>> members(@PathVariable String id, @RequestParam(required = false) String page,
                                                                    @RequestParam(required = false) String size, Authentication authentication) {
        return noStore(queries.members(id, userId(authentication), page, size));
    }

    // ── 개설 · 가입 · 탈퇴 ───────────────────────────────────────────────────

    /** 개설 신청 — 승인제 202 PENDING, 자유 개설 201 ACTIVE */
    @PostMapping
    public ResponseEntity<ApiResponse<GroupCreateResultView>> create(@RequestBody(required = false) GroupCreateRequest body, Authentication authentication) {
        GroupCreateResultView result = creates.create(userId(authentication), body);
        HttpStatus status = "ACTIVE".equals(result.status()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    /** 가입 200 JOINED · 신청 202 APPLIED */
    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<GroupJoinResultView>> join(@PathVariable String id, @RequestBody(required = false) GroupJoinRequest body,
                                                                 Authentication authentication) {
        GroupJoinResultView result = membership.join(id, userId(authentication), body);
        HttpStatus status = "APPLIED".equals(result.result()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}/join")
    public ResponseEntity<Void> cancelJoin(@PathVariable String id, Authentication authentication) {
        membership.cancel(id, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/membership")
    public ResponseEntity<Void> leave(@PathVariable String id, Authentication authentication) {
        membership.leave(id, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    // ── 모임장 · 맡은 매니저 ─────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupDetailView>> update(@PathVariable String id, @RequestBody(required = false) GroupUpdateRequest body,
                                                               Authentication authentication) {
        return noStore(leaders.update(id, userId(authentication), body));
    }

    @PutMapping("/{id}/recruiting")
    public ResponseEntity<Void> recruiting(@PathVariable String id, @RequestBody(required = false) GroupRecruitingRequest body, Authentication authentication) {
        leaders.recruiting(id, userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/chat-links")
    public ResponseEntity<Void> chatLinks(@PathVariable String id, @RequestBody(required = false) GroupChatLinksRequest body, Authentication authentication) {
        leaders.chatLinks(id, userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/applications")
    public ResponseEntity<ApiResponse<GroupApplicationsView>> applications(@PathVariable String id, Authentication authentication) {
        return noStore(leaders.applications(id, userId(authentication)));
    }

    @PostMapping("/{id}/applications/{userId}/approve")
    public ResponseEntity<ApiResponse<GroupMemberCountView>> approve(@PathVariable String id, @PathVariable String userId, Authentication authentication) {
        return noStore(leaders.approve(id, userId, userId(authentication)));
    }

    @PostMapping("/{id}/applications/{userId}/reject")
    public ResponseEntity<Void> reject(@PathVariable String id, @PathVariable String userId, @RequestBody(required = false) GroupReasonRequest body,
                                       Authentication authentication) {
        leaders.reject(id, userId, userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> kick(@PathVariable String id, @PathVariable String userId, Authentication authentication) {
        leaders.kick(id, userId, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leader")
    public ResponseEntity<Void> transferLeader(@PathVariable String id, @RequestBody(required = false) GroupLeaderRequest body, Authentication authentication) {
        leaders.transferLeader(id, userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dissolve")
    public ResponseEntity<Void> dissolve(@PathVariable String id, @RequestBody(required = false) GroupDissolveRequest body, Authentication authentication) {
        leaders.dissolve(id, userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
