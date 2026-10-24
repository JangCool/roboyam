package com.community.comment.entity;

import jakarta.persistence.Column;
import com.community.post.entity.ContentFormat;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 댓글 — V42·V46 의 {@code comments}(post_id 해시 64 파티션, PK = 글 + 댓글). 답글은 1단계({@code parentId} = 원댓글, {@code replyToId} = 실제로 답한 댓글).
 * 추천 수는 원자적 UPDATE 로만 고친다({@code updatable = false}). {@code toString} 을 두지 않는다(비밀번호 해시·IP).
 */
@Entity
@Table(name = "comments")
@IdClass(CommentId.class)
@DynamicUpdate
public class CommentEntity {

    /** 운영자가 숨긴 의견의 숨김 까닭({@code comments.hidden_reason}, V76) — 신고 숨김 까닭(REPORTED · ILLEGAL · RIGHTS)과 같은 칸 */
    public static final String STAFF_HIDDEN = "STAFF";

    @Id
    private Long postId;

    @Id
    private Long id;

    private Long boardId;
    private Long parentId;
    private Long replyToId;
    private Long authorId;
    private String guestName;
    private String guestPasswordHash;
    private String authorIp;
    private String content;
    @Enumerated(EnumType.STRING)
    private ContentFormat contentFormat;

    @Column(updatable = false)
    private int likeCount;
    /** 비추천 수(V78) — {@code PostCounterQueries} 가 원자적으로 고친다. 게시판의 비추천이 꺼져 있으면 응답에 싣지 않는다 */
    private int dislikeCount;

    /** 하이라이트 토론의 입장 배지(PRO · CON · NEUTRAL — {@code DebateStance}), 일반 댓글은 null. 쓸 때 정해지고, 글쓴이가 입장을 바꾸면 소급해 바뀐다(V76·V78) */
    private String stance;

    private Instant hiddenAt;
    private String hiddenReason;
    private Instant hiddenUntil;
    private Instant deletedAt;
    private Instant purgedAt;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected CommentEntity() {
    }

    public static CommentEntity create(long id, long postId, long boardId, Long parentId, Long replyToId, Long authorId, String guestName,
                                       String guestPasswordHash, String authorIp, ContentFormat format, String content, Instant now) {
        return create(id, postId, boardId, parentId, replyToId, authorId, guestName, guestPasswordHash, authorIp, format, content, null, now);
    }

    /** @param stance 하이라이트 토론의 입장 배지(PRO · CON · NEUTRAL), 일반 댓글이면 null */
    public static CommentEntity create(long id, long postId, long boardId, Long parentId, Long replyToId, Long authorId, String guestName,
                                       String guestPasswordHash, String authorIp, ContentFormat format, String content, String stance, Instant now) {
        CommentEntity comment = new CommentEntity();
        comment.stance = stance;
        comment.id = id;
        comment.postId = postId;
        comment.boardId = boardId;
        comment.parentId = parentId;
        comment.replyToId = replyToId;
        comment.authorId = authorId;
        comment.guestName = authorId == null ? guestName : null;
        comment.guestPasswordHash = authorId == null ? guestPasswordHash : null;
        comment.authorIp = authorIp;
        comment.contentFormat = format;
        comment.content = content;
        comment.createdAt = now;
        comment.updatedAt = now;
        return comment;
    }

    public void edit(ContentFormat format, String content, Instant now) {
        this.contentFormat = format;
        this.content = content;
        this.editedAt = now;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** 신고로 숨긴다 — 이미 숨겨져 있으면 그대로(false) */
    public boolean hide(String reason, Instant until, Instant now) {
        if (isHiddenAt(now)) {
            return false;
        }
        this.hiddenAt = now;
        this.hiddenReason = reason;
        this.hiddenUntil = until;
        this.updatedAt = now;
        return true;
    }

    /** 운영자가 의견을 숨긴다(관리자 하이라이트 의견 관리) — 까닭 {@value #STAFF_HIDDEN}, 기간 없음. 이미 숨겨져 있으면(신고 숨김 포함) 그대로(false) */
    public boolean hideByStaff(Instant now) {
        return hide(STAFF_HIDDEN, null, now);
    }

    /** 운영자 숨김을 푼다 — 까닭이 {@value #STAFF_HIDDEN} 인 숨김만(신고 숨김은 풀지 않는다). 풀었으면 true */
    public boolean unhideByStaff(Instant now) {
        if (!STAFF_HIDDEN.equals(hiddenReason) || hiddenAt == null) {
            return false;
        }
        this.hiddenAt = null;
        this.hiddenReason = null;
        this.hiddenUntil = null;
        this.updatedAt = now;
        return true;
    }

    /** 운영자가 숨긴 의견인가(지금 숨김 중이고 까닭이 STAFF) */
    public boolean isStaffHiddenAt(Instant now) {
        return isHiddenAt(now) && STAFF_HIDDEN.equals(hiddenReason);
    }

    public boolean isGuest() {
        return authorId == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 신고로 숨겨진 상태인가(임시조치는 기간 안일 때만) */
    public boolean isHiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }

    public Long getPostId() {
        return postId;
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getReplyToId() {
        return replyToId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getGuestPasswordHash() {
        return guestPasswordHash;
    }

    public String getAuthorIp() {
        return authorIp;
    }

    /** 본문 형식 — 칸이 비어 있던 옛 행은 TEXT */
    public ContentFormat getContentFormat() {
        return contentFormat == null ? ContentFormat.TEXT : contentFormat;
    }

    public String getContent() {
        return content;
    }

    /** 입장 배지(PRO · CON), 일반 댓글은 null */
    public String getStance() {
        return stance;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getDislikeCount() {
        return dislikeCount;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
