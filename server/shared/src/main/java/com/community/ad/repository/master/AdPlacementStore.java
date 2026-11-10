package com.community.ad.repository.master;

import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.AdSenseSettings;
import com.community.ad.entity.AdPlacementSettingEntity;
import com.community.ad.entity.QAdPlacementSettingEntity;
import com.community.operator.entity.QOperatorEntity;
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
 * 광고 노출 자리 설정(V118 {@code ad_placement_settings} — 한 행, id = 1 · V119 {@code adult_enabled} 기본 끔 · V176 구글 애드센스 {@code adsense_*} 기본 끔). 행이 없으면
 * {@link AdPlacements#DEFAULTS}·{@link AdSenseSettings#DEFAULTS}.
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
     * 저장한 설정 + 마지막으로 고친 때·사람(운영자 이름은 지워졌으면 null) + 중도 해지 수수료율(V127 {@code stop_fee_rate} — 광고·사업자 핫딜 공통, 신청 건을 낼 때 그 건에 고정한다)
     * + 구글 애드센스(V176). 수수료율은 공개 자리 설정({@link AdPlacements})에 싣지 않는다(공개는 {@code GET /api/business/refund-policy}).
     */
    public record Stored(AdPlacements value, Instant updatedAt, long updatedBy, String updatedByName, int stopFeeRate, AdSenseSettings adsense) {
    }

    private static final Expression<?>[] ADSENSE_COLUMNS = {S.adsenseMode, S.adsenseClient, S.adsenseAdtest, S.adsenseWideBanner, S.adsenseStripBanner,
            S.adsenseSquareBanner, S.adsenseSmallBanner, S.adsenseWingBanner, S.adsensePostInline, S.adsenseBoardListSlot, S.adsenseBoardListLayoutKey};

    private static final Expression<?>[] COLUMNS = concat(new Expression<?>[]{S.postInlineEnabled, S.postInlineType, S.postInlinePosition, S.wingLeft, S.wingRight,
            S.wingScreens, S.boardListEnabled, S.boardListAfter, S.boardListCount, S.boardListGap, S.adultEnabled, S.updatedAt, S.updatedBy, S.stopFeeRate},
            ADSENSE_COLUMNS);

    private final EntityManager entityManager;

    public AdPlacementStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public Optional<Stored> find() {
        Expression<?>[] withName = concat(COLUMNS, new Expression<?>[]{O.operatorName});
        Tuple t = query.select(withName).from(S).leftJoin(O).on(O.id.eq(S.updatedBy)).where(S.id.eq(ID)).fetchFirst();
        return Optional.ofNullable(t).map(r -> stored(r, r.get(O.operatorName)));
    }

    /**
     * 저장 트랜잭션 안에서 행을 잠그고 읽는다(없으면 비어 있다) — 운영자 이름은 읽지 않는다(null). 잠금은 설정 표만 고르는 id 문장({@code PESSIMISTIC_WRITE} =
     * {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다), 칸은 그 뒤 읽는다.
     */
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

    /** 지금 구글 애드센스 설정(V176) — 저장한 적 없으면 끔. 사본 없이 그때 읽는다(index.html 머리·{@code /ads.txt} 가 다음 요청부터 따라오게) */
    public AdSenseSettings adsense() {
        Tuple t = query.select(ADSENSE_COLUMNS).from(S).where(S.id.eq(ID)).fetchFirst();
        return t == null ? AdSenseSettings.DEFAULTS : adsense(t);
    }

    /** 지금 중도 해지 수수료율(%) — 저장한 적 없으면 기본 10 */
    public int stopFeeRate() {
        Integer rate = query.select(S.stopFeeRate).from(S).where(S.id.eq(ID)).fetchFirst();
        return rate == null ? DEFAULT_STOP_FEE_RATE : rate;
    }

    /** 설정 + 중도 해지 수수료율 + 구글 애드센스를 함께 저장한다(관리자 「광고 기본 설정」) */
    public void save(AdPlacements p, int stopFeeRate, AdSenseSettings adsense, long operatorId, Instant now) {
        save(p, stopFeeRate, true, adsense, operatorId, now);
    }

    /** 설정 + 중도 해지 수수료율을 함께 저장한다 — 애드센스는 그대로(처음 저장이면 끔) */
    public void save(AdPlacements p, int stopFeeRate, long operatorId, Instant now) {
        save(p, stopFeeRate, true, null, operatorId, now);
    }

    /** 자리 설정만 저장한다 — 수수료율·애드센스는 그대로(처음 저장이면 기본 10 · 끔) */
    public void save(AdPlacements p, long operatorId, Instant now) {
        save(p, DEFAULT_STOP_FEE_RATE, false, null, operatorId, now);
    }

    /** @param adsense null 이면 애드센스 칸은 그대로(처음 넣을 때는 끔) */
    private void save(AdPlacements p, int stopFeeRate, boolean withRate, AdSenseSettings adsense, long operatorId, Instant now) {
        String[] screens = p.wings().screens().toArray(String[]::new);
        if (update(p, screens, stopFeeRate, withRate, adsense, operatorId, now) > 0) {
            return;
        }
        // 처음 저장 — 행이 없으면 잠글 것도 없어 두 운영자가 동시에 처음 저장할 수 있다. 넣기가 지면(다른 쪽이 먼저 넣음) 그 행을 고친다
        if (insertIfAbsent(p, screens, stopFeeRate, adsense == null ? AdSenseSettings.DEFAULTS : adsense, operatorId, now) == 0) {
            update(p, screens, stopFeeRate, withRate, adsense, operatorId, now);
        }
    }

    private long update(AdPlacements p, String[] screens, int stopFeeRate, boolean withRate, AdSenseSettings adsense, long operatorId, Instant now) {
        var update = query.update(S).set(S.postInlineEnabled, p.postInline().enabled()).set(S.postInlineType, p.postInline().adType())
                .set(S.postInlinePosition, p.postInline().position()).set(S.wingLeft, p.wings().left()).set(S.wingRight, p.wings().right())
                .set(S.wingScreens, screens).set(S.boardListEnabled, p.boardList().enabled()).set(S.boardListAfter, p.boardList().after())
                .set(S.boardListCount, p.boardList().count()).set(S.boardListGap, p.boardList().gap()).set(S.adultEnabled, p.adult().enabled())
                .set(S.updatedAt, now).set(S.updatedBy, operatorId);
        if (withRate) {
            update.set(S.stopFeeRate, stopFeeRate);
        }
        if (adsense != null) {
            AdSenseSettings.Units u = adsense.units();
            update.set(S.adsenseMode, adsense.mode()).set(S.adsenseClient, adsense.client()).set(S.adsenseAdtest, adsense.adtest())
                    .set(S.adsenseWideBanner, u.wideBanner()).set(S.adsenseStripBanner, u.stripBanner()).set(S.adsenseSquareBanner, u.squareBanner())
                    .set(S.adsenseSmallBanner, u.smallBanner()).set(S.adsenseWingBanner, u.wingBanner()).set(S.adsensePostInline, u.postInline())
                    .set(S.adsenseBoardListSlot, u.boardList().slot()).set(S.adsenseBoardListLayoutKey, u.boardList().layoutKey());
        }
        return update.where(S.id.eq(ID)).execute();
    }

    /**
     * 한 행(id = 1)을 처음 넣는다 — 이미 있으면 아무것도 하지 않는다(넣은 줄 수).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING} 은 JPQL·QueryDSL 에 없다. JPA 로 넣으면 동시에 처음 저장한 쪽이 유일 위반으로
     * 트랜잭션째 깨진다(PostgreSQL 은 위반 한 번에 트랜잭션을 못 쓰게 한다). 날개 화면 배열은 {@code string_to_array} 로 넘긴다(화면 코드에 쉼표가 없다).
     * 비어 있을 수 있는 애드센스 글자 칸은 {@code CAST(… AS varchar)} 로 형을 알려 준다(형을 모르는 null 매개변수).
     */
    private int insertIfAbsent(AdPlacements p, String[] screens, int stopFeeRate, AdSenseSettings adsense, long operatorId, Instant now) {
        AdSenseSettings.Units u = adsense.units();
        return entityManager.createNativeQuery("INSERT INTO ad_placement_settings (id, post_inline_enabled, post_inline_type, post_inline_position, wing_left, "
                        + "wing_right, wing_screens, board_list_enabled, board_list_after, board_list_count, board_list_gap, adult_enabled, stop_fee_rate, "
                        + "updated_at, updated_by, adsense_mode, adsense_client, adsense_adtest, adsense_wide_banner, adsense_strip_banner, adsense_square_banner, "
                        + "adsense_small_banner, adsense_wing_banner, adsense_post_inline, adsense_board_list_slot, adsense_board_list_layout_key) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, string_to_array(?7, ','), ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, CAST(?17 AS varchar), ?18, "
                        + "CAST(?19 AS varchar), CAST(?20 AS varchar), CAST(?21 AS varchar), CAST(?22 AS varchar), CAST(?23 AS varchar), CAST(?24 AS varchar), "
                        + "CAST(?25 AS varchar), CAST(?26 AS varchar)) ON CONFLICT (id) DO NOTHING")
                .setParameter(1, ID).setParameter(2, p.postInline().enabled()).setParameter(3, p.postInline().adType())
                .setParameter(4, p.postInline().position()).setParameter(5, p.wings().left()).setParameter(6, p.wings().right())
                .setParameter(7, String.join(",", screens)).setParameter(8, p.boardList().enabled()).setParameter(9, p.boardList().after())
                .setParameter(10, p.boardList().count()).setParameter(11, p.boardList().gap()).setParameter(12, p.adult().enabled())
                .setParameter(13, stopFeeRate).setParameter(14, now).setParameter(15, operatorId)
                .setParameter(16, adsense.mode()).setParameter(17, adsense.client()).setParameter(18, adsense.adtest())
                .setParameter(19, u.wideBanner()).setParameter(20, u.stripBanner()).setParameter(21, u.squareBanner()).setParameter(22, u.smallBanner())
                .setParameter(23, u.wingBanner()).setParameter(24, u.postInline()).setParameter(25, u.boardList().slot())
                .setParameter(26, u.boardList().layoutKey()).executeUpdate();
    }

    private static Stored stored(Tuple t, String updatedByName) {
        String[] screens = t.get(S.wingScreens);
        List<String> list = screens == null ? List.of() : Arrays.stream(screens).filter(s -> s != null && !s.isEmpty()).toList();
        AdPlacements value = new AdPlacements(new AdPlacements.PostInline(Boolean.TRUE.equals(t.get(S.postInlineEnabled)), t.get(S.postInlineType),
                t.get(S.postInlinePosition)), new AdPlacements.Wings(Boolean.TRUE.equals(t.get(S.wingLeft)), Boolean.TRUE.equals(t.get(S.wingRight)), list),
                new AdPlacements.BoardList(Boolean.TRUE.equals(t.get(S.boardListEnabled)), n(t.get(S.boardListAfter)), n(t.get(S.boardListCount)),
                        n(t.get(S.boardListGap))), new AdPlacements.Adult(Boolean.TRUE.equals(t.get(S.adultEnabled))));
        Long by = t.get(S.updatedBy);
        return new Stored(value, t.get(S.updatedAt), by == null ? 0 : by, updatedByName, n(t.get(S.stopFeeRate)), adsense(t));
    }

    private static AdSenseSettings adsense(Tuple t) {
        String mode = t.get(S.adsenseMode);
        return new AdSenseSettings(mode == null ? AdSenseSettings.OFF : mode, t.get(S.adsenseClient), Boolean.TRUE.equals(t.get(S.adsenseAdtest)),
                new AdSenseSettings.Units(t.get(S.adsenseWideBanner), t.get(S.adsenseStripBanner), t.get(S.adsenseSquareBanner), t.get(S.adsenseSmallBanner),
                        t.get(S.adsenseWingBanner), t.get(S.adsensePostInline),
                        new AdSenseSettings.InFeed(t.get(S.adsenseBoardListSlot), t.get(S.adsenseBoardListLayoutKey))));
    }

    private static Expression<?>[] concat(Expression<?>[] a, Expression<?>[] b) {
        Expression<?>[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
