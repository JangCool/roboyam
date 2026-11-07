package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuySettingsEntity;
import com.community.groupbuy.entity.QGroupBuySettingsEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 공구 설정 화면({@code GroupBuySettingsService})의 읽기·저장 — {@code group_buy_settings} 1행. 숫자 칸은 열 이름으로 고른다(서비스의 칸 목록이 정본). 서비스 안에 있던 SQL 을 옮겼다.
 * 모두 QueryDSL({@link GroupBuyStoreSupport}) — 저장 전 행 잠금은 그 행만 고르는 {@code PESSIMISTIC_WRITE}.
 */
@Repository
public class GroupBuySettingsStore extends GroupBuyStoreSupport {

    private static final QGroupBuySettingsEntity S = QGroupBuySettingsEntity.groupBuySettingsEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    /** 저장된 값 한 벌 — {@code numbers} 열 이름 → 값 */
    public record Stored(Map<String, Long> numbers, Instant updatedAt, String updatedBy) {
    }

    public GroupBuySettingsStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 열 이름 → 칸(정수 칸·큰 정수 칸) */
    private static NumberPath<? extends Number> column(String column) {
        return switch (column) {
            case "hold_minutes" -> S.holdMinutes;
            case "demand_max_days" -> S.demandMaxDays;
            case "first_come_max_days" -> S.firstComeMaxDays;
            case "extension_minutes" -> S.extensionMinutes;
            case "extension_window_minutes" -> S.extensionWindowMinutes;
            case "judge_grace_minutes" -> S.judgeGraceMinutes;
            case "compensation_cap" -> S.compensationCap;
            case "large_refund_threshold" -> S.largeRefundThreshold;
            case "address_reveal_minutes" -> S.addressRevealMinutes;
            case "expired_address_retention_days" -> S.expiredAddressRetentionDays;
            case "ship_within_business_days" -> S.shipWithinBusinessDays;
            case "supply_choice_business_days" -> S.supplyChoiceBusinessDays;
            default -> throw new IllegalArgumentException("공구 설정에 없는 칸: " + column);
        };
    }

    /** 그 열들의 값(차례 그대로) + 마지막 저장 시각·운영자 이름 */
    public Stored load(List<String> columns) {
        List<Expression<?>> select = new ArrayList<>();
        columns.forEach(c -> select.add(column(c)));
        select.add(S.updatedAt);
        select.add(O.operatorName);
        Tuple t = query.select(select.toArray(Expression[]::new)).from(S).leftJoin(O).on(O.id.eq(S.updatedBy)).where(S.id.eq(GroupBuySettingsEntity.ROW_ID)).fetchFirst();
        if (t == null) {
            throw new EmptyResultDataAccessException(1); // 설정 행은 마이그레이션이 넣는다
        }
        Map<String, Long> numbers = new LinkedHashMap<>();
        for (int k = 0; k < columns.size(); k++) {
            Number value = (Number) t.get(k, Object.class);
            numbers.put(columns.get(k), value == null ? 0L : value.longValue());
        }
        return new Stored(numbers, t.get(S.updatedAt), t.get(O.operatorName));
    }

    /** 설정 행을 잠근다(저장끼리 줄 선다) */
    public void lock() {
        if (!query.select(S.id).from(S).where(S.id.eq(GroupBuySettingsEntity.ROW_ID)).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).fetch().iterator()
                .hasNext()) {
            throw new EmptyResultDataAccessException(1);
        }
    }

    /** 그 열들을 통째로 저장한다(열 이름 → 값) */
    @SuppressWarnings("unchecked")
    public void save(Map<String, Long> values, long operatorId, Instant now) {
        JPAUpdateClause update = query.update(S);
        values.forEach((columnName, value) -> {
            NumberPath<? extends Number> path = column(columnName);
            if (path.getType() == Integer.class) {
                update.set((NumberPath<Integer>) path, Math.toIntExact(value));
            } else {
                update.set((NumberPath<Long>) path, value);
            }
        });
        update.set(S.updatedBy, operatorId).set(S.updatedAt, now).where(S.id.eq(GroupBuySettingsEntity.ROW_ID)).execute();
    }
}
