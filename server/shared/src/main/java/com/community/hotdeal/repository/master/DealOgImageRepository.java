package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealOgImageEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

public interface DealOgImageRepository extends RoboyamRepository<DealOgImageEntity, Long> {

    Optional<DealOgImageEntity> findByPublicUrl(String publicUrl);
}
