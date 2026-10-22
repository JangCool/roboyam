package com.community.team.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.team.dto.request.TeamMembersRequest;
import com.community.team.dto.request.TeamMoveRequest;
import com.community.team.dto.request.TeamSaveRequest;
import com.community.team.dto.response.TeamCandidateView;
import com.community.team.dto.response.TeamView;
import com.community.team.service.TeamService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직 구성(팀) API (docs/features/notification-contract.md 「알림 도구·조직 구성」). 조회 {@code team.read}, 만들기·고치기·지우기·옮기기·팀원 관리 {@code team.manage}.
 * 팀원은 운영자 계정이라 응답에 운영자 이메일이 실린다 — 그래서 {@code team.read} 를 가진 운영자만 본다.
 */
@RestController
@RequestMapping("/api/admin/teams")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @RequirePermission(Permission.TEAM_READ)
    @GetMapping
    public ApiResponse<List<TeamView>> list() {
        return ApiResponse.success(service.list());
    }

    @RequirePermission(Permission.TEAM_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<TeamView>> create(@RequestBody TeamSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    @RequirePermission(Permission.TEAM_MANAGE)
    @PutMapping("/{teamId}")
    public ApiResponse<TeamView> update(@PathVariable String teamId, @RequestBody TeamSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, teamId, body));
    }

    /** 팀원이 있으면 409 {@code TEAM_HAS_MEMBERS} */
    @RequirePermission(Permission.TEAM_MANAGE)
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> delete(@PathVariable String teamId, AdminActor actor) {
        service.delete(actor, teamId);
        return ResponseEntity.noContent().build();
    }

    /** 한 칸 위·아래로 옮기고 목록 전부를 돌려준다 */
    @RequirePermission(Permission.TEAM_MANAGE)
    @PutMapping("/{teamId}/move")
    public ApiResponse<List<TeamView>> move(@PathVariable String teamId, @RequestBody TeamMoveRequest body, AdminActor actor) {
        return ApiResponse.success(service.move(actor, teamId, body));
    }

    /** 팀원 전체를 바꾼다 */
    @RequirePermission(Permission.TEAM_MANAGE)
    @PutMapping("/{teamId}/members")
    public ApiResponse<TeamView> setMembers(@PathVariable String teamId, @RequestBody TeamMembersRequest body, AdminActor actor) {
        return ApiResponse.success(service.setMembers(actor, teamId, body));
    }

    /** 팀에 넣을 수 있는 활성 운영자 + 이미 든 사람 표시 */
    @RequirePermission(Permission.TEAM_MANAGE)
    @GetMapping("/{teamId}/candidates")
    public ApiResponse<List<TeamCandidateView>> candidates(@PathVariable String teamId) {
        return ApiResponse.success(service.candidates(teamId));
    }
}
