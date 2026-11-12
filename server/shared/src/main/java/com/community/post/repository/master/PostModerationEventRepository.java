package com.community.post.repository.master;

import com.community.post.entity.PostModerationEventEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

public interface PostModerationEventRepository extends IrobitRepository<PostModerationEventEntity, Long> {

    /** 한 글의 조치 기록 — 최신이 위(색인 ix_post_moderation_events_post) */
    List<PostModerationEventEntity> findByPostIdOrderByIdDesc(Long postId);
}
