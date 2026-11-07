package com.community.identity.entity;

import com.community.identity.dto.IdentityProviderKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 확정된 본인인증 한 줄(회원당 하나) — shared V84·V143 의 {@code identity_verifications}. DI·CI 는 BlindIndex 해시, 이름은 DataCipher 암호문({@code name_enc})뿐이다 —
 * 원문은 없다. {@code toString} 은 기본(칸 값 — 해시·암호문 — 을 싣지 않는다)이다.
 *
 * <p>줄은 {@code IdentityStore#insertVerification}(유일 제약 경합을 한 문장으로 가르는 {@code ON CONFLICT DO NOTHING})이 만들고 탈퇴 때 {@code deleteVerification}(지운 줄을 돌려받는
 * {@code DELETE … RETURNING})이 지운다. 이 엔티티는 읽기·이름 암호문 채우기의 QueryDSL 경로다.
 */
@Entity
@Table(name = "identity_verifications")
public class IdentityVerificationEntity {

    @Id
    private Long userId;

    private String diHash;
    private String ciHash;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private IdentityProviderKind provider;

    private String providerTxId;
    private Instant verifiedAt;

    /** 인증 이름 암호문(V143 — 그 전에 인증한 회원은 null) */
    private String nameEnc;

    protected IdentityVerificationEntity() {
    }
}
