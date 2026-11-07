package com.community.identity.repository.master;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.entity.IdentityVerificationAttemptEntity;
import com.community.identity.entity.QIdentityVerificationAttemptEntity;
import com.community.identity.entity.QIdentityVerificationEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 본인인증 표(shared V84 — {@code identity_verifications} · {@code identity_verification_attempts}) 읽기·쓰기 — {@code IdentityVerificationService}·{@code IdentityWithdrawalRecorder} 가 부른다.
 * 트랜잭션은 부르는 서비스가 연다. 해시만 다룬다 — CI·DI 원문은 이 클래스에 오지 않는다.
 *
 * <p>읽기·이름 암호문 채우기·시도 넣기·끝내기는 JPA·QueryDSL 이다(시도 끝내기는 조건부 UPDATE — 발급 상태일 때만 한 번). Native SQL 로 남긴 것과 이유:
 * {@link #insertVerification}({@code ON CONFLICT DO NOTHING} — 1인 1계정의 마지막 그물인 유일 제약 경합), {@link #deleteVerification}({@code DELETE … RETURNING} — 지운 줄 그대로).
 */
@Repository
public class IdentityStore extends RoboyamRepositoryQDSL {

    private static final QIdentityVerificationEntity V = QIdentityVerificationEntity.identityVerificationEntity;
    private static final QIdentityVerificationAttemptEntity A = QIdentityVerificationAttemptEntity.identityVerificationAttemptEntity;

    /** 확정된 본인인증 한 줄(해시 포함 — 화면에 내지 않는다) */
    public record Verification(long userId, String diHash, String ciHash, LocalDate birthDate, IdentityProviderKind provider, Instant verifiedAt) {
    }

    /** 인증 시도 한 줄 */
    public record Attempt(String id, long userId, IdentityProviderKind provider, String status, String failureCode, Instant createdAt, Instant completedAt) {

        public boolean pending() {
            return "PENDING".equals(status);
        }
    }

    private static final String VERIFICATION_COLUMNS = "user_id, di_hash, ci_hash, birth_date, provider, verified_at";

    private static final Expression<Verification> VERIFICATION = Projections.constructor(Verification.class, V.userId, V.diHash, V.ciHash, V.birthDate, V.provider,
            V.verifiedAt);
    private static final Expression<Attempt> ATTEMPT = Projections.constructor(Attempt.class, A.id, A.userId, A.provider, A.status, A.failureCode, A.createdAt,
            A.completedAt);

    private final IdentityVerificationAttemptRepository attempts;
    private final JdbcTemplate jdbc;

    public IdentityStore(JPAQueryFactory query, IdentityVerificationAttemptRepository attempts, DataSource dataSource) {
        super(query);
        this.attempts = attempts;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 본인인증 결과 ──────────────────────────────────────────────────────────

    public Optional<Verification> findVerification(long userId) {
        return Optional.ofNullable(query.select(VERIFICATION).from(V).where(V.userId.eq(userId)).fetchFirst());
    }

    /**
     * 본인인증 결과를 넣는다. 그 회원·DI·CI·업체 건 번호 중 하나라도 이미 있으면 넣지 않는다(동시에 들어온 다른 확정이 먼저 넣었으면 그 커밋을 기다린 뒤 넣지 않는다).
     *
     * <p>Native SQL 인 이유: 1인 1계정은 DI·CI·업체 건 번호의 <b>유일 제약</b>이 마지막으로 지킨다. 동시에 들어온 두 확정 중 진 쪽이 위반 예외로 트랜잭션을 망가뜨리지 않게
     * (PostgreSQL 은 위반 한 번에 트랜잭션 전체를 못 쓰게 한다) {@code INSERT … ON CONFLICT DO NOTHING} 으로 넣고 들어갔는지는 행 수로 안다 — JPQL·QueryDSL 에 대응 문법이 없다.
     * 이 표의 엔티티는 넣지 않으므로(읽기는 프로젝션) 영속성 컨텍스트와 어긋나지 않는다.
     *
     * @return 넣었으면 true
     */
    public boolean insertVerification(long userId, String diHash, String ciHash, LocalDate birthDate, IdentityProviderKind provider, String providerTxId,
                                      Instant verifiedAt) {
        return jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT DO NOTHING", userId, diHash, ciHash, birthDate, provider.name(), providerTxId, Timestamp.from(verifiedAt)) == 1;
    }

    /** 인증 이름 암호문을 넣는다(확정과 같은 트랜잭션 — V143 {@code name_enc}, DataCipher 용도 {@code identity.name}) */
    public void saveNameCipher(long userId, String nameCipher) {
        query.update(V).set(V.nameEnc, nameCipher).where(V.userId.eq(userId)).execute();
    }

    /** 이름 암호문이 비어 있을 때만 채운다(이름 채우기 재인증 — 채웠으면 true) */
    public boolean fillNameCipher(long userId, String nameCipher) {
        return query.update(V).set(V.nameEnc, nameCipher).where(V.userId.eq(userId), V.nameEnc.isNull()).execute() == 1;
    }

    /** 인증 이름 암호문 — 미인증이거나 이 칸이 생기기 전에 인증했으면 비어 있다 */
    public Optional<String> findNameCipher(long userId) {
        return Optional.ofNullable(query.select(V.nameEnc).from(V).where(V.userId.eq(userId), V.nameEnc.isNotNull()).fetchFirst());
    }

    /** 같은 DI 또는 CI 가 <b>다른</b> 회원에게 묶여 있는가(비어 있는 해시는 견주지 않는다 — SQL 의 {@code = NULL} 처럼 거짓) */
    public boolean linkedToOther(long userId, String diHash, String ciHash) {
        BooleanExpression same = diHash == null ? null : V.diHash.eq(diHash);
        if (ciHash != null) {
            same = same == null ? V.ciHash.eq(ciHash) : same.or(V.ciHash.eq(ciHash));
        }
        if (same == null) {
            return false;
        }
        return query.selectOne().from(V).where(V.userId.ne(userId), same).fetchFirst() != null;
    }

    /**
     * 그 회원의 본인인증 결과를 지우고 지운 줄을 돌려준다(탈퇴 — 해시는 재인증 제한 표로 옮긴다).
     *
     * <p>Native SQL 인 이유: {@code DELETE … RETURNING} — 지운 줄을 같은 문장에서 받아야 「읽은 줄 = 지운 줄」이 보장된다(읽고 지우기로 나누면 그 사이 바뀐 줄을 옮길 수 있다).
     */
    public Optional<Verification> deleteVerification(long userId) {
        return jdbc.query("DELETE FROM identity_verifications WHERE user_id = ? RETURNING " + VERIFICATION_COLUMNS, IdentityStore::verification, userId).stream()
                .findFirst();
    }

    // ── 인증 시도 ─────────────────────────────────────────────────────────────

    /** 시도를 넣는다 — 그 자리에서 INSERT 한다({@code persistAndFlush}) */
    public void insertAttempt(String id, long userId, IdentityProviderKind provider, Instant createdAt) {
        attempts.persistAndFlush(IdentityVerificationAttemptEntity.start(id, userId, provider, createdAt));
    }

    public Optional<Attempt> findAttempt(String id) {
        return Optional.ofNullable(query.select(ATTEMPT).from(A).where(A.id.eq(id)).fetchFirst());
    }

    /** 그 회원의 시도들 — 최근 것부터 */
    public List<Attempt> attemptsOf(long userId) {
        return query.select(ATTEMPT).from(A).where(A.userId.eq(userId)).orderBy(A.createdAt.desc(), A.id.desc()).fetch();
    }

    /**
     * 발급 상태인 시도를 끝낸다(한 번만 — 이미 끝났으면 바꾸지 않는다).
     *
     * @param failureCode 실패면 거절한 오류 코드, 성공이면 null
     * @return 바꿨으면 true
     */
    public boolean finishAttempt(String id, boolean verified, String failureCode, Instant now) {
        var update = query.update(A).set(A.status, verified ? IdentityVerificationAttemptEntity.VERIFIED : IdentityVerificationAttemptEntity.FAILED).set(A.completedAt, now);
        if (verified || failureCode == null) {
            update.setNull(A.failureCode);
        } else {
            update.set(A.failureCode, failureCode);
        }
        return update.where(A.id.eq(id), A.status.eq(IdentityVerificationAttemptEntity.PENDING)).execute() == 1;
    }

    private static Verification verification(ResultSet rs, int row) throws SQLException {
        OffsetDateTime verifiedAt = rs.getObject(6, OffsetDateTime.class);
        return new Verification(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class), IdentityProviderKind.valueOf(rs.getString(5)),
                verifiedAt == null ? null : verifiedAt.toInstant());
    }
}
