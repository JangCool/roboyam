package com.community.post.controller;

import com.community.auth.dto.AuthUser;
import com.community.board.dto.request.PasswordRequest;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDeleteResult;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostLikeResult;
import com.community.post.dto.response.PostWriteResult;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 — 모양은 user-web {@code usePostApi.types.ts} 그대로. 접근 규칙은 {@code SecurityConfig}: 읽기·쓰기·고치기·지우기·비밀번호 확인은 로그인 없이(비회원 글 — 판정은 서비스),
 * 추천은 로그인 필요(없으면 401). 쓰기 요청은 CSRF 헤더가 필요하다(비회원도 {@code XSRF-TOKEN} 쿠키를 받는다).
 * 요청한 사람의 IP 는 {@link ClientIpResolver}(꾸밀 수 없는 값)로 뽑아 서비스에 넘긴다 — 비회원 작성 IP·작성 속도·비밀번호 시도 제한·조회 중복 판정에 쓴다.
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostQueryService queries;
    private final PostWriteService writes;
    private final ClientIpResolver ipResolver;

    public PostController(PostQueryService queries, PostWriteService writes, ClientIpResolver ipResolver) {
        this.queries = queries;
        this.writes = writes;
        this.ipResolver = ipResolver;
    }

    /** 글 하나 — 볼 때 조회수가 오른다. 숨긴 글 403 POST_HIDDEN, 없는 글 404 POST_NOT_FOUND */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailView>> post(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(queries.detail(id, userId(authentication), ipResolver.resolve(request))));
    }

    /** 글쓰기(201) — 비회원은 {@code guest} 칸 */
    @PostMapping
    public ResponseEntity<ApiResponse<PostWriteResult>> create(@RequestBody PostWriteRequest body, Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(writes.create(body, userId(authentication), ipResolver.resolve(request))));
    }

    /** 고치기 — 비회원 글은 본문에 {@code password}. 보호 중 409 POST_PROTECTED */
    @PutMapping("/{id}")
    public ApiResponse<PostDetailView> update(@PathVariable String id, @RequestBody PostUpdateRequest body, Authentication authentication,
                                              HttpServletRequest request) {
        return ApiResponse.success(writes.update(id, body, userId(authentication), ipResolver.resolve(request)));
    }

    /** 지우기 — 비회원 글은 본문에 {@code password}. 보호 중이고 예약 삭제면 {@code scheduledAt} */
    @PostMapping("/{id}/delete")
    public ApiResponse<PostDeleteResult> delete(@PathVariable String id, @RequestBody(required = false) PasswordRequest body, Authentication authentication,
                                                HttpServletRequest request) {
        return ApiResponse.success(writes.delete(id, body == null ? null : body.password(), userId(authentication), ipResolver.resolve(request)));
    }

    /** 비회원 글 비밀번호 확인(204) — 틀리면 403 PASSWORD_MISMATCH, 여러 번 틀리면 429 */
    @PostMapping("/{id}/verify-password")
    public ResponseEntity<Void> verifyPassword(@PathVariable String id, @RequestBody(required = false) PasswordRequest body, HttpServletRequest request) {
        writes.verifyPassword(id, body == null ? null : body.password(), ipResolver.resolve(request));
        return ResponseEntity.noContent().build();
    }

    /** 추천(취소 없음) — 이미 추천 409 ALREADY_LIKED, 내 글 400 CANNOT_LIKE_OWN */
    @PostMapping("/{id}/like")
    public ApiResponse<PostLikeResult> like(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success(writes.like(id, userId(authentication)));
    }

    static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
