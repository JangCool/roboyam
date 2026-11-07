package com.community.operator.repository.master;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * 운영자의 <b>식별 값(이메일·이름)을 정하는 모든 입구</b>를 서버 여러 대 사이에서 한 번에 하나씩만 지나가게 하는 DB 어드바이저리 잠금(트랜잭션이 끝나면 풀린다).
 * 규칙·입구·유일 인덱스는 {@code OperatorIdentityLock}(서비스 쪽 설명과 이름 위반 판정)에 있다.
 *
 * <p><b>잠금 순서</b>: 이 잠금을 <b>먼저</b> 잡은 뒤 행 잠금(운영자·초대 행)을 잡는다 — 거꾸로 하면 교착이 날 수 있다. <b>부르는 서비스의 트랜잭션 안에서</b> 부른다.
 */
@Repository
public class OperatorIdentityLockQueries {

    /** 임의의 고정 키 — 이 앱의 다른 어드바이저리 잠금(7_340_001 기동 맞춤, 7_340_002 배치, 7_340_004 비밀번호 재설정 요청 한도, 7_340_005 소모임 분류)과 겹치지 않게 정한 값 */
    private static final long KEY = 7_340_003L;

    private final EntityManager entityManager;

    public OperatorIdentityLockQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 식별 값 잠금을 잡는다(트랜잭션 끝까지) */
    public void acquire() {
        // Native SQL 인 이유: PostgreSQL 의 pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다
        // pg_advisory_xact_lock 은 void 를 돌려주므로 바깥 select 로 감싸 값 하나로 읽는다
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1)) as acquired").setParameter(1, KEY).getSingleResult();
    }
}
