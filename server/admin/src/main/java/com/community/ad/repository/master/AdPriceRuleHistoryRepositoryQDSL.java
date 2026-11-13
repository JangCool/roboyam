package com.community.ad.repository.master;

import com.community.bizapplication.entity.BizPriceRuleHistoryEntity;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizPriceRuleHistoryEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 요금표 변경 이력 조회(V208 {@code biz_price_rule_history}) — 최신순(시각, 같으면 id 큰 쪽 먼저), 페이징은 DB 에서. 바꾼 운영자 이름은 왼쪽 조인으로 함께 읽는다(광고 노출 자리 이력과 같다).
 */
@Repository
public class AdPriceRuleHistoryRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QBizPriceRuleHistoryEntity HISTORY = QBizPriceRuleHistoryEntity.bizPriceRuleHistoryEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    /** 이력 한 줄 + 바꾼 운영자의 지금 이름(지워졌으면 null) */
    public record Row(BizPriceRuleHistoryEntity history, String operatorName) {
    }

    public AdPriceRuleHistoryRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    public List<Row> page(int page, int size) {
        return rows(query.select(HISTORY, OPERATOR.operatorName).from(HISTORY).leftJoin(OPERATOR).on(OPERATOR.id.eq(HISTORY.operatorId))
                .orderBy(HISTORY.changedAt.desc(), HISTORY.id.desc()).offset((long) page * size).limit(size).fetch());
    }

    public long count() {
        Long total = query.select(HISTORY.count()).from(HISTORY).fetchOne();
        return total == null ? 0 : total;
    }

    /** 그 상품의 지금 값이 바로 바뀐 최근 줄(받기 켬/끔) — {@code ix_bprh_product} */
    public List<Row> recent(BizProduct product, int limit) {
        return rows(query.select(HISTORY, OPERATOR.operatorName).from(HISTORY).leftJoin(OPERATOR).on(OPERATOR.id.eq(HISTORY.operatorId))
                .where(HISTORY.product.eq(product)).orderBy(HISTORY.changedAt.desc(), HISTORY.id.desc()).limit(limit).fetch());
    }

    /** 어느 상품이든 받기 켬/끔이 마지막으로 바뀐 줄 */
    public Optional<Row> latestRuleChange() {
        return rows(query.select(HISTORY, OPERATOR.operatorName).from(HISTORY).leftJoin(OPERATOR).on(OPERATOR.id.eq(HISTORY.operatorId))
                .where(HISTORY.product.isNotNull()).orderBy(HISTORY.changedAt.desc(), HISTORY.id.desc()).limit(1).fetch()).stream().findFirst();
    }

    /** 운영자 이름(지워졌으면 빠진다) */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(OPERATOR.id, OPERATOR.operatorName).from(OPERATOR).where(OPERATOR.id.in(ids)).fetch()) {
            out.put(t.get(OPERATOR.id), t.get(OPERATOR.operatorName));
        }
        return out;
    }

    private static List<Row> rows(List<Tuple> tuples) {
        return tuples.stream().map(t -> new Row(t.get(HISTORY), t.get(OPERATOR.operatorName))).toList();
    }
}
