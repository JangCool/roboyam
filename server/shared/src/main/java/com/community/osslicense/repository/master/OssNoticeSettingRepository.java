package com.community.osslicense.repository.master;

import com.community.osslicense.entity.OssNoticeSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 오픈소스 라이선스 고지 설정 한 줄(V181, id = 1) */
public interface OssNoticeSettingRepository extends RoboyamRepository<OssNoticeSettingEntity, Integer> {

    /** 쓰기 잠금으로 읽는다 — 안내 문구 저장을 한 번에 하나씩 줄 세운다(이 행을 FK 로 가리키는 표가 없다). 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OssNoticeSettingEntity> findWithLockById(Integer id);
}
