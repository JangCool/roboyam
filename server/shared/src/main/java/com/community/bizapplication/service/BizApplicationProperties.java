package com.community.bizapplication.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.biz.* — 사업자 신청 건(biz-application-contract).
 *
 * @param paymentHours   온라인 결제 기한 기본값(시간, ★-2 72시간) — 승인 화면이 기한을 정해 보내지 않으면 쓴다(지금 화면은 늘 보낸다)
 * @param depositDays    수동 입금 기한 기본값(일, ★-2 7일)
 * @param depositAccount 수동 결제 입금 계좌 안내(공개 상세 {@code deposit}) — 가정: 배포 설정 값, local 은 예시 값
 */
@ConfigurationProperties(prefix = "roboyam.biz")
public record BizApplicationProperties(@DefaultValue("72") int paymentHours, @DefaultValue("7") int depositDays, @DefaultValue DepositAccount depositAccount) {

    /** 입금 계좌 — 비면 화면은 「운영팀이 따로 안내」 */
    public record DepositAccount(@DefaultValue("") String bankName, @DefaultValue("") String accountNo, @DefaultValue("") String accountHolder) {
    }

    /** 시험 기본값 */
    public static final BizApplicationProperties DEFAULTS = new BizApplicationProperties(72, 7, new DepositAccount("", "", ""));
}
