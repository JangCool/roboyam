package com.community.withdrawal.repository.master;

import com.community.audit.entity.QAuditLogEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.withdrawal.entity.QWithdrawnMemberContactEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 회원 연락용 이메일 표({@code withdrawn_member_contacts}, shared V84 — {@code WithdrawnMemberContactEntity}) — {@code WithdrawnContactStore} 만 부른다(일반 회원 조회는 이 표를 읽지
 * 않는다 — 논리적 분리, commerce.md 17-2). 이메일은 암호문({@code d<버전>:})으로만 오간다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>읽기는 QueryDSL 이다. Native SQL 로 남긴 것과 이유: {@link #upsert}({@code ON CONFLICT DO UPDATE} — 아래).
 */
@Repository
public class WithdrawnContactRows extends RoboyamRepositoryQDSL {

    private static final QWithdrawnMemberContactEntity C = QWithdrawnMemberContactEntity.withdrawnMemberContactEntity;
    private static final QAuditLogEntity AUDIT = QAuditLogEntity.auditLogEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    /** 감사 기록 덧붙임(JSON 글자 칸 — text)의 열람 사유 — {@code detail::jsonb ->> 'reason'} 과 같은 값. PostgreSQL 함수({@code jsonb(text)} 변환·{@code jsonb_extract_path_text})는 HQL 이 이름 그대로 내려보낸다 */
    private static final StringExpression REVEAL_REASON = Expressions.stringTemplate("jsonb_extract_path_text(jsonb({0}), 'reason')", AUDIT.detail);

    /** 한 줄 — {@code toString} 은 암호문을 싣지 않는다 */
    public record Row(long userId, String emailCipher, Instant withdrawnAt, Instant purgeAfter) {

        @Override
        public String toString() {
            return "Row[userId=" + userId + ", withdrawnAt=" + withdrawnAt + ", purgeAfter=" + purgeAfter + "]";
        }
    }

    /** 감사 기록에서 읽은 열람 한 건 */
    public record Reveal(String operatorName, Instant revealedAt, String reason) {
    }

    private final JdbcTemplate jdbc;

    public WithdrawnContactRows(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 넣는다(이미 있으면 바꾼다 — 탈퇴는 한 번이지만 다시 부르면 마지막 값).
     *
     * <p>Native SQL 인 이유: PostgreSQL {@code INSERT … ON CONFLICT DO UPDATE} 는 JPQL·QueryDSL 에 대응 문법이 없다. 「찾아보고 넣거나 고친다」로 나누면 두 문장 사이 경합에서 유일 제약 위반이
     * 나고, PostgreSQL 은 위반 한 번에 탈퇴 트랜잭션 전체를 못 쓰게 한다. 이 표의 엔티티는 넣기·고치기를 하지 않으므로(읽기만 프로젝션) 영속성 컨텍스트와 어긋나지 않는다.
     */
    public void upsert(long userId, String emailCipher, Instant withdrawnAt, Instant purgeAfter) {
        jdbc.update("INSERT INTO withdrawn_member_contacts (user_id, email_cipher, withdrawn_at, purge_after) VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET "
                + "email_cipher = EXCLUDED.email_cipher, withdrawn_at = EXCLUDED.withdrawn_at, purge_after = EXCLUDED.purge_after", userId, emailCipher,
                Timestamp.from(withdrawnAt), Timestamp.from(purgeAfter));
    }

    public Optional<Row> find(long userId) {
        return Optional.ofNullable(query.select(Projections.constructor(Row.class, C.userId, C.emailCipher, C.withdrawnAt, C.purgeAfter)).from(C)
                .where(C.userId.eq(userId)).fetchFirst());
    }

    /**
     * 그 회원 연락처의 열람 기록 — 감사 기록({@code WITHDRAWN_CONTACT_REVEAL}, 대상 MEMBER)에서 읽는다(최근 것부터). 운영자가 지워졌으면 이름은 null.
     */
    public List<Reveal> reveals(long userId, String action, int limit) {
        return query.select(Projections.constructor(Reveal.class, OPERATOR.operatorName, AUDIT.occurredAt, REVEAL_REASON)).from(AUDIT)
                .leftJoin(OPERATOR).on(OPERATOR.id.eq(AUDIT.actorId))
                .where(AUDIT.action.eq(action), AUDIT.targetType.eq("MEMBER"), AUDIT.targetId.eq(Long.toString(userId)))
                .orderBy(AUDIT.id.desc()).limit(limit).fetch();
    }
}
