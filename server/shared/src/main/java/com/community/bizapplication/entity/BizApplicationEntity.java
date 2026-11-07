package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 신청 건 한 줄 — shared V112 {@code biz_applications}(V115 운영자 등록 · V127 정지·중단 · V140 종이 계약서 수 등이 칸을 더했다). 만들기는 {@link #submitted}·{@link #operator}
 * (저장소가 넣고 바로 내보낸다). 상태 전이는 모두 저장소의 조건부 UPDATE(지금 상태를 조건으로 · {@code version + 1})가 한다 — 엔티티로 고치지 않는다.
 * 읽기는 저장소가 한 줄 모양({@code BizApplicationStore.AppRow})으로 꺼낸다(영속성 문맥의 낡은 엔티티를 보지 않는다).
 *
 * <p>화면 번호({@code application_no} — {@code BA-연도-일련번호})는 DB 시퀀스로 저장소가 미리 만들어 넘긴다.
 */
@Entity
@Table(name = "biz_applications")
public class BizApplicationEntity {

    @Id
    private Long id;

    private String applicationNo;
    @Enumerated(EnumType.STRING)
    private BizApplicationKind kind;
    @Enumerated(EnumType.STRING)
    private BizApplicationStatus status;
    @Enumerated(EnumType.STRING)
    private BizSource source;
    private Long businessProfileId;
    private Long userId;
    private String advertiserName;
    private String companyName;
    private String bizNoLast4;
    private String title;
    private Instant startAt;
    private Instant endAt;
    @Enumerated(EnumType.STRING)
    private BizPaymentMethod preferredMethod;
    @Enumerated(EnumType.STRING)
    private BizPaymentMethod paymentMethod;
    private Long quoteSupply;
    private Long quoteVat;
    private Long quoteTotal;
    private Long approvedSupply;
    private Long approvedVat;
    private Long approvedTotal;
    private String adjustReason;
    private Instant approvedAt;
    private Long approvedBy;
    private Instant paymentDeadlineAt;
    private Instant depositDeadlineAt;
    private Instant paidAt;
    private Long paymentOrderId;
    private Integer activeContractCount;
    /** 계약서 종이 근거 수(V140) — 수동 확정 CHECK 가 파일 수와 더해 본다 */
    private Integer paperContractCount;
    private Instant depositConfirmedAt;
    @Enumerated(EnumType.STRING)
    private BizRejectCode rejectCode;
    private String rejectText;
    private Instant takenDownAt;
    @Enumerated(EnumType.STRING)
    private BizTakedownCause takedownCause;
    private String takedownText;
    private String adPolicyVersion;
    private String refundPolicyVersion;
    private Instant agreedAt;
    private Instant paymentDueNotifiedAt;
    private Instant startNotifiedAt;
    private Instant endNotifiedAt;
    private Instant closedAt;
    /** 바뀔 때마다 + 1(조건부 UPDATE 가 올린다 — JPA 낙관적 잠금이 아니다) */
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Long advertiserId;
    private Long createdBy;
    private Integer weight;
    private Long proposedSupply;
    private String operatorMemo;
    private Long takenDownBy;
    private String takedownMail;
    private Integer pauseCount;
    private Instant pausedAt;
    private Integer pausedMinutes;
    @Enumerated(EnumType.STRING)
    private BizActorType takedownActor;
    @Enumerated(EnumType.STRING)
    private BizPauseChannel takedownChannel;
    private String takedownMemo;
    private Instant stoppedAt;
    private Instant refundFrom;
    private Integer stopFeeRate;

    protected BizApplicationEntity() {
    }

    /** 사업자(회원) 신청 — 심사 대기, 비중은 DB 기본값 5 */
    public static BizApplicationEntity submitted(long id, String applicationNo, BizApplicationKind kind, BizSource source, Long businessProfileId, Long userId,
                                                 String advertiserName, String companyName, String bizNoLast4, String title, Instant startAt, Instant endAt,
                                                 BizPaymentMethod preferredMethod, long quoteSupply, long quoteVat, long quoteTotal, String adPolicyVersion,
                                                 String refundPolicyVersion, Instant agreedAt, int stopFeeRate, Instant now) {
        BizApplicationEntity a = base(id, applicationNo, kind, source, businessProfileId, advertiserName, companyName, bizNoLast4, title, startAt, endAt,
                preferredMethod, quoteSupply, quoteVat, quoteTotal, stopFeeRate, now);
        a.userId = userId;
        a.adPolicyVersion = adPolicyVersion;
        a.refundPolicyVersion = refundPolicyVersion;
        a.agreedAt = agreedAt;
        a.weight = 5;
        return a;
    }

    /** 운영자 등록(04 §2) — 심사 대기 · 결제 방식 MANUAL · 회원·정책 동의 없음(ck_ba_source_operator) */
    public static BizApplicationEntity operator(long id, String applicationNo, BizApplicationKind kind, Long businessProfileId, Long advertiserId, String advertiserName,
                                                String companyName, String bizNoLast4, String title, Instant startAt, Instant endAt, long quoteSupply, long quoteVat,
                                                long quoteTotal, int weight, long proposedSupply, String memo, long createdBy, int stopFeeRate, Instant now) {
        BizApplicationEntity a = base(id, applicationNo, kind, BizSource.OPERATOR, businessProfileId, advertiserName, companyName, bizNoLast4, title, startAt, endAt,
                BizPaymentMethod.MANUAL, quoteSupply, quoteVat, quoteTotal, stopFeeRate, now);
        a.advertiserId = advertiserId;
        a.weight = weight;
        a.proposedSupply = proposedSupply;
        a.operatorMemo = memo;
        a.createdBy = createdBy;
        return a;
    }

    private static BizApplicationEntity base(long id, String applicationNo, BizApplicationKind kind, BizSource source, Long businessProfileId, String advertiserName,
                                             String companyName, String bizNoLast4, String title, Instant startAt, Instant endAt, BizPaymentMethod preferredMethod,
                                             long quoteSupply, long quoteVat, long quoteTotal, int stopFeeRate, Instant now) {
        BizApplicationEntity a = new BizApplicationEntity();
        a.id = id;
        a.applicationNo = applicationNo;
        a.kind = kind;
        a.status = BizApplicationStatus.SUBMITTED;
        a.source = source;
        a.businessProfileId = businessProfileId;
        a.advertiserName = advertiserName;
        a.companyName = companyName;
        a.bizNoLast4 = bizNoLast4;
        a.title = title;
        a.startAt = startAt;
        a.endAt = endAt;
        a.preferredMethod = preferredMethod;
        a.quoteSupply = quoteSupply;
        a.quoteVat = quoteVat;
        a.quoteTotal = quoteTotal;
        a.stopFeeRate = stopFeeRate;
        // DB 기본값과 같은 시작 값(엔티티는 모든 칸을 넣으므로 기본값에 맡기지 않는다)
        a.activeContractCount = 0;
        a.paperContractCount = 0;
        a.version = 0;
        a.pauseCount = 0;
        a.pausedMinutes = 0;
        a.createdAt = now;
        a.updatedAt = now;
        return a;
    }
}
