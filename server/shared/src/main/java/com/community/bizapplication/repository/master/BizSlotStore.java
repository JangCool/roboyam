package com.community.bizapplication.repository.master;

import static com.community.bizapplication.repository.master.BizApplicationStore.instant;
import static com.community.bizapplication.repository.master.BizApplicationStore.names;
import static com.community.bizapplication.repository.master.BizApplicationStore.ts;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 자리(한도) 판정의 DB 쪽 — 같은 상품의 「자리를 차지하는 상태」 신청 건 줄 중 기간이 겹치는 것을 읽고, 승인 때는 상품마다 어드바이저리 잠금을 건다
 * (DB 전용 기능 — {@code pg_advisory_xact_lock} 은 트랜잭션이 끝나면 풀린다. 같은 상품을 승인하는 요청이 겹쳐도 판정·저장이 한 번에 하나씩 된다).
 */
@Repository
public class BizSlotStore {

    /** 겹치는 줄(기간만) */
    public record Occupied(long applicationId, Instant startAt, Instant endAt) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BizSlotStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** 그 상품의 승인 잠금(트랜잭션 끝까지) */
    public void lock(BizProduct product) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, "biz-slot:" + product.name());
    }

    /** [start, end) 와 겹치는 차지 줄 — {@code exceptApplicationId} 는 빼고(자기 자신) */
    public List<Occupied> overlapping(BizProduct product, Instant start, Instant end, Long exceptApplicationId) {
        MapSqlParameterSource args = new MapSqlParameterSource("product", product.name()).addValue("start", ts(start)).addValue("end", ts(end))
                .addValue("statuses", names(BizApplicationStatus.OCCUPYING)).addValue("except", exceptApplicationId == null ? -1L : exceptApplicationId);
        return named.query("SELECT i.application_id, i.start_at, i.end_at FROM biz_application_items i JOIN biz_applications a ON a.id = i.application_id "
                        + "WHERE i.product = :product AND i.start_at < :end AND i.end_at > :start AND a.status IN (:statuses) AND a.id <> :except",
                args, (rs, i) -> new Occupied(rs.getLong(1), instant(rs.getTimestamp(2)), instant(rs.getTimestamp(3))));
    }
}
