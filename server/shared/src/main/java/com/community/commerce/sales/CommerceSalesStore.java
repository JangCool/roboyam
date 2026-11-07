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

    /**
     * 사업자 정보 열두 칸 — <b>겹치는 칸은 운영 주체({@code site_operator}, 정본)</b>에서, 커머스 고유 칸(운영시간·배송·환불 안내)과 마지막 저장은 {@code commerce_business_info} 에서 읽는다
     * (terms-operator-contract 세션 정리 결정). 상호 = 상호(법인은 회사명), 보호책임자 = 운영 주체의 보호책임자(「운영자와 같음」이면 운영자 이름). 개인 단계는 사업자 칸이 비어 있다(빈 글자).
     */
    public BusinessInfo businessInfo() {
        return jdbc.queryForObject("SELECT coalesce(s.business_name, ''), coalesce(s.representative, ''), coalesce(s.registration_number, ''), "
                + "coalesce(s.mail_order_number, ''), coalesce(s.mail_order_authority, ''), coalesce(s.address, ''), coalesce(s.phone, ''), coalesce(s.contact_email, ''), "
                + "b.business_hours, coalesce(CASE WHEN s.privacy_officer_same THEN s.operator_name ELSE s.privacy_officer_name END, ''), coalesce(s.hosting_provider, ''), "
                + "b.shipping_refund_policy, b.updated_at, o.operator_name FROM commerce_business_info b CROSS JOIN site_operator s "
                + "LEFT JOIN operators o ON o.id = b.updated_by WHERE b.id = 1 AND s.id = 1", (rs, i) -> new BusinessInfo(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                instant(rs, 13), rs.getString(14)));
    }

    /** 커머스 고유 칸만 저장한다(운영시간·배송·환불 안내) — 겹치는 칸은 운영 주체 설정이 저장한다 */
    public void saveBusinessInfo(BusinessInfo v, Long operatorId, Instant now) {
        jdbc.update("UPDATE commerce_business_info SET business_hours = ?, shipping_refund_policy = ?, updated_by = ?, updated_at = ? WHERE id = 1", v.businessHours(),
                v.shippingRefundPolicy(), operatorId, Timestamp.from(now));
    }

    /**
     * 운영 주체가 판매를 열 수 있는 단계인가 — 개인사업자 이상이고 통신판매업 신고번호가 있다(판매 켜기 자동 조건 {@code OPERATOR_STAGE}). <b>돈을 받는 기능은 모두 이 한 곳으로 판정한다</b>
     * (사용자 결정 2026-11-07 — 공구 판매 켜기 · 로보티콘 유료 판매 켜기·구매 · 광고/핫딜 유료 신청·결제 시작·승인·입금 확정, terms-operator-contract).
     */
    public boolean operatorCanSell() {
        Boolean ok = jdbc.queryForObject("SELECT stage <> 'INDIVIDUAL' AND coalesce(btrim(mail_order_number), '') <> '' FROM site_operator WHERE id = 1", Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 로보티콘 유료 판매 스위치({@code emoticon_settings.paid_sales_enabled})를 나눔 잠금으로 잡고 읽는다 — 운영 주체를 개인 단계로 바꾸거나 신고번호를 비우기 전에(켜져 있으면 막는다).
     * 설정 저장(행 {@code FOR UPDATE} 뒤 운영 주체 읽기)과 줄을 서서, 켜기가 읽은 운영 주체 단계가 그 커밋 전에 바뀌지 않는다.
     */
    public boolean lockEmoticonPaidSales() {
        Boolean on = jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1 FOR SHARE", Boolean.class);
        return Boolean.TRUE.equals(on);
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
