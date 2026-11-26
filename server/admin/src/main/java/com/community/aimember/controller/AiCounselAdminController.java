package com.community.aimember.controller;

import com.community.aimember.dto.AiCounselAdminDtos;
import com.community.aimember.service.AiCounselAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 연애상담소 설정 API(ai-love-counsel-contract — 관리자 화면 없이 이 API 로 켠다). 읽기 {@code ai-member.read}, 저장 {@code ai-member.manage}. 응답은 {@code Cache-Control: no-store}.
 * 입력 검사는 서비스가 한다. ({@code /api/admin/ai-members/{id}} 와 겹치지만 글자 경로가 먼저다.)
 */
@RestController
@RequestMapping("/api/admin/ai-members/counsel")
public class AiCounselAdminController {

    private final AiCounselAdminService counsel;

    public AiCounselAdminController(AiCounselAdminService counsel) {
        this.counsel = counsel;
    }

    @RequirePermission(Permission.AI_MEMBER_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AiCounselAdminDtos.View>> view() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(counsel.view()));
    }

    /** 400 {@code VALIDATION}(memberDailyLimit · siteDailyLimit · counselPrefixIds · counselors · counselors[i].aiMemberId · counselors[i].style) */
    @RequirePermission(Permission.AI_MEMBER_MANAGE)
    @PutMapping
    public ResponseEntity<ApiResponse<AiCounselAdminDtos.View>> save(@RequestBody(required = false) AiCounselAdminDtos.Save body, AdminActor actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(counsel.save(actor, body)));
    }
}
