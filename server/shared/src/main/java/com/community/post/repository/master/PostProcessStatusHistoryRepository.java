package com.community.post.repository.master;

import com.community.post.entity.PostProcessStatusHistoryEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;
import java.util.Optional;

public interface PostProcessStatusHistoryRepository extends IrobitRepository<PostProcessStatusHistoryEntity, Long> {

    /** 한 글의 상태 이력 — 최신이 위, 50줄까지(색인 ix_post_process_status_history_post) */
    List<PostProcessStatusHistoryEntity> findTop50ByPostIdOrderByIdDesc(Long postId);

    /** 한 글의 마지막 상태 변경(글 상세의 {@code processStatusNote}) */
    Optional<PostProcessStatusHistoryEntity> findFirstByPostIdOrderByIdDesc(Long postId);
}
