package com.community.zconfiguration.jpa;

import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * QueryDSL 로 질의를 짜는 리포지토리의 공통 바탕.
 *
 * <p>어느 데이터소스의 질의 팩토리를 쓸지 여기서 한 번만 정하고 리포지토리는 그것을 물려받는다.
 * 조회용 slave 가 붙으면 이 클래스 옆에 slave 용 바탕을 하나 더 두면 된다.
 *
 * <p>{@code JpaRepository} 를 상속하는 인터페이스와는 같은 도메인에서 나란히 쓴다.
 */
public abstract class RoboyamRepositoryQDSL {

    /**
     * DB 의 현재 시각. 값이 아니라 식(expression)이라 SQL 안에 그대로 들어가 DB 가 찍는다.
     *
     * <p>{@code current_timestamp} 를 쓰지 않는 이유: Hibernate 가 그것을 {@code localtimestamp}
     * (타임존 없는 타입)로 번역해 {@code timestamptz} 컬럼과 세션 타임존에 따라 값이 달라진다.
     * {@code offset_datetime} 은 타임존을 포함한 현재 시각으로 정의되어 PostgreSQL 의
     * {@code current_timestamp}(= {@code now()})로 내려간다.
     */
    protected static final DateTimeExpression<ZonedDateTime> DB_NOW =
            Expressions.dateTimeTemplate(ZonedDateTime.class, "offset_datetime");

    /** 질의 팩토리. 마스터 것을 이름으로 받는다 ({@link JpaMasterConfig#QUERY_FACTORY}). */
    protected final JPAQueryFactory query;

    protected RoboyamRepositoryQDSL(@Qualifier(JpaMasterConfig.QUERY_FACTORY) JPAQueryFactory query) {
        this.query = query;
    }
}
