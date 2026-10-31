package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizProduct;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 요금표(V112 {@code biz_price_rules}) — 상품 하나에 한 줄. 편집 API 는 후속(지금은 시드 값) */
@Repository
public class BizPriceRuleStore {

    /** 요금표 한 줄 — {@code capacity} 는 건당(POST)이면 null(한도 없음) */
    public record PriceRule(BizProduct product, boolean perPost, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, boolean accepting) {
    }

    private final JdbcTemplate jdbc;

    public BizPriceRuleStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Map<BizProduct, PriceRule> all() {
        Map<BizProduct, PriceRule> out = new EnumMap<>(BizProduct.class);
        jdbc.query("SELECT * FROM biz_price_rules", rs -> {
            BizProduct p = BizProduct.valueOf(rs.getString("product"));
            out.put(p, new PriceRule(p, "POST".equals(rs.getString("unit")), rs.getLong("unit_price"), (Integer) rs.getObject("capacity", Integer.class),
                    rs.getInt("max_days"), rs.getInt("max_lead_days"), rs.getBoolean("accepting")));
        });
        return out;
    }

    public Optional<PriceRule> find(BizProduct product) {
        return Optional.ofNullable(all().get(product));
    }

    /** 시험·운영 도구 — 받기 켜기/끄기(요금표 편집 API 가 생기기 전까지) */
    public void setAccepting(BizProduct product, boolean accepting) {
        jdbc.update("UPDATE biz_price_rules SET accepting = ?, updated_at = now() WHERE product = ?", accepting, product.name());
    }
}
