package com.community.notification.service;

import com.community.notification.dto.NotificationChannelPostRequested;
import com.community.notification.dto.NotificationContext;
import com.community.notification.dto.NotificationEmailRequested;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationBuiltinChannelEntity;
import com.community.notification.entity.NotificationRecipientRule;
import com.community.notification.entity.NotificationSeverity;
import com.community.notification.repository.master.NotificationDeliveryRepository;
import com.community.notification.repository.master.NotificationRecipientRepositoryQDSL;
import com.community.notification.repository.master.OperatorNotificationMuteRepository;
import com.community.notification.service.NotificationEventSettings.State;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 알림 발행기 — 사건이 일어난 서비스가 {@code publisher.publish(이벤트, 맥락)} 한 줄로 알림을 낸다. 규약은 {@code .claude/guides/backend/notification.md}.
 *
 * <p><b>트랜잭션</b>: 호출자의 트랜잭션에 참여한다(없으면 자기 트랜잭션을 연다). 알림 행은 업무와 한 트랜잭션에 실려 <b>업무가 롤백되면 알림도 없고</b>, 커밋됐는데 알림이 사라지는 일도 없다.
 * 이메일은 커밋 뒤에 별도로 보낸다({@code NotificationEmailSender} — 메일 서버는 트랜잭션 밖).
 *
 * <p><b>알림 실패가 업무를 깨지 않는다</b>: 수신자 계산·행 만들기를 업무 트랜잭션의 연결에 잡은 <b>JDBC 세이브포인트</b> 안에서 하고 실패하면 그 세이브포인트로 되돌린 뒤 로그만 남기고 0을 돌려준다.
 * try-catch 만으로는 부족하다 — PostgreSQL 은 실패한 문장이 있으면 트랜잭션 전체를 못 쓰게 만들어(그 뒤 커밋이 실패한다) 업무 저장까지 날아간다. 그래서 알림 행은 JPA 영속성 컨텍스트에
 * 올리지 않고 JDBC 로 넣는다(실패한 엔티티가 영속성 컨텍스트에 남아 업무의 다음 flush 를 계속 깨뜨리는 일도 없다). 부르기 직전에 업무 쪽 변경을 먼저 내보내므로({@code flush})
 * 업무 쪽 오류는 알림 오류로 삼켜지지 않고 원래 자리에서 그대로 올라온다.
 *
 * <p><b>받는 사람</b>은 이벤트의 수신 규칙({@link NotificationRecipientRule})이 정한다 — 본인 / 권한 보유자(+ 최고 관리자, 활성 운영자만) / 최고 관리자 — 에 맥락의 추가 수신자를 더하고, 행위자 제외
 * 이벤트면 행위자를 뺀다. 이벤트 관리에서 꺼진 이벤트(필수 이벤트는 못 끈다)와 운영자가 개인 설정에서 끈 이벤트(필수 이벤트는 무시)는 건너뛴다.
 *
 * <p><b>추가 수신 팀·공유 채널</b>({@code broadcastAllowed} 인 이벤트만): 이벤트에 고른 사용 중인 팀의 팀원 중 <b>활성 운영자이고 이 이벤트를 볼 권한({@code requiredPermission})이 있는 사람</b>이 받는 사람에 더해진다
 * (권한 없는 팀원은 건너뛴다 — 권한 상승 방지. 이미 받는 사람은 한 번만). Slack · Mattermost 도구는 받는 사람 수와 상관없이 <b>이벤트당 도구마다 한 번</b> 커밋 뒤에 올린다
 * ({@code NotificationChannelPostSender}). 이메일은 이벤트의 이메일 전달과 기본 제공 이메일 도구가 모두 켜져 있을 때만 나간다.
 *
 * <p><b>묶기</b>: 이벤트에 {@code collapseSeconds} 가 있으면, 같은 수신자가 같은 이벤트·같은 묶음 키({@code dedupeKey})의 알림을 그 시간 안에 이미 받았을 때 새 알림을 <b>만들지 않는다</b>
 * (처음 것이 남고 이후 중복은 사라진다 — 기존 행을 고치지 않는다. 그래서 새 알림 도착 감지({@code latestId})가 어긋나지 않고, 묶음 시간은 처음 알림의 생성 시각부터 센다).
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /** 묶음 키·이동 경로의 열 길이(V40) */
    private static final int DEDUPE_MAX = 120;
    private static final int LINK_MAX = 300;

    private final NotificationEventSettings settings;
    private final NotificationRecipientRepositoryQDSL recipients;
    private final OperatorNotificationMuteRepository mutes;
    private final NotificationDeliveryRepository delivery;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final DataSource dataSource;

    public NotificationPublisher(NotificationEventSettings settings, NotificationRecipientRepositoryQDSL recipients, OperatorNotificationMuteRepository mutes,
                                 NotificationDeliveryRepository delivery, SnowflakeIdGenerator ids, ApplicationEventPublisher events, EntityManager entityManager,
                                 DataSource dataSource) {
        this.settings = settings;
        this.recipients = recipients;
        this.mutes = mutes;
        this.delivery = delivery;
        this.ids = ids;
        this.events = events;
        this.entityManager = entityManager;
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.dataSource = dataSource;
    }

    /**
     * 알림을 낸다.
     *
     * @return 만든 알림 수(수신자 수). 받을 사람이 없거나 이벤트가 꺼져 있거나 묶였거나 실패했으면 0 — 실패는 던지지 않고 로그만 남긴다
     */
    @Transactional
    public int publish(AdminNotificationEvent event, NotificationContext context) {
        if (!event.featureReady()) {
            log.warn("준비 중인 알림 이벤트는 발행하지 않는다 event={}", event.code());
            return 0;
        }
        entityManager.flush(); // 업무 쪽 변경을 먼저 내보낸다 — 그 실패가 아래 try 에서 알림 실패로 삼켜지지 않게
        try {
            return inSavepoint(() -> create(event, context));
        } catch (RuntimeException e) {
            // 예외 문구에 SQL 인자(운영자 이름 등)가 실릴 수 있어 클래스 이름만 남긴다
            log.error("알림을 만들지 못했다 — 업무에는 영향이 없다 event={} 원인={}", event.code(), e.getClass().getSimpleName());
            return 0;
        }
    }

    /**
     * 업무 트랜잭션이 쥔 JDBC 연결에서 세이브포인트를 잡고 일을 하다가 실패하면 그 세이브포인트로 되돌린 뒤 예외를 다시 던진다.
     * 스프링의 중첩 트랜잭션({@code PROPAGATION_NESTED})은 JPA 트랜잭션 관리자에서 쓰지 못해(Hibernate 방언이 세이브포인트를 내놓지 않는다) 연결을 직접 쓴다.
     * 업무 트랜잭션의 연결이 아니면(트랜잭션 밖이면) 되돌릴 수 없어 시작하지 않는다.
     */
    private int inSavepoint(java.util.function.IntSupplier work) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                throw new IllegalStateException("업무 트랜잭션의 연결을 얻지 못했다");
            }
            Savepoint savepoint = connection.setSavepoint();
            try {
                int result = work.getAsInt();
                connection.releaseSavepoint(savepoint);
                return result;
            } catch (RuntimeException e) {
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollbackFailure) {
                    log.error("알림 세이브포인트를 되돌리지 못했다 원인={}", rollbackFailure.getClass().getSimpleName());
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("알림 세이브포인트를 만들지 못했다", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private int create(AdminNotificationEvent event, NotificationContext context) {
        Instant now = Instant.now();
        State state = settings.of(event);
        if (!state.enabled()) {
            log.debug("꺼진 알림 이벤트라 발행하지 않는다 event={}", event.code());
            return 0;
        }

        Set<Long> targets = recipientsOf(event, context, now);
        addTeamMembers(event, targets, now);
        if (event.excludeActor() && context.actorId() != null) {
            targets.remove(context.actorId());
        }
        if (!event.mandatory() && !targets.isEmpty()) {
            mutes.findByEventCodeAndOperatorIdIn(event.code(), targets).forEach(mute -> targets.remove(mute.getOperatorId()));
        }
        String dedupeKey = dedupeKeyOf(context.dedupeKey());
        // 묶음 때문에 받을 사람이 모두 사라졌으면(= 같은 알림이 방금 나갔으면) 공유 채널에도 다시 올리지 않는다
        boolean collapsed = false;
        if (event.collapseSeconds() > 0 && !targets.isEmpty()) {
            targets.removeAll(recentlyNotified(event, dedupeKey, targets, now));
            collapsed = targets.isEmpty();
        }

        NotificationSeverity severity = context.severity() != null ? context.severity() : event.severity();
        String argsJson = NotificationArgs.encode(context.args());
        String link = linkOf(event, context);
        if (!targets.isEmpty()) {
            OffsetDateTime createdAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
            List<Object[]> rows = new ArrayList<>();
            for (Long operatorId : targets) {
                rows.add(new Object[] {ids.nextId(), operatorId, event.code(), severity.name(), event.titleKey(), event.bodyKey(), argsJson, link, dedupeKey, createdAt});
            }
            // Native SQL(JDBC)인 이유: 알림 실패가 업무 트랜잭션을 망가뜨리지 않게 세이브포인트 안에서 영속성 컨텍스트를 거치지 않고 넣는다(클래스 설명)
            jdbc.batchUpdate("insert into admin_notifications (id, operator_id, event_code, severity, title_key, body_key, args, link_path, dedupe_key, created_at) "
                    + "values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)", rows);

            // 이메일 도구(기본 제공)가 꺼져 있으면 이벤트에 이메일이 켜져 있어도 보내지 않는다
            if (state.emailEnabled() && delivery.builtinEnabled(NotificationBuiltinChannelEntity.EMAIL)) {
                Set<Long> mailTargets = new LinkedHashSet<>(targets);
                mailTargets.removeAll(recipients.emailOptedOut(targets));
                if (!mailTargets.isEmpty()) {
                    events.publishEvent(new NotificationEmailRequested(event.code(), event.titleKey(), event.bodyKey(), argsJson, link, new ArrayList<>(mailTargets)));
                }
            }
        }

        // 공유 채널(Slack · Mattermost): 받는 사람 수와 상관없이 도구마다 이벤트당 한 번. 본인 전용·필수 이벤트는 코드가 막는다(broadcastAllowed)
        if (event.broadcastAllowed() && !collapsed) {
            List<Long> channelIds = delivery.enabledChannelIdsOf(event.code());
            if (!channelIds.isEmpty()) {
                events.publishEvent(new NotificationChannelPostRequested(event.code(), severity, event.titleKey(), event.bodyKey(), argsJson, link, channelIds));
            }
        }
        if (!targets.isEmpty()) {
            log.info("알림을 만들었다 event={} 받는 사람={}명 actor={}", event.code(), targets.size(), context.actorId());
        }
        return targets.size();
    }

    /**
     * 이벤트의 「추가 수신 팀」 팀원을 받는 사람에 더한다. 코드에 고정된 기본 수신 규칙은 그대로이고(권한 상승 방지), 팀원은 <b>사용 중인 팀</b>의 <b>지금 활성인 운영자</b>이면서
     * <b>이 이벤트를 볼 권한</b>({@code requiredPermission} — 최고 관리자는 모두)이 있는 사람만 받는다 — 권한이 없는 팀원에게 알림 행을 만들지 않는다. 이미 받는 사람은 한 번만 센다.
     */
    private void addTeamMembers(AdminNotificationEvent event, Set<Long> targets, Instant now) {
        if (!event.broadcastAllowed()) {
            return;
        }
        Set<Long> members = delivery.enabledTeamMemberIdsOf(event.code());
        if (members.isEmpty()) {
            return;
        }
        Set<Long> allowed = new LinkedHashSet<>(recipients.active(members, now));
        if (event.requiredPermission() != null && !allowed.isEmpty()) {
            allowed.retainAll(recipients.activeHolders(List.of(event.requiredPermission().code()), now));
        }
        targets.addAll(allowed);
    }

    /** 수신 규칙 + 추가 수신자. 「내 계정」 이벤트(SELF)는 계정 상태와 상관없이 쌓고, 권한 보유자·최고 관리자·추가 수신자는 지금 활성인 사람만 받는다 */
    private Set<Long> recipientsOf(AdminNotificationEvent event, NotificationContext context, Instant now) {
        Set<Long> targets = new LinkedHashSet<>();
        switch (event.recipient()) {
            case SELF -> {
                if (context.targetOperatorId() == null) {
                    throw new IllegalArgumentException("대상 운영자가 없다");
                }
                targets.addAll(recipients.existing(Set.of(context.targetOperatorId())));
            }
            case PERMISSION_HOLDERS -> targets.addAll(recipients.activeHolders(event.recipientPermissions().stream().map(permission -> permission.code()).toList(), now));
            case ROOT -> targets.addAll(recipients.activeRoots(now));
        }
        targets.addAll(recipients.active(context.extraRecipientIds(), now));
        return targets;
    }

    /** 묶음 시간 안에 같은 이벤트·같은 묶음 키의 알림을 이미 받은 사람 */
    private Set<Long> recentlyNotified(AdminNotificationEvent event, String dedupeKey, Set<Long> targets, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", event.code())
                .addValue("key", dedupeKey)
                .addValue("since", OffsetDateTime.ofInstant(now.minus(Duration.ofSeconds(event.collapseSeconds())), ZoneOffset.UTC))
                .addValue("ids", targets);
        List<Long> found = namedJdbc.queryForList("select distinct operator_id from admin_notifications where event_code = :code "
                + "and dedupe_key is not distinct from :key and created_at > :since and operator_id in (:ids)", params, Long.class);
        return new LinkedHashSet<>(found);
    }

    private static String dedupeKeyOf(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.length() <= DEDUPE_MAX ? key : key.substring(0, DEDUPE_MAX);
    }

    /** 이동 경로 템플릿의 {@code {이름}} 자리를 채운다(값은 URL 인코딩). 채울 값이 없는 자리가 있으면 링크 없음 */
    static String resolveLink(String template, Map<String, String> linkArgs) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = linkArgs.get(matcher.group(1));
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        }
        matcher.appendTail(out);
        return out.length() <= LINK_MAX ? out.toString() : null;
    }

    private static String linkOf(AdminNotificationEvent event, NotificationContext context) {
        String link = resolveLink(event.linkPath(), context.linkArgs());
        if (link == null && event.linkPath() != null) {
            log.debug("이동 경로를 채우지 못해 링크 없이 낸다 event={}", event.code());
        }
        return link;
    }
}
