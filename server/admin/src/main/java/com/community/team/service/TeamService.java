package com.community.team.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.repository.master.NotificationDeliveryRepository;
import com.community.notification.repository.master.NotificationRecipientRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.team.dto.TeamMemberRow;
import com.community.team.dto.request.TeamMembersRequest;
import com.community.team.dto.request.TeamMoveRequest;
import com.community.team.dto.request.TeamSaveRequest;
import com.community.team.dto.response.TeamCandidateView;
import com.community.team.dto.response.TeamMemberView;
import com.community.team.dto.response.TeamView;
import com.community.team.entity.TeamEntity;
import com.community.team.entity.TeamMemberEntity;
import com.community.team.exception.TeamHasMembersException;
import com.community.team.exception.TeamLimitExceededException;
import com.community.team.exception.TeamNameDuplicatedException;
import com.community.team.exception.TeamNotFoundException;
import com.community.team.exception.TeamOperatorNotFoundException;
import com.community.team.repository.master.TeamMemberRepository;
import com.community.team.repository.master.TeamRepository;
import com.community.team.repository.master.TeamRepositoryQDSL;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 구성(팀) — 팀 만들기·고치기·지우기·순서 옮기기·팀원 바꾸기 (docs/features/notification-contract.md 「알림 도구·조직 구성」).
 *
 * <ul>
 *   <li>이름은 앞뒤 공백을 자른 1~50자이고 대소문자를 무시하고 유일하다(409 {@code TEAM_NAME_DUPLICATED}), 설명은 500자 이하. 팀은 최대 {@value #MAX_TEAMS}개(409 {@code TEAM_LIMIT_EXCEEDED}).</li>
 *   <li>정렬 순서는 서버가 정한다 — 새 팀은 맨 뒤, 옮기기는 한 칸 위·아래로 하고 전체를 1부터 다시 매긴다. 끝에서 더 옮기는 요청은 아무것도 바꾸지 않고 목록을 돌려준다.</li>
 *   <li><b>팀원이 있는 팀은 지울 수 없다</b>(409 {@code TEAM_HAS_MEMBERS}) — 대신 「사용 안 함」. 지우면 이 팀을 추가 수신 팀으로 고른 이벤트에서도 함께 빠진다(DB 연쇄 삭제).</li>
 *   <li>팀원 = 운영자 계정. 변경은 전체 교체이고, <b>새로 넣는 사람은 지금 활성인 운영자만</b> 된다(없거나 활성이 아니면 400 {@code TEAM_OPERATOR_NOT_FOUND}) — 이미 든 사람은 나중에
 *       멈춰도 그대로 둘 수 있다. 멈춘 팀과 활성이 아닌 팀원은 알림을 받지 않는다(발행기가 거른다). 운영자 계정이 지워지면 팀원 행도 함께 지워진다.</li>
 *   <li>같은 팀을 동시에 바꾸는 요청은 행 잠금으로 줄 세운다. 변경은 감사 기록으로 남긴다(이름·사용 여부 전후, 팀원은 추가/뺀 운영자 id — 이메일은 싣지 않는다).</li>
 * </ul>
 * 입구의 권한(조회 {@code team.read}, 변경 {@code team.manage})은 컨트롤러가 선언한다.
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    static final int MAX_TEAMS = 100;
    static final int MAX_MEMBERS = 500;
    static final int NAME_MAX = 50;
    static final int DESCRIPTION_MAX = 500;

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamRepositoryQDSL queries;
    private final NotificationRecipientRepositoryQDSL recipients;
    private final NotificationDeliveryRepository delivery;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;

    public TeamService(TeamRepository teams, TeamMemberRepository members, TeamRepositoryQDSL queries, NotificationRecipientRepositoryQDSL recipients,
                       NotificationDeliveryRepository delivery, SnowflakeIdGenerator ids, AuditLogService audit) {
        this.teams = teams;
        this.members = members;
        this.queries = queries;
        this.recipients = recipients;
        this.delivery = delivery;
        this.ids = ids;
        this.audit = audit;
    }

    /** 모든 팀(정렬 순서대로, 팀원 포함) */
    @Transactional(readOnly = true)
    public List<TeamView> list() {
        return views(teams.findAllByOrderBySortOrderAscIdAsc(), Instant.now());
    }

    @Transactional
    public TeamView create(AdminActor actor, TeamSaveRequest request) {
        Fields fields = validate(request, true);
        List<TeamEntity> all = teams.findAllByOrderBySortOrderAscIdAsc();
        if (all.size() >= MAX_TEAMS) {
            throw new TeamLimitExceededException(MAX_TEAMS);
        }
        if (queries.existsByName(fields.name(), null)) {
            throw new TeamNameDuplicatedException();
        }
        Instant now = Instant.now();
        int nextOrder = all.stream().mapToInt(TeamEntity::getSortOrder).max().orElse(0) + 1;
        TeamEntity team = TeamEntity.create(ids.nextId(), fields.name(), fields.description(), fields.enabled(), nextOrder, actor.operatorId(), now);
        teams.persist(team);
        flushOrDuplicated();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("teamName", team.getTeamName());
        after.put("enabled", team.isEnabled());
        audit.record(event(actor, AuditActions.TEAM_CREATE, team.getId()).with("before", Map.of()).with("after", after));
        log.info("팀을 만들었다 teamId={} by operatorId={}", team.getId(), actor.operatorId());
        return viewOf(team.getId(), now);
    }

    @Transactional
    public TeamView update(AdminActor actor, String teamId, TeamSaveRequest request) {
        TeamEntity team = lock(teamId);
        Fields fields = validate(request, false);
        if (queries.existsByName(fields.name(), team.getId())) {
            throw new TeamNameDuplicatedException();
        }
        boolean enabled = fields.enabledOrNull() == null ? team.isEnabled() : fields.enabledOrNull();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (!team.getTeamName().equals(fields.name())) {
            before.put("teamName", team.getTeamName());
            after.put("teamName", fields.name());
        }
        if (!java.util.Objects.equals(team.getDescription(), fields.description())) {
            before.put("description", team.getDescription() == null ? "" : team.getDescription());
            after.put("description", fields.description() == null ? "" : fields.description());
        }
        if (team.isEnabled() != enabled) {
            before.put("enabled", team.isEnabled());
            after.put("enabled", enabled);
        }
        Instant now = Instant.now();
        if (!after.isEmpty()) {
            team.edit(fields.name(), fields.description(), enabled, actor.operatorId(), now);
            flushOrDuplicated();
            audit.record(event(actor, AuditActions.TEAM_UPDATE, team.getId()).with("before", before).with("after", after));
            log.info("팀을 고쳤다 teamId={} by operatorId={}", team.getId(), actor.operatorId());
        }
        return viewOf(team.getId(), now);
    }

    @Transactional
    public void delete(AdminActor actor, String teamId) {
        TeamEntity team = lock(teamId);
        if (members.countByTeamId(team.getId()) > 0) {
            throw new TeamHasMembersException();
        }
        String name = team.getTeamName();
        teams.delete(team);
        teams.flush();
        audit.record(event(actor, AuditActions.TEAM_DELETE, team.getId()).with("deleted", List.of(name)));
        log.info("팀을 지웠다 teamId={} by operatorId={}", team.getId(), actor.operatorId());
    }

    /** 한 칸 위·아래로 옮기고 전체 목록을 돌려준다 */
    @Transactional
    public List<TeamView> move(AdminActor actor, String teamId, TeamMoveRequest request) {
        TeamEntity target = lock(teamId);
        String direction = request == null || request.direction() == null ? "" : request.direction().strip().toUpperCase(java.util.Locale.ROOT);
        if (!direction.equals("UP") && !direction.equals("DOWN")) {
            throw ValidationException.of("direction", new MessageRef("team.direction.invalid"));
        }
        List<TeamEntity> all = new ArrayList<>(teams.findAllByOrderBySortOrderAscIdAsc());
        int at = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(target.getId())) {
                at = i;
                break;
            }
        }
        int to = direction.equals("UP") ? at - 1 : at + 1;
        Instant now = Instant.now();
        if (at >= 0 && to >= 0 && to < all.size()) {
            all.add(to, all.remove(at));
            for (int i = 0; i < all.size(); i++) {
                TeamEntity team = all.get(i);
                if (team.getSortOrder() != i + 1) {
                    team.reorder(i + 1, actor.operatorId(), now);
                }
            }
            audit.record(event(actor, AuditActions.TEAM_MOVE, target.getId()).with("from", Integer.toString(at + 1)).with("to", Integer.toString(to + 1)));
        } else if (at >= 0) {
            // 끝에서 더 옮기려는 요청 — 바꾸지 않는다. 순서에 틈이 있었으면 이 기회에 1부터 다시 매긴다
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getSortOrder() != i + 1) {
                    all.get(i).reorder(i + 1, actor.operatorId(), now);
                }
            }
        }
        return views(all, now);
    }

    /** 팀원 전체를 바꾼다 */
    @Transactional
    public TeamView setMembers(AdminActor actor, String teamId, TeamMembersRequest request) {
        TeamEntity team = lock(teamId);
        if (request == null || request.operatorIds() == null) {
            throw ValidationException.of("operatorIds", new MessageRef("team.operatorIds.required"));
        }
        if (request.operatorIds().size() > MAX_MEMBERS) {
            throw ValidationException.of("operatorIds", new MessageRef("team.operatorIds.max", String.valueOf(MAX_MEMBERS)));
        }
        Set<Long> wanted = new LinkedHashSet<>();
        for (String raw : request.operatorIds()) {
            Long id = parseId(raw);
            if (id == null) {
                throw new TeamOperatorNotFoundException();
            }
            wanted.add(id);
        }
        Set<Long> current = new LinkedHashSet<>();
        members.findByTeamId(team.getId()).forEach(member -> current.add(member.getOperatorId()));
        Set<Long> added = new LinkedHashSet<>(wanted);
        added.removeAll(current);
        Set<Long> removed = new LinkedHashSet<>(current);
        removed.removeAll(wanted);

        Instant now = Instant.now();
        if (!added.isEmpty() && !new HashSet<>(recipients.active(added, now)).containsAll(added)) {
            throw new TeamOperatorNotFoundException(); // 없는 운영자이거나 지금 활성이 아니다
        }
        if (!removed.isEmpty()) {
            members.deleteByTeamIdAndOperatorIdIn(team.getId(), removed);
        }
        for (Long operatorId : added) {
            members.persist(TeamMemberEntity.create(team.getId(), operatorId, actor.operatorId(), now));
        }
        if (!added.isEmpty() || !removed.isEmpty()) {
            try {
                members.flush();
            } catch (DataIntegrityViolationException e) {
                throw new TeamOperatorNotFoundException(); // 넣는 도중 운영자 계정이 지워졌다
            }
            team.touch(actor.operatorId(), now);
            AuditEvent event = event(actor, AuditActions.TEAM_MEMBERS_UPDATE, team.getId())
                    .with("before", Map.of("memberCount", current.size())).with("after", Map.of("memberCount", wanted.size()));
            if (!added.isEmpty()) {
                event = event.with("added", added.stream().map(String::valueOf).toList());
            }
            if (!removed.isEmpty()) {
                event = event.with("deleted", removed.stream().map(String::valueOf).toList());
            }
            audit.record(event);
            log.info("팀원을 바꿨다 teamId={} 추가={} 뺌={} by operatorId={}", team.getId(), added.size(), removed.size(), actor.operatorId());
        }
        return viewOf(team.getId(), now);
    }

    /** 팀에 넣을 수 있는 운영자(활성 운영자) — 이미 든 사람도 표시해서 */
    @Transactional(readOnly = true)
    public List<TeamCandidateView> candidates(String teamId) {
        Long id = parseId(teamId);
        TeamEntity team = id == null ? null : teams.findById(id).orElse(null);
        if (team == null) {
            throw new TeamNotFoundException();
        }
        Set<Long> already = new HashSet<>();
        members.findByTeamId(team.getId()).forEach(member -> already.add(member.getOperatorId()));
        return queries.activeOperators(Instant.now()).stream()
                .map(row -> new TeamCandidateView(Long.toString(row.operatorId()), row.operatorName(), row.email(), already.contains(row.operatorId()))).toList();
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private TeamEntity lock(String teamId) {
        Long id = parseId(teamId);
        return id == null ? fail() : teams.findWithLockById(id).orElseGet(TeamService::fail);
    }

    private static TeamEntity fail() {
        throw new TeamNotFoundException();
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 영속성 컨텍스트의 변경을 내보내 이름 유일 인덱스 위반(동시 요청)을 중복 오류로 바꾼다 */
    private void flushOrDuplicated() {
        try {
            teams.flush();
        } catch (DataIntegrityViolationException e) {
            throw new TeamNameDuplicatedException();
        }
    }

    private record Fields(String name, String description, Boolean enabledOrNull) {

        boolean enabled() {
            return enabledOrNull == null || enabledOrNull;
        }
    }

    private Fields validate(TeamSaveRequest request, boolean creating) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String name = request == null || request.teamName() == null ? "" : request.teamName().strip();
        if (name.isEmpty()) {
            invalid.put("teamName", new MessageRef("team.name.required"));
        } else if (name.length() > NAME_MAX) {
            invalid.put("teamName", new MessageRef("team.name.max", String.valueOf(NAME_MAX)));
        } else if (name.chars().anyMatch(Character::isISOControl)) {
            invalid.put("teamName", new MessageRef("team.name.control"));
        }
        String description = request == null || request.description() == null ? null : request.description().strip();
        if (description != null && description.isEmpty()) {
            description = null;
        }
        if (description != null && description.length() > DESCRIPTION_MAX) {
            invalid.put("description", new MessageRef("team.description.max", String.valueOf(DESCRIPTION_MAX)));
        } else if (description != null && description.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            invalid.put("description", new MessageRef("team.description.control"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        return new Fields(name, description, request.enabled());
    }

    private static AuditEvent event(AdminActor actor, String action, long teamId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("TEAM", Long.toString(teamId));
    }

    private TeamView viewOf(long teamId, Instant now) {
        return views(teams.findAllByOrderBySortOrderAscIdAsc(), now).stream().filter(view -> view.teamId().equals(Long.toString(teamId))).findFirst()
                .orElseThrow(TeamNotFoundException::new);
    }

    private List<TeamView> views(List<TeamEntity> all, Instant now) {
        Map<Long, List<TeamMemberRow>> byTeam = queries.membersByTeam(now);
        Map<Long, Integer> used = delivery.eventCountByTeam();
        List<TeamView> out = new ArrayList<>();
        int order = 1;
        for (TeamEntity team : all) {
            List<TeamMemberView> memberViews = byTeam.getOrDefault(team.getId(), List.of()).stream()
                    .map(row -> new TeamMemberView(Long.toString(row.operatorId()), row.operatorName(), row.email(), row.active())).toList();
            out.add(new TeamView(Long.toString(team.getId()), team.getTeamName(), team.getDescription(), order++, team.isEnabled(), memberViews, used.getOrDefault(team.getId(), 0)));
        }
        return out;
    }
}
