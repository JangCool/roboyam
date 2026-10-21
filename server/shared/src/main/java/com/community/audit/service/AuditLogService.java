package com.community.audit.service;

import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditLogEntity;
import com.community.audit.repository.master.AuditLogRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.response.RequestIdHolder;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 기록 쓰기 — 역할·권한·운영자 상태 변경, 로그인 성공/실패, 권한 거부를 {@code audit_logs} 에 남긴다(조회 API 는 이번 단계 밖).
 *
 * <ul>
 *   <li>변경을 기록할 때는 <b>업무 트랜잭션 안에서 부른다</b> — 같은 트랜잭션에 실려 변경이 롤백되면 기록도 함께 사라진다(없던 일이 기록에 남지 않는다).</li>
 *   <li>트랜잭션 밖(로그인 실패·권한 거부 등)에서 부르면 자기 트랜잭션을 연다.</li>
 *   <li>{@code detail} 은 짧은 JSON 이다. 비밀번호·토큰은 넣지 않는다(호출하는 쪽이 지킨다). 요청 ID 는 요청 문맥에서 가져온다.</li>
 * </ul>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** detail 이 이보다 길면 내용을 버리고 잘렸다는 표시만 남긴다(사유 같은 사람이 쓴 글이 기록을 키우지 않게) */
    private static final int DETAIL_MAX = 4000;

    private final AuditLogRepository logs;
    private final SnowflakeIdGenerator ids;

    public AuditLogService(AuditLogRepository logs, SnowflakeIdGenerator ids) {
        this.logs = logs;
        this.ids = ids;
    }

    /** 부르는 쪽의 트랜잭션이 있으면 거기에 실린다(없으면 자기 트랜잭션) */
    @Transactional
    public void record(AuditEvent event) {
        write(event);
    }

    /**
     * 부르는 쪽의 트랜잭션과 <b>따로</b> 남긴다. 거절처럼 요청이 예외로 끝나 업무 트랜잭션이 롤백되는 일(권한 거부)도 기록은 남아야 할 때 쓴다.
     * 기록 표는 다른 표와 잠금을 다투지 않아(외래키 없음) 업무 트랜잭션이 행을 잡고 있어도 막히지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIsolated(AuditEvent event) {
        write(event);
    }

    private void write(AuditEvent event) {
        logs.persist(AuditLogEntity.create(ids.nextId(), Instant.now(), event.actorType(), event.actorId(), event.action(), event.result(),
                event.targetType(), event.targetId(), event.ip(), RequestIdHolder.requestId(), detailJson(event)));
        log.debug("감사 기록 action={} result={} actor={} target={}:{}", event.action(), event.result(), event.actorId(), event.targetType(), event.targetId());
    }

    private static String detailJson(AuditEvent event) {
        if (event.detail().isEmpty()) {
            return null;
        }
        String json = MAPPER.writeValueAsString(event.detail());
        return json.length() <= DETAIL_MAX ? json : "{\"truncated\":true}"; // 잘린 JSON 을 남기지 않는다
    }
}
