package com.community.title.repository.master;

import com.community.title.entity.TitleSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 작가 칭호 설정({@code title_settings}, 행 하나) */
public interface TitleSettingRepository extends IrobitRepository<TitleSettingEntity, Integer> {

    /** 저장 — 행을 잠그고 읽는다(이 행을 FK 로 가리키는 표가 없다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TitleSettingEntity> findWithLockById(Integer id);
}
