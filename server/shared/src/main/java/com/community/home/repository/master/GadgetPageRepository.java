package com.community.home.repository.master;

import com.community.home.entity.GadgetPageEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 가젯 페이지({@code gadget_pages}) */
public interface GadgetPageRepository extends RoboyamRepository<GadgetPageEntity, Long> {

    /** 만든 차례 */
    List<GadgetPageEntity> findAllByOrderByIdAsc();

    Optional<GadgetPageEntity> findByPageKey(String pageKey);

    boolean existsByPageKeyAndIdNot(String pageKey, Long id);

    boolean existsByPageKey(String pageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GadgetPageEntity> findWithLockById(Long id);
}
