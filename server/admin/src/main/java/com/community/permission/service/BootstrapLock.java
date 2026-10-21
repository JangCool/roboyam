package com.community.permission.service;

import jakarta.persistence.EntityManager;

/**
 * 기동 때 카탈로그를 맞추고 내장 역할을 넣는 일을 서버 여러 대 사이에서 한 번에 한 대씩만 하게 하는 DB 어드바이저리 잠금(트랜잭션이 끝나면 풀린다).
 * 호출하는 쪽은 트랜잭션 안이어야 한다.
 */
public final class BootstrapLock {

    /** 임의의 고정 키 — 이 앱의 다른 어드바이저리 잠금과 겹치지 않게 정한 값 */
    private static final long KEY = 7_340_001L;

    private BootstrapLock() {
    }

    public static void acquire(EntityManager entityManager) {
        // Native SQL 인 이유: PostgreSQL 의 pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다
        // pg_advisory_xact_lock 은 void 를 돌려주므로 바깥 select 로 감싸 값 하나로 읽는다
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1)) as acquired").setParameter(1, KEY).getSingleResult();
    }
}
