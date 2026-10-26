package com.community.group.service;

import com.community.group.dto.response.GroupEventAttendeePageView;
import com.community.group.dto.response.GroupEventCountsView;
import com.community.group.dto.response.GroupEventDetailView;
import com.community.group.dto.response.GroupEventPageView;
import com.community.group.dto.response.GroupEventRowView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupEventEntity;
import com.community.group.entity.GroupEventEntity.State;
import com.community.group.entity.GroupEventMode;
import com.community.group.entity.GroupEventResponseType;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.exception.GroupEventNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.repository.master.GroupEventQueries;
import com.community.group.repository.master.GroupEventRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 일정 읽기(community api-groups.md 9절 — 4단계) — 목록(다가오는/지난) · 상세 · 참석자. <b>일정은 멤버(모임장 포함)에게만 보인다</b>.
 * 판정 차례: 로그인 401 → 소모임(404 {@code GROUP_NOT_FOUND} · 403 {@code GROUP_INACTIVE}) → 멤버(403 {@code GROUP_NOT_MEMBER}) → 입력 400 → 일정(404 {@code GROUP_EVENT_NOT_FOUND}).
 * 쓰기는 {@link GroupEventService} — 여기 {@link #detailOf} 는 그 결과를 같은 모양으로 만드는 데도 쓴다.
 */
@Service
public class GroupEventQueryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final String SCOPE_UPCOMING = "upcoming";
    static final String SCOPE_PAST = "past";

    private final GroupAccess access;
    private final GroupEventRepository eventRepository;
    private final GroupEventQueries events;
    private final GroupPeople people;

    public GroupEventQueryService(GroupAccess access, GroupEventRepository eventRepository, GroupEventQueries events, GroupPeople people) {
        this.access = access;
        this.eventRepository = eventRepository;
        this.events = events;
        this.people = people;
    }

    /** 일정 목록 — {@code scope} upcoming(기본, 시작 전·진행 중 가까운 순) · past(끝남·취소 최근 순). 크기 1~50(기본 20) */
    @Transactional(readOnly = true)
    public GroupEventPageView list(String rawId, Long userId, String scopeRaw, String pageRaw, String sizeRaw) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        requireMember(group, me);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String scope = scopeRaw == null || scopeRaw.isBlank() ? SCOPE_UPCOMING : scopeRaw.strip();
        if (!SCOPE_UPCOMING.equals(scope) && !SCOPE_PAST.equals(scope)) {
            invalid.put("scope", new MessageRef("group.event.scope.invalid"));
        }
        Integer page = GroupQueryService.intIn(pageRaw, 1, 1, 100_000);
        if (page == null) {
            invalid.put("page", new MessageRef("group.list.page.invalid"));
        }
        Integer size = GroupQueryService.intIn(sizeRaw, DEFAULT_SIZE, 1, MAX_SIZE);
        if (size == null) {
            invalid.put("size", new MessageRef("group.list.size.invalid", String.valueOf(MAX_SIZE)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        boolean past = SCOPE_PAST.equals(scope);
        Instant now = Instant.now();
        long offset = (long) (page - 1) * size;
        List<GroupEventEntity> found = new ArrayList<>(past ? events.past(group.getId(), now, offset, size + 1) : events.upcoming(group.getId(), now, offset, size + 1));
        boolean hasNext = found.size() > size;
        if (hasNext) {
            found = found.subList(0, size);
        }
        long total = past ? events.countPast(group.getId(), now) : events.countUpcoming(group.getId(), now);
        return new GroupEventPageView(rows(group, found, me, now), page, hasNext, total);
    }

    @Transactional(readOnly = true)
    public GroupEventDetailView detail(String rawId, String rawEventId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        requireMember(group, me);
        return detailOf(group, requireEvent(group, rawEventId), me);
    }

    /** 참석자 — 참석(GOING)으로 응답한 지금 멤버, 응답이 최근인 순. 크기 1~50(기본 20) */
    @Transactional(readOnly = true)
    public GroupEventAttendeePageView attendees(String rawId, String rawEventId, Long userId, String pageRaw, String sizeRaw) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        requireMember(group, me);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Integer page = GroupQueryService.intIn(pageRaw, 1, 1, 100_000);
        if (page == null) {
            invalid.put("page", new MessageRef("group.list.page.invalid"));
        }
        Integer size = GroupQueryService.intIn(sizeRaw, DEFAULT_SIZE, 1, MAX_SIZE);
        if (size == null) {
            invalid.put("size", new MessageRef("group.list.size.invalid", String.valueOf(MAX_SIZE)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        GroupEventEntity event = requireEvent(group, rawEventId);
        List<GroupEventQueries.Attendee> found = new ArrayList<>(events.attendees(group.getId(), event.getId(), (long) (page - 1) * size, size + 1));
        boolean hasNext = found.size() > size;
        if (hasNext) {
            found = found.subList(0, size);
        }
        GroupPeople.Members persons = people.members(found.stream().map(GroupEventQueries.Attendee::userId).toList(), false);
        List<GroupEventAttendeePageView.Item> items = found.stream()
                .map(a -> new GroupEventAttendeePageView.Item(GroupUsers.of(persons.of(a.userId())), a.respondedAt(), group.getLeaderId().equals(a.userId())))
                .toList();
        return new GroupEventAttendeePageView(items, page, hasNext, events.counts(group.getId(), event.getId()).going());
    }

    // ── 모양 만들기(쓰기 서비스도 쓴다) ────────────────────────────────────────

    /** 일정 상세 — 내 응답·집계·권한을 붙인다. 부르는 쪽의 트랜잭션 안에서 */
    GroupEventDetailView detailOf(GroupEntity group, GroupEventEntity event, long viewerId) {
        Instant now = Instant.now();
        State state = event.state(now);
        GroupEventQueries.Counts counts = events.counts(group.getId(), event.getId());
        GroupEventResponseType mine = events.myResponses(viewerId, List.of(event.getId())).get(event.getId());
        GroupMemberEntity row = access.membership(group.getId(), viewerId).orElse(null);
        // 모임장 또는 「일정 관리」(EVENTS)를 맡은 매니저 — 소모임의 모든 일정을 고칠 수 있다(만든 사람과 무관)
        boolean manager = GroupAccess.can(group, row, viewerId, GroupManagerPermission.EVENTS);
        GroupPeople.Members persons = people.members(List.of(event.getCreatorId()), true);
        return new GroupEventDetailView(Long.toString(event.getId()), event.getTitle(), event.getStartsAt(), event.getEndsAt(), event.getMode().name(),
                event.getPlace(), state.name(), counts.going(), mine == null ? null : mine.name(), event.getDescription(),
                new GroupEventCountsView(counts.going(), counts.notGoing(), counts.maybe()), event.getMode() == GroupEventMode.OFFLINE || group.isOffline(),
                GroupUsers.of(persons.of(event.getCreatorId())), state == State.UPCOMING, manager && (state == State.UPCOMING || state == State.ONGOING),
                event.getCancelReason(), event.getCanceledAt());
    }

    private List<GroupEventRowView> rows(GroupEntity group, List<GroupEventEntity> found, long viewerId, Instant now) {
        List<Long> ids = found.stream().map(GroupEventEntity::getId).toList();
        Map<Long, Integer> going = events.goingCounts(group.getId(), ids);
        Map<Long, GroupEventResponseType> mine = events.myResponses(viewerId, ids);
        return found.stream().map(e -> {
            GroupEventResponseType my = mine.get(e.getId());
            return new GroupEventRowView(Long.toString(e.getId()), e.getTitle(), e.getStartsAt(), e.getEndsAt(), e.getMode().name(), e.getPlace(),
                    e.state(now).name(), going.getOrDefault(e.getId(), 0), my == null ? null : my.name());
        }).toList();
    }

    // ── 판정 도우미 ─────────────────────────────────────────────────────────

    /** 이 소모임의 지금 멤버(모임장 포함)인가 — 아니면 403 {@code GROUP_NOT_MEMBER}(신청 대기·나감·강퇴 포함) */
    void requireMember(GroupEntity group, long userId) {
        if (!group.getLeaderId().equals(userId) && access.membership(group.getId(), userId).isEmpty()) {
            throw new GroupNotMemberException();
        }
    }

    /** 이 소모임의 일정 — 없거나 다른 소모임의 일정이면 404 {@code GROUP_EVENT_NOT_FOUND} */
    GroupEventEntity requireEvent(GroupEntity group, String rawEventId) {
        Long eventId = GroupQueryService.parseLong(rawEventId);
        if (eventId == null) {
            throw new GroupEventNotFoundException();
        }
        return eventRepository.findByIdAndGroupId(eventId, group.getId()).orElseThrow(GroupEventNotFoundException::new);
    }
}
