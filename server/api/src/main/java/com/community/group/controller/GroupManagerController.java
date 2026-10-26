package com.community.group.controller;

import com.community.group.dto.request.GroupManagerLimitRequest;
import com.community.group.dto.request.GroupManagerRequest;
import com.community.group.dto.response.GroupManagerListView;
import com.community.group.service.GroupManagerService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소모임 매니저(3단계 — community docs/api-groups.md 5-1 의 경로 그대로). 모두 회원만({@code SecurityConfig} — 없으면 401). 판정·오류는 {@link GroupManagerService}.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupManagerController {

    private final GroupManagerService managers;

    public GroupManagerController(GroupManagerService managers) {
        this.managers = managers;
    }

    /** 매니저 목록 — 멤버만 */
    @GetMapping("/{id}/managers")
    public ResponseEntity<ApiResponse<GroupManagerListView>> list(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(managers.list(id, GroupController.userId(authentication))));
    }

    /** 임명 · 맡은 일 바꾸기 — 모임장만, 204 */
    @PutMapping("/{id}/managers/{userId}")
    public ResponseEntity<Void> appoint(@PathVariable String id, @PathVariable String userId, @RequestBody(required = false) GroupManagerRequest body,
                                        Authentication authentication) {
        managers.appoint(id, userId, GroupController.userId(authentication), body);
        return ResponseEntity.noContent().build();
    }

    /** 자진 사퇴 — 매니저 본인, 204. {@code /managers/{userId}} 보다 먼저 맞는 고정 경로다 */
    @DeleteMapping("/{id}/managers/me")
    public ResponseEntity<Void> resign(@PathVariable String id, Authentication authentication) {
        managers.resign(id, GroupController.userId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** 해임 — 모임장만, 204(이미 매니저가 아니어도 204) */
    @DeleteMapping("/{id}/managers/{userId}")
    public ResponseEntity<Void> remove(@PathVariable String id, @PathVariable String userId, Authentication authentication) {
        managers.remove(id, userId, GroupController.userId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** 인원 설정 — 모임장만, 204 */
    @PutMapping("/{id}/manager-limit")
    public ResponseEntity<Void> limit(@PathVariable String id, @RequestBody(required = false) GroupManagerLimitRequest body, Authentication authentication) {
        managers.setLimit(id, GroupController.userId(authentication), body);
        return ResponseEntity.noContent().build();
    }
}
