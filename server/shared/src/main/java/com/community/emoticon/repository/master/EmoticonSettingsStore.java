package com.community.emoticon.repository.master;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 설정 행({@code emoticon_settings}, V142 — 한 행 · 유료 칸 V143) 읽기·쓰기. Native SQL(JDBC)인 이유는 {@link EmoticonPackStore} 와 같다(받기·심사와 같은 연결에서 잠금 차례를 지키고,
 * 시험이 스프링 문맥 없이 서비스를 조립한다). 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class EmoticonSettingsStore {

    /** 설정 한 행(마지막으로 저장한 운영자 이름 포함 — 없으면 null). 가격 단계는 오름차순 */
    public record SettingsRow(int rewardPerAcquire, int rewardDailyCap, int itemMin, int itemMax, int pendingLimit, boolean paidSalesEnabled, int feeRatePercent,
                              int settlementDay, int payoutMinimum, int refundDays, List<Integer> priceTiers, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    /** 저장할 값 */
    public record SettingsValues(int rewardPerAcquire, int rewardDailyCap, int itemMin, int itemMax, int pendingLimit, boolean paidSalesEnabled, int feeRatePercent,
                                 int settlementDay, int payoutMinimum, int refundDays, List<Integer> priceTiers) {
    }

    private static final String SELECT = "SELECT s.reward_per_acquire, s.reward_daily_cap, s.item_min, s.item_max, s.pending_limit, s.updated_at, s.updated_by, o.operator_name, "
            + "s.paid_sales_enabled, s.fee_rate_percent, s.settlement_day, s.payout_minimum, s.refund_days, s.price_tiers "
            + "FROM emoticon_settings s LEFT JOIN operators o ON o.id = s.updated_by WHERE s.id = 1";

    private final JdbcTemplate jdbc;

    public EmoticonSettingsStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public SettingsRow read() {
        return jdbc.queryForObject(SELECT, EmoticonSettingsStore::rowOf);
    }

    /** 저장하기 전에 — 행을 잠근다(같은 설정의 동시 저장이 전→후 감사를 어긋나게 하지 않게, 가격을 정하는 처리와 줄을 서게) */
    public SettingsRow readForUpdate() {
        jdbc.query("SELECT id FROM emoticon_settings WHERE id = 1 FOR UPDATE", rs -> {
        });
        return read();
    }

    /**
     * 가격을 정하기 전에(승인·가격 바꾸기) — 행을 나눔 잠금으로 잡는다. 설정 저장(FOR UPDATE)이 그동안 가격 단계·유료 판매 켜기를 바꾸지 못해, 읽은 단계로 정한 가격이
     * 커밋 때도 열린 단계다(설정 저장이 먼저면 그 커밋 뒤의 값을 읽는다).
     */
    public SettingsRow readShared() {
        jdbc.query("SELECT id FROM emoticon_settings WHERE id = 1 FOR SHARE", rs -> {
        });
        return read();
    }

    public void save(SettingsValues v, long operatorId, Instant now) {
        jdbc.execute((ConnectionCallback<Integer>) con -> {
            Array tiers = con.createArrayOf("integer", v.priceTiers().toArray());
            try (var ps = con.prepareStatement("UPDATE emoticon_settings SET reward_per_acquire = ?, reward_daily_cap = ?, item_min = ?, item_max = ?, pending_limit = ?, "
                    + "paid_sales_enabled = ?, fee_rate_percent = ?, settlement_day = ?, payout_minimum = ?, refund_days = ?, price_tiers = ?, updated_at = ?, updated_by = ? "
                    + "WHERE id = 1")) {
                ps.setInt(1, v.rewardPerAcquire());
                ps.setInt(2, v.rewardDailyCap());
                ps.setInt(3, v.itemMin());
                ps.setInt(4, v.itemMax());
                ps.setInt(5, v.pendingLimit());
                ps.setBoolean(6, v.paidSalesEnabled());
                ps.setInt(7, v.feeRatePercent());
                ps.setInt(8, v.settlementDay());
                ps.setInt(9, v.payoutMinimum());
                ps.setInt(10, v.refundDays());
                ps.setArray(11, tiers);
                ps.setTimestamp(12, Timestamp.from(now));
                ps.setLong(13, operatorId);
                return ps.executeUpdate();
            }
        });
    }

    /** 상점에 나온 팩(판매 중 · 판매 중단)이 쓰는 유료 가격 — 설정에서 뺄 수 없는 단계(오름차순) */
    public List<Integer> tiersInUse() {
        return jdbc.queryForList("SELECT DISTINCT price FROM emoticon_packs WHERE status IN ('APPROVED', 'SUSPENDED') AND price > 0 ORDER BY price", Integer.class);
    }

    private static SettingsRow rowOf(ResultSet rs, int i) throws SQLException {
        Timestamp at = rs.getTimestamp(6);
        Long by = rs.getObject(7, Long.class);
        List<Integer> tiers = new ArrayList<>();
        Array array = rs.getArray(14);
        if (array != null) {
            for (Object v : (Object[]) array.getArray()) {
                tiers.add(((Number) v).intValue());
            }
        }
        return new SettingsRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getBoolean(9), rs.getInt(10), rs.getInt(11), rs.getInt(12),
                rs.getInt(13), List.copyOf(tiers), at == null ? null : at.toInstant(), by, rs.getString(8));
    }
}
