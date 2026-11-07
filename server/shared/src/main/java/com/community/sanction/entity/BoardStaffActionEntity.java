package com.community.sanction.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 게시판 매니저·스텝(·운영자 회원 계정)의 처리 기록 한 줄 — shared V137 · V138 · V141 의 {@code board_staff_actions}.
 * 제재 줄({@link #sanction}, kind SANCTION — ISSUE · LIFT)과 글·댓글 처리 줄({@link #content}, kind POST · COMMENT — HIDE · DELETE, 스냅숏과 함께)이 있다.
 * 되돌림은 저장소의 조건부 UPDATE(아직 되돌리지 않았을 때만)가 한다.
 */
@Entity
@Table(name = "board_staff_actions")
public class BoardStaffActionEntity {

    public static final String KIND_SANCTION = "SANCTION";

    @Id
    private Long id;

    private Long boardId;
    private Long actorId;
    /** MANAGER · STAFF · OPERATOR(운영자 회원 계정 — {@code actorOperatorId} 있음) */
    private String actorRole;
    private Long actorOperatorId;
    /** SANCTION · POST · COMMENT */
    private String kind;
    /** ISSUE · LIFT · HIDE · DELETE */
    private String action;
    private Long targetUserId;
    private Long boardSanctionId;
    private Long postId;
    private Long commentId;
    private String reason;
    private String reasonCode;
    private String targetTitle;
    private String targetExcerpt;
    private String targetAuthorName;
    private Instant createdAt;
    private Instant restoredAt;
    /** MANAGER · OPERATOR */
    private String restoredByKind;
    private Long restoredById;
    private String restoreReason;

    protected BoardStaffActionEntity() {
    }

    /** 제재 처리 한 줄(kind SANCTION) */
    public static BoardStaffActionEntity sanction(long id, long boardId, long actorId, String actorRole, Long actorOperatorId, String action, long targetUserId,
                                                  long boardSanctionId, Long postId, Long commentId, String reason, Instant now) {
        BoardStaffActionEntity a = base(id, boardId, actorId, actorRole, actorOperatorId, KIND_SANCTION, action, targetUserId, postId, commentId, now);
        a.boardSanctionId = boardSanctionId;
        a.reason = reason;
        return a;
    }

    /** 글·댓글 처리 한 줄(kind POST · COMMENT) — 스냅숏(제목 · 댓글 앞 40자 · 작성자 이름)과 함께 */
    public static BoardStaffActionEntity content(long id, long boardId, long actorId, String actorRole, Long actorOperatorId, String kind, String action,
                                                 Long targetUserId, long postId, Long commentId, String reasonCode, String title, String excerpt, String authorName,
                                                 Instant now) {
        BoardStaffActionEntity a = base(id, boardId, actorId, actorRole, actorOperatorId, kind, action, targetUserId, postId, commentId, now);
        a.reasonCode = reasonCode;
        a.targetTitle = title;
        a.targetExcerpt = excerpt;
        a.targetAuthorName = authorName;
        return a;
    }

    private static BoardStaffActionEntity base(long id, long boardId, long actorId, String actorRole, Long actorOperatorId, String kind, String action,
                                               Long targetUserId, Long postId, Long commentId, Instant now) {
        BoardStaffActionEntity a = new BoardStaffActionEntity();
        a.id = id;
        a.boardId = boardId;
        a.actorId = actorId;
        a.actorRole = actorRole;
        a.actorOperatorId = actorOperatorId;
        a.kind = kind;
        a.action = action;
        a.targetUserId = targetUserId;
        a.postId = postId;
        a.commentId = commentId;
        a.createdAt = now;
        return a;
    }
}
