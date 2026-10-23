package com.community.membernotification.repository.master;

import com.community.membernotification.entity.MemberEventSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface MemberEventSettingRepository extends RoboyamRepository<MemberEventSettingEntity, String> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 이벤트를 동시에 바꾸는 요청을 줄 세울 때 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberEventSettingEntity> findWithLockByCode(String code);
}
