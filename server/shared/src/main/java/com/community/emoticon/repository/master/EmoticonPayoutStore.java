package com.community.emoticon.repository.master;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 작가 정산 정보({@code emoticon_payout_accounts}, V141 — 작가마다 한 줄). 원문 칸은 {@code DataCipher} 암호문만 오가고(이 클래스는 풀지 않는다), 화면용 가린 값은 따로 둔다.
 * JDBC 인 이유는 {@link EmoticonPackStore} 와 같다.
 */
@Repository
public class EmoticonPayoutStore {

    /** 정산 정보 한 줄 — {@code *Cipher} 는 암호문 */
    public record PayoutRow(long userId, String payeeType, String businessNumberCipher, String businessNumberMasked, String bankCode, String accountNumberCipher,
                            String accountLast4, String holderNameCipher, String holderMasked, Instant createdAt, Instant updatedAt) {
    }

    private static final String COLUMNS = "user_id, payee_type, business_number_enc, business_number_masked, bank_code, account_number_enc, account_last4, holder_name_enc, "
            + "holder_masked, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public EmoticonPayoutStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<PayoutRow> find(long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM emoticon_payout_accounts WHERE user_id = ?", (rs, i) -> new PayoutRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getObject(10, OffsetDateTime.class).toInstant(),
                rs.getObject(11, OffsetDateTime.class).toInstant()), userId).stream().findFirst();
    }

    /** 여러 작가의 정산 정보(S4 정산 목록 — 작가 구분 · 가린 계좌) */
    public Map<Long, PayoutRow> findAll(Collection<Long> userIds) {
        Map<Long, PayoutRow> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        String marks = String.join(", ", Collections.nCopies(userIds.size(), "?"));
        jdbc.query("SELECT " + COLUMNS + " FROM emoticon_payout_accounts WHERE user_id IN (" + marks + ")", rs -> {
            out.put(rs.getLong(1), new PayoutRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getString(8), rs.getString(9), rs.getObject(10, OffsetDateTime.class).toInstant(), rs.getObject(11, OffsetDateTime.class).toInstant()));
        }, userIds.toArray());
        return out;
    }

    public boolean registered(long userId) {
        return !jdbc.queryForList("SELECT 1 FROM emoticon_payout_accounts WHERE user_id = ?", Integer.class, userId).isEmpty();
    }

    /** 정산 정보를 등록한 작가들 */
    public Set<Long> registered(Collection<Long> userIds) {
        Set<Long> out = new HashSet<>();
        if (userIds.isEmpty()) {
            return out;
        }
        String marks = String.join(", ", Collections.nCopies(userIds.size(), "?"));
        out.addAll(jdbc.queryForList("SELECT user_id FROM emoticon_payout_accounts WHERE user_id IN (" + marks + ")", Long.class, userIds.toArray()));
        return out;
    }

    /** 넣거나 바꾼다(작가마다 한 줄 — 처음 등록 시각은 남긴다) */
    public void upsert(PayoutRow row) {
        OffsetDateTime at = OffsetDateTime.ofInstant(row.updatedAt(), ZoneOffset.UTC);
        jdbc.update("INSERT INTO emoticon_payout_accounts (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET "
                        + "payee_type = EXCLUDED.payee_type, business_number_enc = EXCLUDED.business_number_enc, business_number_masked = EXCLUDED.business_number_masked, "
                        + "bank_code = EXCLUDED.bank_code, account_number_enc = EXCLUDED.account_number_enc, account_last4 = EXCLUDED.account_last4, "
                        + "holder_name_enc = EXCLUDED.holder_name_enc, holder_masked = EXCLUDED.holder_masked, updated_at = EXCLUDED.updated_at",
                row.userId(), row.payeeType(), row.businessNumberCipher(), row.businessNumberMasked(), row.bankCode(), row.accountNumberCipher(), row.accountLast4(),
                row.holderNameCipher(), row.holderMasked(), at, at);
    }
}
