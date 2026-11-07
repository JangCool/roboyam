package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 보상 이벤트 한 건 — shared V163 {@code reward_events}(docs/features/event-admin-contract.md). 만들기는 {@link #create}, 운영자의 고치기는 {@link #edit}(고치기 범위 검사는 서비스가
 * 먼저 한다 — 잠근 뒤 처음 읽은 엔티티에만 부른다). 상태 전이(시작·마감·자동 마감·지급 끝·취소)와 선착순 인원 올리기는 저장소의 조건부 UPDATE 다(엔티티를 거치지 않는다).
 */
@Entity
@Table(name = "reward_events")
public class RewardEventEntity {

    @Id
    private Long id;
    private Long no;
    private String name;
    private String memo;
    private String noticeUrl;
    @Enumerated(EnumType.STRING)
    private RewardEventMode mode;
    @Enumerated(EnumType.STRING)
    private RewardEventStatus status;
    private Instant startAt;
    private Instant endAt;
    private boolean excludeOperators;
    @Enumerated(EnumType.STRING)
    private RewardEventTrigger firstComeTrigger;
    private Integer firstComeLimit;
    private int firstComeCount;
    private boolean sourceManual;
    private boolean sourceComment;
    private String drawPostRef;
    private Long drawPostId;
    private Instant drawFrom;
    private Instant drawTo;
    private boolean drawExcludeAuthor;
    @Enumerated(EnumType.STRING)
    private RewardBulkScope bulkScope;
    private Integer bulkMinGrade;
    private LocalDate bulkJoinedFrom;
    private LocalDate bulkJoinedTo;
    private boolean bulkIdentityOnly;
    private Integer bulkActiveDays;
    private Instant closedAt;
    private Instant doneAt;
    private String cancelReason;
    private Instant canceledAt;
    private Long canceledBy;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected RewardEventEntity() {
    }

    /** 이벤트 설정 값(만들기·고치기 공통) — 방식에 맞지 않는 칸은 서비스가 비워 넘긴다 */
    public record Values(String name, String memo, String noticeUrl, RewardEventMode mode, Instant startAt, Instant endAt, boolean excludeOperators,
                         RewardEventTrigger firstComeTrigger, Integer firstComeLimit, boolean sourceManual, boolean sourceComment, String drawPostRef, Long drawPostId,
                         Instant drawFrom, Instant drawTo, boolean drawExcludeAuthor, RewardBulkScope bulkScope, Integer bulkMinGrade, LocalDate bulkJoinedFrom,
                         LocalDate bulkJoinedTo, boolean bulkIdentityOnly, Integer bulkActiveDays) {
    }

    /** 새 이벤트(준비) */
    public static RewardEventEntity create(long id, long no, Values v, long operatorId, Instant now) {
        RewardEventEntity e = new RewardEventEntity();
        e.id = id;
        e.no = no;
        e.status = RewardEventStatus.DRAFT;
        e.createdBy = operatorId;
        e.createdAt = now;
        e.apply(v, operatorId, now);
        return e;
    }

    /** 고치기 — 범위 검사(잠긴 칸이 바뀌지 않았는가)는 서비스가 먼저 했다 */
    public void edit(Values v, long operatorId, Instant now) {
        apply(v, operatorId, now);
    }

    private void apply(Values v, long operatorId, Instant now) {
        name = v.name();
        memo = v.memo();
        noticeUrl = v.noticeUrl();
        mode = v.mode();
        startAt = v.startAt();
        endAt = v.endAt();
        excludeOperators = v.excludeOperators();
        firstComeTrigger = v.firstComeTrigger();
        firstComeLimit = v.firstComeLimit();
        sourceManual = v.sourceManual();
        sourceComment = v.sourceComment();
        drawPostRef = v.drawPostRef();
        drawPostId = v.drawPostId();
        drawFrom = v.drawFrom();
        drawTo = v.drawTo();
        drawExcludeAuthor = v.drawExcludeAuthor();
        bulkScope = v.bulkScope();
        bulkMinGrade = v.bulkMinGrade();
        bulkJoinedFrom = v.bulkJoinedFrom();
        bulkJoinedTo = v.bulkJoinedTo();
        bulkIdentityOnly = v.bulkIdentityOnly();
        bulkActiveDays = v.bulkActiveDays();
        updatedBy = operatorId;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getNo() {
        return no;
    }

    public String getName() {
        return name;
    }

    public String getMemo() {
        return memo;
    }

    public String getNoticeUrl() {
        return noticeUrl;
    }

    public RewardEventMode getMode() {
        return mode;
    }

    public RewardEventStatus getStatus() {
        return status;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isExcludeOperators() {
        return excludeOperators;
    }

    public RewardEventTrigger getFirstComeTrigger() {
        return firstComeTrigger;
    }

    public Integer getFirstComeLimit() {
        return firstComeLimit;
    }

    public int getFirstComeCount() {
        return firstComeCount;
    }

    public boolean isSourceManual() {
        return sourceManual;
    }

    public boolean isSourceComment() {
        return sourceComment;
    }

    public String getDrawPostRef() {
        return drawPostRef;
    }

    public Long getDrawPostId() {
        return drawPostId;
    }

    public Instant getDrawFrom() {
        return drawFrom;
    }

    public Instant getDrawTo() {
        return drawTo;
    }

    public boolean isDrawExcludeAuthor() {
        return drawExcludeAuthor;
    }

    public RewardBulkScope getBulkScope() {
        return bulkScope;
    }

    public Integer getBulkMinGrade() {
        return bulkMinGrade;
    }

    public LocalDate getBulkJoinedFrom() {
        return bulkJoinedFrom;
    }

    public LocalDate getBulkJoinedTo() {
        return bulkJoinedTo;
    }

    public boolean isBulkIdentityOnly() {
        return bulkIdentityOnly;
    }

    public Integer getBulkActiveDays() {
        return bulkActiveDays;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Long getCanceledBy() {
        return canceledBy;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
