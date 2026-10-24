package com.community.block.controller;

import com.community.auth.dto.AuthUser;
import com.community.block.dto.request.BlockedWordRequest;
import com.community.block.dto.request.MemoRequest;
import com.community.block.dto.response.BlockResultView;
import com.community.block.dto.response.BlockedUserListView;
import com.community.block.dto.response.BlockedWordListView;
import com.community.block.dto.response.BlockedWordView;
import com.community.block.dto.response.MemoListView;
import com.community.block.dto.response.MemoResultView;
import com.community.block.service.BlockService;
import com.community.block.service.MemoService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
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
 * 차단(닉네임·단어)·이용자 메모 API(공개 사이트) — user-web {@code useBlockApi.ts}·{@code useMemoApi.ts} 머리말 그대로. 로그인 필요(401, {@code SecurityConfig} 의 {@code /api/**}),
 * 내 것만(경로의 회원 ID 는 상대). 개인 정보라 {@code Cache-Control: no-store}, 쓰기는 CSRF 헤더가 필요하다.
 */
@RestController
@RequestMapping("/api")
public class BlockController {

    private final BlockService blocks;
    private final MemoService memos;

    public BlockController(BlockService blocks, MemoService memos) {
        this.blocks = blocks;
        this.memos = memos;
    }

    @PutMapping("/users/{id}/block")
    public ResponseEntity<ApiResponse<BlockResultView>> block(@PathVariable String id, Authentication authentication) {
        return noStore(HttpStatus.OK, blocks.block(userId(authentication), id));
    }

    @DeleteMapping("/users/{id}/block")
    public ResponseEntity<Void> unblock(@PathVariable String id, Authentication authentication) {
        blocks.unblock(userId(authentication), id);
        return noContent();
    }

    @GetMapping("/me/blocks/users")
    public ResponseEntity<ApiResponse<BlockedUserListView>> blockedUsers(@RequestParam(required = false) Integer size, @RequestParam(required = false) String cursor,
                                                                       Authentication authentication) {
        return noStore(HttpStatus.OK, blocks.users(userId(authentication), size, cursor));
    }

    @GetMapping("/me/blocks/words")
    public ResponseEntity<ApiResponse<BlockedWordListView>> words(Authentication authentication) {
        return noStore(HttpStatus.OK, blocks.words(userId(authentication)));
    }

    @PostMapping("/me/blocks/words")
    public ResponseEntity<ApiResponse<BlockedWordView>> addWord(@RequestBody(required = false) BlockedWordRequest body, Authentication authentication) {
        return noStore(HttpStatus.CREATED, blocks.addWord(userId(authentication), body == null ? null : body.word()));
    }

    @DeleteMapping("/me/blocks/words/{id}")
    public ResponseEntity<Void> removeWord(@PathVariable String id, Authentication authentication) {
        blocks.removeWord(userId(authentication), id);
        return noContent();
    }

    @PutMapping("/users/{id}/memo")
    public ResponseEntity<ApiResponse<MemoResultView>> saveMemo(@PathVariable String id, @RequestBody(required = false) MemoRequest body,
                                                                Authentication authentication) {
        return noStore(HttpStatus.OK, memos.save(userId(authentication), id, body == null ? null : body.memo()));
    }

    @DeleteMapping("/users/{id}/memo")
    public ResponseEntity<Void> removeMemo(@PathVariable String id, Authentication authentication) {
        memos.remove(userId(authentication), id);
        return noContent();
    }

    @GetMapping("/me/memos")
    public ResponseEntity<ApiResponse<MemoListView>> memoList(@RequestParam(required = false) Integer size, @RequestParam(required = false) String cursor,
                                                              Authentication authentication) {
        return noStore(HttpStatus.OK, memos.list(userId(authentication), size, cursor));
    }

    /** 로그인한 회원 — 보안 설정이 막지만 혹시 빠져도 401 로 닫는다 */
    static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
