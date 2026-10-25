package com.community.post.repository.master;

import com.community.post.entity.PostModerationEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface PostModerationEventRepository extends RoboyamRepository<PostModerationEventEntity, Long> {

    /** 한 글의 조치 기록 — 최신이 위(색인 ix_post_moderation_events_post) */
    List<PostModerationEventEntity> findByPostIdOrderByIdDesc(Long postId);
}
