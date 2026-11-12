package com.community.business.repository.master;

import com.community.business.entity.BusinessServiceEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusinessServiceRepository extends IrobitRepository<BusinessServiceEntity, Long> {

    List<BusinessServiceEntity> findByProfileId(Long profileId);

    List<BusinessServiceEntity> findByProfileIdIn(Collection<Long> profileIds);

    Optional<BusinessServiceEntity> findByProfileIdAndService(Long profileId, BusinessServiceKind service);
}
