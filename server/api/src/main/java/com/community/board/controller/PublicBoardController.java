package com.community.board.controller;

import com.community.auth.dto.AuthUser;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.dto.response.BoardListResponse;
import com.community.board.service.PublicBoardService;
import com.community.post.dto.response.PostListResponse;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 게시판 — 로그인 없이 부른다(로그인했으면 그 사람 기준으로 판정한다). 모양은 user-web {@code useBoardApi.types.ts}·{@code usePostApi.types.ts} 그대로.
 * 보는 사람마다 다를 수 있어 브라우저·중간 캐시에 남기지 않는다({@code no-store}). 서버 응답 캐시도 두지 않는다(계약 추가 결정 3).
 */
@RestController
@RequestMapping("/api/boards")
public class PublicBoardController {

    private final PublicBoardService boardService;
    private final PostQueryService postQueries;

    public PublicBoardController(PublicBoardService boardService, PostQueryService postQueries) {
        this.boardService = boardService;
        this.postQueries = postQueries;
    }

    /** 메뉴·허브용 켜진 게시판 전체(정렬 순서대로) */
    @GetMapping
    public ResponseEntity<ApiResponse<BoardListResponse>> boards() {
        return noStore(ApiResponse.success(boardService.boards()));
    }

    /** 지금 사용자가 새 글을 쓸 수 있는 게시판(글쓰기 화면의 게시판 고르기) */
    @GetMapping("/writable")
    public ResponseEntity<ApiResponse<BoardListResponse>> writable(Authentication authentication) {
        return noStore(ApiResponse.success(boardService.writable(userId(authentication))));
    }

    /** 게시판 하나 + 유효 정책 + 지금 보는 사람의 판정. 꺼진 게시판 403 BOARD_INACTIVE, 없는 게시판 404 BOARD_NOT_FOUND */
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BoardDetailView>> board(@PathVariable String slug, Authentication authentication) {
        return noStore(ApiResponse.success(boardService.board(slug, userId(authentication))));
    }

    /** 글 목록 — {@code prefix}(말머리 ID)·{@code sort}(LATEST·OLDEST)·{@code q}(검색어)·{@code page}(1부터). 로그인했으면 줄마다 차단({@code blocked})·작성자 메모를 싣는다 */
    @GetMapping("/{slug}/posts")
    public ResponseEntity<ApiResponse<PostListResponse>> posts(@PathVariable String slug, @RequestParam(required = false) String prefix,
                                                               @RequestParam(required = false, defaultValue = "LATEST") String sort,
                                                               @RequestParam(required = false) String q, @RequestParam(required = false, defaultValue = "1") int page,
                                                               Authentication authentication) {
        return noStore(ApiResponse.success(postQueries.list(slug, prefix, sort, q, page, userId(authentication))));
    }

    static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }

    static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
