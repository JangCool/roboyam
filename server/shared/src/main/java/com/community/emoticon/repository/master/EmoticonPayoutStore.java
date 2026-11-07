package com.community.emoticon.repository.master;

import com.community.emoticon.entity.QEmoticonPayoutAccountEntity;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 작가 정산 정보({@code emoticon_payout_accounts}, V143 — 작가마다 한 줄). 원문 칸은 {@code DataCipher} 암호문만 오가고(이 클래스는 풀지 않는다), 화면용 가린 값은 따로 둔다.
 * QueryDSL 이다({@link EmoticonStoreSupport}). Native SQL 로 남긴 것과 이유: 넣거나 바꾸기({@code ON CONFLICT (user_id) DO UPDATE} — 처음 등록 시각을 남기는 upsert).
 */
@Repository
public class EmoticonPayoutStore extends EmoticonStoreSupport {

    /** 정산 정보 한 줄 — {@code *Cipher} 는 암호문 */
    public record PayoutRow(long userId, String payeeType, String businessNumberCipher, String businessNumberMasked, String bankCode, String accountNumberCipher,
                            String accountLast4, String holderNameCipher, String holderMasked, Instant createdAt, Instant updatedAt) {
    }

    private static final String COLUMNS = "user_id, payee_type, business_number_enc, business_number_masked, bank_code, account_number_enc, account_last4, holder_name_enc, "
            + "holder_masked, created_at, updated_at";
    private static final QEmoticonPayoutAccountEntity A = QEmoticonPayoutAccountEntity.emoticonPayoutAccountEntity;

    public EmoticonPayoutStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    private JPAQuery<Tuple> rows() {
        return query.select(A.userId, A.payeeType, A.businessNumberEnc, A.businessNumberMasked, A.bankCode, A.accountNumberEnc, A.accountLast4, A.holderNameEnc,
                A.holderMasked, A.createdAt, A.updatedAt).from(A);
    }

    public Optional<PayoutRow> find(long userId) {
        return Optional.ofNullable(rows().where(A.userId.eq(userId)).fetchFirst()).map(EmoticonPayoutStore::rowOf);
    }

    /** 여러 작가의 정산 정보(S4 정산 목록 — 작가 구분 · 가린 계좌) */
    public Map<Long, PayoutRow> findAll(Collection<Long> userIds) {
        Map<Long, PayoutRow> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : rows().where(A.userId.in(userIds)).fetch()) {
            PayoutRow row = rowOf(t);
            out.put(row.userId(), row);
        }
        return out;
    }

    public boolean registered(long userId) {
        return query.selectOne().from(A).where(A.userId.eq(userId)).fetchFirst() != null;
    }

    /** 정산 정보를 등록한 작가들 */
    public Set<Long> registered(Collection<Long> userIds) {
        Set<Long> out = new HashSet<>();
        if (userIds.isEmpty()) {
            return out;
        }
        out.addAll(query.select(A.userId).from(A).where(A.userId.in(userIds)).fetch());
        return out;
    }

    /**
     * 넣거나 바꾼다(작가마다 한 줄 — 처음 등록 시각은 남긴다).
     * Native SQL 인 이유: {@code ON CONFLICT (user_id) DO UPDATE} — 처음 등록과 바꾸기가 동시에 와도 유일 위반 없이 한 줄이 된다.
     */
    public void upsert(PayoutRow row) {
        OffsetDateTime at = OffsetDateTime.ofInstant(row.updatedAt(), ZoneOffset.UTC);
        jdbc.update("INSERT INTO emoticon_payout_accounts (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET "
                        + "payee_type = EXCLUDED.payee_type, business_number_enc = EXCLUDED.business_number_enc, business_number_masked = EXCLUDED.business_number_masked, "
                        + "bank_code = EXCLUDED.bank_code, account_number_enc = EXCLUDED.account_number_enc, account_last4 = EXCLUDED.account_last4, "
                        + "holder_name_enc = EXCLUDED.holder_name_enc, holder_masked = EXCLUDED.holder_masked, updated_at = EXCLUDED.updated_at",
                row.userId(), row.payeeType(), row.businessNumberCipher(), row.businessNumberMasked(), row.bankCode(), row.accountNumberCipher(), row.accountLast4(),
                row.holderNameCipher(), row.holderMasked(), at, at);
    }

    private static PayoutRow rowOf(Tuple t) {
        return new PayoutRow(t.get(A.userId), t.get(A.payeeType), t.get(A.businessNumberEnc), t.get(A.businessNumberMasked), t.get(A.bankCode), t.get(A.accountNumberEnc),
                t.get(A.accountLast4), t.get(A.holderNameEnc), t.get(A.holderMasked), t.get(A.createdAt), t.get(A.updatedAt));
    }
}
