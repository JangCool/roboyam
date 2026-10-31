package com.community.business.repository.master;

import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessStatus;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 사업자 신원 — 회원 화면(api)과 운영자 심사(admin)가 같이 쓴다 */
public interface BusinessProfileRepository extends RoboyamRepository<BusinessProfileEntity, Long> {

    Optional<BusinessProfileEntity> findByUserId(Long userId);

    /** 회원의 신원을 잠근다(신청·수정이 겹쳐도 한쪽만) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BusinessProfileEntity> findWithLockByUserId(Long userId);

    /** 처리하려는 신원을 잠근다(승인·반려·정지가 겹쳐도 한쪽만 이긴다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BusinessProfileEntity> findWithLockById(Long id);

    /** 다른 줄 중 그 상태(살아 있는 줄)에 같은 등록번호가 있는가 — 409 {@code BUSINESS_DUPLICATE} */
    boolean existsByBizNoHashAndStatusInAndIdNot(String bizNoHash, Collection<BusinessStatus> statuses, Long id);
}
