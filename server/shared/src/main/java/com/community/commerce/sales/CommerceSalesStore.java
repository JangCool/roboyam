package com.community.commerce.sales;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 판매 스위치·운영 체크리스트·사업자 정보(V104 — {@code commerce_sales_settings}·{@code commerce_sales_checklist}·{@code commerce_business_info}, 모두 1행 또는 고정 줄).
 * 운영자 이름은 {@code operators.operator_name} 을 이어 읽는다(지운 운영자는 null).
 */
@Repository
public class CommerceSalesStore {

    /** 스위치 한 줄 */
    public record SwitchRow(boolean groupBuyOpen, Instant changedAt, String changedBy, String reason) {
    }

    /** 체크 항목 한 줄 */
    public record ChecklistRow(String key, boolean checked, String checkedBy, Instant checkedAt) {
    }

    private final JdbcTemplate jdbc;

    public CommerceSalesStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public boolean groupBuyOpen() {
        Boolean open = jdbc.queryForObject("SELECT groupbuy_open FROM commerce_sales_settings WHERE id = 1", Boolean.class);
        return Boolean.TRUE.equals(open);
    }

    public SwitchRow switchRow() {
        return jdbc.queryForObject("SELECT s.groupbuy_open, s.changed_at, o.operator_name, s.change_reason FROM commerce_sales_settings s "
                + "LEFT JOIN operators o ON o.id = s.changed_by WHERE s.id = 1", (rs, i) -> new SwitchRow(rs.getBoolean(1), instant(rs, 2), rs.getString(3), rs.getString(4)));
    }

    /** 스위치 행을 잠근다(켜기·끄기가 줄 선다) */
    public boolean lockSwitch() {
        Boolean open = jdbc.queryForObject("SELECT groupbuy_open FROM commerce_sales_settings WHERE id = 1 FOR UPDATE", Boolean.class);
        return Boolean.TRUE.equals(open);
    }

    public void setSwitch(boolean open, String reason, Long operatorId, Instant now) {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = ?, change_reason = ?, changed_by = ?, changed_at = ? WHERE id = 1", open, reason, operatorId,
                Timestamp.from(now));
    }

    public List<ChecklistRow> checklist() {
        return jdbc.query("SELECT c.item_key, c.checked, o.operator_name, c.checked_at FROM commerce_sales_checklist c LEFT JOIN operators o ON o.id = c.checked_by "
                + "ORDER BY c.sort_order", (rs, i) -> new ChecklistRow(rs.getString(1), rs.getBoolean(2), rs.getString(3), instant(rs, 4)));
    }

    /** @return 바뀌었으면 true(같은 값이면 false — 감사를 남기지 않는다) */
    public boolean check(String key, boolean checked, Long operatorId, Instant now) {
        return jdbc.update("UPDATE commerce_sales_checklist SET checked = ?, checked_by = ?, checked_at = ? WHERE item_key = ? AND checked <> ?", checked,
                checked ? operatorId : null, checked ? Timestamp.from(now) : null, key, checked) == 1;
    }

    public BusinessInfo businessInfo() {
        return jdbc.queryForObject("SELECT b.company_name, b.representative, b.business_no, b.mail_order_no, b.mail_order_authority, b.address, b.phone, b.email, "
                + "b.business_hours, b.privacy_officer, b.hosting_provider, b.shipping_refund_policy, b.updated_at, o.operator_name FROM commerce_business_info b "
                + "LEFT JOIN operators o ON o.id = b.updated_by WHERE b.id = 1", (rs, i) -> new BusinessInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), instant(rs, 13),
                rs.getString(14)));
    }

    public void saveBusinessInfo(BusinessInfo v, Long operatorId, Instant now) {
        jdbc.update("UPDATE commerce_business_info SET company_name = ?, representative = ?, business_no = ?, mail_order_no = ?, mail_order_authority = ?, address = ?, "
                        + "phone = ?, email = ?, business_hours = ?, privacy_officer = ?, hosting_provider = ?, shipping_refund_policy = ?, updated_by = ?, updated_at = ? WHERE id = 1",
                v.companyName(), v.representative(), v.businessNo(), v.mailOrderNo(), v.mailOrderAuthority(), v.address(), v.phone(), v.email(), v.businessHours(),
                v.privacyOfficer(), v.hostingProvider(), v.shippingRefundPolicy(), operatorId, Timestamp.from(now));
    }

    /** 켜진 택배사가 하나라도 있나(판매 켜기 자동 조건 — 공구) */
    public boolean anyActiveCarrier() {
        Boolean any = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM commerce_carriers WHERE enabled)", Boolean.class);
        return Boolean.TRUE.equals(any);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
