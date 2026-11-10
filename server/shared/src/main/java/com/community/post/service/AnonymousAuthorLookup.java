package com.community.post.service;

import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.post.entity.PostEntity;
import com.community.post.exception.AnonymousTargetNotFoundException;
import com.community.post.exception.NotAnonymousContentException;
import com.community.post.repository.master.PostRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 익명 글·댓글의 작성 회원 찾기(anonymous-board-contract 정책 9·10) — 「이 글/댓글 작성자」 제재(관리자 회원 정지 · 게시판 매니저 제재)와 작성자 열람이 같이 쓴다.
 * 작성자 회원 ID 는 이 찾기의 결과로만 서버 안에서 쓰고, 열람 API 가 아니면 응답·감사에 싣지 않는다(봉인).
 *
 * <ul>
 *   <li>글 ID · 댓글 ID 중 <b>하나만</b> — 둘 다면 400 {@code fields.<댓글 칸>}, 둘 다 없으면 부르는 쪽이 정한다({@link #given}).</li>
 *   <li>형식이 틀리거나 없거나 파기됐거나 작성 회원이 없으면 404 {@code ANONYMOUS_TARGET_NOT_FOUND}. 익명이 아니면 409 {@code NOT_ANONYMOUS}.</li>
 *   <li>지운 글·댓글·숨긴 글도 찾는다(제재·열람은 지운 뒤에도 할 수 있다 — 파기({@code purged})만 없다).</li>
 * </ul>
 */
@Component
public class AnonymousAuthorLookup {

    private final PostRepository posts;
    private final CommentRepository comments;
    private final CommentRepositoryQDSL commentQueries;

    public AnonymousAuthorLookup(PostRepository posts, CommentRepository comments, CommentRepositoryQDSL commentQueries) {
        this.posts = posts;
        this.comments = comments;
        this.commentQueries = commentQueries;
    }

    /**
     * 찾은 작성자 — {@code commentId} 가 있으면 그 댓글의 작성자, 없으면 글의 작성자.
     *
     * @param postTitle 그 글의 제목(제재 계기 스냅숏)
     */
    public record Found(long userId, long postId, Long commentId, long boardId, String postTitle) {
    }

    /** 익명 대상 칸을 하나라도 보냈는가 */
    public static boolean given(String postIdText, String commentIdText) {
        return !blank(postIdText) || !blank(commentIdText);
    }

    /**
     * @param postField    글 칸 이름(오류 경로 — 예 {@code anonymousPostId})
     * @param commentField 댓글 칸 이름(예 {@code anonymousCommentId})
     */
    public Found resolve(String postIdText, String commentIdText, String postField, String commentField) {
        if (!blank(postIdText) && !blank(commentIdText)) {
            Map<String, MessageRef> invalid = new LinkedHashMap<>();
            invalid.put(commentField, new MessageRef("anonymous.target.one"));
            throw new ValidationException(invalid);
        }
        if (!blank(commentIdText)) {
            Long commentId = parse(commentIdText);
            Long postId = commentId == null ? null : commentQueries.postIdOf(commentId).orElse(null);
            CommentEntity comment = postId == null ? null : comments.findByPostIdAndId(postId, commentId).orElse(null);
            PostEntity post = comment == null ? null : posts.findById(postId).orElse(null);
            if (comment == null || post == null || comment.getAuthorId() == null || comment.getPurgedAt() != null) {
                throw new AnonymousTargetNotFoundException();
            }
            if (!comment.isAnonymous()) {
                throw new NotAnonymousContentException();
            }
            return new Found(comment.getAuthorId(), postId, commentId, comment.getBoardId(), post.getTitle());
        }
        Long postId = parse(postIdText);
        PostEntity post = postId == null ? null : posts.findById(postId).orElse(null);
        if (post == null || post.getAuthorId() == null || post.getPurgedAt() != null) {
            throw new AnonymousTargetNotFoundException();
        }
        if (!post.isAnonymous()) {
            throw new NotAnonymousContentException();
        }
        return new Found(post.getAuthorId(), postId, null, post.getBoardId(), post.getTitle());
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static Long parse(String raw) {
        if (blank(raw) || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
