package com.community.post.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.post.dto.response.TagCountView;
import com.community.post.service.AdminPostTagService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 태그 — 관리자(post-tags-contract 「관리자」). 글 목록 가젯 ⚙ 「태그 탭」 고르기 칸이 쓰므로 권한은 홈·가젯 구성 저장과 같은 {@code layout.settings}.
 */
@RestController
@RequestMapping("/api/admin/tags")
public class AdminPostTagController {

    private final AdminPostTagService service;

    public AdminPostTagController(AdminPostTagService service) {
        this.service = service;
    }

    /**
     * 최근 많이 달린 태그 — {@code boards}(slug 쉼표 이음, 비면 전체) · {@code days}(기본 30) · {@code limit}(기본 30) → {@code [{name, count}]}(달린 글 수 내림차순)
     */
    @RequirePermission(Permission.LAYOUT_SETTINGS)
    @GetMapping("/popular")
    public ApiResponse<List<TagCountView>> popular(@RequestParam(required = false) String boards, @RequestParam(required = false) Integer days,
                                                   @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(service.popular(boards, days, limit));
    }
}
