package com.community.profile.controller;

import com.community.auth.dto.AuthUser;
import com.community.post.dto.response.PostFeedPageView;
import com.community.profile.dto.response.AuthorCommentPageView;
import com.community.profile.dto.response.PublicProfileView;
import com.community.profile.service.UserProfileService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 회원 프로필 — <b>비회원도</b> 읽는다({@code SecurityConfig} 허용 경로). docs/features/member-activity-contract.md 「공개 프로필」.
 * {@code size}·{@code cursor} 는 글자로 받아 서비스가 검사한다(틀리면 {@code 400 VALIDATION fields.size|cursor}).
 */
@RestController
@RequestMapping("/api/users/{id}")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ApiResponse<PublicProfileView> profile(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success(service.profile(id, viewerId(authentication)));
    }

    /** 작성글 — 최신이 위. 로그인했으면 줄의 차단·작성자 메모를 싣는다 */
    @GetMapping("/posts")
    public ApiResponse<PostFeedPageView> posts(@PathVariable String id, @RequestParam(required = false) String size, @RequestParam(required = false) String cursor,
                                               Authentication authentication) {
        return ApiResponse.success(service.posts(id, size, cursor, viewerId(authentication)));
    }

    /** 작성 댓글 — 최신이 위. 로그인했으면 줄의 차단을 싣는다 */
    @GetMapping("/comments")
    public ApiResponse<AuthorCommentPageView> comments(@PathVariable String id, @RequestParam(required = false) String size,
                                                       @RequestParam(required = false) String cursor, Authentication authentication) {
        return ApiResponse.success(service.comments(id, size, cursor, viewerId(authentication)));
    }

    /** 로그인한 회원(비회원 null) */
    private static Long viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
