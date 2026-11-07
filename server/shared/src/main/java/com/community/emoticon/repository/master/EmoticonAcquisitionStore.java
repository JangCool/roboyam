package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonAcquisitionEntity;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.QEmoticonAcquisitionEntity;
import com.community.emoticon.entity.QEmoticonPackEntity;
import com.community.emoticon.entity.QEmoticonPurchaseEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 보유({@code emoticon_acquisitions}, V142 · V144) — (회원, 팩) 하나에 줄 하나. 출처 {@code kind} = {@code FREE}(무료 받기) · {@code PURCHASE}(유료 구매 — {@code purchase_id}).
 * 빼거나 환불하면 {@code removed_at} 만 찍고 줄은 남긴다(다시 가지면 지운다) — 받기 보상·받은 수는 그 줄의 처음 한 번만이다. 토큰 검사·내 로보티콘·팩의 {@code acquired} 는
 * 출처와 상관없이 이 표의 「지금 가진 줄」을 본다. QueryDSL 이다({@link EmoticonStoreSupport}) — Native SQL 로 남긴 것은 없다.
 *
 * <p><b>작가 자기 팩(후속 결정 1, 2026-11-05)</b>: 작가는 상점에 나온(판매 중·판매 중단) 자기 팩을 사거나 받지 않아도 가진 것으로 본다 — <b>파생 규칙</b>
 * ({@code emoticon_packs.creator_id = 나 AND status IN ('APPROVED','SUSPENDED')}, {@link #creatorOwned})이고 받기 줄을 만들지 않는다. 그래서 받은 수·받기 보상·판매·정산에 들어가지 않고,
 * 팩 주인이 바뀌거나(새 주인이 곧바로 가진다) 판매 중단돼도(가진 사람처럼 계속 쓴다) 따로 맞출 줄이 없다. 「가졌는가」를 묻는 곳({@link #ownedAmong} · {@link #owned} ·
 * {@link #creatorOwns})이 모두 이 규칙을 같이 쓴다 — 토큰 검사·내 로보티콘·팩의 {@code acquired}·결제 준비·결제 확정.
 */
@Repository
public class EmoticonAcquisitionStore extends EmoticonStoreSupport {

    public static final String FREE = "FREE";
    public static final String PURCHASE = "PURCHASE";
    /** 보상 이벤트로 받은 줄(V163 — reward_result_id = 그 줄 결과). 작가 받기 보상·받은 수 규칙은 무료 받기와 같다(받은 수는 줄마다 처음 한 번, 보상 포인트는 없다) */
    public static final String EVENT = "EVENT";

    /** 보유 한 줄 — {@code removedAt} 이 있으면 지금은 가지지 않았다(목록에서 뺐다 · 환불했다) */
    public record AcquisitionRow(long id, long userId, long packId, String kind, Long purchaseId, Instant firstAcquiredAt, Instant acquiredAt, Instant removedAt) {

        public boolean owned() {
            return removedAt == null;
        }

        public boolean purchased() {
            return PURCHASE.equals(kind);
        }
    }

    /** 지금 가진 팩 한 개 — 산 팩이면 구매 ID·결제 금액, 내가 만든 팩이면 {@code creator}(가진 시각 = 상점에 처음 나온 시각) */
    public record OwnedPack(long packId, Instant acquiredAt, Long purchaseId, int purchaseAmount, boolean creator) {
    }

    private static final QEmoticonAcquisitionEntity A = QEmoticonAcquisitionEntity.emoticonAcquisitionEntity;
    private static final QEmoticonPackEntity K = new QEmoticonPackEntity("k");
    private static final QEmoticonPurchaseEntity PU = QEmoticonPurchaseEntity.emoticonPurchaseEntity;

    public EmoticonAcquisitionStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 작가 자기 팩의 파생 규칙(별칭 {@code k} = 팩) — 상점에 나온 팩(판매 중·판매 중단)의 작가 */
    public static BooleanExpression creatorOwned(QEmoticonPackEntity k) {
        return EmoticonPackStore.published(k);
    }

    public Optional<AcquisitionRow> find(long userId, long packId) {
        Tuple t = query.select(A.id, A.userId, A.packId, A.kind, A.purchaseId, A.firstAcquiredAt, A.acquiredAt, A.removedAt).from(A)
                .where(A.userId.eq(userId), A.packId.eq(packId)).fetchFirst();
        return Optional.ofNullable(t).map(r -> new AcquisitionRow(r.get(A.id), r.get(A.userId), r.get(A.packId), r.get(A.kind), r.get(A.purchaseId),
                r.get(A.firstAcquiredAt), r.get(A.acquiredAt), r.get(A.removedAt)));
    }

    /** 무료 받기 — 새 줄 */
    public void insert(long id, long userId, long packId, Instant now) {
        insert(EmoticonAcquisitionEntity.create(id, userId, packId, FREE, null, now));
    }

    /** 유료 구매 — 새 줄 */
    public void insertPurchase(long id, long userId, long packId, long purchaseId, Instant now) {
        insert(EmoticonAcquisitionEntity.create(id, userId, packId, PURCHASE, purchaseId, now));
    }

    /** 환불해 보유를 뺐던 산 줄을 새 구매로 다시 가진다 */
    public boolean repurchase(long id, long purchaseId, Instant now) {
        return query.update(A).set(A.purchaseId, purchaseId).set(A.acquiredAt, now).setNull(A.removedAt)
                .where(A.id.eq(id), A.kind.eq(PURCHASE), A.removedAt.isNotNull()).execute() == 1;
    }

    /** 뺀 무료 팩을 다시 받는다(받은 시각만 새로). 이벤트로 받았다가 빼거나 회수된 줄(V163)은 무료 받기 줄로 돌아간다(그 줄 결과와의 이음은 지운다) */
    public void restore(long id, Instant now) {
        query.update(A).set(A.kind, FREE).setNull(A.rewardResultId).set(A.acquiredAt, now).setNull(A.removedAt)
                .where(A.id.eq(id), A.kind.in(FREE, EVENT), A.removedAt.isNotNull()).execute();
    }

    /** 환불했던 산 줄 — 그 팩이 무료가 된 뒤 받으면 무료 받기로 다시 가진다(받은 수·보상은 줄마다 처음 한 번이라 새로 생기지 않는다) */
    public void restoreAsFree(long id, Instant now) {
        query.update(A).set(A.kind, FREE).setNull(A.purchaseId).set(A.acquiredAt, now).setNull(A.removedAt)
                .where(A.id.eq(id), A.kind.eq(PURCHASE), A.removedAt.isNotNull()).execute();
    }

    /** 무료로(또는 보상 이벤트로) 받은 팩을 목록에서 뺀다 — 가지고 있을 때만(바뀐 줄 수). 산 팩은 빼지 않는다(환불은 구매 내역) */
    public int remove(long userId, long packId, Instant now) {
        return (int) query.update(A).set(A.removedAt, now).where(A.userId.eq(userId), A.packId.eq(packId), A.kind.in(FREE, EVENT), A.removedAt.isNull()).execute();
    }

    /** 보상 이벤트 지급 결과 — 새 줄 · 빼거나 환불한 줄을 이벤트 줄로 다시 가짐 · 이미 가짐(아무것도 바꾸지 않았다) */
    public enum EventGrant { NEW, REACQUIRED, ALREADY_OWNED }

    /**
     * 보상 이벤트로 팩을 준다(V163 — event-admin-contract 「보상 줄」 EMOTICON_PACK). 없으면 새 줄(종류 EVENT, 받은 수 + 1), 빼거나 환불해 지금 가지지 않은 줄이면 그 줄을 이벤트 줄로
     * 다시 가진다(받은 수는 줄마다 처음 한 번이라 그대로), 이미 가졌으면 그대로. 작가 받기 보상 포인트는 주지 않는다(부르는 쪽이 부르지 않는다). 작가 자기 팩은 부르는 쪽이 먼저 본다.
     *
     * <p>Native SQL(JDBC)인 이유: 보상 줄 처리는 줄마다 JDBC 세이브포인트 안에서 돈다 — 그 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용이 된다(repository.md).
     * 넣기는 {@code INSERT … ON CONFLICT (user_id, pack_id) DO NOTHING} — 같은 회원의 동시 받기와 겹쳐도 실패 대신 0 줄이다.
     */
    public EventGrant grantFromEvent(long id, long userId, long packId, long resultId, Instant now) {
        int inserted = jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, purchase_id, reward_result_id, first_acquired_at, acquired_at) "
                + "VALUES (?, ?, ?, 'EVENT', NULL, ?, ?, ?) ON CONFLICT (user_id, pack_id) DO NOTHING", id, userId, packId, resultId, ts(now), ts(now));
        if (inserted == 1) {
            jdbc.update("UPDATE emoticon_packs SET acquire_count = acquire_count + 1 WHERE id = ?", packId);
            return EventGrant.NEW;
        }
        int again = jdbc.update("UPDATE emoticon_acquisitions SET kind = 'EVENT', purchase_id = NULL, reward_result_id = ?, acquired_at = ?, removed_at = NULL "
                + "WHERE user_id = ? AND pack_id = ? AND removed_at IS NOT NULL", resultId, ts(now), userId, packId);
        return again == 1 ? EventGrant.REACQUIRED : EventGrant.ALREADY_OWNED;
    }

    /** 보상 이벤트 회수 — 그 줄 결과로 가진 이벤트 줄의 보유를 뺀다(바뀐 줄 수 — 회원이 이미 뺐거나 다른 길로 바뀐 줄이면 0). JDBC 인 이유는 {@link #grantFromEvent} 와 같다 */
    public int revokeEventGrant(long resultId, Instant now) {
        return jdbc.update("UPDATE emoticon_acquisitions SET removed_at = ? WHERE reward_result_id = ? AND kind = 'EVENT' AND removed_at IS NULL", ts(now), resultId);
    }

    /** 환불 — 그 구매로 가진 줄의 보유를 뺀다(바뀐 줄 수) */
    public int revokePurchase(long purchaseId, Instant now) {
        return (int) query.update(A).set(A.removedAt, now).where(A.purchaseId.eq(purchaseId), A.kind.eq(PURCHASE), A.removedAt.isNull()).execute();
    }

    /**
     * 이 팩들 중 회원이 지금 가진 것(받기·구매 줄 + 내가 만든 상점 팩). 예전 {@code UNION} 한 문장과 같이 한 문장 — 팩 줄에서 「받기 줄이 있다 또는 내가 만든 상점 팩」을 고른다
     * (받기 줄은 팩 줄을 외래키로 가리켜 같은 결과다. 두 질의로 나누면 그 사이 팩 상태가 바뀔 때 가진 팩이 빠질 수 있다 — 글 쓰기의 이모티콘 검사도 이것을 부른다).
     */
    public Set<Long> ownedAmong(long userId, Collection<Long> packIds) {
        if (packIds.isEmpty()) {
            return Set.of();
        }
        BooleanExpression acquired = JPAExpressions.selectOne().from(A).where(A.packId.eq(K.id), A.userId.eq(userId), A.removedAt.isNull()).exists();
        return new HashSet<>(query.select(K.id).from(K).where(K.id.in(packIds), acquired.or(K.creatorId.eq(userId).and(creatorOwned(K)))).fetch());
    }

    /**
     * 이 팩들 중 판매 중(APPROVED)인 것 — 운영자 회원 계정이 가지지 않아도 쓰는 팩(운영자 회원 계정 연결 2026-11-05). 가진 것으로 치지 않으므로 {@link #ownedAmong} 에 넣지 않는다
     * (팩의 {@code acquired}·내 로보티콘·받은 수·판매에 들어가지 않는다).
     */
    public Set<Long> onSaleAmong(Collection<Long> packIds) {
        if (packIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(K.id).from(K).where(K.status.eq(EmoticonPackStatus.APPROVED), K.id.in(packIds)).fetch());
    }

    /** 회원이 상점에 나온 그 팩의 작가인가(작가 자기 팩 — 사지 않아도 가진다) */
    public boolean creatorOwns(long userId, long packId) {
        return query.selectOne().from(K).where(K.id.eq(packId), K.creatorId.eq(userId), creatorOwned(K)).fetchFirst() != null;
    }

    /**
     * 회원이 지금 가진 팩(최근 가진 것 먼저) — 산 팩이면 구매 ID·결제 금액. 내가 만든 상점 팩은 받기 줄이 없어도 들어가고({@code creator}, 가진 시각 = 처음 나온 시각),
     * 같은 팩에 받기 줄이 남아 있어도(규칙 전 개발 데이터) 한 번만 — 작가 쪽으로 센다.
     * 받기 줄과 작가 팩을 두 질의로 읽어 합친다 — 차례는 예전 {@code UNION ALL … ORDER BY at DESC, tie DESC} 와 같다(가진 시각 ↓, 받기 줄 ID · 팩 ID ↓). 두 쪽의 팩은 겹치지 않는다.
     */
    public Map<Long, OwnedPack> owned(long userId) {
        record Owned(OwnedPack pack, long tie) {
        }
        List<Owned> rows = new ArrayList<>();
        for (Tuple t : query.select(A.packId, A.acquiredAt, A.purchaseId, PU.amount, A.id).from(A).leftJoin(PU).on(PU.id.eq(A.purchaseId))
                .where(A.userId.eq(userId), A.removedAt.isNull(),
                        JPAExpressions.selectOne().from(K).where(K.id.eq(A.packId), K.creatorId.eq(A.userId), creatorOwned(K)).notExists())
                .fetch()) {
            Integer amount = t.get(PU.amount);
            rows.add(new Owned(new OwnedPack(t.get(A.packId), t.get(A.acquiredAt), t.get(A.purchaseId), amount == null ? 0 : amount, false), t.get(A.id)));
        }
        for (Tuple t : query.select(K.id, K.publishedAt, K.createdAt).from(K).where(K.creatorId.eq(userId), creatorOwned(K)).fetch()) {
            Instant at = t.get(K.publishedAt) != null ? t.get(K.publishedAt) : t.get(K.createdAt);
            rows.add(new Owned(new OwnedPack(t.get(K.id), at, null, 0, true), t.get(K.id)));
        }
        rows.sort(Comparator.comparing((Owned o) -> o.pack().acquiredAt()).reversed().thenComparing(Comparator.comparingLong(Owned::tie).reversed()));
        Map<Long, OwnedPack> out = new LinkedHashMap<>();
        rows.forEach(o -> out.put(o.pack().packId(), o.pack()));
        return out;
    }
}
