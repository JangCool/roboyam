package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizPriceRuleEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 요금표(V112 {@code biz_price_rules}) — 상품 하나에 한 줄. 편집 API 는 후속(지금은 시드 값). QueryDSL 만 쓴다 */
@Repository
public class BizPriceRuleStore extends IrobitRepositoryQDSL {

    private static final QBizPriceRuleEntity R = QBizPriceRuleEntity.bizPriceRuleEntity;
    /** DB 의 현재 시각({@link #DB_NOW} 와 같은 식) — {@code Instant} 칸에 넣을 때 */
    private static final DateTimeExpression<Instant> DB_INSTANT = Expressions.dateTimeTemplate(Instant.class, "offset_datetime");

    /** 요금표 한 줄 — {@code capacity} 는 건당(POST)이면 null(한도 없음) */
    public record PriceRule(BizProduct product, boolean perPost, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, boolean accepting) {
    }

    public BizPriceRuleStore(JPAQueryFactory query) {
        super(query);
    }

    public Map<BizProduct, PriceRule> all() {
        Map<BizProduct, PriceRule> out = new EnumMap<>(BizProduct.class);
        query.select(R.product, R.unit, R.unitPrice, R.capacity, R.maxDays, R.maxLeadDays, R.accepting).from(R).fetch().forEach(t -> {
            BizProduct p = t.get(R.product);
            out.put(p, new PriceRule(p, "POST".equals(t.get(R.unit)), t.get(R.unitPrice), t.get(R.capacity), t.get(R.maxDays), t.get(R.maxLeadDays),
                    Boolean.TRUE.equals(t.get(R.accepting))));
        });
        return out;
    }

    public Optional<PriceRule> find(BizProduct product) {
        return Optional.ofNullable(all().get(product));
    }

    /** 시험·운영 도구 — 받기 켜기/끄기(요금표 편집 API 가 생기기 전까지). 바뀐 시각은 DB 시각(예전 {@code now()}) */
    public void setAccepting(BizProduct product, boolean accepting) {
        query.update(R).set(R.accepting, accepting).set(R.updatedAt, DB_INSTANT).where(R.product.eq(product)).execute();
    }
}
