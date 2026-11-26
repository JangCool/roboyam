package com.community.counsel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 연애상담소 사연 하나 — {@code counsel_posts}(shared V274, 글 1:1). 관리자 서버 실행기({@code CounselRunner})가 「AI 상담소」 말머리 새 글을 판정하며 만든다.
 * 위험 판정 결과 · 답할 상담사(판정 때 쉬지 않던 상담사 — 화면 차례) · 사연 쓴 사람이 고른 답을 든다. 상담사 답 자체는 그 글의 댓글이다(작성자 = 상담사 AI 회원).
 */
@Entity
@Table(name = "counsel_posts")
public class CounselPostEntity {

    /** 위험 판정 까닭 — 낱말 목록 */
    public static final String RISK_WORD = "WORD";
    /** 위험 판정 까닭 — AI 분류 */
    public static final String RISK_AI = "AI";
    /** 고른 답이 사람 회원 답일 때의 상담사 칸 값 */
    public static final long HUMAN = 0L;

    @Id
    private Long postId;

    private Long boardId;
    private Long authorId;

    @Enumerated(EnumType.STRING)
    private CounselState state;

    private String riskReason;
    private short riskChecks;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] counselorIds;

    private Long pickedCommentId;
    private Long pickedAiMemberId;
    private Instant pickedAt;
    private Instant postCreatedAt;
    private Instant createdAt;
    private Instant closedAt;

    protected CounselPostEntity() {
    }

    /** 낱말 목록에 걸린 위험 사연 — 답하지 않으므로 처음부터 끝난 줄 */
    public static CounselPostEntity risk(long postId, long boardId, long authorId, Instant postCreatedAt, String reason, Instant now) {
        CounselPostEntity e = base(postId, boardId, authorId, postCreatedAt, now);
        e.state = CounselState.RISK;
        e.riskReason = reason;
        e.closedAt = now;
        return e;
    }

    /** 낱말 목록을 지났다 — AI 분류를 기다린다 */
    public static CounselPostEntity checking(long postId, long boardId, long authorId, Instant postCreatedAt, Instant now) {
        CounselPostEntity e = base(postId, boardId, authorId, postCreatedAt, now);
        e.state = CounselState.CHECKING;
        return e;
    }

    private static CounselPostEntity base(long postId, long boardId, long authorId, Instant postCreatedAt, Instant now) {
        CounselPostEntity e = new CounselPostEntity();
        e.postId = postId;
        e.boardId = boardId;
        e.authorId = authorId;
        e.counselorIds = new Long[0];
        e.postCreatedAt = postCreatedAt;
        e.createdAt = now;
        return e;
    }

    /** AI 분류 한 번 실패(다음 바퀴에 다시) */
    public void checkFailed() {
        this.riskChecks = (short) (riskChecks + 1);
    }

    /** AI 분류가 위험이라고 했다 — 답하지 않는다 */
    public void markRisk(String reason, Instant now) {
        this.riskChecks = (short) (riskChecks + 1);
        this.state = CounselState.RISK;
        this.riskReason = reason;
        this.closedAt = now;
    }

    /**
     * 통과 — 이 상담사들이 답한다(비었으면 답할 상담사가 없어 그대로 끝낸다).
     *
     * @param counted AI 분류를 실제로 불렀는가(시도 수에 더한다)
     */
    public void open(List<Long> counselors, boolean counted, Instant now) {
        if (counted) {
            this.riskChecks = (short) (riskChecks + 1);
        }
        this.state = CounselState.OPEN;
        this.counselorIds = counselors.toArray(Long[]::new);
        if (counselors.isEmpty()) {
            this.closedAt = now;
        }
    }

    /** 답 차례를 끝낸다(모두 답했거나 시한이 지났다) */
    public void close(Instant now) {
        if (closedAt == null) {
            this.closedAt = now;
        }
    }

    /** 「가장 도움 된 조언」을 골랐다 — 다시 고르기는 없다(부르는 쪽이 확인) */
    public void pick(long commentId, long aiMemberIdOrHuman, Instant now) {
        this.pickedCommentId = commentId;
        this.pickedAiMemberId = aiMemberIdOrHuman;
        this.pickedAt = now;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public CounselState getState() {
        return state;
    }

    public boolean isRisk() {
        return state == CounselState.RISK;
    }

    public String getRiskReason() {
        return riskReason;
    }

    public int getRiskChecks() {
        return riskChecks;
    }

    public List<Long> getCounselorIds() {
        return counselorIds == null ? List.of() : Arrays.asList(counselorIds.clone());
    }

    public Long getPickedCommentId() {
        return pickedCommentId;
    }

    public Long getPickedAiMemberId() {
        return pickedAiMemberId;
    }

    public Instant getPickedAt() {
        return pickedAt;
    }

    public Instant getPostCreatedAt() {
        return postCreatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
