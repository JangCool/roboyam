package com.community.business.entity;

import com.community.business.dto.BusinessSuspendPlan;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 사업자 신원 한 줄 — V111 {@code business_profiles}. 회원당 하나이고 반려 뒤 재신청은 같은 줄을 다시 심사 중으로 돌린다. 상태는 아래 도메인 메서드로만 바꾼다
 * (정해진 상태에서만 — 아니면 {@link IllegalStateException}, 서비스가 먼저 확인해 409 {@code BUSINESS_STATE_CHANGED} 로 막는다).
 *
 * <p>개인정보 칸은 암호문({@code *_enc} — {@code DataCipher})이고 번호 비교는 {@link #getBizNoHash()}(BlindIndex), 끝 4자리만 평문이다. {@code toString} 을 두지 않는다.
 */
@Entity
@Table(name = "business_profiles")
@DynamicUpdate
public class BusinessProfileEntity {

    /** 신청서의 신원 칸(암호문·해시까지 만든 값) — 신청·재신청·수정이 같은 모양을 넘긴다 */
    public record Identity(String companyName, String bizNoEnc, String bizNoHash, String bizNoLast4, String ownerNameEnc, String phoneEnc, String emailEnc,
                           String postcode, String address1Enc, String address2Enc, long certificateId, String termsVersion, String privacyVersion) {
    }

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private BusinessStatus status;

    private boolean reReview;
    private String companyName;
    private String bizNoEnc;
    private String bizNoHash;
    private String bizNoLast4;
    private String ownerNameEnc;
    private String phoneEnc;
    private String emailEnc;
    private String postcode;
    private String address1Enc;
    private String address2Enc;
    private Long certificateId;
    private String termsVersion;
    private String privacyVersion;
    private Instant agreedAt;
    private Instant submittedAt;
    private Instant reviewedAt;
    private Long reviewedBy;
    private Instant firstApprovedAt;
    private String ntsOverrideReason;

    @Enumerated(EnumType.STRING)
    private BusinessRejectCode rejectCode;
    private String rejectText;

    @Enumerated(EnumType.STRING)
    private BusinessSuspendCode suspendCode;
    private String suspendText;

    @Enumerated(EnumType.STRING)
    private BusinessSuspendPlan.Pending suspendPendingAction;
    @Enumerated(EnumType.STRING)
    private BusinessSuspendPlan.Scheduled suspendScheduledAction;
    @Enumerated(EnumType.STRING)
    private BusinessSuspendPlan.Running suspendRunningAction;
    private Instant suspendedAt;
    private Long suspendedBy;
    private Instant withdrawnAt;
    private Instant erasedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected BusinessProfileEntity() {
    }

    /** 처음 신청 — 심사 중 */
    public static BusinessProfileEntity apply(long id, long userId, Identity identity, Instant now) {
        BusinessProfileEntity e = new BusinessProfileEntity();
        e.id = id;
        e.userId = userId;
        e.status = BusinessStatus.PENDING;
        e.fill(identity, now);
        e.submittedAt = now;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /** 반려 뒤 재신청 — 같은 줄을 심사 중으로(반려 사유는 지운다, 처리 기록에 남아 있다) */
    public void reapply(Identity identity, Instant now) {
        require(status == BusinessStatus.REJECTED);
        this.status = BusinessStatus.PENDING;
        this.reReview = false;
        this.rejectCode = null;
        this.rejectText = null;
        this.reviewedAt = null;
        this.reviewedBy = null;
        this.ntsOverrideReason = null;
        fill(identity, now);
        this.submittedAt = now;
        this.updatedAt = now;
    }

    /** 수정할 수 있는 상태인가 — 승인 또는 재심사 중(F7: 재심사 중에도 회원 화면은 「승인 + 재심사 중」이라 다시 고칠 수 있다) */
    public boolean editable() {
        return status == BusinessStatus.APPROVED || (status == BusinessStatus.PENDING && reReview);
    }

    /**
     * 승인 사업자의 정보 수정. 상호·번호가 바뀌면 승인이 재심사 중(F7)이 된다 — 이미 재심사 중이면 그대로 재심사 중.
     *
     * @return 이번 수정으로 재심사가 새로 시작됐는가
     */
    public boolean update(Identity identity, boolean identityChanged, Instant now) {
        require(editable());
        fill(identity, now);
        this.updatedAt = now;
        if (identityChanged && status == BusinessStatus.APPROVED) {
            this.status = BusinessStatus.PENDING;
            this.reReview = true;
            this.submittedAt = now;
            this.reviewedAt = null;
            this.reviewedBy = null;
            this.ntsOverrideReason = null;
            return true;
        }
        return false;
    }

    /** 승인 — 심사 중만. 국세청 조회 불가로 승인하면 사유(B1) */
    public void approve(long operatorId, String ntsOverrideReason, Instant now) {
        require(status == BusinessStatus.PENDING);
        this.status = BusinessStatus.APPROVED;
        this.reReview = false;
        this.reviewedAt = now;
        this.reviewedBy = operatorId;
        this.ntsOverrideReason = ntsOverrideReason;
        if (firstApprovedAt == null) {
            this.firstApprovedAt = now;
        }
        this.updatedAt = now;
    }

    /** 반려 — 심사 중만(재심사 반려도 같다 — 이미 예약·진행 중인 건의 처리는 신청 건 쪽 규칙) */
    public void reject(long operatorId, BusinessRejectCode code, String text, Instant now) {
        require(status == BusinessStatus.PENDING);
        this.status = BusinessStatus.REJECTED;
        this.reReview = false;
        this.rejectCode = code;
        this.rejectText = text;
        this.reviewedAt = now;
        this.reviewedBy = operatorId;
        this.updatedAt = now;
    }

    /** 정지할 수 있는가 — 승인 또는 재심사 중(승인을 받은 적이 있고 예약 건이 돌고 있다) */
    public boolean suspendable() {
        return status == BusinessStatus.APPROVED || (status == BusinessStatus.PENDING && reReview);
    }

    /** 정지(H2) — 처리 선택은 저장만, 실제 처리는 신청 건 훅 */
    public void suspend(long operatorId, BusinessSuspendCode code, String text, BusinessSuspendPlan plan, Instant now) {
        require(suspendable());
        this.status = BusinessStatus.SUSPENDED;
        this.reReview = false;
        this.suspendCode = code;
        this.suspendText = text;
        this.suspendPendingAction = plan.pending();
        this.suspendScheduledAction = plan.scheduled();
        this.suspendRunningAction = plan.running();
        this.suspendedAt = now;
        this.suspendedBy = operatorId;
        this.updatedAt = now;
    }

    /** 정지 해제 — 정지만, 승인으로 돌아간다(정지 사유는 처리 기록에 남아 있다) */
    public void unsuspend(Instant now) {
        require(status == BusinessStatus.SUSPENDED);
        this.status = BusinessStatus.APPROVED;
        this.suspendCode = null;
        this.suspendText = null;
        this.suspendPendingAction = null;
        this.suspendScheduledAction = null;
        this.suspendRunningAction = null;
        this.suspendedAt = null;
        this.suspendedBy = null;
        this.updatedAt = now;
    }

    /** 회원 탈퇴(F8) — 탈퇴 가드는 부르는 쪽(신청 건 훅)이 먼저 본다 */
    public void withdraw(Instant now) {
        require(status != BusinessStatus.WITHDRAWN);
        this.status = BusinessStatus.WITHDRAWN;
        this.reReview = false;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    private void fill(Identity identity, Instant now) {
        this.companyName = identity.companyName();
        this.bizNoEnc = identity.bizNoEnc();
        this.bizNoHash = identity.bizNoHash();
        this.bizNoLast4 = identity.bizNoLast4();
        this.ownerNameEnc = identity.ownerNameEnc();
        this.phoneEnc = identity.phoneEnc();
        this.emailEnc = identity.emailEnc();
        this.postcode = identity.postcode();
        this.address1Enc = identity.address1Enc();
        this.address2Enc = identity.address2Enc();
        this.certificateId = identity.certificateId();
        this.termsVersion = identity.termsVersion();
        this.privacyVersion = identity.privacyVersion();
        this.agreedAt = now;
    }

    private static void require(boolean ok) {
        if (!ok) {
            throw new IllegalStateException("사업자 신원 상태가 이 처리를 허용하지 않는다");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BusinessStatus getStatus() {
        return status;
    }

    public boolean isReReview() {
        return reReview;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getBizNoEnc() {
        return bizNoEnc;
    }

    public String getBizNoHash() {
        return bizNoHash;
    }

    public String getBizNoLast4() {
        return bizNoLast4;
    }

    public String getOwnerNameEnc() {
        return ownerNameEnc;
    }

    public String getPhoneEnc() {
        return phoneEnc;
    }

    public String getEmailEnc() {
        return emailEnc;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getAddress1Enc() {
        return address1Enc;
    }

    public String getAddress2Enc() {
        return address2Enc;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getFirstApprovedAt() {
        return firstApprovedAt;
    }

    public BusinessRejectCode getRejectCode() {
        return rejectCode;
    }

    public String getRejectText() {
        return rejectText;
    }

    public BusinessSuspendCode getSuspendCode() {
        return suspendCode;
    }

    public String getSuspendText() {
        return suspendText;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public Instant getErasedAt() {
        return erasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
