package com.community.operator.repository.master;

import com.community.operator.entity.OperatorCertRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;

public interface OperatorCertRequestRepository extends RoboyamRepository<OperatorCertRequestEntity, Long> {

    /** 그 키의 {@code since} 이후 요청을 최근순으로 {@code limit} 개까지 */
    List<OperatorCertRequestEntity> findByKeyHashAndRequestedAtAfterOrderByRequestedAtDesc(String keyHash, Instant since, Limit limit);
}
