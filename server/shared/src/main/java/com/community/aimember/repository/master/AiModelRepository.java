package com.community.aimember.repository.master;

import com.community.aimember.entity.AiModelEntity;
import com.community.aimember.entity.AiProvider;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 모델 목록({@code ai_models}) */
public interface AiModelRepository extends RoboyamRepository<AiModelEntity, Long> {

    /** 화면·고르기 차례 — 제공사 안에서 sort_order, 같으면 ID */
    List<AiModelEntity> findAllByOrderByProviderAscSortOrderAscIdAsc();

    List<AiModelEntity> findByEnabledTrue();

    boolean existsByProviderAndModelId(AiProvider provider, String modelId);

    Optional<AiModelEntity> findFirstByProviderAndDefaultModelTrue(AiProvider provider);

    /** 바꾸기 — 같은 제공사의 줄을 모두 잠근다(기본 옮기기·동시 추가가 서로 줄 선다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AiModelEntity> findWithLockByProviderOrderBySortOrderAscIdAsc(AiProvider provider);
}
