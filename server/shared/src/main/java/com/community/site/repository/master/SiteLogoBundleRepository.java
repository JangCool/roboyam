package com.community.site.repository.master;

import com.community.site.entity.SiteLogoBundleEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 로고 묶음({@code site_logo_bundles}, V249) */
public interface SiteLogoBundleRepository extends IrobitRepository<SiteLogoBundleEntity, Long> {

    /** 보관함 차례 — 기본 제공(1·2) 먼저, 그다음 올린 차례(앱이 매긴 ID 는 시간순이다) */
    List<SiteLogoBundleEntity> findAllByOrderByIdAsc();

    /** 그날의 그 벌 묶음(기념일 로고의 짝 찾기 — V251) */
    Optional<SiteLogoBundleEntity> findFirstByBuiltInKeyAndDayKey(String builtInKey, String dayKey);

    /** 행을 쓰기 잠금으로 읽는다 — 같은 묶음을 동시에 고치는 요청을 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SiteLogoBundleEntity> findWithLockById(Long id);
}
