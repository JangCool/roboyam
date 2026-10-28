package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 계좌 송금({@code commerce_bank_transfers} — 결정 6·39·67, 9-3 과 같은 방식). 계좌 암호문은 송금 완료 때 지운다.
 */
@Repository
public class BankTransferStore {

    public record TransferRow(long id, String sourceKind, long sourceId, Long participationId, long userId, long amount, String status, String bankEnc, String accountNoEnc,
                              String holderEnc) {
    }

    private static final String COLUMNS = "id, source_kind, source_id, participation_id, user_id, amount, status, bank_enc, account_no_enc, holder_enc";

    private final JdbcTemplate jdbc;

    public BankTransferStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 송금 대기를 만든다(같은 원천은 한 번 — 이미 있으면 false) */
    public boolean insert(long id, String sourceKind, long sourceId, Long participationId, long userId, long amount, String reason, Long operatorId, Instant now) {
        return jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, participation_id, user_id, amount, status, reason, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'AWAITING_ACCOUNT', ?, ?, ?) ON CONFLICT (source_kind, source_id) DO NOTHING", id, sourceKind, sourceId, participationId, userId, amount,
                reason, operatorId, ts(now)) == 1;
    }

    public Optional<TransferRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commerce_bank_transfers WHERE id = ?", BankTransferStore::row, id).stream().findFirst();
    }

    public Optional<TransferRow> bySource(String sourceKind, long sourceId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commerce_bank_transfers WHERE source_kind = ? AND source_id = ?", BankTransferStore::row, sourceKind, sourceId).stream()
                .findFirst();
    }

    public List<TransferRow> ofParticipation(long participationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commerce_bank_transfers WHERE participation_id = ? ORDER BY id", BankTransferStore::row, participationId);
    }

    /** 계좌를 받는다(송금 전일 때만) */
    public boolean setAccount(long id, String bankEnc, String accountNoEnc, String holderEnc) {
        return jdbc.update("UPDATE commerce_bank_transfers SET bank_enc = ?, account_no_enc = ?, holder_enc = ?, status = 'READY' WHERE id = ? "
                + "AND status IN ('AWAITING_ACCOUNT', 'READY')", bankEnc, accountNoEnc, holderEnc, id) == 1;
    }

    /** 송금 완료 — 계좌 암호문을 지운다(이긴 쪽만) */
    public boolean markSent(long id, long operatorId, Instant now) {
        return jdbc.update("UPDATE commerce_bank_transfers SET status = 'SENT', sent_by = ?, sent_at = ?, bank_enc = NULL, account_no_enc = NULL, holder_enc = NULL, "
                + "account_erased_at = ? WHERE id = ? AND status = 'READY'", operatorId, ts(now), ts(now), id) == 1;
    }

    static TransferRow row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new TransferRow(rs.getLong(1), rs.getString(2), rs.getLong(3), nLong(rs, "participation_id"), rs.getLong(5), rs.getLong(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10));
    }
}
