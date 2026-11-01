package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealDomainEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface DealDomainRepository extends RoboyamRepository<DealDomainEntity, Long> {

    Optional<DealDomainEntity> findByDomain(String domain);

    /** 관리자 링크 규칙 목록(도메인 차례) */
    List<DealDomainEntity> findAllByOrderByDomainAsc();
}
