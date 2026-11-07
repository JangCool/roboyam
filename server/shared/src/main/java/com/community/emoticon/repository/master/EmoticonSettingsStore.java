package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonSettingsEntity;
import com.community.emoticon.entity.QEmoticonPackEntity;
import com.community.emoticon.entity.QEmoticonSettingsEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 설정 행({@code emoticon_settings}, V142 — 한 행 · 유료 칸 V143) 읽기·쓰기. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이다({@link EmoticonStoreSupport}) — 행 잠금은 설정 표만 고르는 문장: 저장 전 {@code PESSIMISTIC_WRITE}(PostgreSQL {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다),
 * 가격을 정하기 전 {@code PESSIMISTIC_READ}({@code FOR SHARE}) — 둘은 서로 막는다. 가격 단계 배열({@code integer[]})은 엔티티의 배열 칸으로 쓴다. Native SQL 로 남긴 것은 없다.
 */
@Repository
public class EmoticonSettingsStore extends EmoticonStoreSupport {

    /** 설정 한 행(마지막으로 저장한 운영자 이름 포함 — 없으면 null). 가격 단계는 오름차순 */
    public record SettingsRow(int rewardPerAcquire, int rewardDailyCap, int itemMin, int itemMax, int pendingLimit, boolean paidSalesEnabled, int feeRatePercent,
                              int settlementDay, int payoutMinimum, int refundDays, List<Integer> priceTiers, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    /** 저장할 값 */
    public record SettingsValues(int rewardPerAcquire, int rewardDailyCap, int itemMin, int itemMax, int pendingLimit, boolean paidSalesEnabled, int feeRatePercent,
                                 int settlementDay, int payoutMinimum, int refundDays, List<Integer> priceTiers) {
    }

    private static final QEmoticonSettingsEntity S = QEmoticonSettingsEntity.emoticonSettingsEntity;
    private static final QEmoticonPackEntity P = QEmoticonPackEntity.emoticonPackEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    public EmoticonSettingsStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 설정 행 — 없으면 예전처럼 {@link EmptyResultDataAccessException}(행은 마이그레이션이 넣는다) */
    public SettingsRow read() {
        Tuple t = query.select(S.rewardPerAcquire, S.rewardDailyCap, S.itemMin, S.itemMax, S.pendingLimit, S.paidSalesEnabled, S.feeRatePercent, S.settlementDay,
                        S.payoutMinimum, S.refundDays, S.priceTiers, S.updatedAt, S.updatedBy, O.operatorName).from(S).leftJoin(O).on(O.id.eq(S.updatedBy))
                .where(S.id.eq(EmoticonSettingsEntity.ROW_ID)).fetchFirst();
        if (t == null) {
            throw new EmptyResultDataAccessException(1);
        }
        Integer[] tiers = t.get(S.priceTiers);
        return new SettingsRow(t.get(S.rewardPerAcquire), t.get(S.rewardDailyCap), t.get(S.itemMin), t.get(S.itemMax), t.get(S.pendingLimit), t.get(S.paidSalesEnabled),
                t.get(S.feeRatePercent), t.get(S.settlementDay), t.get(S.payoutMinimum), t.get(S.refundDays), tiers == null ? List.of() : List.copyOf(Arrays.asList(tiers)),
                t.get(S.updatedAt), t.get(S.updatedBy), t.get(O.operatorName));
    }

    /** 저장하기 전에 — 행을 잠근다(같은 설정의 동시 저장이 전→후 감사를 어긋나게 하지 않게, 가격을 정하는 처리와 줄을 서게) */
    public SettingsRow readForUpdate() {
        query.select(S.id).from(S).where(S.id.eq(EmoticonSettingsEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst();
        return read();
    }

    /**
     * 가격을 정하기 전에(승인·가격 바꾸기) — 행을 나눔 잠금으로 잡는다. 설정 저장(쓰기 잠금)이 그동안 가격 단계·유료 판매 켜기를 바꾸지 못해, 읽은 단계로 정한 가격이
     * 커밋 때도 열린 단계다(설정 저장이 먼저면 그 커밋 뒤의 값을 읽는다).
     */
    public SettingsRow readShared() {
        query.select(S.id).from(S).where(S.id.eq(EmoticonSettingsEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_READ).fetchFirst();
        return read();
    }

    public void save(SettingsValues v, long operatorId, Instant now) {
        query.update(S).set(S.rewardPerAcquire, v.rewardPerAcquire()).set(S.rewardDailyCap, v.rewardDailyCap()).set(S.itemMin, v.itemMin()).set(S.itemMax, v.itemMax())
                .set(S.pendingLimit, v.pendingLimit()).set(S.paidSalesEnabled, v.paidSalesEnabled()).set(S.feeRatePercent, v.feeRatePercent())
                .set(S.settlementDay, v.settlementDay()).set(S.payoutMinimum, v.payoutMinimum()).set(S.refundDays, v.refundDays())
                .set(S.priceTiers, v.priceTiers().toArray(Integer[]::new)).set(S.updatedAt, now).set(S.updatedBy, operatorId)
                .where(S.id.eq(EmoticonSettingsEntity.ROW_ID)).execute();
    }

    /** 상점에 나온 팩(판매 중 · 판매 중단)이 쓰는 유료 가격 — 설정에서 뺄 수 없는 단계(오름차순) */
    public List<Integer> tiersInUse() {
        return query.select(P.price).distinct().from(P).where(EmoticonPackStore.published(P), P.price.gt(0)).orderBy(P.price.asc()).fetch();
    }
}
