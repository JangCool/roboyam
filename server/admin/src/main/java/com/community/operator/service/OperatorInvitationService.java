package com.community.operator.service;

import com.community.adminauth.config.AdminAuthProperties;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.dto.OperatorInvitationIssued;
import com.community.operator.dto.request.OperatorInviteRequest;
import com.community.operator.dto.response.OperatorInvitationView;
import com.community.operator.dto.response.OperatorRoleRef;
import com.community.operator.entity.OperatorInvitationEntity;
import com.community.operator.entity.OperatorInvitationRoleEntity;
import com.community.operator.exception.InvitationDuplicatedException;
import com.community.operator.exception.InvitationNotFoundException;
import com.community.operator.exception.OperatorEmailDuplicatedException;
import com.community.operator.repository.master.OperatorInvitationRepository;
import com.community.operator.repository.master.OperatorInvitationRoleRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.role.entity.RoleEntity;
import com.community.role.repository.master.RoleRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 초대 — 1회용 초대 링크를 이메일로 보낸다 (docs/features/roles-contract.md, docs/domain/operator-permissions.md "추가 확정").
 *
 * <ul>
 *   <li>링크는 유효 72시간(가정, {@code roboyam.admin-auth.invitation-valid-hours}). 토큰 원문은 <b>커밋된 뒤 메일로만</b> 나가고 DB 에는 SHA-256 해시만 남는다.</li>
 *   <li>같은 이메일에 <b>유효한 초대가 이미 있으면</b> 409 {@code INVITATION_DUPLICATED}, 기한이 지난 초대는 새 초대가 대신한다(옛 것을 지운다).
 *       이미 운영자인 이메일은 409 {@code OPERATOR_EMAIL_DUPLICATED}(운영자 바로 추가가 같은 이메일의 유효한 초대를 막는 409 {@code OPERATOR_INVITATION_PENDING} 의 짝). 수락하지 않은 초대는 같은 이메일에 하나뿐이다(유일 인덱스).</li>
 *   <li>역할을 함께 정할 수 있고 {@link OperatorRoleAssignmentPolicy} 가 운영자에게 역할을 줄 때와 같은 검사를 한다(권한 상승 방지 포함).</li>
 *   <li>취소하면 초대를 지워 링크를 못 쓰게 한다. 링크를 받아 확인·수락하는 일은 {@link OperatorInvitationAcceptService}(로그인 전 공개 API) 가 한다. 취소한 초대는 행이 지워지므로 링크로는 「모르는 토큰」(404 {@code INVITATION_NOT_FOUND}) 과 구분되지 않는다.</li>
 * </ul>
 * 메일은 이 트랜잭션이 끝난 뒤 {@code OperatorMailSender} 가 보낸다(메일 서버가 느려도 DB 연결을 붙잡지 않는다). 이메일 주소·토큰을 로그에 남기지 않는다.
 */
@Service
public class OperatorInvitationService {

    private static final Logger log = LoggerFactory.getLogger(OperatorInvitationService.class);

    private final OperatorInvitationRepository invitations;
    private final OperatorInvitationRoleRepository invitationRoles;
    private final OperatorRepository operators;
    private final RoleRepository roles;
    private final OperatorRoleAssignmentPolicy rolePolicy;
    private final AdminAuthProperties props;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;

    public OperatorInvitationService(OperatorInvitationRepository invitations, OperatorInvitationRoleRepository invitationRoles, OperatorRepository operators,
                                     RoleRepository roles, OperatorRoleAssignmentPolicy rolePolicy, AdminAuthProperties props, SnowflakeIdGenerator ids,
                                     AuditLogService audit, ApplicationEventPublisher events, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.invitations = invitations;
        this.invitationRoles = invitationRoles;
        this.operators = operators;
        this.roles = roles;
        this.rolePolicy = rolePolicy;
        this.props = props;
        this.ids = ids;
        this.audit = audit;
        this.events = events;
    }

    /** 아직 수락하지 않은 초대 전부(기한이 지난 것도 {@code expired} 로 함께), 최근 초대가 앞 */
    @Transactional(readOnly = true)
    public List<OperatorInvitationView> list() {
        Instant now = Instant.now();
        List<OperatorInvitationEntity> pending = invitations.findByConsumedAtIsNullOrderByCreatedAtDescIdDesc();
        Map<Long, List<String>> roleIds = new HashMap<>();
        if (!pending.isEmpty()) {
            invitationRoles.findByInvitationIdIn(pending.stream().map(OperatorInvitationEntity::getId).toList())
                    .forEach(row -> roleIds.computeIfAbsent(row.getInvitationId(), k -> new ArrayList<>()).add(row.getRoleId()));
        }
        Set<String> allRoleIds = roleIds.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        Map<String, RoleEntity> roleById = roles.findAllById(allRoleIds).stream().collect(Collectors.toMap(RoleEntity::getRoleId, Function.identity()));
        return pending.stream().map(invitation -> new OperatorInvitationView(Long.toString(invitation.getId()), invitation.getEmail(),
                roleIds.getOrDefault(invitation.getId(), List.of()).stream().filter(roleById::containsKey).sorted()
                        .map(id -> new OperatorRoleRef(id, roleById.get(id).getRoleName())).toList(),
                invitation.getCreatedAt(), invitation.getExpiresAt(), invitation.isExpired(now))).toList();
    }

    /**
     * 초대 링크 메일을 보낸다(커밋 뒤). 검사 순서: 이메일 형식(400) → 이미 운영자(409) → 역할 → 유효한 초대가 이미 있음(409).
     * 운영자 바로 추가({@link OperatorService#create})와 같은 {@link OperatorIdentityLock} 아래에서 검사해, 같은 이메일로 동시에 직접 추가와 초대가 들어와도 하나만 성공한다.
     */
    @Transactional
    public void invite(AdminActor actor, OperatorInviteRequest request) {
        String email = UserPolicy.normalizeEmail(request.email());
        UserPolicy.emailViolation(email).ifPresent(message -> {
            throw ValidationException.of("email", message);
        });
        OperatorIdentityLock.acquire(entityManager);
        if (operators.existsByEmail(email)) {
            throw new OperatorEmailDuplicatedException();
        }
        List<RoleEntity> chosen = rolePolicy.resolve(actor, request.roleIds(), Set.of());

        Instant now = Instant.now();
        Optional<OperatorInvitationEntity> existing = invitations.findByEmailAndConsumedAtIsNull(email);
        if (existing.isPresent()) {
            if (!existing.get().isExpired(now)) {
                throw new InvitationDuplicatedException();
            }
            invitations.delete(existing.get()); // 기한이 지난 초대는 새 초대가 대신한다 — 지운 뒤 내보내야 유일 인덱스에 걸리지 않는다
            invitations.flush();
        }

        String token = Digests.newToken();
        Instant expiresAt = now.plus(Duration.ofHours(props.invitationValidHours()));
        OperatorInvitationEntity invitation = OperatorInvitationEntity.create(ids.nextId(), email, Digests.sha256Hex(token), actor.operatorId(), now, expiresAt);
        try {
            invitations.persistAndFlush(invitation); // 아래 역할 행이 참조하므로 먼저 내보낸다. 동시에 같은 이메일이 들어오면 여기서 걸린다
        } catch (DataIntegrityViolationException e) {
            throw new InvitationDuplicatedException();
        }
        for (RoleEntity role : chosen) {
            invitationRoles.persist(OperatorInvitationRoleEntity.create(ids.nextId(), invitation.getId(), role.getRoleId()));
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.INVITATION_CREATE, AuditResult.SUCCESS)
                .target("INVITATION", Long.toString(invitation.getId())).with("roles", chosen.stream().map(RoleEntity::getRoleId).toList()));
        events.publishEvent(new OperatorInvitationIssued(email, token, expiresAt));
        log.info("운영자 초대를 만들었다 invitationId={} 역할={}개 by operatorId={}", invitation.getId(), chosen.size(), actor.operatorId());
    }

    /** 초대를 취소한다 — 링크를 못 쓰게 한다. 없는(이미 취소·수락된) 초대는 404 */
    @Transactional
    public void cancel(AdminActor actor, String invitationId) {
        long id;
        try {
            id = Long.parseLong(invitationId);
        } catch (NumberFormatException e) {
            throw new InvitationNotFoundException();
        }
        OperatorInvitationEntity invitation = invitations.findById(id).filter(found -> found.getConsumedAt() == null)
                .orElseThrow(InvitationNotFoundException::new);
        invitations.delete(invitation);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.INVITATION_CANCEL, AuditResult.SUCCESS)
                .target("INVITATION", Long.toString(id)));
        log.info("운영자 초대를 취소했다 invitationId={} by operatorId={}", id, actor.operatorId());
    }
}
