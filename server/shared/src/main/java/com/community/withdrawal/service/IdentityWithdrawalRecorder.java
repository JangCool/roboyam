package com.community.withdrawal.service;

import com.community.identity.repository.master.IdentityStore;
import com.community.identity.service.IdentityVerificationService;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.entity.WithdrawalKind;
import com.community.withdrawal.repository.master.IdentityWithdrawalStore;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 후 재가입·재인증 제한(30일 — commerce.md 12-1 · 17-2)의 기록과 확인. 같은 이메일·같은 소셜 계정·같은 본인인증 값의 <b>해시만</b> {@code identity_withdrawals} 에 남기고
 * ({@code expires_at} = 탈퇴 시각 + 설정 일수, 기본 30일), 정리 배치({@code member-privacy-retention})가 기간이 끝나면 지운다. 누구의 것인지 잇는 칸은 남기지 않는다.
 *
 * <p>쓰는 탈퇴 기능은 아직 없다(세션 정리 결정 14) — 탈퇴 기능이 탈퇴 트랜잭션 안에서 {@link #recordEmail} · {@link #recordSocial} · {@link #recordIdentity} 를 부른다.
 * 본인인증 확정은 같은 표를 해시로 바로 본다({@code IdentityVerificationService}).
 *
 * <ul>
 *   <li>이메일 정규화: 소문자 + 모든 공백 제거. 소셜: {@code <공급자 대문자>:<공급자의 회원 번호>}(앞뒤 공백 제거).</li>
 *   <li>본인인증: 원문 DI·CI 는 남아 있지 않으므로 {@code identity_verifications} 의 해시를 그대로 옮기고 그 줄을 지운다(1인 1계정 묶음이 풀리고 30일 제한으로 바뀐다).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class IdentityWithdrawalRecorder {

    /** 이메일 해시의 용도 — 한 번 정하면 바꾸지 않는다 */
    public static final DataPurpose EMAIL = DataPurpose.of("withdrawal.email");
    /** 소셜 계정 해시의 용도 */
    public static final DataPurpose SOCIAL = DataPurpose.of("withdrawal.social");

    private final IdentityWithdrawalStore withdrawals;
    private final IdentityStore identities;
    private final BlindIndex index;
    private final SnowflakeIdGenerator ids;
    private final WithdrawalProperties properties;

    public IdentityWithdrawalRecorder(IdentityWithdrawalStore withdrawals, IdentityStore identities, BlindIndex index, SnowflakeIdGenerator ids,
                                      WithdrawalProperties properties) {
        this.withdrawals = withdrawals;
        this.identities = identities;
        this.index = index;
        this.ids = ids;
        this.properties = properties;
    }

    /** 이메일 정규화 — 소문자 + 모든 공백 제거 */
    public static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 소셜 계정 식별값 정규화 — {@code <공급자 대문자>:<회원 번호>} */
    public static String normalizeSocial(String provider, String providerUserId) {
        return provider.strip().toUpperCase(Locale.ROOT) + ":" + providerUserId.strip();
    }

    /** 종류·값의 해시 — 이메일·소셜은 정규화해 해시하고, 본인인증 값(원문 DI·CI)은 본인인증과 같은 용도로 해시한다 */
    public String hash(WithdrawalKind kind, String value) {
        return switch (kind) {
            case EMAIL -> index.hash(EMAIL, normalizeEmail(value));
            case SOCIAL -> index.hash(SOCIAL, value.strip());
            case IDENTITY_DI -> index.hash(IdentityVerificationService.DI, value);
            case IDENTITY_CI -> index.hash(IdentityVerificationService.CI, value);
        };
    }

    /** 탈퇴한 이메일을 남긴다 */
    @Transactional
    public void recordEmail(String email, Instant withdrawnAt) {
        recordHash(WithdrawalKind.EMAIL, hash(WithdrawalKind.EMAIL, email), withdrawnAt);
    }

    /** 탈퇴한 소셜 계정을 남긴다 */
    @Transactional
    public void recordSocial(String provider, String providerUserId, Instant withdrawnAt) {
        recordHash(WithdrawalKind.SOCIAL, hash(WithdrawalKind.SOCIAL, normalizeSocial(provider, providerUserId)), withdrawnAt);
    }

    /**
     * 그 회원의 본인인증을 탈퇴 처리한다 — DI·CI 해시를 제한 표로 옮기고 본인인증 줄을 지운다. 본인인증하지 않은 회원이면 아무것도 하지 않는다.
     *
     * @return 옮긴 해시 수(0~2)
     */
    @Transactional
    public int recordIdentity(long userId, Instant withdrawnAt) {
        Optional<IdentityStore.Verification> removed = identities.deleteVerification(userId);
        if (removed.isEmpty()) {
            return 0;
        }
        int moved = 0;
        if (removed.get().diHash() != null) {
            recordHash(WithdrawalKind.IDENTITY_DI, removed.get().diHash(), withdrawnAt);
            moved++;
        }
        if (removed.get().ciHash() != null) {
            recordHash(WithdrawalKind.IDENTITY_CI, removed.get().ciHash(), withdrawnAt);
            moved++;
        }
        return moved;
    }

    /** 이미 해시한 값을 남긴다 — {@code expires_at} = 탈퇴 시각 + 설정 일수 */
    @Transactional
    public void recordHash(WithdrawalKind kind, String hash, Instant withdrawnAt) {
        withdrawals.insert(ids.nextId(), kind, hash, withdrawnAt, withdrawnAt.plus(Duration.ofDays(properties.rejoinBlockDays())));
    }

    /**
     * 그 종류·값이 {@code now} 에 아직 막혀 있으면 막힘이 끝나는 순간(가입 때 이메일·소셜 재가입 확인용).
     *
     * @param value 이메일은 받은 그대로(여기서 정규화), 소셜은 {@link #normalizeSocial} 의 결과, 본인인증은 원문 DI·CI
     */
    public Optional<Instant> blockedUntil(WithdrawalKind kind, String value, Instant now) {
        return withdrawals.blockedUntil(kind, hash(kind, value), now);
    }
}
