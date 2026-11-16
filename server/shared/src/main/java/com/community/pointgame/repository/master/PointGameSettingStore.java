package com.community.pointgame.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.pointgame.entity.PointGameSettingEntity;
import com.community.pointgame.entity.QPointGameSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 포인트 게임 설정 행({@code point_game_settings}, V234) 읽기·잠그기·고치기 — {@code PointGameSettingsService} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class PointGameSettingStore extends IrobitRepositoryQDSL {

    /** 설정 한 줄 + 마지막으로 저장한 운영자 이름(없거나 지워졌으면 null) */
    public record SettingsRow(String providerName, String checkUrl, Instant updatedAt, String updatedByName) {
    }

    /** 잠근 뒤 지금 값(전→후 감사용) */
    public record Current(String providerName, String checkUrl) {
    }

    private static final QPointGameSettingEntity S = QPointGameSettingEntity.pointGameSettingEntity;
    private static final QOperatorEntity SAVED = new QOperatorEntity("pointGameSettingUpdatedBy");

    private final EntityManager entityManager;

    public PointGameSettingStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 지금 설정(행이 없으면 처음 값) */
    public SettingsRow settings() {
        Tuple t = query.select(S.lotteryProviderName, S.lotteryCheckUrl, S.updatedAt, SAVED.operatorName).from(S).leftJoin(SAVED).on(SAVED.id.eq(S.updatedBy))
                .where(S.id.eq(PointGameSettingEntity.ROW_ID)).fetchOne();
        return t == null ? new SettingsRow(PointGameSettingEntity.DEFAULT_PROVIDER_NAME, PointGameSettingEntity.DEFAULT_CHECK_URL, null, null)
                : new SettingsRow(t.get(S.lotteryProviderName), t.get(S.lotteryCheckUrl), t.get(S.updatedAt), t.get(SAVED.operatorName));
    }

    /**
     * 설정 행을 잠근 뒤 지금 값(같은 화면의 동시 저장이 전→후 감사를 어긋나게 하지 않게).
     *
     * <p>행 만들기가 Native SQL 인 이유: {@code INSERT … ON CONFLICT (id) DO NOTHING} — 「없으면 만든다」를 동시 저장이 겹쳐도 한 문장으로(upsert 는 JPQL·QueryDSL 에 없다).
     * 잠금은 설정 표만 고르는 한 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다).
     */
    public Current lock() {
        entityManager.createNativeQuery("INSERT INTO point_game_settings (id) VALUES (?1) ON CONFLICT (id) DO NOTHING").setParameter(1, PointGameSettingEntity.ROW_ID)
                .executeUpdate();
        Tuple t = query.select(S.lotteryProviderName, S.lotteryCheckUrl).from(S).where(S.id.eq(PointGameSettingEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return new Current(t.get(S.lotteryProviderName), t.get(S.lotteryCheckUrl));
    }

    public void update(String providerName, String checkUrl, long operatorId, Instant now) {
        query.update(S).set(S.lotteryProviderName, providerName).set(S.lotteryCheckUrl, checkUrl).set(S.updatedBy, operatorId).set(S.updatedAt, now)
                .where(S.id.eq(PointGameSettingEntity.ROW_ID)).execute();
    }
}
