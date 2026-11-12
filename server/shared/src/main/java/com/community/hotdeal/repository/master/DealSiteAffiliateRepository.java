package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealSiteAffiliateEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;
import java.util.Optional;

public interface DealSiteAffiliateRepository extends IrobitRepository<DealSiteAffiliateEntity, Long> {

    Optional<DealSiteAffiliateEntity> findByDomain(String domain);

    /** 관리자 사이트 제휴 몰 목록(도메인 차례) */
    List<DealSiteAffiliateEntity> findAllByOrderByDomainAsc();
}
