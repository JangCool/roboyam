package com.community.group.service;

import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupEventRequest;
import com.community.group.dto.request.GroupEventResponseRequest;
import com.community.group.dto.response.GroupEventCountsView;
import com.community.group.dto.response.GroupEventDetailView;
import com.community.group.dto.response.GroupEventResponseResultView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupEventEntity;
import com.community.group.entity.GroupEventEntity.State;
import com.community.group.entity.GroupEventMode;
import com.community.group.entity.GroupEventResponseEntity;
import com.community.group.entity.GroupEventResponseType;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.exception.GroupEventCanceledException;
import com.community.group.exception.GroupEventClosedException;
import com.community.group.exception.GroupEventEndedException;
import com.community.group.exception.GroupEventLimitReachedException;
import com.community.group.repository.master.GroupEventQueries;
import com.community.group.repository.master.GroupEventRepository;
import com.community.group.repository.master.GroupEventResponseRepository;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 일정 쓰기(community api-groups.md 9절 — 4단계) — 모임장·일정 관리(EVENTS) 매니저의 만들기·수정·취소와 멤버의 참석 응답.
 * <b>모두 소모임 행을 먼저 잠근다</b>({@link GroupAccess#lockActive}) — 앞으로 열려 있는 일정 수 상한과 같은 사람의 응답이 동시에 겹쳐도 어긋나지 않고, 나가기·강퇴·해산과 한 줄로 선다.
 *
 * <p>판정 차례(1단계와 같다): 로그인 401 → 소모임 404 · 403 {@code GROUP_INACTIVE} → 역할(일반 멤버·비멤버 403 {@code GROUP_LEADER_ONLY}, 일정 관리가 없는 매니저
 * 403 {@code GROUP_MANAGER_PERMISSION_DENIED} · 응답은 멤버 403 {@code GROUP_NOT_MEMBER}) → 쓰기 자격(403 {@code EMAIL_NOT_VERIFIED} → {@code ACCOUNT_RESTRICTED}
 * → {@code BUSINESS_ACCOUNT_RESTRICTED}) → 입력 400 → 대상·상태(404 {@code GROUP_EVENT_NOT_FOUND} · 409). 수정·취소는 입력이 일정 상태에 기대어 일정·상태를 먼저 본다(원본과 같다).
 * 만들기·수정(실제로 바뀐 것이 있을 때)·취소는 멤버 전원(일을 한 사람 제외)에게 알림 {@code GROUP_EVENT}.
 */
@Service
public class GroupEventService {

    private static final Logger log = LoggerFactory.getLogger(GroupEventService.class);

    static final String CREATED = "CREATED";
    static final String UPDATED = "UPDATED";
    static final String CANCELED = "CANCELED";

    private final GroupAccess access;
    private final GroupEventQueryService query;
    private final GroupEventRepository eventRepository;
    private final GroupEventResponseRepository responseRepository;
    private final GroupEventQueries events;
    private final GroupMemberRepository members;
    private final GroupSettingsService settings;
    private final GroupNotices notices;
    private final SnowflakeIdGenerator ids;

    public GroupEventService(GroupAccess access, GroupEventQueryService query, GroupEventRepository eventRepository, GroupEventResponseRepository responseRepository,
                             GroupEventQueries events, GroupMemberRepository members, GroupSettingsService settings, GroupNotices notices, SnowflakeIdGenerator ids) {
        this.access = access;
        this.query = query;
        this.eventRepository = eventRepository;
        this.responseRepository = responseRepository;
        this.events = events;
        this.members = members;
        this.settings = settings;
        this.notices = notices;
        this.ids = ids;
    }

    // ── 모임장 · 일정 관리 매니저: 만들기 · 수정 · 취소 ──────────────────────────

    /** 만들기(201) — 앞으로 열려 있는 일정이 운영 한도 이상이면 409 {@code GROUP_EVENT_LIMIT_REACHED}. 소모임의 최근 활동 시각이 갱신된다 */
    @Transactional
    public GroupEventDetailView create(String rawId, Long userId, GroupEventRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.EVENTS);
        access.requireWriter(me);
        Instant now = Instant.now();

        GroupRuleValues.Policy policy = settings.policy(); // 일정 한도 — 관리자 앱 운영 한도(없으면 설정 파일), 요청마다 읽는다
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        GroupEventRules.Parsed in = GroupEventRules.parse(request, policy, now, null, errors);
        if (in.mode() == GroupEventMode.OFFLINE && !in.offlineAck()) {
            errors.put("offlineAck", new MessageRef("group.event.offlineAck.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (events.countUpcoming(group.getId(), now) >= policy.eventMaxUpcoming()) {
            throw new GroupEventLimitReachedException(policy.eventMaxUpcoming());
        }

        GroupEventEntity event = GroupEventEntity.create(ids.nextId(), group.getId(), me, in.title(), in.description(), in.startsAt(), in.endsAt(), in.mode(),
                in.place(), now);
        eventRepository.persist(event);
        group.touchActivity(now);
        notifyMembers(group, event, CREATED, null, me);
        log.info("소모임 일정을 만들었다 groupId={} eventId={} by userId={}", group.getId(), event.getId(), me);
        return query.detailOf(group, event, me);
    }

    /**
     * 수정 — 끝나지 않은 일정만(끝남 409 {@code GROUP_EVENT_ENDED} · 취소 409 {@code GROUP_EVENT_CANCELED}). 진행 중이면 시작 시각을 바꿀 수 없다({@code fields.startsAt}).
     * 오프라인 확인은 온라인을 오프라인으로 바꿀 때만 묻는다. 실제로 바뀐 것이 있을 때만 저장·알림, 참석 응답은 그대로 남는다.
     */
    @Transactional
    public GroupEventDetailView update(String rawId, String rawEventId, Long userId, GroupEventRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.EVENTS);
        access.requireWriter(me);
        GroupEventEntity event = query.requireEvent(group, rawEventId);
        Instant now = Instant.now();
        State state = event.state(now);
        requireOpen(state);

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        GroupEventRules.Parsed in = GroupEventRules.parse(request, settings.policy(), now, state == State.ONGOING ? event.getStartsAt() : null, errors);
        if (in.mode() == GroupEventMode.OFFLINE && event.getMode() != GroupEventMode.OFFLINE && !in.offlineAck()) {
            errors.put("offlineAck", new MessageRef("group.event.offlineAck.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        boolean changed = !Objects.equals(in.title(), event.getTitle()) || !Objects.equals(in.description(), event.getDescription())
                || !Objects.equals(in.startsAt(), event.getStartsAt()) || !Objects.equals(in.endsAt(), event.getEndsAt()) || in.mode() != event.getMode()
                || !Objects.equals(in.place(), event.getPlace());
        if (changed) {
            event.apply(in.title(), in.description(), in.startsAt(), in.endsAt(), in.mode(), in.place(), now);
            group.touchActivity(now);
            notifyMembers(group, event, UPDATED, null, me);
            log.info("소모임 일정을 고쳤다 groupId={} eventId={} by userId={}", group.getId(), event.getId(), me);
        }
        return query.detailOf(group, event, me);
    }

    /** 취소(204) — 이미 취소된 일정은 알림 없이 그대로 성공(멱등). 끝난 일정은 409 {@code GROUP_EVENT_ENDED}. 사유 100자 이하({@code fields.reason}) */
    @Transactional
    public void cancel(String rawId, String rawEventId, Long userId, String reasonRaw) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.EVENTS);
        access.requireWriter(me);
        GroupEventEntity event = query.requireEvent(group, rawEventId);
        Instant now = Instant.now();
        State state = event.state(now);
        if (state == State.CANCELED) {
            return;
        }
        if (state == State.ENDED) {
            throw new GroupEventEndedException();
        }
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String reason = GroupEventRules.cancelReason(reasonRaw, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        event.cancel(reason, now);
        notifyMembers(group, event, CANCELED, reason, me);
        log.info("소모임 일정을 취소했다 groupId={} eventId={} by userId={}", group.getId(), event.getId(), me);
    }

    // ── 멤버: 참석 응답 ─────────────────────────────────────────────────────

    /** 참석·불참·미정을 고른다(이미 같은 응답이면 그대로 200). 시작하기 전까지만 */
    @Transactional
    public GroupEventResponseResultView respond(String rawId, String rawEventId, Long userId, GroupEventResponseRequest request) {
        return write(rawId, rawEventId, userId, request, true);
    }

    /** 내 응답을 지운다(없어도 200 — 멱등) */
    @Transactional
    public GroupEventResponseResultView clear(String rawId, String rawEventId, Long userId) {
        return write(rawId, rawEventId, userId, null, false);
    }

    private GroupEventResponseResultView write(String rawId, String rawEventId, Long userId, GroupEventResponseRequest request, boolean set) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        query.requireMember(group, me);
        access.requireWriter(me);
        GroupEventResponseType response = null;
        if (set) {
            response = GroupEventResponseType.of(request == null ? null : request.response())
                    .orElseThrow(() -> ValidationException.of("response", new MessageRef("group.event.response.invalid")));
        }
        GroupEventEntity event = query.requireEvent(group, rawEventId);
        Instant now = Instant.now();
        State state = event.state(now);
        if (state == State.CANCELED) {
            throw new GroupEventCanceledException();
        }
        if (state != State.UPCOMING) {
            throw new GroupEventClosedException();
        }
        GroupEventResponseEntity mine = responseRepository.findByEventIdAndUserId(event.getId(), me).orElse(null);
        if (response == null) {
            if (mine != null) {
                responseRepository.delete(mine);
            }
        } else if (mine == null) {
            responseRepository.persist(GroupEventResponseEntity.of(event.getId(), me, response, now));
        } else {
            mine.change(response, now);
        }
        responseRepository.flush(); // 방금 바꾼 응답이 집계에 보이도록 밀어낸 뒤 센다
        GroupEventQueries.Counts counts = events.counts(group.getId(), event.getId());
        return new GroupEventResponseResultView(response == null ? null : response.name(),
                new GroupEventCountsView(counts.going(), counts.notGoing(), counts.maybe()));
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static void requireOpen(State state) {
        if (state == State.CANCELED) {
            throw new GroupEventCanceledException();
        }
        if (state == State.ENDED) {
            throw new GroupEventEndedException();
        }
    }

    /** 지금 멤버 전원(일을 한 사람 제외)에게 일정 알림 — 개인 설정에서 「소모임 일정」을 끈 사람에게는 발행기가 만들지 않는다 */
    private void notifyMembers(GroupEntity group, GroupEventEntity event, String action, String reason, long actorId) {
        for (GroupMemberEntity m : members.findByGroupIdAndStatus(group.getId(), GroupMemberStatus.ACTIVE)) {
            if (!m.getUserId().equals(actorId)) {
                notices.event(group, m.getUserId(), actorId, event.getId(), event.getTitle(), action, reason);
            }
        }
    }
}
