package com.community.adminauth.service;

import com.community.adminauth.config.AdminPasswordResetProperties;
import com.community.adminauth.exception.TooManyRequestsException;
import com.community.operator.entity.OperatorCertRequestEntity;
import com.community.operator.repository.master.OperatorCertRequestRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 승인번호 <b>요청</b>의 남용 제한 — 같은 이메일(해시 키)은 쿨다운 60초 + 창(1시간) 안에 5회, 같은 IP 는 창 안에 20회(가정, {@link AdminPasswordResetProperties}).
 *
 * <ul>
 *   <li><b>가입 여부와 무관하게 똑같이 센다</b> — 없는 이메일이어도 같은 한도에 걸린다(존재 노출 금지). 키는 접두어를 붙인 SHA-256 해시라 이메일·IP 원문이 남지 않는다.</li>
 *   <li>받아들인 요청만 기록한다(거절된 요청은 기록하지 않아 거절이 한도를 늘리지 않는다). 확인과 기록 사이의 경합은 두 키에 거는 트랜잭션 어드바이저리 잠금으로 줄 세운다
 *       (잠금 순서는 키 정렬 순서라 교착이 없다).</li>
 *   <li>IP 에는 쿨다운이 없다 — 한 사무실(NAT) 안의 서로 다른 운영자가 막히지 않게 한다. 이메일 쿨다운이 같은 IP+이메일의 반복을 막는다.</li>
 * </ul>
 * 부르는 쪽의 트랜잭션 안에서 쓴다. 기록 정리는 DB 배치 {@code auth-token-cleanup}.
 */
@Component
public class PasswordResetRequestLimiter {

    /** 어드바이저리 잠금 종류 번호 — 이 앱의 다른 잠금 키(7_340_001 기동 맞춤, 7_340_002 배치, 7_340_003 닉네임)와 겹치지 않게 정한 값 */
    private static final int LOCK_CLASS = 7_340_004;

    private final OperatorCertRequestRepository requests;
    private final AdminPasswordResetProperties props;
    private final SnowflakeIdGenerator ids;
    private final EntityManager entityManager;

    public PasswordResetRequestLimiter(OperatorCertRequestRepository requests, AdminPasswordResetProperties props, SnowflakeIdGenerator ids,
                                       EntityManager entityManager) {
        this.requests = requests;
        this.props = props;
        this.ids = ids;
        this.entityManager = entityManager;
    }

    /**
     * 한도 안이면 이 요청을 기록하고, 아니면 {@code 429 TOO_MANY_REQUESTS}(+기다릴 초)를 던진다.
     *
     * @param emailKey 이메일 해시 키
     * @param ipKey    IP 해시 키
     */
    public void consume(String emailKey, String ipKey, Instant now) {
        Stream.of(emailKey, ipKey).sorted().forEach(this::lock);

        Duration window = Duration.ofMinutes(props.windowMinutes());
        Instant since = now.minus(window);
        List<OperatorCertRequestEntity> byEmail = requests.findByKeyHashAndRequestedAtAfterOrderByRequestedAtDesc(emailKey, since, Limit.of(props.emailLimit()));
        List<OperatorCertRequestEntity> byIp = requests.findByKeyHashAndRequestedAtAfterOrderByRequestedAtDesc(ipKey, since, Limit.of(props.ipLimit()));

        long wait = 0;
        if (!byEmail.isEmpty()) {
            Instant cooldownEnds = byEmail.get(0).getRequestedAt().plusSeconds(props.cooldownSeconds());
            if (cooldownEnds.isAfter(now)) {
                wait = Math.max(wait, secondsUntil(cooldownEnds, now));
            }
        }
        if (byEmail.size() >= props.emailLimit()) {
            wait = Math.max(wait, secondsUntil(byEmail.get(byEmail.size() - 1).getRequestedAt().plus(window), now));
        }
        if (byIp.size() >= props.ipLimit()) {
            wait = Math.max(wait, secondsUntil(byIp.get(byIp.size() - 1).getRequestedAt().plus(window), now));
        }
        if (wait > 0) {
            throw new TooManyRequestsException(wait);
        }

        requests.persist(OperatorCertRequestEntity.create(ids.nextId(), emailKey, now));
        requests.persist(OperatorCertRequestEntity.create(ids.nextId(), ipKey, now));
    }

    private void lock(String key) {
        // Native SQL 인 이유: pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다(void 반환이라 바깥 select 로 감싸 값 하나로 읽는다)
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1, hashtext(?2))) as acquired")
                .setParameter(1, LOCK_CLASS).setParameter(2, key).getSingleResult();
    }

    /** 남은 초(올림, 최소 1) */
    private static long secondsUntil(Instant target, Instant now) {
        long millis = Duration.between(now, target).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
