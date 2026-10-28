package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 상품정보제공고시 양식·적용 단품·항목 값·판매자 추가 항목(계약 결정 87). 상품마다(결정 89) — 저장은 상품 단위로 통째로 바꾼다(양식 줄을 지우면 적용 단품·값은 CASCADE).
 */
@Repository
public class GroupBuyNoticeStore {

    /** 양식 한 장(적용 단품 이름·고시 항목 값·판매자 추가 항목 포함) */
    public record NoticeRow(long id, int sortOrder, String categoryCode, String title, String catalogVersion, List<Long> skuIds, List<String> skuNames,
                            Map<String, ValueRow> values, List<CustomRow> customItems) {
    }

    /** 고시 항목 값 */
    public record ValueRow(String itemKey, String value, boolean unavailable, String unavailableReason) {
    }

    /** 판매자 추가 항목(차례대로) */
    public record CustomRow(String name, String value, int sortOrder) {
    }

    private final JdbcTemplate jdbc;

    public GroupBuyNoticeStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<NoticeRow> notices(long productId) {
        List<NoticeRow> out = new ArrayList<>();
        jdbc.query("SELECT id, sort_order, category_code, title, catalog_version FROM group_buy_notices WHERE product_id = ? ORDER BY sort_order", rs -> {
            out.add(new NoticeRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), new ArrayList<>(), new ArrayList<>(),
                    new LinkedHashMap<>(), new ArrayList<>()));
        }, productId);
        for (NoticeRow notice : out) {
            jdbc.query("SELECT o.id, o.name FROM group_buy_notice_skus n JOIN group_buy_product_skus o ON o.id = n.product_sku_id WHERE n.notice_id = ? ORDER BY o.sort_order, o.id",
                    rs -> {
                        notice.skuIds().add(rs.getLong(1));
                        notice.skuNames().add(rs.getString(2));
                    }, notice.id());
            jdbc.query("SELECT custom, item_key, custom_name, sort_order, value, unavailable, unavailable_reason FROM group_buy_notice_values WHERE notice_id = ? "
                    + "ORDER BY custom, sort_order, item_key", rs -> {
                        if (rs.getBoolean(1)) {
                            notice.customItems().add(new CustomRow(rs.getString(3), rs.getString(5), rs.getInt(4)));
                        } else {
                            notice.values().put(rs.getString(2), new ValueRow(rs.getString(2), rs.getString(5), rs.getBoolean(6), rs.getString(7)));
                        }
                    }, notice.id());
        }
        return out;
    }

    public void deleteAll(long productId) {
        jdbc.update("DELETE FROM group_buy_notices WHERE product_id = ?", productId);
    }

    public void insertNotice(long id, long productId, int sortOrder, String categoryCode, String title, String catalogVersion, Instant now) {
        jdbc.update("INSERT INTO group_buy_notices (id, product_id, sort_order, category_code, title, catalog_version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, productId, sortOrder, categoryCode, title, catalogVersion, ts(now), ts(now));
    }

    public void insertSku(long noticeId, long productId, long productSkuId) {
        jdbc.update("INSERT INTO group_buy_notice_skus (notice_id, product_id, product_sku_id) VALUES (?, ?, ?)", noticeId, productId, productSkuId);
    }

    public void insertValue(long id, long noticeId, String itemKey, String value, boolean unavailable, String reason) {
        jdbc.update("INSERT INTO group_buy_notice_values (id, notice_id, custom, item_key, value, unavailable, unavailable_reason) VALUES (?, ?, false, ?, ?, ?, ?)", id,
                noticeId, itemKey, value, unavailable, reason);
    }

    public void insertCustom(long id, long noticeId, String name, String value, int sortOrder) {
        jdbc.update("INSERT INTO group_buy_notice_values (id, notice_id, custom, custom_name, value, sort_order) VALUES (?, ?, true, ?, ?, ?)", id, noticeId, name, value,
                sortOrder);
    }
}
