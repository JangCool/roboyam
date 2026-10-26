package com.community.group.controller;

import com.community.board.dto.response.BoardDetailView;
import com.community.group.dto.request.GroupPostVisibilityRequest;
import com.community.group.dto.response.GroupPostPageView;
import com.community.group.dto.response.GroupPostVisibilityView;
import com.community.group.dto.response.GroupPostWriteResultView;
import com.community.group.service.GroupPostService;
import com.community.post.dto.request.PostWriteRequest;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소모임 글(community docs/api-groups.md 8절 — 2단계) — 글 탭·소모임 게시판 정보는 로그인 없이, 글쓰기·공개 전환·봤어요는 회원만({@code SecurityConfig} — 없으면 401).
 * 글 상세·고치기·지우기·추천·댓글·투표는 기존 글 API({@code /api/posts/{id}…})를 그대로 쓴다(판정은 {@code GroupPostGate}). 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupPostController {

    private final GroupPostService posts;
    private final ClientIpResolver ipResolver;

    public GroupPostController(GroupPostService posts, ClientIpResolver ipResolver) {
        this.posts = posts;
        this.ipResolver = ipResolver;
    }

    @GetMapping("/{id}/posts")
    public ResponseEntity<ApiResponse<GroupPostPageView>> list(@PathVariable String id, @RequestParam(required = false) String cursor,
                                                               @RequestParam(required = false) String size, Authentication authentication) {
        return noStore(posts.list(id, GroupController.userId(authentication), cursor, size));
    }

    /** 글쓰기(201) — 비공개 글로 만들어진다 */
    @PostMapping("/{id}/posts")
    public ResponseEntity<ApiResponse<GroupPostWriteResultView>> create(@PathVariable String id, @RequestBody(required = false) PostWriteRequest body,
                                                                       Authentication authentication, HttpServletRequest request) {
        GroupPostWriteResultView result = posts.create(id, GroupController.userId(authentication), body, ipResolver.resolve(request));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    @PutMapping("/{id}/posts/{postId}/visibility")
    public ResponseEntity<ApiResponse<GroupPostVisibilityView>> visibility(@PathVariable String id, @PathVariable String postId,
                                                                          @RequestBody(required = false) GroupPostVisibilityRequest body,
                                                                          Authentication authentication) {
        return noStore(posts.visibility(id, postId, GroupController.userId(authentication), body));
    }

    @PostMapping("/{id}/seen")
    public ResponseEntity<Void> seen(@PathVariable String id, Authentication authentication) {
        posts.seen(id, GroupController.userId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** 소모임 게시판 정보(로보얌 추가) — {@code GET /api/boards/{slug}} 와 같은 모양, 보는 사람 판정은 소모임 기준 */
    @GetMapping("/{id}/board")
    public ResponseEntity<ApiResponse<BoardDetailView>> board(@PathVariable String id, Authentication authentication) {
        return noStore(posts.board(id, GroupController.userId(authentication)));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
