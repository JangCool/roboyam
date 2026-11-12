package com.community.home.repository.master;

import com.community.home.entity.HomeLayoutEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 테마별 홈 배치({@code home_layouts}) — 키는 테마 id */
public interface HomeLayoutRepository extends IrobitRepository<HomeLayoutEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HomeLayoutEntity> findWithLockByTheme(String theme);
}
