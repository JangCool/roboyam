package com.community.ad.repository.master;

import com.community.ad.dto.AdPlacements;
import com.community.ad.entity.AdPlacementSettingEntity;
import com.community.ad.entity.QAdPlacementSettingEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 광고 노출 자리 설정(V118 {@code ad_placement_settings} — 한 행, id = 1 · V119 {@code adult_enabled} 기본 끔). 행이 없으면 {@link AdPlacements#DEFAULTS}.
 * QueryDSL 만 쓴다 — 저장은 「있으면 고치기, 없으면 넣기」(부르는 쪽이 같은 트랜잭션에서 {@link #lock} 으로 행을 잠근 뒤 부른다).
 */
@Repository
public class AdPlacementStore extends RoboyamRepositoryQDSL {

    /** 중도 해지 수수료율 기본값(%, V127 — 행이 없을 때) */
    public static final int DEFAULT_STOP_FEE_RATE = 10;
    public static final int STOP_FEE_RATE_MIN = 0;
    public static final int STOP_FEE_RATE_MAX = 30;

    private static final QAdPlacementSettingEntity S = QAdPlacementSettingEntity.adPlacementSettingEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final int ID = AdPlacementSettingEntity.SINGLE_ID;

    /**
     * 저장한 설정 + 마지막으로 고친 때·사람(운영자 이름은 지워졌으면 null) + 중도 해지 수수료율(V127 {@code stop_fee_rate} — 광고·사업자 핫딜 공통, 신청 건을 낼 때 그 건에 고정한다).
     * 수수료율은 공개 자리 설정({@link AdPlacements})에 싣지 않는다(공개는 {@code GET /api/business/refund-policy}).
     */
    public record Stored(AdPlacements value, Instant updatedAt, long updatedBy, String updatedByName, int stopFeeRate) {
    }

    private static final Expression<?>[] COLUMNS = {S.postInlineEnabled, S.postInlineType, S.postInlinePosition, S.wingLeft, S.wingRight, S.wingScreens,
            S.boardListEnabled, S.boardListAfter, S.boardListCount, S.boardListGap, S.adultEnabled, S.updatedAt, S.updatedBy, S.stopFeeRate};

    private final EntityManager entityManager;

    public AdPlacementStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public Optional<Stored> find() {
        Expression<?>[] withName = Arrays.copyOf(COLUMNS, COLUMNS.length + 1);
        withName[COLUMNS.length] = O.operatorName;
        Tuple t = query.select(withName).from(S).leftJoin(O).on(O.id.eq(S.updatedBy)).where(S.id.eq(ID)).fetchFirst();
        return Optional.ofNullable(t).map(r -> stored(r, r.get(O.operatorName)));
    }

    /** 저장 트랜잭션 안에서 행을 잠그고 읽는다(없으면 비어 있다) — 운영자 이름은 읽지 않는다(null) */
    public Optional<Stored> lock() {
        if (query.select(S.id).from(S).where(S.id.eq(ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(query.select(COLUMNS).from(S).where(S.id.eq(ID)).fetchFirst()).map(r -> stored(r, null));
    }

    /** 지금 설정 — 저장한 적 없으면 기본값 */
    public AdPlacements current() {
        return find().map(Stored::value).orElse(AdPlacements.DEFAULTS);
    }

    /** 지금 중도 해지 수수료율(%) — 저장한 적 없으면 기본 10 */
    public int stopFeeRate() {
        Integer rate = query.select(S.stopFeeRate).from(S).where(S.id.eq(ID)).fetchFirst();
        return rate == null ? DEFAULT_STOP_FEE_RATE : rate;
    }

    /** 설정 + 중도 해지 수수료율을 함께 저장한다(관리자 「광고 기본 설정」) */
    public void save(AdPlacements p, int stopFeeRate, long operatorId, Instant now) {
        save(p, stopFeeRate, true, operatorId, now);
    }

    /** 자리 설정만 저장한다 — 수수료율은 그대로(처음 저장이면 기본 10) */
    public void save(AdPlacements p, long operatorId, Instant now) {
        save(p, DEFAULT_STOP_FEE_RATE, false, operatorId, now);
    }

    private void save(AdPlacements p, int stopFeeRate, boolean withRate, long operatorId, Instant now) {
        String[] screens = p.wings().screens().toArray(String[]::new);
        var update = query.update(S).set(S.postInlineEnabled, p.postInline().enabled()).set(S.postInlineType, p.postInline().adType())
                .set(S.postInlinePosition, p.postInline().position()).set(S.wingLeft, p.wings().left()).set(S.wingRight, p.wings().right())
                .set(S.wingScreens, screens).set(S.boardListEnabled, p.boardList().enabled()).set(S.boardListAfter, p.boardList().after())
                .set(S.boardListCount, p.boardList().count()).set(S.boardListGap, p.boardList().gap()).set(S.adultEnabled, p.adult().enabled())
                .set(S.updatedAt, now).set(S.updatedBy, operatorId);
        if (withRate) {
            update.set(S.stopFeeRate, stopFeeRate);
        }
        if (update.where(S.id.eq(ID)).execute() == 0) {
            JpaStoreWrites.insert(entityManager, AdPlacementSettingEntity.first(p.postInline().enabled(), p.postInline().adType(), p.postInline().position(),
                    p.wings().left(), p.wings().right(), screens, p.boardList().enabled(), p.boardList().after(), p.boardList().count(), p.boardList().gap(),
                    p.adult().enabled(), stopFeeRate, operatorId, now));
        }
    }

    private static Stored stored(Tuple t, String updatedByName) {
        String[] screens = t.get(S.wingScreens);
        List<String> list = screens == null ? List.of() : Arrays.stream(screens).filter(s -> s != null && !s.isEmpty()).toList();
        AdPlacements value = new AdPlacements(new AdPlacements.PostInline(Boolean.TRUE.equals(t.get(S.postInlineEnabled)), t.get(S.postInlineType),
                t.get(S.postInlinePosition)), new AdPlacements.Wings(Boolean.TRUE.equals(t.get(S.wingLeft)), Boolean.TRUE.equals(t.get(S.wingRight)), list),
                new AdPlacements.BoardList(Boolean.TRUE.equals(t.get(S.boardListEnabled)), n(t.get(S.boardListAfter)), n(t.get(S.boardListCount)),
                        n(t.get(S.boardListGap))), new AdPlacements.Adult(Boolean.TRUE.equals(t.get(S.adultEnabled))));
        Long by = t.get(S.updatedBy);
        return new Stored(value, t.get(S.updatedAt), by == null ? 0 : by, updatedByName, n(t.get(S.stopFeeRate)));
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
