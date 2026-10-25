package com.community.report.service;

import com.community.report.entity.ReportEventEntity;
import com.community.report.repository.master.ReportEventRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 신고 사건 기록({@code report_events}) 남기기 — 공개 서버 신고 접수(신고·자동 숨김)와 관리자 신고 관리(검토 시작·조치·무시·다시 열기)가 함께 쓴다.
 * 부르는 서비스의 업무 트랜잭션 안에서 부른다(일과 기록이 함께 커밋·롤백된다).
 */
@Component
public class ReportCaseLog {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ReportEventRepository events;
    private final SnowflakeIdGenerator ids;

    public ReportCaseLog(ReportEventRepository events, SnowflakeIdGenerator ids) {
        this.events = events;
        this.ids = ids;
    }

    /** 운영자가 한 일 */
    public void operator(long caseId, String type, long operatorId, Map<String, Object> detail, Instant now) {
        events.persist(ReportEventEntity.create(ids.nextId(), caseId, type, ReportEventEntity.BY_OPERATOR, operatorId, json(detail), now));
    }

    /** 자동으로 일어난 일(자동 숨김) */
    public void system(long caseId, String type, Map<String, Object> detail, Instant now) {
        events.persist(ReportEventEntity.create(ids.nextId(), caseId, type, ReportEventEntity.BY_SYSTEM, null, json(detail), now));
    }

    /** 회원의 신고 — 신고자 ID 는 남기지 않는다(덧붙임에 {@code reportId}·{@code reason}) */
    public void reported(long caseId, long reportId, String reason, Instant now) {
        events.persist(ReportEventEntity.create(ids.nextId(), caseId, ReportEventEntity.REPORTED, ReportEventEntity.BY_MEMBER, null,
                json(Map.of("reportId", Long.toString(reportId), "reason", reason)), now));
    }

    private static String json(Map<String, Object> detail) {
        return detail == null || detail.isEmpty() ? null : MAPPER.writeValueAsString(detail);
    }
}
