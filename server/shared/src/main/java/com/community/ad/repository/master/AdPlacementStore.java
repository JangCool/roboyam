package com.community.ad.repository.master;

import com.community.ad.dto.AdPlacements;
import com.community.ad.entity.AdPlacementSettingEntity;
import com.community.ad.entity.QAdPlacementSettingEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 광고 노출 자리 설정(V118 {@code ad_placement_settings} — 한 행, id = 1 · V119 {@code adult_enabled} 기본 끔). 행이 없으면 {@link AdPlacements#DEFAULTS}.
 * 저장은 「있으면 고치기(QueryDSL), 없으면 넣기(네이티브 {@code ON CONFLICT} — 처음 저장의 동시 경합)」(부르는 쪽이 같은 트랜잭션에서 {@link #lock} 으로 행을 잠근 뒤 부른다).
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
        if (!RowLocks.forUpdate(entityManager, AdPlacementSettingEntity.class, ID)) {
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
        if (update(p, screens, stopFeeRate, withRate, operatorId, now) > 0) {
            return;
        }
        // 처음 저장 — 행이 없으면 잠글 것도 없어 두 운영자가 동시에 처음 저장할 수 있다. 넣기가 지면(다른 쪽이 먼저 넣음) 그 행을 고친다
        if (insertIfAbsent(p, screens, stopFeeRate, operatorId, now) == 0) {
            update(p, screens, stopFeeRate, withRate, operatorId, now);
        }
    }

    private long update(AdPlacements p, String[] screens, int stopFeeRate, boolean withRate, long operatorId, Instant now) {
        var update = query.update(S).set(S.postInlineEnabled, p.postInline().enabled()).set(S.postInlineType, p.postInline().adType())
                .set(S.postInlinePosition, p.postInline().position()).set(S.wingLeft, p.wings().left()).set(S.wingRight, p.wings().right())
                .set(S.wingScreens, screens).set(S.boardListEnabled, p.boardList().enabled()).set(S.boardListAfter, p.boardList().after())
                .set(S.boardListCount, p.boardList().count()).set(S.boardListGap, p.boardList().gap()).set(S.adultEnabled, p.adult().enabled())
                .set(S.updatedAt, now).set(S.updatedBy, operatorId);
        if (withRate) {
            update.set(S.stopFeeRate, stopFeeRate);
        }
        return update.where(S.id.eq(ID)).execute();
    }

    /**
     * 한 행(id = 1)을 처음 넣는다 — 이미 있으면 아무것도 하지 않는다(넣은 줄 수).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING} 은 JPQL·QueryDSL 에 없다. JPA 로 넣으면 동시에 처음 저장한 쪽이 유일 위반으로
     * 트랜잭션째 깨진다(PostgreSQL 은 위반 한 번에 트랜잭션을 못 쓰게 한다). 날개 화면 배열은 {@code string_to_array} 로 넘긴다(화면 코드에 쉼표가 없다).
     */
    private int insertIfAbsent(AdPlacements p, String[] screens, int stopFeeRate, long operatorId, Instant now) {
        return entityManager.createNativeQuery("INSERT INTO ad_placement_settings (id, post_inline_enabled, post_inline_type, post_inline_position, wing_left, "
                        + "wing_right, wing_screens, board_list_enabled, board_list_after, board_list_count, board_list_gap, adult_enabled, stop_fee_rate, "
                        + "updated_at, updated_by) VALUES (?1, ?2, ?3, ?4, ?5, ?6, string_to_array(?7, ','), ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15) "
                        + "ON CONFLICT (id) DO NOTHING")
                .setParameter(1, ID).setParameter(2, p.postInline().enabled()).setParameter(3, p.postInline().adType())
                .setParameter(4, p.postInline().position()).setParameter(5, p.wings().left()).setParameter(6, p.wings().right())
                .setParameter(7, String.join(",", screens)).setParameter(8, p.boardList().enabled()).setParameter(9, p.boardList().after())
                .setParameter(10, p.boardList().count()).setParameter(11, p.boardList().gap()).setParameter(12, p.adult().enabled())
                .setParameter(13, stopFeeRate).setParameter(14, now).setParameter(15, operatorId).executeUpdate();
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
