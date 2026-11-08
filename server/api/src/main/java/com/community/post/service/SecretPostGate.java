package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardActorResolver;
import com.community.post.entity.PostEntity;
import com.community.post.exception.SecretPostException;
import org.springframework.stereotype.Component;

/**
 * 비밀글 판정 한 곳(V166 운영 게시판 설정 {@code secretMode}). <b>읽을 수 있는 사람</b> = 글쓴이(회원) · 그 게시판의 매니저·스텝 · 운영자 회원 계정
 * ({@link BoardActorResolver} 가 운영자 회원 계정을 모든 일반 게시판의 운영진으로 넣는다 — 게시판 응답 {@code viewer.staff} 와 같은 판정). 관리자 앱 운영자는 관리자 API 로 늘 본다.
 *
 * <ul>
 *   <li>글 하나를 여는 공개 API(상세·고치기·댓글 목록·댓글 쓰기·댓글 고치기·지우기·추천·반응·투표·찬반 토론·고정 요청·신고)는 403 {@code SECRET_POST}
 *       — 대부분 {@code GroupPostGate#resolve} 가 부른다.</li>
 *   <li>같은 게시판 목록은 줄을 남기고(제목 그대로, {@code secret: true}) 읽을 수 없는 사람에게는 썸네일·이미지·투표 표시를 비운다({@code PostQueryService}).</li>
 *   <li>섞인 목록은 질의에서 뺀다({@code PostRepositoryQDSL#feedVisible}).</li>
 * </ul>
 * 비밀글이 아니면 회원 판정을 읽지 않는다(요청당 추가 질의 없음).
 */
@Component
public class SecretPostGate {

    private final BoardActorResolver actors;

    public SecretPostGate(BoardActorResolver actors) {
        this.actors = actors;
    }

    /** 이 사람이 이 글을 읽을 수 있는가 — 비밀글이 아니면 늘 true */
    public boolean canRead(BoardCatalogSnapshot s, PostEntity post, Long viewerId) {
        if (!post.isSecret()) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        if (viewerId.equals(post.getAuthorId())) {
            return true;
        }
        return post.getBoardId() != null && actors.resolve(viewerId, s).staffOf(post.getBoardId());
    }

    /** 이미 만든 판정({@link BoardActor})으로 — 목록처럼 여러 줄을 볼 때 */
    public static boolean canRead(boolean secret, Long authorId, long boardId, BoardActor actor) {
        if (!secret) {
            return true;
        }
        if (actor == null || actor.guest()) {
            return false;
        }
        return actor.userId().equals(authorId) || actor.staffOf(boardId);
    }

    /** 읽을 수 없는 비밀글이면 403 {@code SECRET_POST}(본문 {@code boardSlug}) */
    public void requireReadable(BoardCatalogSnapshot s, PostEntity post, Long viewerId) {
        if (canRead(s, post, viewerId)) {
            return;
        }
        String slug = post.getBoardId() == null ? null : s.board(post.getBoardId()).map(b -> b.slug()).orElse(null);
        throw new SecretPostException(slug);
    }
}
