package com.community.bizapplication.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * irobit.biz.* — 사업자 신청 건(biz-application-contract).
 *
 * @param paymentHours   온라인 결제 기한 기본값(시간, ★-2 72시간) — 승인 화면이 기한을 정해 보내지 않으면 쓴다(지금 화면은 늘 보낸다)
 * @param depositDays    수동 입금 기한 기본값(일, ★-2 7일)
 * @param depositAccount 수동 결제 입금 계좌 안내(공개 상세 {@code deposit}) — 가정: 배포 설정 값, local 은 예시 값
 * @param selfApproval   운영자 등록 건을 등록한 운영자가 직접 승인해도 되는가(04-ia-review §2 — 1인 운영이라 기본 허용)
 * @param pauseLimit     광고주 요청 일시 정지 횟수 한도(한 건에 — biz-pause-contract 규칙 1, 기본 3). 사업자·운영자 대신 처리 모두 같은 셈
 */
@ConfigurationProperties(prefix = "irobit.biz")
public record BizApplicationProperties(@DefaultValue("72") int paymentHours, @DefaultValue("7") int depositDays, @DefaultValue DepositAccount depositAccount,
                                       @DefaultValue("allow") SelfApproval selfApproval, @DefaultValue("3") int pauseLimit) {

    /** 정지 횟수 기본값 */
    public static final int DEFAULT_PAUSE_LIMIT = 3;

    /** 시험·옛 부르는 쪽 — 정지 횟수는 기본값 */
    public BizApplicationProperties(int paymentHours, int depositDays, DepositAccount depositAccount, SelfApproval selfApproval) {
        this(paymentHours, depositDays, depositAccount, selfApproval, DEFAULT_PAUSE_LIMIT);
    }

    /** 입금 계좌 — 비면 화면은 「운영팀이 따로 안내」 */
    public record DepositAccount(@DefaultValue("") String bankName, @DefaultValue("") String accountNo, @DefaultValue("") String accountHolder) {
    }

    /** 자기 승인 — ALLOW 승인 창 경고 + 메모 필수 + 처리 기록·감사 {@code selfApproved} / DENY 409 {@code BIZ_SELF_APPROVAL_DENIED} */
    public enum SelfApproval {
        ALLOW, DENY
    }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public BizApplicationProperties {
        selfApproval = selfApproval == null ? SelfApproval.ALLOW : selfApproval;
        pauseLimit = pauseLimit < 1 ? DEFAULT_PAUSE_LIMIT : pauseLimit;
    }

    /** 시험 기본값 */
    public static final BizApplicationProperties DEFAULTS = new BizApplicationProperties(72, 7, new DepositAccount("", "", ""), SelfApproval.ALLOW);
}
