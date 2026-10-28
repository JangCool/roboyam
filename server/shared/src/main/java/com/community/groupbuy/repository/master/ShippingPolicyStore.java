package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 배송비 정책({@code shipping_policies}) — 정책 이름별 기간 줄(commerce.md 10-2 「배송비 정책과 예약 변경」). 기간 겹침은 DB 배제 제약, 적용이 시작된 줄의 불변은 DB 트리거가 막는다.
 */
@Repository
public class ShippingPolicyStore {

    /** 정책 줄 */
    public record PolicyRow(long id, String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant effectiveFrom,
                            Instant effectiveTo) {
    }

    private static final String COLUMNS = "id, code, name, base_fee, free_shipping_over, return_fee, return_fee_mode, effective_from, effective_to";

    private final JdbcTemplate jdbc;

    public ShippingPolicyStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 시각에 유효한 줄 */
    public Optional<PolicyRow> effective(String code, Instant at) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_policies WHERE code = ? AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)",
                ShippingPolicyStore::row, code, ts(at), ts(at)).stream().findFirst();
    }

    /** 같은 정책 이름의 줄 전부(시작 차례) */
    public List<PolicyRow> history(String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_policies WHERE code = ? ORDER BY effective_from", ShippingPolicyStore::row, code);
    }

    /** 모든 줄(정책 이름 · 시작 차례) — 설정 화면 */
    public List<PolicyRow> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_policies ORDER BY code, effective_from", ShippingPolicyStore::row);
    }

    public Optional<PolicyRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM shipping_policies WHERE id = ?", ShippingPolicyStore::row, id).stream().findFirst();
    }

    /** 아직 시작하지 않은 줄을 지운다(시작한 줄은 0 행 — 트리거도 막는다) */
    public boolean deleteNotStarted(long id, Instant now) {
        return jdbc.update("DELETE FROM shipping_policies WHERE id = ? AND effective_from > ?", id, ts(now)) == 1;
    }

    /** 지운 예약 바로 앞 줄의 끝을 다시 연다(그 예약 시작 시각에 닫혀 있던 줄) */
    public void reopen(String code, Instant closedAt) {
        jdbc.update("UPDATE shipping_policies SET effective_to = NULL WHERE code = ? AND effective_to = ?", code, ts(closedAt));
    }

    /** 이 정책 이름의 운영자 이름 붙은 줄(만든 사람) */
    public java.util.Map<Long, String> creators() {
        java.util.Map<Long, String> out = new java.util.HashMap<>();
        jdbc.query("SELECT s.id, o.operator_name FROM shipping_policies s LEFT JOIN operators o ON o.id = s.created_by", rs -> {
            out.put(rs.getLong(1), rs.getString(2));
        });
        return out;
    }

    /** 줄의 만든 시각 */
    public java.util.Map<Long, Instant> createdAt() {
        java.util.Map<Long, Instant> out = new java.util.HashMap<>();
        jdbc.query("SELECT id, created_at FROM shipping_policies", rs -> {
            out.put(rs.getLong(1), rs.getTimestamp(2).toInstant());
        });
        return out;
    }

    /** 같은 정책 이름의 예약·등록을 줄 세운다(트랜잭션 범위 어드바이저리 잠금) */
    public void lockCode(String code) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('shipping_policy:' || ?))", code);
    }

    public void insert(long id, String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant from, Instant to,
                       Long operatorId, Instant now) {
        jdbc.update("INSERT INTO shipping_policies (id, code, name, base_fee, free_shipping_over, return_fee, return_fee_mode, effective_from, effective_to, created_by, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, code, name, baseFee, freeShippingOver, returnFee, returnFeeMode, ts(from), ts(to), operatorId,
                ts(now));
    }

    /** 끝이 열린(또는 더 늦은) 줄을 그 시각에 닫는다 */
    public boolean close(long id, Instant at) {
        return jdbc.update("UPDATE shipping_policies SET effective_to = ? WHERE id = ? AND (effective_to IS NULL OR effective_to > ?)", ts(at), id, ts(at)) == 1;
    }

    static PolicyRow row(ResultSet rs, int i) throws SQLException {
        return new PolicyRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getLong("base_fee"), nLong(rs, "free_shipping_over"),
                rs.getLong("return_fee"), rs.getString("return_fee_mode"), instant(rs.getTimestamp("effective_from")), instant(rs.getTimestamp("effective_to")));
    }
}
