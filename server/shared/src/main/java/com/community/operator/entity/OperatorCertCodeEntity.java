package com.community.operator.entity;

import com.community.zconfiguration.crypto.Digests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 운영자 비밀번호 재설정 승인번호 한 건 — V23__operator_password_reset.sql 의 {@code operator_cert_codes}. 이메일 해시 키마다 최대 한 행이고,
 * 새로 받으면 같은 행을 새 번호로 바꾼다({@link #reissue} — 이전 번호는 못 쓴다).
 *
 * <ul>
 *   <li>번호 원문은 저장하지 않는다. 행마다 무작위 salt 를 두고 {@code SHA-256(salt:번호)} 만 둔다. 6자리 숫자는 해시로 지킬 수 없으므로(100만 가지) 보호는 짧은 유효·시도 횟수 제한·요청 한도다.</li>
 *   <li>{@code operatorId} 가 null 이면 <b>가짜 행</b>이다 — 없는 이메일·로그인할 수 없는 계정에도 같은 모양으로 만들어 확인 응답이 계정 존재를 알리지 않게 한다. 번호는 아무에게도 전달되지 않는다.</li>
 *   <li>상태 변경은 행을 쓰기 잠금으로 읽은 트랜잭션 안에서 이 메서드들로만 한다. {@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString}).</li>
 * </ul>
 */
@Entity
@Table(name = "operator_cert_codes")
public class OperatorCertCodeEntity {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private Long id;

    private String emailKey;
    private Long operatorId;
    private String codeSalt;
    private String codeHash;
    private Instant expiresAt;
    private int failCount;
    private Instant usedAt;
    private Instant createdAt;

    protected OperatorCertCodeEntity() {
    }

    /** @param operatorId 승인번호를 받을 수 있는 운영자. 가짜 행이면 null */
    public static OperatorCertCodeEntity issue(long id, String emailKey, Long operatorId, String code, Instant now, Instant expiresAt) {
        OperatorCertCodeEntity entity = new OperatorCertCodeEntity();
        entity.id = id;
        entity.emailKey = emailKey;
        entity.reissue(operatorId, code, now, expiresAt);
        return entity;
    }

    /** 새 번호로 바꾼다 — 틀린 횟수·사용 표시를 처음으로 되돌린다(이전 번호는 해시가 바뀌어 못 쓴다) */
    public void reissue(Long operatorId, String code, Instant now, Instant expiresAt) {
        this.operatorId = operatorId;
        this.codeSalt = newSalt();
        this.codeHash = hash(codeSalt, code);
        this.expiresAt = expiresAt;
        this.failCount = 0;
        this.usedAt = null;
        this.createdAt = now;
    }

    /** 입력한 번호가 맞는가 — 시간이 일정하게 걸리게 비교한다 */
    public boolean matches(String code) {
        return MessageDigest.isEqual(hash(codeSalt, code).getBytes(StandardCharsets.UTF_8), codeHash.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isExhausted(int maxFailures) {
        return failCount >= maxFailures;
    }

    /** 틀렸다 — 횟수를 하나 더한다 */
    public void fail() {
        failCount++;
    }

    /** 썼다 — 다시 쓸 수 없다 */
    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public int getFailCount() {
        return failCount;
    }

    private static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String salt, String code) {
        return Digests.sha256Hex(salt + ":" + code);
    }
}
