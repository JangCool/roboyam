package com.community.group.controller;

import com.community.group.dto.response.MyGroupsView;
import com.community.group.service.GroupCreateService;
import com.community.group.service.GroupQueryService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 소모임 — 회원만(없으면 401). 가입한 소모임 · 승인 대기 · 내 개설 신청, 개설 신청 취소 */
@RestController
@RequestMapping("/api/me/groups")
public class MyGroupController {

    private final GroupQueryService queries;
    private final GroupCreateService creates;

    public MyGroupController(GroupQueryService queries, GroupCreateService creates) {
        this.queries = queries;
        this.creates = creates;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyGroupsView>> mine(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(queries.mine(GroupController.userId(authentication))));
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> cancelRequest(@PathVariable String id, Authentication authentication) {
        creates.cancelRequest(id, GroupController.userId(authentication));
        return ResponseEntity.noContent().build();
    }
}
