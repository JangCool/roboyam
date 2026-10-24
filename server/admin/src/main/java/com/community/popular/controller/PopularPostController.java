package com.community.popular.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.popular.dto.request.PopularExcludeRequest;
import com.community.popular.dto.response.PopularExcludedView;
import com.community.popular.dto.response.PopularRankingView;
import com.community.popular.service.PopularAdminService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기글 관리 API(관리자 앱) — 기간별 순위 보기({@code popular.read}), 빼기·다시 넣기({@code popular.manage}, CSRF). 규칙은 {@link PopularAdminService}.
 */
@RestController
@RequestMapping("/api/admin/popular-posts")
public class PopularPostController {

    private final PopularAdminService service;

    public PopularPostController(PopularAdminService service) {
        this.service = service;
    }

    /** {@code period} REALTIME(기본) · YESTERDAY · WEEK(대소문자 무시) */
    @RequirePermission(Permission.POPULAR_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<PopularRankingView>> ranking(@RequestParam(required = false) String period) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.ranking(period)));
    }

    /** 뺀다 — 새로 뺐으면 201, 이미 빠져 있으면 200(그 줄 그대로) */
    @RequirePermission(Permission.POPULAR_MANAGE)
    @PostMapping("/exclusions")
    public ResponseEntity<ApiResponse<PopularExcludedView>> exclude(@RequestBody(required = false) PopularExcludeRequest body, AdminActor actor) {
        PopularAdminService.ExcludeResult result = service.exclude(actor, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(result.view()));
    }

    /** 다시 넣는다(204) — 빠져 있지 않아도 204 */
    @RequirePermission(Permission.POPULAR_MANAGE)
    @DeleteMapping("/exclusions/{postId}")
    public ResponseEntity<Void> restore(@PathVariable String postId, AdminActor actor) {
        service.restore(actor, postId);
        return ResponseEntity.noContent().build();
    }
}
