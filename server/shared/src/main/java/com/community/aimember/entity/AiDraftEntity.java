package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * AI 생성물 한 건 — {@code ai_member_drafts}(shared V183). 검수 대기함이자 활동 기록이다: 생성 호출 한 번마다 한 줄(실패도 — 하루 호출 수는 이 표에서 센다).
 * 상태 흐름은 {@link AiDraftStatus}. 본문에는 모델이 만든 글만 들어간다(프롬프트·회원 댓글 원문은 남기지 않는다).
 */
@Entity
@Table(name = "ai_member_drafts")
public class AiDraftEntity {

    @Id
    private Long id;

    private Long aiMemberId;

    @Enumerated(EnumType.STRING)
    private AiDraftKind kind;

    @Enumerated(EnumType.STRING)
    private AiDraftStatus status;

    private Long boardId;
    private Long groupId;
    private Long postId;
    private Long parentCommentId;
    private Long replyToCommentId;
    private Long counterpartUserId;
    private boolean counterpartAi;
    private Short round;
    private String title;
    private String body;

    @Enumerated(EnumType.STRING)
    private AiProvider provider;

    private String model;
    private int inputTokens;
    private int outputTokens;
    private boolean instant;
    private String failReason;
    /** 실패 줄의 제공사 메시지(가린 것 · 300자 이하) — 옛 줄·등록 단계 실패는 비어 있다 */
    private String failDetail;
    private Instant createdAt;
    private Instant reviewedAt;
    private Long reviewedBy;
    private Long publishedPostId;
    private Long publishedCommentId;
    private Instant publishedAt;
    /** 뉴스 글의 소재 글(사이트 회원이 쓴 「뉴스」 말머리 글 — V190). 뉴스 글이 아니면 null */
    private Long sourcePostId;

    protected AiDraftEntity() {
    }

    /** 무엇에 대한 생성물인가 — 글이면 게시판·소모임만(뉴스 글이면 소재 글까지), 댓글·답글이면 글·댓글·상대까지 */
    public record Target(AiDraftKind kind, Long boardId, Long groupId, Long postId, Long parentCommentId, Long replyToCommentId, Long counterpartUserId,
                         boolean counterpartAi, Integer round, Long sourcePostId) {

        public Target(AiDraftKind kind, Long boardId, Long groupId, Long postId, Long parentCommentId, Long replyToCommentId, Long counterpartUserId,
                      boolean counterpartAi, Integer round) {
            this(kind, boardId, groupId, postId, parentCommentId, replyToCommentId, counterpartUserId, counterpartAi, round, null);
        }

        public static Target post(long boardId, Long groupId) {
            return new Target(AiDraftKind.POST, boardId, groupId, null, null, null, null, false, null, null);
        }

        /** 뉴스 글 — 게시판 글(소모임 아님) + 소재 글 */
        public static Target newsPost(long boardId, long sourcePostId) {
            return new Target(AiDraftKind.POST, boardId, null, null, null, null, null, false, null, sourcePostId);
        }
    }

    /** 생성에 성공한 한 건 — {@code instant} 면 바로 게시 대상(APPROVED), 아니면 검수 대기(PENDING) */
    public static AiDraftEntity generated(long id, long aiMemberId, Target t, String title, String body, AiProvider provider, String model, int in, int out,
                                          boolean instant, Instant now) {
        AiDraftEntity d = base(id, aiMemberId, t, provider, model, now);
        d.status = instant ? AiDraftStatus.APPROVED : AiDraftStatus.PENDING;
        d.title = title;
        d.body = body == null ? "" : body;
        d.inputTokens = Math.max(0, in);
        d.outputTokens = Math.max(0, out);
        d.instant = instant;
        return d;
    }

    /** 생성 호출이 실패한 한 건 */
    public static AiDraftEntity failed(long id, long aiMemberId, Target t, AiProvider provider, String model, int in, int out, String reason, Instant now) {
        return failed(id, aiMemberId, t, provider, model, in, out, reason, null, now);
    }

    /** 생성 호출이 실패한 한 건 — 제공사 메시지(가린 것 · 300자 이하, V189 {@code fail_detail})까지 */
    public static AiDraftEntity failed(long id, long aiMemberId, Target t, AiProvider provider, String model, int in, int out, String reason, String detail,
                                       Instant now) {
        AiDraftEntity d = base(id, aiMemberId, t, provider, model, now);
        d.status = AiDraftStatus.FAILED;
        d.body = "";
        d.inputTokens = Math.max(0, in);
        d.outputTokens = Math.max(0, out);
        d.failReason = reason;
        d.failDetail = detail;
        return d;
    }

    private static AiDraftEntity base(long id, long aiMemberId, Target t, AiProvider provider, String model, Instant now) {
        AiDraftEntity d = new AiDraftEntity();
        d.id = id;
        d.aiMemberId = aiMemberId;
        d.kind = t.kind();
        d.boardId = t.boardId();
        d.groupId = t.groupId();
        d.postId = t.postId();
        d.parentCommentId = t.parentCommentId();
        d.replyToCommentId = t.replyToCommentId();
        d.counterpartUserId = t.counterpartUserId();
        d.counterpartAi = t.counterpartAi();
        d.round = t.round() == null ? null : t.round().shortValue();
        d.provider = provider;
        d.model = model;
        d.sourcePostId = t.sourcePostId();
        d.createdAt = now;
        return d;
    }

    /** 관리자가 게시를 승인했다(수정 후 게시면 제목·본문을 바꾼 뒤) — 실제 등록은 api 서버 실행기 */
    public void approve(String title, String body, long operatorId, Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (body != null) {
            this.body = body;
        }
        this.status = AiDraftStatus.APPROVED;
        this.reviewedAt = now;
        this.reviewedBy = operatorId;
    }

    public void discard(long operatorId, Instant now) {
        this.status = AiDraftStatus.DISCARDED;
        this.reviewedAt = now;
        this.reviewedBy = operatorId;
    }

    /** 기존 쓰기 경로로 등록했다 */
    public void published(Long postId, Long commentId, Instant now) {
        this.status = AiDraftStatus.PUBLISHED;
        this.publishedPostId = postId;
        this.publishedCommentId = commentId;
        this.publishedAt = now;
    }

    /** 등록하지 못했다(금지어·제재·글이 사라짐 등) */
    public void fail(String reason) {
        this.status = AiDraftStatus.FAILED;
        this.failReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getAiMemberId() {
        return aiMemberId;
    }

    public AiDraftKind getKind() {
        return kind;
    }

    public AiDraftStatus getStatus() {
        return status;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public Long getReplyToCommentId() {
        return replyToCommentId;
    }

    public Long getCounterpartUserId() {
        return counterpartUserId;
    }

    public boolean isCounterpartAi() {
        return counterpartAi;
    }

    public Integer getRound() {
        return round == null ? null : round.intValue();
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public boolean isInstant() {
        return instant;
    }

    public String getFailReason() {
        return failReason;
    }

    public String getFailDetail() {
        return failDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Long getPublishedPostId() {
        return publishedPostId;
    }

    public Long getPublishedCommentId() {
        return publishedCommentId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Long getSourcePostId() {
        return sourcePostId;
    }
}
