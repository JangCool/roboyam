package com.community.mail.outbox.service;

import com.community.mail.outbox.dto.OutboxMail;
import com.community.mail.outbox.repository.master.MailOutboxStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커머스 메일 대기열에 넣기(docs/domain/commerce.md 14절, commerce-foundation-contract 결정 10·11·17) — <b>업무 트랜잭션 안에서만</b> 부른다({@code MANDATORY} —
 * 트랜잭션 밖이면 던진다). 업무가 커밋되면 메일도 대기열에 남고, 롤백되면 함께 사라진다. 메일 서버가 죽어 있어도 업무는 성공하고 메일은 나중에 나간다(P27).
 *
 * <ul>
 *   <li>같은 {@code dedupeKey} 로 두 번 넣으면 두 번째는 넣지 않는다({@code false}) — 같은 일을 다시 처리해도 메일은 한 통.</li>
 *   <li>받는 주소는 넣지 않는다 — 보낼 때 워커가 {@code users.email} 을 읽는다.</li>
 *   <li>보내기는 배치를 켠 관리자 서버의 워커({@link MailOutboxWorker}, 30초)가 한다.</li>
 * </ul>
 */
@Service
public class MailOutbox {

    private static final Logger log = LoggerFactory.getLogger(MailOutbox.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MailOutboxStore store;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    @Autowired
    public MailOutbox(MailOutboxStore store, SnowflakeIdGenerator ids) {
        this(store, ids, Clock.systemUTC());
    }

    public MailOutbox(MailOutboxStore store, SnowflakeIdGenerator ids, Clock clock) {
        this.store = store;
        this.ids = ids;
        this.clock = clock;
    }

    /** @return 넣었으면 true, 같은 열쇠가 이미 있어 넣지 않았으면 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(OutboxMail mail) {
        boolean inserted = store.insert(ids.nextId(), mail.dedupeKey(), mail.userId(), mail.kind().code(), JSON.writeValueAsString(mail.variables()),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (inserted) {
            log.info("메일 대기열에 넣었다 kind={} userId={}", mail.kind(), mail.userId());
        } else {
            log.info("같은 메일이 이미 대기열에 있어 넣지 않았다 kind={} userId={}", mail.kind(), mail.userId());
        }
        return inserted;
    }
}
