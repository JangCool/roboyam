package com.community.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 글 — V42·V46 의 {@code posts}(id 월 범위 파티션). ID 는 Snowflake(앱 발급)라 새 행은 {@code persist()} 로 넣는다.
 *
 * <ul>
 *   <li>작성자: 회원 글은 {@code authorId}, 비회원 글은 {@code guestName} + {@code guestPasswordHash}(bcrypt 해시만). 작성 IP 는 둘 다 남긴다(개인정보 배치가 보유기간 뒤 지운다).</li>
 *   <li>숫자 칸(조회·추천·보이는 댓글 수)은 이 엔티티로 고치지 않는다 — 동시에 오르내리므로 원자적 UPDATE({@code PostCounterQueries})만 쓴다({@code updatable = false}).</li>
 *   <li>보이는가: {@link #isGoneAt(Instant)}(지움·예약 삭제 시각 지남·공개 아님) · {@link #isHiddenAt(Instant)}(신고 숨김 — 임시조치는 기간이 끝나면 다시 보인다).</li>
 * </ul>
 * {@code toString} 을 두지 않는다(비밀번호 해시·IP).
 */
@Entity
@Table(name = "posts")
@DynamicUpdate
public class PostEntity {

    public static final String PUBLISHED = "PUBLISHED";

    @Id
    private Long id;

    private Long boardId;
    private Long prefixId;
    private Long authorId;
    private String guestName;
    private String guestPasswordHash;
    private String authorIp;
    private String title;
    private String content;
    @Enumerated(EnumType.STRING)
    private ContentFormat contentFormat;
    /** HTML 글의 보이는 글자(검색·같은 글 반복 판정) — TEXT 글은 null(본문이 평문이다) */
    private String contentText;
    private String status;
    private boolean adult;
    private boolean hasImage;

    @Column(updatable = false)
    private int viewCount;
    @Column(updatable = false)
    private int likeCount;
    @Column(updatable = false)
    private int commentCount;

    private Instant pinnedAt;
    private Instant hiddenAt;
    private String hiddenReason;
    private Instant hiddenUntil;
    private Instant deleteScheduledAt;
    private Instant deletedAt;
    private Instant purgedAt;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected PostEntity() {
    }

    /**
     * 새 글. 회원 글이면 {@code authorId} 가 있고 비회원 정보는 null, 비회원 글이면 그 반대다.
     *
     * @param contentText HTML 글의 보이는 글자(TEXT 글은 무시하고 null 로 둔다)
     */
    public static PostEntity create(long id, long boardId, Long prefixId, Long authorId, String guestName, String guestPasswordHash, String authorIp,
                                    String title, ContentFormat format, String content, String contentText, boolean adult, boolean hasImage, Instant now) {
        PostEntity post = new PostEntity();
        post.id = id;
        post.boardId = boardId;
        post.prefixId = prefixId;
        post.authorId = authorId;
        post.guestName = authorId == null ? guestName : null;
        post.guestPasswordHash = authorId == null ? guestPasswordHash : null;
        post.authorIp = authorIp;
        post.title = title;
        post.contentFormat = format;
        post.content = content;
        post.contentText = format == ContentFormat.HTML ? contentText : null;
        post.status = PUBLISHED;
        post.adult = adult;
        post.hasImage = hasImage;
        post.createdAt = now;
        post.updatedAt = now;
        return post;
    }

    /** 작성자가 고친다 */
    public void edit(Long prefixId, String title, ContentFormat format, String content, String contentText, boolean adult, boolean hasImage, Instant now) {
        this.prefixId = prefixId;
        this.title = title;
        this.contentFormat = format;
        this.content = content;
        this.contentText = format == ContentFormat.HTML ? contentText : null;
        this.adult = adult;
        this.hasImage = hasImage;
        this.editedAt = now;
        this.updatedAt = now;
    }

    /** 지운다(원문은 보유기간까지 남는다) */
    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** 예약 삭제 — 이미 예약돼 있으면 처음 시각 그대로(멱등) */
    public Instant scheduleDelete(Instant at, Instant now) {
        if (deleteScheduledAt == null) {
            this.deleteScheduledAt = at;
            this.updatedAt = now;
        }
        return deleteScheduledAt;
    }

    /** 신고로 숨긴다 — 이미 숨겨져 있으면 그대로 */
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

    public boolean isGuest() {
        return authorId == null;
    }

    /** 더는 보이지 않는 글인가 — 지움 · 예약 삭제 시각 지남 · 공개 아님(임시저장) */
    public boolean isGoneAt(Instant now) {
        return deletedAt != null || !PUBLISHED.equals(status) || (deleteScheduledAt != null && !deleteScheduledAt.isAfter(now));
    }

    /** 신고로 숨겨진 상태인가(임시조치는 기간 안일 때만) */
    public boolean isHiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getPrefixId() {
        return prefixId;
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

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    /** 본문 형식 — 칸이 비어 있던 옛 행은 TEXT */
    public ContentFormat getContentFormat() {
        return contentFormat == null ? ContentFormat.TEXT : contentFormat;
    }

    /** 보이는 글자 — HTML 글은 저장해 둔 평문, TEXT 글은 본문 그대로 */
    public String plainContent() {
        return getContentFormat() == ContentFormat.HTML && contentText != null ? contentText : content;
    }

    public boolean isAdult() {
        return adult;
    }

    public boolean isHasImage() {
        return hasImage;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public Instant getDeleteScheduledAt() {
        return deleteScheduledAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
