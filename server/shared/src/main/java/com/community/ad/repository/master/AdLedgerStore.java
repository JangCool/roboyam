package com.community.ad.repository.master;

import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 광고 일정표의 장부 읽기(ad-contract) — 장부 = 신청 건({@code biz_applications}) 중 그 자리(상품) 줄이 있는 건. 유형 7개(배너 넷·한 줄·핫딜 홈 상단·말머리 상단)가 상품 이름 그대로다.
 * <b>장부에 드는 상태</b>: 자리를 차지하는 상태(결제·입금 대기 · 예약 · 내림) + 운영자 등록의 심사 대기(광고 심사 큐에도 있다) + 승인 뒤 취소된 건. 사업자 신청의 심사 대기·반려·만료·
 * 승인 전 취소는 심사 큐에만 있다.
 *
 * <p>공개 노출 조회(후속 — {@code AdSlot}·가젯 {@code AD})의 서버 쿼리 후보도 같은 모양이다: {@code status = 'SCHEDULED' AND start_at <= now < end_at} 인 그 상품 줄의 건 + 최신 승인 소재 버전,
 * 비중({@code weight}) 가중 무작위는 노출 쪽이 고른다.
 */
@Repository
public class AdLedgerStore {

    /** 장부에 드는 건(별칭 a) */
    static final String LEDGER = "(a.status IN ('AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED', 'TAKEN_DOWN') OR (a.status = 'SUBMITTED' AND a.source = 'OPERATOR') "
            + "OR (a.status = 'CANCELED' AND a.approved_at IS NOT NULL))";
    static final int MAX_ROWS = 2000;

    private final NamedParameterJdbcTemplate named;
    private final BizApplicationStore apps;

    public AdLedgerStore(DataSource dataSource, BizApplicationStore apps) {
        this.named = new NamedParameterJdbcTemplate(dataSource);
        this.apps = apps;
    }

    /**
     * 그 상품의 장부 — 기간이 {@code [from, to)} 와 겹치는 건(둘 다 null 이면 기간 무관), 검색(번호·광고주·상호·제목). 시작이 늦은 것 먼저, 최대 2,000건.
     */
    public List<AppRow> ledger(BizProduct product, Instant from, Instant to, String q) {
        StringBuilder sql = new StringBuilder("SELECT a.id FROM biz_applications a JOIN biz_application_items i ON i.application_id = a.id AND i.product = :product WHERE ")
                .append(LEDGER);
        MapSqlParameterSource args = new MapSqlParameterSource("product", product.name());
        if (from != null && to != null) {
            sql.append(" AND a.start_at < :to AND a.end_at > :from");
            args.addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (a.application_no ILIKE :like OR a.company_name ILIKE :like OR a.advertiser_name ILIKE :like OR a.title ILIKE :like)");
            args.addValue("like", "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.append(" ORDER BY a.start_at DESC, a.id DESC LIMIT ").append(MAX_ROWS);
        List<Long> ids = named.queryForList(sql.toString(), args, Long.class);
        List<AppRow> rows = apps.findAll(ids);
        java.util.Map<Long, AppRow> byId = new java.util.HashMap<>();
        rows.forEach(r -> byId.put(r.id(), r));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 그 상품의 지금 살아 있는 건(결제·입금 대기 · 끝나지 않은 예약) — 유형 요약 */
    public List<AppRow> live(BizProduct product, Instant now) {
        List<Long> ids = named.queryForList("SELECT a.id FROM biz_applications a JOIN biz_application_items i ON i.application_id = a.id AND i.product = :product "
                        + "WHERE a.status IN ('AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED') AND a.end_at > :now LIMIT " + MAX_ROWS,
                new MapSqlParameterSource("product", product.name()).addValue("now", Timestamp.from(now)), Long.class);
        return apps.findAll(ids);
    }
}
