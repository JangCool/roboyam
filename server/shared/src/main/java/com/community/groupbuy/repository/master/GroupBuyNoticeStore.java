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
 * 상품정보제공고시 양식·적용 옵션·항목 값(계약 결정 87). 저장은 공구 단위로 통째로 바꾼다(양식 줄을 지우면 옵션·값은 CASCADE).
 */
@Repository
public class GroupBuyNoticeStore {

    /** 양식 한 장(적용 옵션 이름·항목 값 포함) */
    public record NoticeRow(long id, int sortOrder, String categoryCode, String title, String catalogVersion, List<Long> optionIds, List<String> optionNames,
                            Map<String, ValueRow> values) {
    }

    /** 항목 값 */
    public record ValueRow(String itemKey, String value, boolean unavailable, String unavailableReason) {
    }

    private final JdbcTemplate jdbc;

    public GroupBuyNoticeStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<NoticeRow> notices(long groupBuyId) {
        List<NoticeRow> out = new ArrayList<>();
        jdbc.query("SELECT id, sort_order, category_code, title, catalog_version FROM group_buy_notices WHERE group_buy_id = ? ORDER BY sort_order", rs -> {
            out.add(new NoticeRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), new ArrayList<>(), new ArrayList<>(),
                    new LinkedHashMap<>()));
        }, groupBuyId);
        for (NoticeRow notice : out) {
            jdbc.query("SELECT o.id, o.name FROM group_buy_notice_options n JOIN group_buy_options o ON o.id = n.option_id WHERE n.notice_id = ? ORDER BY o.sort_order, o.id",
                    rs -> {
                        notice.optionIds().add(rs.getLong(1));
                        notice.optionNames().add(rs.getString(2));
                    }, notice.id());
            jdbc.query("SELECT item_key, value, unavailable, unavailable_reason FROM group_buy_notice_values WHERE notice_id = ? ORDER BY item_key",
                    rs -> {
                        notice.values().put(rs.getString(1), new ValueRow(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4)));
                    }, notice.id());
        }
        return out;
    }

    public void deleteAll(long groupBuyId) {
        jdbc.update("DELETE FROM group_buy_notices WHERE group_buy_id = ?", groupBuyId);
    }

    public void insertNotice(long id, long groupBuyId, int sortOrder, String categoryCode, String title, String catalogVersion, Instant now) {
        jdbc.update("INSERT INTO group_buy_notices (id, group_buy_id, sort_order, category_code, title, catalog_version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, groupBuyId, sortOrder, categoryCode, title, catalogVersion, ts(now), ts(now));
    }

    public void insertOption(long noticeId, long groupBuyId, long optionId) {
        jdbc.update("INSERT INTO group_buy_notice_options (notice_id, group_buy_id, option_id) VALUES (?, ?, ?)", noticeId, groupBuyId, optionId);
    }

    public void insertValue(long noticeId, String itemKey, String value, boolean unavailable, String reason) {
        jdbc.update("INSERT INTO group_buy_notice_values (notice_id, item_key, value, unavailable, unavailable_reason) VALUES (?, ?, ?, ?, ?)", noticeId, itemKey, value,
                unavailable, reason);
    }

    /** DRAFT 옵션을 다시 넣기 전 — 양식별 적용 옵션 이름(다시 넣은 뒤 이름으로 다시 잇는다) */
    public Map<Long, List<String>> optionNamesByNotice(long groupBuyId) {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        jdbc.query("SELECT n.notice_id, o.name FROM group_buy_notice_options n JOIN group_buy_options o ON o.id = n.option_id WHERE n.group_buy_id = ?",
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
                }, groupBuyId);
        return out;
    }
}
