package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealOgImageEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.Optional;

public interface DealOgImageRepository extends IrobitRepository<DealOgImageEntity, Long> {

    Optional<DealOgImageEntity> findByPublicUrl(String publicUrl);
}
