package com.community.permission.repository.master;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * 기동 때 카탈로그를 맞추고 내장 역할을 넣는 일을 서버 여러 대 사이에서 한 번에 한 대씩만 하게 하는 DB 어드바이저리 잠금(트랜잭션이 끝나면 풀린다).
 * 부르는 쪽: 권한·알림·배치 카탈로그 맞춤({@code *CatalogSynchronizer}), 내장 역할 넣기({@code BuiltinRoleSeeder}). <b>부르는 서비스의 트랜잭션 안에서</b> 부른다.
 */
@Repository
public class BootstrapLockQueries {

    /** 임의의 고정 키 — 이 앱의 다른 어드바이저리 잠금과 겹치지 않게 정한 값 */
    private static final long KEY = 7_340_001L;

    private final EntityManager entityManager;

    public BootstrapLockQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 기동 잠금을 잡는다(트랜잭션 끝까지) */
    public void acquire() {
        // Native SQL 인 이유: PostgreSQL 의 pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다
        // pg_advisory_xact_lock 은 void 를 돌려주므로 바깥 select 로 감싸 값 하나로 읽는다
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1)) as acquired").setParameter(1, KEY).getSingleResult();
    }
}
