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
    /** 숨김 까닭: 운영자가 숨김(관리자 게시글 관리 — V80). 신고 숨김 까닭은 {@code ReportReasons.HIDDEN_*} */
    public static final String STAFF_HIDDEN = "STAFF";

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

    /** 소모임 글이면 그 소모임(V91 — 쓸 때 정하고 바뀌지 않는다), 일반 글 null */
    @Column(updatable = false)
    private Long groupId;
    /**
     * 소모임 글이 비멤버에게 보이는가(유효 노출 — V91 {@code is_public}, V94 에서 뜻이 넓어졌다) — 일반 글은 늘 false.
     * 저장 규칙: {@code (소모임 PUBLIC AND members_only_at IS NULL) OR public_granted_at IS NOT NULL}(쓰는 길은 {@code GroupPostVisibilityService} 하나)
     */
    @Column(name = "is_public")
    private boolean publicPost;
    /** 비공개 소모임에서 승인·직접 공개된 시각·처리자(V94) — null 이면 개별 공개가 아니다 */
    private Instant publicGrantedAt;
    private Long publicGrantedBy;
    /** 공개 소모임에서 모임장·매니저가 멤버 전용으로 돌린 시각·처리자(V94) — 비공개 소모임에서는 잠자는 값 */
    private Instant membersOnlyAt;
    private Long membersOnlyBy;
    /** 작성자가 소모임을 스스로 나가 가려진 시각(V91) — 다시 가입하면 비운다. 일괄 UPDATE({@code GroupPostQueries})로만 바꾼다 */
    @Column(updatable = false, insertable = false)
    private Instant groupLeftAt;

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

    /**
     * 소모임 글 — 그 소모임의 게시판에 쓴다. 비공개 소모임이면 멤버만 보고, 공개 소모임이면 쓰는 순간 비멤버에게도 보인다(V94 — api-groups.md 11-1).
     * 소모임 글은 19금이 될 수 없고 비회원이 쓰지 않는다. 부르는 쪽이 소모임 행을 잠근 뒤 부른다(범위 변경과 줄을 선다).
     */
    public static PostEntity createInGroup(long id, long groupId, long boardId, Long prefixId, long authorId, String authorIp, String title, ContentFormat format,
                                           String content, String contentText, boolean hasImage, boolean publicGroup, Instant now) {
        PostEntity post = create(id, boardId, prefixId, authorId, null, null, authorIp, title, format, content, contentText, false, hasImage, now);
        post.groupId = groupId;
        post.publicPost = publicGroup;
        return post;
    }

    /** 비공개 소모임 글을 개별 공개한다(승인·직접 공개) — 바뀌었으면 true. 멤버 전용 기록은 지운다(서로 배타) */
    public boolean grantPublic(Instant now, long byUserId) {
        if (groupId == null || (publicPost && publicGrantedAt != null)) {
            return false;
        }
        this.publicPost = true;
        this.publicGrantedAt = now;
        this.publicGrantedBy = byUserId;
        this.membersOnlyAt = null;
        this.membersOnlyBy = null;
        this.updatedAt = now;
        return true;
    }

    /** 비공개 소모임의 개별 공개를 거둔다(되돌리기) — 바뀌었으면 true */
    public boolean revokePublic(Instant now) {
        if (groupId == null || (!publicPost && publicGrantedAt == null)) {
            return false;
        }
        this.publicPost = false;
        this.publicGrantedAt = null;
        this.publicGrantedBy = null;
        this.updatedAt = now;
        return true;
    }

    /** 공개 소모임의 글을 멤버 전용으로 돌린다 — 바뀌었으면 true. 개별 공개 기록은 지운다(서로 배타) */
    public boolean markMembersOnly(Instant now, long byUserId) {
        if (groupId == null || (!publicPost && membersOnlyAt != null)) {
            return false;
        }
        this.publicPost = false;
        this.membersOnlyAt = now;
        this.membersOnlyBy = byUserId;
        this.publicGrantedAt = null;
        this.publicGrantedBy = null;
        this.updatedAt = now;
        return true;
    }

    /** 공개 소모임에서 멤버 전용으로 돌린 글을 다시 공개한다 — 바뀌었으면 true */
    public boolean releaseMembersOnly(Instant now) {
        if (groupId == null || (publicPost && membersOnlyAt == null)) {
            return false;
        }
        this.publicPost = true;
        this.membersOnlyAt = null;
        this.membersOnlyBy = null;
        this.updatedAt = now;
        return true;
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

    // ── 운영자 조치(관리자 게시글 관리 — docs/features/post-admin-contract.md). 상태 확인은 부르는 서비스가 한다 ──

    /** 운영자가 숨긴다 — 까닭 {@value #STAFF_HIDDEN}, 기간 없음. 고정을 푼다 */
    public void staffHide(Instant now) {
        this.hiddenAt = now;
        this.hiddenReason = STAFF_HIDDEN;
        this.hiddenUntil = null;
        this.pinnedAt = null;
        this.updatedAt = now;
    }

    /** 숨김을 푼다(까닭이 무엇이든 — 신고 숨김도) */
    public void unhide(Instant now) {
        this.hiddenAt = null;
        this.hiddenReason = null;
        this.hiddenUntil = null;
        this.updatedAt = now;
    }

    /** 자동 숨김을 운영자가 확정한다(관리자 신고 관리 「자동 숨김 확정」) — 권리침해 임시조치면 기한을 지워 계속 숨긴다. 까닭은 그대로. 상태 확인은 부르는 서비스가 한다 */
    public void keepHidden(Instant now) {
        this.hiddenUntil = null;
        this.updatedAt = now;
    }

    /** 운영자가 지운다 — 고정을 푼다(원문은 보유기간까지 남는다) */
    public void staffDelete(Instant now) {
        this.deletedAt = now;
        this.pinnedAt = null;
        this.updatedAt = now;
    }

    /** 지운 글을 되살린다(예약 삭제도 거둔다) — 숨김 상태는 그대로 둔다 */
    public void restore(Instant now) {
        this.deletedAt = null;
        this.deleteScheduledAt = null;
        this.updatedAt = now;
    }

    /** 다른 게시판·말머리로 옮긴다 — 고정을 푼다 */
    public void moveTo(long boardId, Long prefixId, Instant now) {
        this.boardId = boardId;
        this.prefixId = prefixId;
        this.pinnedAt = null;
        this.updatedAt = now;
    }

    /** 말머리를 바꾼다(공지 지정) */
    public void changePrefix(Long prefixId, Instant now) {
        this.prefixId = prefixId;
        this.updatedAt = now;
    }

    /** 고정한다 — 이미 고정돼 있으면 처음 시각 그대로 */
    public void pin(Instant now) {
        if (pinnedAt == null) {
            this.pinnedAt = now;
            this.updatedAt = now;
        }
    }

    /** 고정을 푼다 */
    public void unpin(Instant now) {
        if (pinnedAt != null) {
            this.pinnedAt = null;
            this.updatedAt = now;
        }
    }

    /** 원문을 파기했는가(보유기간 배치) — 복구할 수 없다 */
    public boolean isPurged() {
        return purgedAt != null;
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

    public String getStatus() {
        return status;
    }

    public Instant getHiddenUntil() {
        return hiddenUntil;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
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

    public Long getGroupId() {
        return groupId;
    }

    /** 소모임 글인가 */
    public boolean isGroupPost() {
        return groupId != null;
    }

    /** 비멤버에게 보이는 소모임 글인가(유효 노출 — 공개 소모임의 글 · 개별 공개된 글) */
    public boolean isPublicPost() {
        return publicPost;
    }

    public Instant getPublicGrantedAt() {
        return publicGrantedAt;
    }

    public Long getPublicGrantedBy() {
        return publicGrantedBy;
    }

    public Instant getMembersOnlyAt() {
        return membersOnlyAt;
    }

    public Long getMembersOnlyBy() {
        return membersOnlyBy;
    }

    public Instant getGroupLeftAt() {
        return groupLeftAt;
    }

    /** 작성자가 소모임을 나가 가려졌나 */
    public boolean isGroupLeft() {
        return groupLeftAt != null;
    }
}
