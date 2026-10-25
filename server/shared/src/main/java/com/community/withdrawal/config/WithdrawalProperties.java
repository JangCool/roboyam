package com.community.withdrawal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.withdrawal.* — 탈퇴 대비(docs/domain/commerce.md 17-2 · 17-3, docs/features/commerce-foundation-contract.md 「0-4」).
 *
 * @param rejoinBlockDays       같은 이메일·소셜 계정·본인인증 값이 탈퇴 뒤 다시 가입·인증할 수 없는 기간(일, 기본 30). 탈퇴 때 {@code identity_withdrawals.expires_at} 으로 박는다 —
 *                              나중에 바꿔도 이미 탈퇴한 사람의 기간은 그대로다
 * @param contactRetentionYears 탈퇴 회원 연락용 이메일을 마지막 거래일부터 보관하는 기간(년, 기본 5 — 전자상거래법 시행령 제6조)
 */
@ConfigurationProperties(prefix = "roboyam.withdrawal")
public record WithdrawalProperties(@DefaultValue("30") int rejoinBlockDays, @DefaultValue("5") int contactRetentionYears) {

    public WithdrawalProperties {
        if (rejoinBlockDays < 1 || rejoinBlockDays > 3650) {
            throw new IllegalArgumentException("roboyam.withdrawal.rejoin-block-days 는 1~3650 이어야 합니다: " + rejoinBlockDays);
        }
        if (contactRetentionYears < 1 || contactRetentionYears > 50) {
            throw new IllegalArgumentException("roboyam.withdrawal.contact-retention-years 는 1~50 이어야 합니다: " + contactRetentionYears);
        }
    }
}
