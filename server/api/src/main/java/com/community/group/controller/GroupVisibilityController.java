package com.community.group.controller;

import com.community.group.dto.request.GroupPostVisibilityRequest;
import com.community.group.dto.request.GroupVisibilityRequests;
import com.community.group.dto.response.GroupPostVisibilityView;
import com.community.group.dto.response.GroupVisibilityViews;
import com.community.group.service.GroupPostVisibilityService;
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
 * 소모임 공개 범위·공개 요청(community docs/api-groups.md 11-4·11-5·11-6 — 5단계). 모두 회원만({@code SecurityConfig} — 없으면 401). 응답은 {@code Cache-Control: no-store}.
 * 글 단위 공개 전환({@code PUT …/posts/{postId}/visibility}, 8절 → 11-6 개정)도 여기로 옮겼다(경로·본문은 그대로, 응답에 {@code publicBy}·{@code membersOnly} 가 더해졌다).
 */
@RestController
@RequestMapping("/api/groups")
public class GroupVisibilityController {

    private final GroupPostVisibilityService visibility;

    public GroupVisibilityController(GroupPostVisibilityService visibility) {
        this.visibility = visibility;
    }

    /** 공개 범위 변경 미리보기 — 모임장만 */
    @GetMapping("/{id}/visibility-preview")
    public ResponseEntity<ApiResponse<GroupVisibilityViews.Preview>> preview(@PathVariable String id, @RequestParam(required = false) String to,
                                                                             Authentication authentication) {
        return noStore(visibility.preview(id, GroupController.userId(authentication), to));
    }

    /** 공개 범위 변경 — 모임장만 */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<ApiResponse<GroupVisibilityViews.ChangeResult>> change(@PathVariable String id,
                                                                                 @RequestBody(required = false) GroupVisibilityRequests.Change body,
                                                                                 Authentication authentication) {
        return noStore(visibility.changeVisibility(id, GroupController.userId(authentication), body));
    }

    /** 글 단위 공개 — 직접 공개 · 되돌리기 · 멤버 전용으로 돌리기 · 다시 공개 */
    @PutMapping("/{id}/posts/{postId}/visibility")
    public ResponseEntity<ApiResponse<GroupPostVisibilityView>> postVisibility(@PathVariable String id, @PathVariable String postId,
                                                                              @RequestBody(required = false) GroupPostVisibilityRequest body,
                                                                              Authentication authentication) {
        return noStore(visibility.setPostVisibility(id, postId, GroupController.userId(authentication), body));
    }

    /** 공개 요청(201) — 작성자 */
    @PostMapping("/{id}/posts/{postId}/publish-request")
    public ResponseEntity<ApiResponse<GroupVisibilityViews.PublishResult>> request(@PathVariable String id, @PathVariable String postId,
                                                                                   @RequestBody(required = false) GroupVisibilityRequests.Publish body,
                                                                                   Authentication authentication) {
        GroupVisibilityViews.PublishResult result = visibility.requestPublish(id, postId, GroupController.userId(authentication), body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    /** 공개 요청 취소(204, 멱등) — 작성자 */
    @DeleteMapping("/{id}/posts/{postId}/publish-request")
    public ResponseEntity<Void> cancel(@PathVariable String id, @PathVariable String postId, Authentication authentication) {
        visibility.cancelRequest(id, postId, GroupController.userId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** 처리 대기 목록 — 모임장 · 「글 공개 처리·되돌리기」 매니저 */
    @GetMapping("/{id}/publish-requests")
    public ResponseEntity<ApiResponse<GroupVisibilityViews.RequestList>> list(@PathVariable String id, @RequestParam(required = false) String status,
                                                                              @RequestParam(required = false) String cursor, @RequestParam(required = false) String size,
                                                                              Authentication authentication) {
        return noStore(visibility.listRequests(id, GroupController.userId(authentication), status, cursor, size));
    }

    /** 승인 */
    @PostMapping("/{id}/posts/{postId}/publish-request/approve")
    public ResponseEntity<ApiResponse<GroupPostVisibilityView>> approve(@PathVariable String id, @PathVariable String postId,
                                                                       @RequestBody(required = false) GroupVisibilityRequests.Approve body,
                                                                       Authentication authentication) {
        return noStore(visibility.approve(id, postId, GroupController.userId(authentication), body));
    }

    /** 거절 */
    @PostMapping("/{id}/posts/{postId}/publish-request/reject")
    public ResponseEntity<ApiResponse<GroupVisibilityViews.RejectResult>> reject(@PathVariable String id, @PathVariable String postId,
                                                                                 @RequestBody(required = false) GroupVisibilityRequests.Reject body,
                                                                                 Authentication authentication) {
        return noStore(visibility.reject(id, postId, GroupController.userId(authentication), body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
