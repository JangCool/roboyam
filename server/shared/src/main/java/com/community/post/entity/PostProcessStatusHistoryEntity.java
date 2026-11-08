package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 글 처리 상태가 바뀐 기록 한 줄 — V168 {@code post_process_status_history}. 상태가 실제로 바뀔 때마다 한 줄 쌓고 고치지 않는다
 * (처음 접수 RECEIVED 는 줄이 없다, 같은 값으로 바꾸면 아무것도 남기지 않는다).
 *
 * <ul>
 *   <li>바꾼 쪽({@code actorKind}): {@link #OPERATOR}(관리자 앱 운영자 — {@code changedByOperatorId}, 또는 공개 사이트의 운영자 회원 계정 — {@code changedByUserId}) ·
 *       {@link #BOARD_STAFF}(그 게시판 매니저·스텝 — 회원) · {@link #GROUP_STAFF}(그 소모임의 소모임장·매니저 — 회원).</li>
 *   <li>{@code boardId} 는 바꾼 때 글이 있던 게시판(관리자가 글을 옮겨도 그대로). 글을 지우면(게시판 삭제) 같이 지우고, 글 원문을 파기하면 메모를 비운다(R__batch_18).</li>
 * </ul>
 */
@Entity
@Table(name = "post_process_status_history")
public class PostProcessStatusHistoryEntity {

    public static final String OPERATOR = "OPERATOR";
    public static final String BOARD_STAFF = "BOARD_STAFF";
    public static final String GROUP_STAFF = "GROUP_STAFF";

    /** 메모 최대 글자(V168) */
    public static final int NOTE_MAX = 200;

    @Id
    private Long id;

    private Long postId;
    private Long boardId;
    private String fromStatus;
    private String toStatus;
    private String note;
    private String actorKind;
    private Long changedByUserId;
    private Long changedByOperatorId;
    private Instant changedAt;

    protected PostProcessStatusHistoryEntity() {
    }

    /**
     * @param changedByUserId     바꾼 회원(공개 사이트) — 관리자 앱이면 null
     * @param changedByOperatorId 바꾼 운영자(관리자 앱) — 공개 사이트면 null
     */
    public static PostProcessStatusHistoryEntity create(long id, long postId, long boardId, PostProcessStatus from, PostProcessStatus to, String note,
                                                        String actorKind, Long changedByUserId, Long changedByOperatorId, Instant now) {
        PostProcessStatusHistoryEntity row = new PostProcessStatusHistoryEntity();
        row.id = id;
        row.postId = postId;
        row.boardId = boardId;
        row.fromStatus = from.name();
        row.toStatus = to.name();
        row.note = note;
        row.actorKind = actorKind;
        row.changedByUserId = changedByUserId;
        row.changedByOperatorId = changedByOperatorId;
        row.changedAt = now;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public String getActorKind() {
        return actorKind;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public Long getChangedByOperatorId() {
        return changedByOperatorId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
