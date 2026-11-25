package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.CommerceTrackingSettingsEntity;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QCommerceCarrierEntity;
import com.community.groupbuy.entity.QCommerceTrackingSettingsEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyShipmentEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 배송 추적(계약 결정 90) — 배송 조회 설정({@code commerce_tracking_settings} 1행)과 발송 줄({@code group_buy_shipments})의 추적 칸. 모두 QueryDSL({@link GroupBuyStoreSupport}).
 *
 * <ul>
 *   <li>집기({@link #claim})는 그 발송 줄만 고르는 {@code SKIP LOCKED} 잠금(배치 — 차례 조건 포함) 또는 기다리는 잠금([지금 조회]) → 하루 조회 수 올리기 + 다음 조회 시각을 임대로 미루기 →
 *       읽기(같은 짧은 트랜잭션). 조회 호출은 트랜잭션 밖이고 결과는 다시 짧은 트랜잭션에서 적는다(에스크로 대기열과 같은 모양).</li>
 *   <li>잠금 세기: PESSIMISTIC_WRITE = {@code FOR NO KEY UPDATE}. 발송 줄을 FK 로 가리키는 것은 에스크로 대기열뿐이고 그 넣기는 발송·송장 정정 흐름이 주문 행을 잠근 뒤 한다 —
 *       KEY SHARE 를 막지 않아도 결과가 같다.</li>
 *   <li>키 오류 알림 상태({@link #raiseAlert})는 조건부 UPDATE — 같은 코드가 이미 적혀 있으면 이기지 못한다(같은 오류 반복 알림 막기).</li>
 * </ul>
 */
@Repository
public class ParcelTrackingStore extends GroupBuyStoreSupport {

    private static final QCommerceTrackingSettingsEntity T = QCommerceTrackingSettingsEntity.commerceTrackingSettingsEntity;
    private static final QGroupBuyShipmentEntity SH = QGroupBuyShipmentEntity.groupBuyShipmentEntity;
    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QCommerceCarrierEntity CA = QCommerceCarrierEntity.commerceCarrierEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QOperatorEntity KO = new QOperatorEntity("keyOperator");

    /** 설정 한 벌 — {@code apiKeyCipher} 는 서비스만 본다(응답·로그에 싣지 않는다) */
    public record SettingsRow(boolean enabled, int intervalMinutes, boolean autoDeliverEnabled, int autoDeliverDays, String apiKeyCipher, Instant keyUpdatedAt,
                              String keyUpdatedBy, String alertCode, Instant alertAt, Instant updatedAt, String updatedBy) {

        @Override
        public String toString() {
            return "SettingsRow[enabled=" + enabled + ", intervalMinutes=" + intervalMinutes + ", autoDeliverEnabled=" + autoDeliverEnabled + ", autoDeliverDays="
                    + autoDeliverDays + ", apiKey=" + (apiKeyCipher == null ? "none" : "set") + "]";
        }
    }

    /** 발송 줄의 추적 칸 + 조회에 필요한 값(택배사의 스마트택배 코드·주문 상태) */
    public record TrackingRow(long shipmentId, long participationId, String kind, String carrierCode, String sweetTrackerCode, String trackingNo, Instant shippedAt,
                              Instant deliveredAt, ParticipationStatus orderStatus, Integer level, boolean complete, String eventsJson, Instant checkedAt, Instant nextAt,
                              int failCount, String errorCode, LocalDate day, int dayCount, Instant completedAt, String holdReason) {
    }

    /** 발송 뒤 N일 자동 배송완료 대상 */
    public record ShippedOrder(long participationId, Instant shippedAt) {
    }

    private static final ConstructorExpression<TrackingRow> TRACKING = Projections.constructor(TrackingRow.class, SH.id, SH.participationId, SH.kind, SH.carrierCode,
            CA.sweettrackerCode, SH.trackingNo, SH.shippedAt, SH.deliveredAt, PA.status, SH.trackingLevel, SH.trackingComplete, SH.trackingEvents, SH.trackingCheckedAt,
            SH.trackingNextAt, SH.trackingFailCount, SH.trackingErrorCode, SH.trackingDay, SH.trackingDayCount, SH.trackingCompletedAt, SH.trackingHoldReason);

    public ParcelTrackingStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    public SettingsRow settings() {
        Tuple t = query.select(T.enabled, T.intervalMinutes, T.autoDeliverEnabled, T.autoDeliverDays, T.apiKeyCipher, T.keyUpdatedAt, KO.operatorName, T.alertCode, T.alertAt,
                        T.updatedAt, O.operatorName)
                .from(T).leftJoin(O).on(O.id.eq(T.updatedBy)).leftJoin(KO).on(KO.id.eq(T.keyUpdatedBy)).where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID)).fetchFirst();
        if (t == null) {
            throw new EmptyResultDataAccessException(1); // 설정 행은 마이그레이션이 넣는다
        }
        return new SettingsRow(Boolean.TRUE.equals(t.get(T.enabled)), t.get(T.intervalMinutes), Boolean.TRUE.equals(t.get(T.autoDeliverEnabled)), t.get(T.autoDeliverDays),
                t.get(T.apiKeyCipher), t.get(T.keyUpdatedAt), t.get(KO.operatorName), t.get(T.alertCode), t.get(T.alertAt), t.get(T.updatedAt), t.get(O.operatorName));
    }

    /** 설정 행을 잠근다(저장끼리 줄 선다 — 이 표를 FK 로 가리키는 표가 없다) */
    public void lockSettings() {
        if (query.select(T.id).from(T).where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() == null) {
            throw new EmptyResultDataAccessException(1);
        }
    }

    public void saveSettings(boolean enabled, int intervalMinutes, boolean autoDeliverEnabled, int autoDeliverDays, long operatorId, Instant now) {
        query.update(T).set(T.enabled, enabled).set(T.intervalMinutes, intervalMinutes).set(T.autoDeliverEnabled, autoDeliverEnabled).set(T.autoDeliverDays, autoDeliverDays)
                .set(T.updatedAt, now).set(T.updatedBy, operatorId).where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID)).execute();
    }

    /** API 키 암호문 저장 — 키 오류 알림 상태도 지운다(새 키) */
    public void saveKey(String cipher, long operatorId, Instant now) {
        query.update(T).set(T.apiKeyCipher, cipher).set(T.keyUpdatedAt, now).set(T.keyUpdatedBy, operatorId).setNull(T.alertCode).setNull(T.alertAt)
                .where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID)).execute();
    }

    /** API 키 지우기 — 지운 것이 있으면 true */
    public boolean clearKey(long operatorId, Instant now) {
        return query.update(T).setNull(T.apiKeyCipher).set(T.keyUpdatedAt, now).set(T.keyUpdatedBy, operatorId).setNull(T.alertCode).setNull(T.alertAt)
                .where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID), T.apiKeyCipher.isNotNull()).execute() == 1;
    }

    /** 키 오류를 적는다 — 같은 코드가 이미 적혀 있으면 false(알리지 않는다) */
    public boolean raiseAlert(String code, Instant now) {
        return query.update(T).set(T.alertCode, code).set(T.alertAt, now)
                .where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID), T.alertCode.isNull().or(T.alertCode.ne(code))).execute() == 1;
    }

    /** 키로 조회가 다시 됐다 — 알림 상태를 지운다(다음 오류를 다시 알린다) */
    public void clearAlert() {
        query.update(T).setNull(T.alertCode).setNull(T.alertAt).where(T.id.eq(CommerceTrackingSettingsEntity.ROW_ID), T.alertCode.isNotNull()).execute();
    }

    // ── 발송 줄 추적 ────────────────────────────────────────────────────────

    /** 자동 조회 대상인 발송 줄 조건 — 배송완료 전·추적 완료 전·택배사에 스마트택배 코드·처음 발송은 주문 SHIPPED, 교환 재발송은 주문 DELIVERED(교환은 배송완료 뒤에 한다) */
    private static BooleanExpression trackable() {
        return SH.deliveredAt.isNull().and(SH.trackingComplete.isFalse()).and(CA.sweettrackerCode.isNotNull())
                .and(SH.kind.eq("ORIGINAL").and(PA.status.eq(ParticipationStatus.SHIPPED)).or(SH.kind.eq("EXCHANGE").and(PA.status.eq(ParticipationStatus.DELIVERED))));
    }

    /** 차례가 된 자동 조회 대상(다음 조회 시각 차례, 아직 한 번도 안 본 줄 먼저) — {@code shippedAfter} 보다 뒤에 발송한 것만 */
    public List<Long> due(Instant now, Instant shippedAfter, int limit) {
        return query.select(SH.id).from(SH).join(PA).on(PA.id.eq(SH.participationId)).join(CA).on(CA.code.eq(SH.carrierCode))
                .where(trackable(), SH.shippedAt.gt(shippedAfter), SH.trackingNextAt.isNull().or(SH.trackingNextAt.loe(now)))
                .orderBy(SH.trackingNextAt.asc().nullsFirst(), SH.id.asc()).limit(limit).fetch();
    }

    public Optional<TrackingRow> find(long shipmentId) {
        return Optional.ofNullable(query.select(TRACKING).from(SH).join(PA).on(PA.id.eq(SH.participationId)).leftJoin(CA).on(CA.code.eq(SH.carrierCode))
                .where(SH.id.eq(shipmentId)).fetchFirst());
    }

    /** 그 주문의 발송 줄 추적 칸(발송 차례) */
    public List<TrackingRow> ofParticipation(long participationId) {
        return query.select(TRACKING).from(SH).join(PA).on(PA.id.eq(SH.participationId)).leftJoin(CA).on(CA.code.eq(SH.carrierCode))
                .where(SH.participationId.eq(participationId)).orderBy(SH.shippedAt.asc(), SH.id.asc()).fetch();
    }

    /** 그 주문에서 지금 조회할 수 있는 발송 줄([지금 조회] — 차례 조건 없이) */
    public List<Long> trackableOf(long participationId) {
        return query.select(SH.id).from(SH).join(PA).on(PA.id.eq(SH.participationId)).join(CA).on(CA.code.eq(SH.carrierCode))
                .where(SH.participationId.eq(participationId), SH.deliveredAt.isNull(), CA.sweettrackerCode.isNotNull(),
                        SH.kind.eq("ORIGINAL").and(PA.status.eq(ParticipationStatus.SHIPPED)).or(SH.kind.eq("EXCHANGE").and(PA.status.eq(ParticipationStatus.DELIVERED))))
                .orderBy(SH.shippedAt.asc(), SH.id.asc()).fetch();
    }

    /** 집기 결과 — {@code LIMIT} 하루 조회 수를 다 썼다, {@code BUSY} 다른 서버가 잡았거나 차례가 아니다 */
    public enum Claim {
        CLAIMED, LIMIT, BUSY
    }

    public record Claimed(Claim claim, TrackingRow row) {
    }

    /**
     * 집는다 — 배치({@code due=true})는 그 줄만 {@code SKIP LOCKED} 로 잠그고(차례 조건 포함), [지금 조회]는 기다려 잠근다. 오늘(KST) 조회 수가 {@code dailyLimit} 이면
     * 다음 조회를 내일 0시로 미루고 {@code LIMIT}. 아니면 조회 수를 하나 올리고 다음 조회를 {@code leaseUntil} 로 미룬다(서버 여럿이 겹쳐 부르지 않게).
     */
    public Claimed claim(long shipmentId, boolean due, Instant now, LocalDate today, Instant tomorrow, Instant leaseUntil, int dailyLimit) {
        var lock = query.select(SH.id).from(SH).where(SH.id.eq(shipmentId), SH.deliveredAt.isNull()).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        if (due) {
            lock.where(SH.trackingComplete.isFalse(), SH.trackingNextAt.isNull().or(SH.trackingNextAt.loe(now))).setHint("jakarta.persistence.lock.timeout", -2);
        }
        if (lock.fetchFirst() == null) {
            return new Claimed(Claim.BUSY, null);
        }
        TrackingRow row = find(shipmentId).orElseThrow();
        int used = today.equals(row.day()) ? row.dayCount() : 0;
        if (used >= dailyLimit) {
            query.update(SH).set(SH.trackingNextAt, tomorrow).where(SH.id.eq(shipmentId)).execute();
            return new Claimed(Claim.LIMIT, row);
        }
        query.update(SH).set(SH.trackingDay, today).set(SH.trackingDayCount, used + 1).set(SH.trackingNextAt, leaseUntil).where(SH.id.eq(shipmentId)).execute();
        return new Claimed(Claim.CLAIMED, find(shipmentId).orElseThrow());
    }

    /** 조회됨 — 단계·완료·이력·조회 시각·다음 조회·실패 횟수(스캔 없음이면 이어진 횟수)·택배사 완료 시각·자동 완료를 하지 않은 사유 */
    public void recordResult(long shipmentId, int level, boolean complete, String eventsJson, Instant checkedAt, Instant nextAt, int failCount, String errorCode,
                             Instant completedAt, String holdReason) {
        query.update(SH).set(SH.trackingLevel, level).set(SH.trackingComplete, complete).set(SH.trackingEvents, eventsJson).set(SH.trackingCheckedAt, checkedAt)
                .set(SH.trackingNextAt, nextAt).set(SH.trackingFailCount, failCount).set(SH.trackingErrorCode, errorCode).set(SH.trackingCompletedAt, completedAt)
                .set(SH.trackingHoldReason, holdReason).set(SH.updatedAt, checkedAt).where(SH.id.eq(shipmentId)).execute();
    }

    /** 실패 — 단계·이력은 그대로, 조회 시각·다음 조회·실패 횟수·오류 코드 */
    public void recordFailure(long shipmentId, Instant checkedAt, Instant nextAt, int failCount, String errorCode) {
        query.update(SH).set(SH.trackingCheckedAt, checkedAt).set(SH.trackingNextAt, nextAt).set(SH.trackingFailCount, failCount).set(SH.trackingErrorCode, errorCode)
                .where(SH.id.eq(shipmentId)).execute();
    }

    /** 하루 제한(105) — 오늘 조회 수를 다 쓴 것으로, 다음 조회는 내일 */
    public void recordDailyLimit(long shipmentId, Instant checkedAt, LocalDate today, int dailyLimit, Instant tomorrow) {
        query.update(SH).set(SH.trackingCheckedAt, checkedAt).set(SH.trackingDay, today).set(SH.trackingDayCount, dailyLimit).set(SH.trackingNextAt, tomorrow)
                .set(SH.trackingErrorCode, "105").where(SH.id.eq(shipmentId)).execute();
    }

    /** 키 문제로 멈춤 — 실패 횟수는 그대로, 다음 조회만 */
    public void release(long shipmentId, Instant nextAt, String errorCode) {
        query.update(SH).set(SH.trackingNextAt, nextAt).set(SH.trackingErrorCode, errorCode).where(SH.id.eq(shipmentId)).execute();
    }

    /** 교환 재발송 줄의 배송완료 시각(주문 상태는 그대로 — 교환은 배송완료 뒤) — 바꿨으면 true */
    public boolean markExchangeDelivered(long shipmentId, Instant deliveredAt, Instant now) {
        return query.update(SH).set(SH.deliveredAt, deliveredAt).set(SH.updatedAt, now).where(SH.id.eq(shipmentId), SH.kind.eq("EXCHANGE"), SH.deliveredAt.isNull())
                .execute() == 1;
    }

    /** 발송 뒤 N일 자동 배송완료 대상 — {@code shippedBefore} 전에 발송한 SHIPPED 주문(발송 차례) */
    public List<ShippedOrder> shippedBefore(Instant shippedBefore, int limit) {
        return query.select(Projections.constructor(ShippedOrder.class, PA.id, PA.shippedAt)).from(PA)
                .where(PA.status.eq(ParticipationStatus.SHIPPED), PA.shippedAt.lt(shippedBefore)).orderBy(PA.shippedAt.asc(), PA.id.asc()).limit(limit).fetch();
    }
}
