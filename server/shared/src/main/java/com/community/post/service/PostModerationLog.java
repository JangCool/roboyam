package com.community.post.service;

import com.community.post.entity.PostModerationEventEntity;
import com.community.post.repository.master.PostModerationEventRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글 조치 기록({@code post_moderation_events}) 남기기 — 관리자 게시글 관리(운영자 조치)와 공개 서버 신고 자동 숨김(SYSTEM)이 함께 쓴다.
 * 부르는 서비스의 업무 트랜잭션 안에서 부른다(조치와 기록이 함께 커밋·롤백된다). docs/features/post-admin-contract.md.
 */
@Component
public class PostModerationLog {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final PostModerationEventRepository events;
    private final SnowflakeIdGenerator ids;

    public PostModerationLog(PostModerationEventRepository events, SnowflakeIdGenerator ids) {
        this.events = events;
        this.ids = ids;
    }

    /**
     * 운영자 조치 한 줄.
     *
     * @param detail 덧붙임(없으면 null 또는 빈 맵 — 값이 null 인 칸도 그대로 싣는다)
     */
    public void operator(long postId, String type, long operatorId, String reason, Map<String, Object> detail, Instant now) {
        events.persist(PostModerationEventEntity.create(ids.nextId(), postId, type, PostModerationEventEntity.BY_OPERATOR, operatorId, reason, json(detail), now));
    }

    /** 자동 조치 한 줄(신고 자동 숨김 등 — 사유 없음) */
    public void system(long postId, String type, Map<String, Object> detail, Instant now) {
        events.persist(PostModerationEventEntity.create(ids.nextId(), postId, type, PostModerationEventEntity.BY_SYSTEM, null, null, json(detail), now));
    }

    private static String json(Map<String, Object> detail) {
        return detail == null || detail.isEmpty() ? null : MAPPER.writeValueAsString(detail);
    }
}
