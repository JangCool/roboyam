package com.community.mail.repository.master;

import com.community.mail.entity.MailSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface MailSettingRepository extends RoboyamRepository<MailSettingEntity, Integer> {

    /** 행 잠금을 잡고 읽는다 — 관리자 화면의 저장이 동시에 들어와도 이력의 「이전 값」이 어긋나지 않게 한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MailSettingEntity> findWithLockById(Integer id);
}
