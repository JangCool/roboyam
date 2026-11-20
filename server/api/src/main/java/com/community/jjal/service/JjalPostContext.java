package com.community.jjal.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.group.service.GroupPostGate;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.zconfiguration.exception.CommonException;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 짤이 보이는 글·댓글 판정 — 담기({@code POST /api/me/jjals/collect})와 표시 정보({@code GET /api/jjals?postId=})가 같이 쓴다(my-jjal-contract 「담기」·정책 5).
 * 보는 사람이 그 글을 읽을 수 있는가는 글 보기와 같은 판정({@link GroupPostGate#resolve} — 게시판 꺼짐·소모임·성인 전용 게시판·19금 글·비밀글)이고, 숨긴 글·지운 글은 보이지 않는다.
 * 막힌 까닭은 밖으로 가르지 않는다(부르는 쪽이 「없는 짤」로 답한다).
 */
@Component
public class JjalPostContext {

    private final BoardCatalog catalog;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final GroupPostGate groupGate;

    public JjalPostContext(BoardCatalog catalog, PostRepository posts, CommentRepository comments, GroupPostGate groupGate) {
        this.catalog = catalog;
        this.posts = posts;
        this.comments = comments;
        this.groupGate = groupGate;
    }

    /**
     * 보이는 글.
     *
     * @param adult 19금 게시판(성인 전용)의 글이거나 19금 글 — 이 글·댓글의 짤은 담을 수 없다
     */
    public record Visible(PostEntity post, EffectiveBoard board, boolean adult) {
    }

    /** 보는 사람에게 보이는 글 — 아니면 null */
    public Visible post(long postId, Long viewerId, Instant now) {
        PostEntity post = posts.findById(postId).filter(p -> !p.isGoneAt(now) && !p.isHiddenAt(now)).orElse(null);
        if (post == null) {
            return null;
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard board;
        try {
            board = groupGate.resolve(s, post, viewerId).board();
        } catch (CommonException e) {
            return null; // 읽을 수 없는 글은 없는 글과 같다
        }
        return new Visible(post, board, board.adultOnly() || post.isAdult());
    }

    /** 그 글의 보이는 댓글인가(지우지 않았고 숨기지 않았다) */
    public boolean commentVisible(long postId, long commentId, Instant now) {
        CommentEntity c = comments.findByPostIdAndId(postId, commentId).orElse(null);
        return c != null && !c.isDeleted() && !c.isHiddenAt(now);
    }
}
