package com.community.notification.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.dto.request.NotificationEventUpdateRequest;
import com.community.notification.dto.response.NotificationEventView;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationBuiltinChannelEntity;
import com.community.notification.entity.NotificationChannelEntity;
import com.community.notification.entity.NotificationEventEntity;
import com.community.notification.exception.NotificationBroadcastNotAllowedException;
import com.community.notification.exception.NotificationChannelDisabledException;
import com.community.notification.exception.NotificationChannelReferenceNotFoundException;
import com.community.notification.exception.NotificationEventNotFoundException;
import com.community.notification.exception.NotificationEventNotReadyException;
import com.community.notification.exception.NotificationMandatoryException;
import com.community.notification.repository.master.AdminNotificationRepositoryQDSL;
import com.community.notification.repository.master.NotificationChannelRepository;
import com.community.notification.repository.master.NotificationDeliveryRepository;
import com.community.notification.repository.master.NotificationEventRepository;
import com.community.notification.service.NotificationEventSettings.State;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.team.entity.TeamEntity;
import com.community.team.exception.TeamDisabledException;
import com.community.team.exception.TeamReferenceNotFoundException;
import com.community.team.repository.master.TeamRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 관리 — 이벤트마다 <b>사용 여부 · 전달 도구 · 추가 수신 팀</b>을 바꾼다(앱 안 서랍 전달은 늘 켜짐). 수신 규칙·심각도·필수 여부는 코드에 고정이다(화면에서 바꾸지 않는다 — 권한 상승 방지).
 *
 * <ul>
 *   <li><b>검사 순서</b>: 없는 이벤트(404 {@code NOTIFICATION_EVENT_NOT_FOUND}) → 입력 형식(400 {@code fields.enabled|emailEnabled}) → 준비 중 이벤트(409
 *       {@code NOTIFICATION_EVENT_NOT_READY}) → 필수 이벤트를 끄려 함(409 {@code NOTIFICATION_MANDATORY}) → 이메일을 보낼 수 없는 이벤트의 이메일을 켬(400 {@code fields.emailEnabled|channels})
 *       → 모르는 도구·팀(400 {@code NOTIFICATION_CHANNEL_NOT_FOUND}·{@code TEAM_NOT_FOUND}) → 본인 전용·필수 이벤트에 팀·공유 채널(400 {@code NOTIFICATION_BROADCAST_NOT_ALLOWED}
 *       {@code fields.channels|teamIds}) → 팀을 바꾸는데 {@code team.read} 없음(403) → 꺼진 도구·멈춘 팀을 새로 고름(409 {@code NOTIFICATION_CHANNEL_DISABLED}·{@code TEAM_DISABLED}).</li>
 *   <li><b>전달 도구</b>: 요청의 {@code channels} 는 {@code email}(= 기존 {@code emailEnabled}) · {@code browser} · Slack/Mattermost 도구 id. 보내면 그 목록이 전부이고, 안 보내면(예전 요청) {@code emailEnabled} 만 바뀐다.
 *       서랍은 늘 켜져 있어 저장하지 않는다(보내도 무시). Slack · Mattermost 와 추가 수신 팀은 {@code broadcastAllowed} 인 이벤트에만 쓸 수 있다.
 *       브라우저 알림은 순수 클라이언트 동작이라 서버는 선택을 저장만 한다.</li>
 *   <li><b>이미 고른 것은 그대로 둘 수 있다</b> — 꺼진 도구·멈춘 팀은 <b>새로</b> 고를 수 없을 뿐이다. 도구·팀을 지우면 DB 가 이벤트 선택에서 함께 뺀다.</li>
 *   <li>추가 수신 팀의 <b>이름</b>은 {@code team.read} 가 있어야 팀 목록에서 읽으므로, 팀 선택을 <b>바꾸려면</b> {@code team.read} 도 필요하다(그대로 보내는 것은 된다).</li>
 *   <li>같은 이벤트를 동시에 바꾸는 요청은 행 잠금으로 줄 세운다. 바뀐 때만 감사 {@code NOTIFICATION_EVENT_UPDATE}(전·후 값: 사용·이메일·브라우저·도구 id·팀 id)를 남긴다.</li>
 *   <li>목록의 「최근 30일 발송 수」는 그 기간에 만들어진 알림 행 수다(받은 사람마다 한 건). 공유 채널 게시는 센 수에 들지 않는다.</li>
 * </ul>
 * 입구의 권한(조회 {@code notification.read}, 변경 {@code notification.manage})은 컨트롤러가 선언한다.
 */
@Service
public class NotificationEventAdminService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventAdminService.class);

    private static final Duration SENT_WINDOW = Duration.ofDays(30);

    private final NotificationEventRepository events;
    private final NotificationEventSettings settings;
    private final AdminNotificationRepositoryQDSL notifications;
    private final NotificationDeliveryRepository delivery;
    private final NotificationChannelRepository channels;
    private final TeamRepository teams;
    private final AccessDeniedRecorder denied;
    private final AuditLogService audit;
    private final Messages messages;
    private final EntityManager entityManager;

    public NotificationEventAdminService(NotificationEventRepository events, NotificationEventSettings settings, AdminNotificationRepositoryQDSL notifications,
                                         NotificationDeliveryRepository delivery, NotificationChannelRepository channels, TeamRepository teams, AccessDeniedRecorder denied,
                                         AuditLogService audit, Messages messages, EntityManager entityManager) {
        this.events = events;
        this.settings = settings;
        this.notifications = notifications;
        this.delivery = delivery;
        this.channels = channels;
        this.teams = teams;
        this.denied = denied;
        this.audit = audit;
        this.messages = messages;
        this.entityManager = entityManager;
    }

    /** 모든 이벤트(준비 중 포함) — 영역·코드순 */
    @Transactional(readOnly = true)
    public List<NotificationEventView> list() {
        Map<String, State> states = settings.all();
        Map<String, Long> sent = notifications.countByEventSince(Instant.now().minus(SENT_WINDOW));
        Map<String, List<Long>> customByEvent = delivery.channelIdsByEvent();
        Map<String, List<Long>> teamsByEvent = delivery.teamIdsByEvent();
        Locale locale = messages.locale();
        return Arrays.stream(AdminNotificationEvent.values()).sorted(NotificationVisibility.ORDER)
                .map(event -> view(event, states.get(event.code()), sent.getOrDefault(event.code(), 0L), locale, customByEvent.getOrDefault(event.code(), List.of()),
                        teamsByEvent.getOrDefault(event.code(), List.of())))
                .toList();
    }

    @Transactional
    public NotificationEventView update(AdminActor actor, String code, NotificationEventUpdateRequest request) {
        AdminNotificationEvent event = AdminNotificationEvent.ofCode(code).orElseThrow(NotificationEventNotFoundException::new);
        boolean channelsGiven = request != null && request.channels() != null;
        boolean teamsGiven = request != null && request.teamIds() != null;
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (request == null || request.enabled() == null) {
            invalid.put("enabled", new MessageRef("notification.event.required"));
        }
        if (!channelsGiven && (request == null || request.emailEnabled() == null)) {
            invalid.put("emailEnabled", new MessageRef("notification.event.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        boolean enabled = request.enabled();
        if (!event.featureReady()) {
            throw new NotificationEventNotReadyException();
        }
        if (event.mandatory() && !enabled) {
            throw new NotificationMandatoryException();
        }
        Selection selection = channelsGiven ? parseChannels(request.channels()) : null;
        boolean emailEnabled = channelsGiven ? selection.email() : request.emailEnabled();
        if (!event.emailable() && emailEnabled) {
            throw ValidationException.of(channelsGiven ? "channels" : "emailEnabled", new MessageRef("notification.event.emailNotAllowed"));
        }
        Set<Long> wantedTeams = teamsGiven ? parseTeamIds(request.teamIds()) : null;
        if (!event.broadcastAllowed()) {
            boolean channelsNotAllowed = channelsGiven && !selection.custom().isEmpty();
            boolean teamsNotAllowed = teamsGiven && !wantedTeams.isEmpty();
            if (channelsNotAllowed || teamsNotAllowed) {
                throw new NotificationBroadcastNotAllowedException(channelsNotAllowed, teamsNotAllowed);
            }
        }

        Instant now = Instant.now();
        NotificationEventEntity row = events.findWithLockByCode(code).orElseGet(() -> { // 맞춤 전에 불린 드문 경우 — 처음 값으로 만든다
            NotificationEventEntity created = NotificationEventEntity.of(event, now);
            events.persist(created);
            return created;
        });
        entityManager.flush(); // 이벤트 행이 있어야 아래 연결 표(외래키)에 쓸 수 있다
        boolean wasEnabled = row.isEnabled();
        boolean wasEmailEnabled = row.isEmailEnabled();
        boolean wasBrowserEnabled = row.isBrowserEnabled();
        List<Long> currentCustom = delivery.channelIdsOf(code);
        List<Long> currentTeams = delivery.teamIdsOf(code);

        if (teamsGiven && !new HashSet<>(currentTeams).equals(wantedTeams) && !actor.can(Permission.TEAM_READ)) {
            // 팀 이름을 읽을 수 없는 운영자가 팀 id 를 더듬어 보내지 못하게 한다(없는 팀·멈춘 팀 오류로 팀의 존재가 새지 않게)
            denied.record(actor, "MISSING_PERMISSION", "NOTIFICATION_EVENT", code, List.of(Permission.TEAM_READ.code()));
            throw new PermissionDeniedException();
        }
        if (channelsGiven) {
            checkCustomChannels(selection.custom(), currentCustom);
            if (selection.email() && !wasEmailEnabled && !delivery.builtinEnabled(NotificationBuiltinChannelEntity.EMAIL)) {
                throw new NotificationChannelDisabledException();
            }
            if (selection.browser() && !wasBrowserEnabled && !delivery.builtinEnabled(NotificationBuiltinChannelEntity.BROWSER)) {
                throw new NotificationChannelDisabledException();
            }
        } else if (emailEnabled && !wasEmailEnabled && !delivery.builtinEnabled(NotificationBuiltinChannelEntity.EMAIL)) {
            throw new NotificationChannelDisabledException();
        }
        if (teamsGiven) {
            checkTeams(wantedTeams, currentTeams);
        }

        boolean browserEnabled = channelsGiven ? selection.browser() : wasBrowserEnabled;
        List<Long> newCustom = channelsGiven ? selection.custom() : currentCustom;
        List<Long> newTeams = teamsGiven ? new ArrayList<>(wantedTeams) : currentTeams;
        boolean customChanged = !new HashSet<>(currentCustom).equals(new HashSet<>(newCustom));
        boolean teamsChanged = !new HashSet<>(currentTeams).equals(new HashSet<>(newTeams));

        Map<String, Object> before = new LinkedHashMap<>(); // 바뀐 값만 남긴다(그대로인 칸까지 「꺼짐 → 꺼짐」으로 찍히지 않게)
        Map<String, Object> after = new LinkedHashMap<>();
        if (wasEnabled != enabled) {
            before.put("enabled", wasEnabled);
            after.put("enabled", enabled);
        }
        if (wasEmailEnabled != emailEnabled) {
            before.put("emailEnabled", wasEmailEnabled);
            after.put("emailEnabled", emailEnabled);
        }
        if (wasBrowserEnabled != browserEnabled) {
            before.put("browserEnabled", wasBrowserEnabled);
            after.put("browserEnabled", browserEnabled);
        }
        if (customChanged) {
            before.put("channels", currentCustom.stream().map(String::valueOf).toList());
            after.put("channels", newCustom.stream().map(String::valueOf).toList());
        }
        if (teamsChanged) {
            before.put("teamIds", currentTeams.stream().map(String::valueOf).toList());
            after.put("teamIds", newTeams.stream().map(String::valueOf).toList());
        }
        if (!after.isEmpty()) {
            if (wasEnabled != enabled || wasEmailEnabled != emailEnabled || wasBrowserEnabled != browserEnabled) {
                row.change(enabled, emailEnabled, browserEnabled, actor.operatorId(), now);
            }
            if (customChanged) {
                delivery.replaceChannels(code, newCustom);
            }
            if (teamsChanged) {
                delivery.replaceTeams(code, newTeams);
            }
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_EVENT_UPDATE, AuditResult.SUCCESS).target("NOTIFICATION_EVENT", code)
                    .with("before", before).with("after", after));
            log.info("알림 이벤트 설정을 바꿨다 event={} enabled={} emailEnabled={} browserEnabled={} 도구={}개 팀={}개 by operatorId={}", code, enabled, emailEnabled, browserEnabled,
                    newCustom.size(), newTeams.size(), actor.operatorId());
        }
        long sent = notifications.countByEventSince(now.minus(SENT_WINDOW)).getOrDefault(code, 0L);
        return view(event, new State(enabled, emailEnabled, browserEnabled), sent, messages.locale(), newCustom, newTeams);
    }

    // ── 입력 해석 ──────────────────────────────────────────────────────────

    /** 고른 전달 도구 — {@code email} · {@code browser} 와 Slack/Mattermost 도구 id. 서랍은 무시한다 */
    private record Selection(boolean email, boolean browser, List<Long> custom) {
    }

    private Selection parseChannels(List<String> raw) {
        boolean email = false;
        boolean browser = false;
        Set<Long> custom = new TreeSet<>(); // id 순서로 — 목록 조회와 같은 차례
        for (String entry : raw) {
            String id = entry == null ? "" : entry.strip();
            switch (id.toLowerCase(Locale.ROOT)) {
                case NotificationBuiltinChannelEntity.DRAWER -> {
                    // 서랍은 항상 켜져 있어 고르는 것이 아니다 — 보내도 무시한다
                }
                case NotificationBuiltinChannelEntity.EMAIL -> email = true;
                case NotificationBuiltinChannelEntity.BROWSER -> browser = true;
                default -> {
                    Long channelId = parseId(id);
                    if (channelId == null) {
                        throw new NotificationChannelReferenceNotFoundException();
                    }
                    custom.add(channelId);
                }
            }
        }
        return new Selection(email, browser, new ArrayList<>(custom));
    }

    private Set<Long> parseTeamIds(List<String> raw) {
        Set<Long> ids = new TreeSet<>();
        for (String entry : raw) {
            Long id = parseId(entry == null ? "" : entry.strip());
            if (id == null) {
                throw new TeamReferenceNotFoundException();
            }
            ids.add(id);
        }
        return ids;
    }

    private void checkCustomChannels(List<Long> wanted, List<Long> current) {
        if (wanted.isEmpty()) {
            return;
        }
        Map<Long, NotificationChannelEntity> found = new HashMap<>();
        channels.findAllById(wanted).forEach(channel -> found.put(channel.getId(), channel));
        for (Long id : wanted) {
            NotificationChannelEntity channel = found.get(id);
            if (channel == null) {
                throw new NotificationChannelReferenceNotFoundException();
            }
            if (!channel.isEnabled() && !current.contains(id)) {
                throw new NotificationChannelDisabledException();
            }
        }
    }

    private void checkTeams(Set<Long> wanted, List<Long> current) {
        if (wanted.isEmpty()) {
            return;
        }
        Map<Long, TeamEntity> found = new HashMap<>();
        teams.findAllById(wanted).forEach(team -> found.put(team.getId(), team));
        for (Long id : wanted) {
            TeamEntity team = found.get(id);
            if (team == null) {
                throw new TeamReferenceNotFoundException();
            }
            if (!team.isEnabled() && !current.contains(id)) {
                throw new TeamDisabledException();
            }
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── 응답 ────────────────────────────────────────────────────────────────

    private NotificationEventView view(AdminNotificationEvent event, State state, long sent, Locale locale, List<Long> customChannels, List<Long> teamIds) {
        List<String> channelIds = new ArrayList<>();
        if (state.emailEnabled()) {
            channelIds.add(NotificationBuiltinChannelEntity.EMAIL);
        }
        if (state.browserEnabled()) {
            channelIds.add(NotificationBuiltinChannelEntity.BROWSER);
        }
        customChannels.forEach(id -> channelIds.add(String.valueOf(id)));
        return new NotificationEventView(event.code(), event.area().id(), event.severity().kind(), messages.get(new MessageRef(event.nameKey()), locale),
                messages.get(new MessageRef(event.descriptionKey()), locale), messages.get(new MessageRef(event.recipientsKey()), locale), event.mandatory(),
                event.emailable(), state.enabled(), state.emailEnabled(), event.featureReady(), sent, channelIds, teamIds.stream().map(String::valueOf).toList(),
                event.broadcastAllowed());
    }
}
