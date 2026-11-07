package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 심사 건 — shared V142 {@code emoticon_reviews}(+ V147 시스템이 닫은 까닭). 신청 한 번이 한 줄이고 팩마다 대기 건은 하나(부분 유일 색인).
 * 읽기는 저장소의 한 줄 모양(프로젝션), 처리는 저장소의 조건부 UPDATE 다 — 이 엔티티는 넣기({@code persist})·질의 경로(Q 클래스)·행 잠금에 쓴다.
 */
@Entity
@Table(name = "emoticon_reviews")
public class EmoticonReviewEntity {

    @Id
    private Long id;
    private Long packId;
    private Long revisionId;
    private Long previousRevisionId;
    private Long creatorId;
    @Enumerated(EnumType.STRING)
    private EmoticonReviewKind kind;
    @Enumerated(EnumType.STRING)
    private EmoticonReviewStatus status;
    private Instant submittedAt;
    private Instant agreeOwnershipAt;
    private Instant agreeLicenseAt;
    private Instant agreePolicyAt;
    private String consentVersion;
    private Instant decidedAt;
    private Long decidedBy;
    private String reason;
    private Integer approvedPrice;
    private String systemCause;

    protected EmoticonReviewEntity() {
    }

    /** 새 대기 건 — 동의 셋의 시각은 신청 시각 */
    public static EmoticonReviewEntity pending(long id, long packId, long revisionId, Long previousRevisionId, long creatorId, EmoticonReviewKind kind, Instant now,
                                               String consentVersion) {
        EmoticonReviewEntity r = new EmoticonReviewEntity();
        r.id = id;
        r.packId = packId;
        r.revisionId = revisionId;
        r.previousRevisionId = previousRevisionId;
        r.creatorId = creatorId;
        r.kind = kind;
        r.status = EmoticonReviewStatus.PENDING;
        r.submittedAt = now;
        r.agreeOwnershipAt = now;
        r.agreeLicenseAt = now;
        r.agreePolicyAt = now;
        r.consentVersion = consentVersion;
        return r;
    }
}
