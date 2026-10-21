package com.community.profile.service;

import jakarta.persistence.EntityManager;

/**
 * 닉네임(운영자 이름) 변경을 서버 여러 대 사이에서 한 번에 하나씩만 하게 하는 DB 어드바이저리 잠금(트랜잭션이 끝나면 풀린다).
 * 이름은 운영자마다 다른 행에 있어 행 잠금으로는 「같은 이름으로 동시에 바꾸는 두 요청」을 줄 세울 수 없다. 닉네임 변경은 드물어 전역 잠금이어도 부담이 없다.
 * 호출하는 쪽은 트랜잭션 안이어야 한다. 운영자 이름을 정하는 다른 입구(초대 수락)도 같은 키로 줄 선다.
 */
public final class ProfileNicknameLock {

    /** 임의의 고정 키 — 이 앱의 다른 어드바이저리 잠금(7_340_001 기동 맞춤, 7_340_002 배치)과 겹치지 않게 정한 값 */
    private static final long KEY = 7_340_003L;

    private ProfileNicknameLock() {
    }

    public static void acquire(EntityManager entityManager) {
        // Native SQL 인 이유: PostgreSQL 의 pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다
        // pg_advisory_xact_lock 은 void 를 돌려주므로 바깥 select 로 감싸 값 하나로 읽는다
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1)) as acquired").setParameter(1, KEY).getSingleResult();
    }
}
