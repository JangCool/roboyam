package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonPriceEventEntity;
import com.community.emoticon.entity.QEmoticonPriceEventEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 가격 전환 기록({@code emoticon_price_events}, V143 — 추가만). 운영자 가격 바꾸기(사유) · 새 판 승인으로 바뀐 가격(심사 건, 사유 없음).
 * QueryDSL 이다({@link EmoticonStoreSupport}) — Native SQL 로 남긴 것은 없다.
 */
@Repository
public class EmoticonPriceEventStore extends EmoticonStoreSupport {

    /** 가격 전환 한 줄 — 처리한 운영자 이름(지워졌으면 null) */
    public record PriceEventRow(long id, long packId, int fromPrice, int toPrice, String reason, Long reviewId, Instant changedAt, Long changedBy, String changedByName) {
    }

    private static final QEmoticonPriceEventEntity E = QEmoticonPriceEventEntity.emoticonPriceEventEntity;
    private static final QEmoticonPriceEventEntity E2 = new QEmoticonPriceEventEntity("e2");
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    public EmoticonPriceEventStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public void insert(long id, long packId, int fromPrice, int toPrice, String reason, Long reviewId, Instant at, long operatorId) {
        insert(EmoticonPriceEventEntity.create(id, packId, fromPrice, toPrice, reason, reviewId, at, operatorId));
    }

    private JPAQuery<Tuple> rows() {
        return query.select(E.id, E.packId, E.fromPrice, E.toPrice, E.reason, E.reviewId, E.changedAt, E.changedBy, O.operatorName).from(E).leftJoin(O)
                .on(O.id.eq(E.changedBy));
    }

    /** 그 팩의 기록 — 최근이 앞 */
    public List<PriceEventRow> byPack(long packId) {
        return rows().where(E.packId.eq(packId)).orderBy(E.changedAt.desc(), E.id.desc()).fetch().stream().map(EmoticonPriceEventStore::rowOf)
                .collect(Collectors.toList());
    }

    /**
     * 팩마다 가장 최근 기록 — (바꾼 시각, ID) 가 가장 큰 줄: 더 늦은 줄이 없는 줄(예전 {@code DISTINCT ON … ORDER BY changed_at DESC, id DESC} 와 같은 줄).
     */
    public Map<Long, PriceEventRow> latest(Collection<Long> packIds) {
        Map<Long, PriceEventRow> out = new HashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        for (Tuple t : rows().where(E.packId.in(packIds), JPAExpressions.selectOne().from(E2)
                .where(E2.packId.eq(E.packId), E2.changedAt.gt(E.changedAt).or(E2.changedAt.eq(E.changedAt).and(E2.id.gt(E.id)))).notExists()).fetch()) {
            PriceEventRow row = rowOf(t);
            out.put(row.packId(), row);
        }
        return out;
    }

    private static PriceEventRow rowOf(Tuple t) {
        return new PriceEventRow(t.get(E.id), t.get(E.packId), t.get(E.fromPrice), t.get(E.toPrice), t.get(E.reason), t.get(E.reviewId), t.get(E.changedAt),
                t.get(E.changedBy), t.get(O.operatorName));
    }
}
