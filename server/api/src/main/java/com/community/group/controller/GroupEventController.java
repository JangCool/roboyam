package com.community.group.controller;

import com.community.group.dto.request.GroupEventRequest;
import com.community.group.dto.request.GroupEventResponseRequest;
import com.community.group.dto.request.GroupReasonRequest;
import com.community.group.dto.response.GroupEventAttendeePageView;
import com.community.group.dto.response.GroupEventDetailView;
import com.community.group.dto.response.GroupEventPageView;
import com.community.group.dto.response.GroupEventResponseResultView;
import com.community.group.service.GroupEventQueryService;
import com.community.group.service.GroupEventService;
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
 * 소모임 일정(community docs/api-groups.md 9절 — 4단계). 일정은 멤버에게만 보여서 읽기도 로그인이 필요하다(비회원 401 — {@code SecurityConfig} 의 기본 인증,
 * 비멤버 403 {@code GROUP_NOT_MEMBER}). 응답에 보는 사람의 응답·권한이 섞여 있어 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/groups/{id}/events")
public class GroupEventController {

    private final GroupEventQueryService query;
    private final GroupEventService service;

    public GroupEventController(GroupEventQueryService query, GroupEventService service) {
        this.query = query;
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GroupEventPageView>> list(@PathVariable String id, @RequestParam(required = false) String scope,
                                                                @RequestParam(required = false) String page, @RequestParam(required = false) String size,
                                                                Authentication authentication) {
        return noStore(query.list(id, GroupController.userId(authentication), scope, page, size));
    }

    /** 만들기(201) — 모임장 · 일정 관리 매니저 */
    @PostMapping
    public ResponseEntity<ApiResponse<GroupEventDetailView>> create(@PathVariable String id, @RequestBody(required = false) GroupEventRequest body,
                                                                    Authentication authentication) {
        GroupEventDetailView created = service.create(id, GroupController.userId(authentication), body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(created));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<GroupEventDetailView>> detail(@PathVariable String id, @PathVariable String eventId, Authentication authentication) {
        return noStore(query.detail(id, eventId, GroupController.userId(authentication)));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<GroupEventDetailView>> update(@PathVariable String id, @PathVariable String eventId,
                                                                    @RequestBody(required = false) GroupEventRequest body, Authentication authentication) {
        return noStore(service.update(id, eventId, GroupController.userId(authentication), body));
    }

    /** 취소(204) — 본문 {@code { reason? }}(100자 이하) */
    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String id, @PathVariable String eventId, @RequestBody(required = false) GroupReasonRequest body,
                                       Authentication authentication) {
        service.cancel(id, eventId, GroupController.userId(authentication), body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{eventId}/response")
    public ResponseEntity<ApiResponse<GroupEventResponseResultView>> respond(@PathVariable String id, @PathVariable String eventId,
                                                                             @RequestBody(required = false) GroupEventResponseRequest body,
                                                                             Authentication authentication) {
        return noStore(service.respond(id, eventId, GroupController.userId(authentication), body));
    }

    @DeleteMapping("/{eventId}/response")
    public ResponseEntity<ApiResponse<GroupEventResponseResultView>> clear(@PathVariable String id, @PathVariable String eventId,
                                                                           Authentication authentication) {
        return noStore(service.clear(id, eventId, GroupController.userId(authentication)));
    }

    @GetMapping("/{eventId}/attendees")
    public ResponseEntity<ApiResponse<GroupEventAttendeePageView>> attendees(@PathVariable String id, @PathVariable String eventId,
                                                                             @RequestParam(required = false) String page,
                                                                             @RequestParam(required = false) String size, Authentication authentication) {
        return noStore(query.attendees(id, eventId, GroupController.userId(authentication), page, size));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
