package com.community.jjal.repository.master;

import com.community.jjal.entity.JjalBoxItemEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;
import java.util.Optional;

public interface JjalBoxItemRepository extends IrobitRepository<JjalBoxItemEntity, Long> {

    Optional<JjalBoxItemEntity> findByUserIdAndFileId(Long userId, Long fileId);

    boolean existsByUserIdAndFileId(Long userId, Long fileId);

    long countByUserId(Long userId);

    /** 내 보관함 — 회원이 정한 순서(순서 값 · 같으면 ID) */
    List<JjalBoxItemEntity> findAllByUserIdOrderBySortKeyAscIdAsc(Long userId);
}
