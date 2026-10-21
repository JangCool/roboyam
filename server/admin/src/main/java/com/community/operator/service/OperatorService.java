package com.community.operator.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.dto.OperatorCredentialsIssued;
import com.community.operator.dto.request.OperatorCreateRequest;
import com.community.operator.dto.request.OperatorRejectRequest;
import com.community.operator.dto.request.OperatorStatusRequest;
import com.community.operator.dto.request.OperatorUpdateRequest;
import com.community.operator.dto.response.OperatorPage;
import com.community.operator.dto.response.OperatorRoleRef;
import com.community.operator.dto.response.OperatorView;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.entity.OperatorTab;
import com.community.operator.exception.OperatorEmailDuplicatedException;
import com.community.operator.exception.OperatorNotFoundException;
import com.community.operator.exception.OperatorNotPendingException;
import com.community.operator.exception.OperatorPeriodReversedException;
import com.community.operator.exception.OperatorRootProtectedException;
import com.community.operator.repository.master.OperatorRepository;
import com.community.operator.repository.master.OperatorRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.role.dto.OperatorRoleRow;
import com.community.role.entity.OperatorRoleEntity;
import com.community.role.entity.RoleEntity;
import com.community.role.repository.master.OperatorRoleRepository;
import com.community.role.repository.master.RoleRepositoryQDSL;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자(관리자 앱 계정) 목록·만들기·수정·상태·반려 (docs/features/roles-contract.md, docs/domain/operator-permissions.md).
 *
 * <ul>
 *   <li><b>루트 계정(최고 관리자)은 어떤 변경도 거절한다</b>(409 {@code OPERATOR_ROOT_PROTECTED}) — 루트가 하나뿐이고 전면 보호되므로 마지막 활성 최고 관리자는 늘 지켜진다.</li>
 *   <li>상태는 네 개(승인·잠금·만료·사용)다. 잠금({@code locked})을 바꾸려면 {@code operator.lock}, 나머지는 {@code operator.manage}.</li>
 *   <li>역할을 주고 빼는 일은 {@link OperatorRoleAssignmentPolicy} 가 검사한다 — {@code role.grant} 필요, <b>권한 상승 방지</b>(자기 권한 범위 안에서만), 종합 관리 역할은 최고 관리자만.</li>
 *   <li>운영자 만들기는 승인된 상태로 만들고 <b>임시 비밀번호는 커밋된 뒤 이메일로만 안내</b>한다(응답·DB·로그에 남지 않는다. 메일 서버가 없으면 보내지 못한다 — 받은 사람은 비밀번호 재설정으로도 비밀번호를 정할 수 있다).</li>
 *   <li>반려는 승인 대기 건만 되고(409 {@code OPERATOR_NOT_PENDING}) 반려한 신청은 지운다(보관 방식은 미확정). 지우는 API 는 따로 없다 — 「사용 중지」가 대신한다.</li>
 *   <li>같은 운영자를 동시에 바꾸는 요청은 행 잠금으로 줄 세운다. 모든 변경은 감사 기록으로 남긴다(비밀번호·토큰 제외).</li>
 * </ul>
 */
@Service
public class OperatorService {

    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);

    private final OperatorRepository operators;
    private final OperatorRepositoryQDSL operatorQueries;
    private final RoleRepositoryQDSL roleQueries;
    private final OperatorRoleRepository operatorRoles;
    private final OperatorRoleAssignmentPolicy rolePolicy;
    private final PasswordEncoder encoder;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;
    private final ApplicationEventPublisher events;

    public OperatorService(OperatorRepository operators, OperatorRepositoryQDSL operatorQueries, RoleRepositoryQDSL roleQueries,
                           OperatorRoleRepository operatorRoles, OperatorRoleAssignmentPolicy rolePolicy, PasswordEncoder encoder, SnowflakeIdGenerator ids,
                           AuditLogService audit, AccessDeniedRecorder denied, ApplicationEventPublisher events) {
        this.operators = operators;
        this.operatorQueries = operatorQueries;
        this.roleQueries = roleQueries;
        this.operatorRoles = operatorRoles;
        this.rolePolicy = rolePolicy;
        this.encoder = encoder;
        this.ids = ids;
        this.audit = audit;
        this.denied = denied;
        this.events = events;
    }

    /** 탭별 목록과 탭마다 몇 명인지. 키워드는 이름·이메일 부분 일치(대소문자 무시), 비어 있으면 거르지 않는다. 최근 등록순 */
    @Transactional(readOnly = true)
    public OperatorPage list(OperatorTab tab, String keyword) {
        Instant now = Instant.now();
        List<OperatorEntity> found = operatorQueries.search(tab, keyword, now);
        Map<Long, List<OperatorRoleRow>> roles = roleQueries.rolesOfOperators(found.stream().map(OperatorEntity::getId).toList());
        List<OperatorView> items = found.stream().map(operator -> view(operator, roles.getOrDefault(operator.getId(), List.of()), now)).toList();
        return new OperatorPage(items, items.size(), operatorQueries.counts(now));
    }

    /** 계정을 바로 만든다 — 승인된 상태로. 검사 순서: 이름·이메일 형식(400) → 이메일 중복(409) → 역할 */
    @Transactional
    public OperatorView create(AdminActor actor, OperatorCreateRequest request) {
        String name = request.operatorName().strip();
        String email = UserPolicy.normalizeEmail(request.email());
        UserPolicy.emailViolation(email).ifPresent(message -> {
            throw ValidationException.of("email", message);
        });
        if (operators.existsByEmail(email)) {
            throw new OperatorEmailDuplicatedException();
        }
        List<RoleEntity> roles = rolePolicy.resolve(actor, request.roleIds(), Set.of());

        Instant now = Instant.now();
        String temporaryPassword = TemporaryPassword.generate();
        OperatorEntity operator = OperatorEntity.createApproved(ids.nextId(), email, name, encoder.encode(temporaryPassword), now);
        try {
            operators.persistAndFlush(operator); // 아래 역할 행이 참조하므로 먼저 내보낸다. 동시에 같은 이메일이 들어오면 여기서 걸린다
        } catch (DataIntegrityViolationException e) {
            throw new OperatorEmailDuplicatedException();
        }
        for (RoleEntity role : roles) {
            operatorRoles.persist(OperatorRoleEntity.create(ids.nextId(), operator.getId(), role.getRoleId(), actor.operatorId(), now));
        }
        audit.record(operatorEvent(actor, AuditActions.OPERATOR_CREATE, operator.getId()).with("roles", roles.stream().map(RoleEntity::getRoleId).toList()));
        events.publishEvent(new OperatorCredentialsIssued(email, name, temporaryPassword));
        log.info("운영자를 만들었다 operatorId={} 역할={}개 by operatorId={}", operator.getId(), roles.size(), actor.operatorId());
        return view(operator, roles.stream().map(role -> new OperatorRoleRow(operator.getId(), role.getRoleId(), role.getRoleName())).toList(), now);
    }

    /**
     * 이름·역할·계정 기간을 고친다. 검사 순서: 없는 운영자(404) → 루트(409) → 이름(400) → 기간 거꾸로(400) → 역할.
     * {@code roleIds} 가 없으면(null) 역할은 그대로 둔다.
     */
    @Transactional
    public OperatorView update(AdminActor actor, String operatorId, OperatorUpdateRequest request) {
        OperatorEntity operator = lock(operatorId);
        requireNotRoot(operator);
        if (request.accountStartAt() != null && request.accountEndAt() != null && request.accountStartAt().isAfter(request.accountEndAt())) {
            throw new OperatorPeriodReversedException();
        }

        Instant now = Instant.now();
        AuditEvent event = operatorEvent(actor, AuditActions.OPERATOR_UPDATE, operator.getId());
        if (request.roleIds() != null) {
            Set<String> current = roleQueries.roleIdsOfOperator(operator.getId());
            List<RoleEntity> roles = rolePolicy.resolve(actor, request.roleIds(), current);
            Set<String> wanted = new LinkedHashSet<>(roles.stream().map(RoleEntity::getRoleId).toList());
            List<String> granted = wanted.stream().filter(id -> !current.contains(id)).toList();
            List<String> revoked = current.stream().filter(id -> !wanted.contains(id)).toList();
            roleQueries.deleteOperatorRoles(operator.getId(), revoked);
            for (String roleId : granted) {
                operatorRoles.persist(OperatorRoleEntity.create(ids.nextId(), operator.getId(), roleId, actor.operatorId(), now));
            }
            if (!granted.isEmpty() || !revoked.isEmpty()) {
                event = event.with("rolesGranted", granted).with("rolesRevoked", revoked);
            }
        }
        operator.updateProfile(request.operatorName().strip(), request.accountStartAt(), request.accountEndAt(), now);
        audit.record(event);
        return viewOf(operator, now);
    }

    /**
     * 계정 상태(승인·잠금·만료·사용)를 바꾼다 — 보낸 칸만. 잠금을 바꾸려면 {@code operator.lock}, 나머지는 {@code operator.manage}.
     * 로그인 상태는 요청마다 다시 읽으므로 잠그거나 사용을 멈추면 그 운영자의 다음 요청부터 막힌다.
     */
    @Transactional
    public OperatorView changeStatus(AdminActor actor, String operatorId, OperatorStatusRequest request) {
        OperatorEntity operator = lock(operatorId);
        requireNotRoot(operator);
        boolean lockChange = request.locked() != null && request.locked() != operator.isLocked();
        if (lockChange && !actor.can(Permission.OPERATOR_LOCK)) {
            denied.record(actor, "MISSING_PERMISSION", "OPERATOR", Long.toString(operator.getId()), List.of(Permission.OPERATOR_LOCK.code()));
            throw new PermissionDeniedException();
        }
        boolean manageChange = request.approved() != null || request.expired() != null || request.enabled() != null;
        if (manageChange && !actor.can(Permission.OPERATOR_MANAGE)) {
            denied.record(actor, "MISSING_PERMISSION", "OPERATOR", Long.toString(operator.getId()), List.of(Permission.OPERATOR_MANAGE.code()));
            throw new PermissionDeniedException();
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (request.approved() != null && request.approved() != operator.isApproved()) {
            before.put("approved", operator.isApproved());
            after.put("approved", request.approved());
        }
        if (lockChange) {
            before.put("locked", operator.isLocked());
            after.put("locked", request.locked());
        }
        if (request.expired() != null && request.expired() != operator.isExpired()) {
            before.put("expired", operator.isExpired());
            after.put("expired", request.expired());
        }
        if (request.enabled() != null && request.enabled() != operator.isEnabled()) {
            before.put("enabled", operator.isEnabled());
            after.put("enabled", request.enabled());
        }

        Instant now = Instant.now();
        if (!after.isEmpty()) {
            operator.changeStatus(request.approved(), request.locked(), request.expired(), request.enabled(), now);
            audit.record(operatorEvent(actor, AuditActions.OPERATOR_STATUS_CHANGE, operator.getId()).with("before", before).with("after", after));
            log.info("운영자 상태를 바꿨다 operatorId={} 바뀐 칸={} by operatorId={}", operator.getId(), after.keySet(), actor.operatorId());
        }
        return viewOf(operator, now);
    }

    /** 승인 대기(신청) 건을 반려한다 — 사유는 선택(200자 이하). 반려한 신청은 지운다 */
    @Transactional
    public void reject(AdminActor actor, String operatorId, OperatorRejectRequest request) {
        OperatorEntity operator = lock(operatorId);
        requireNotRoot(operator);
        if (operator.isApproved()) {
            throw new OperatorNotPendingException();
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        AuditEvent event = operatorEvent(actor, AuditActions.OPERATOR_REJECT, operator.getId());
        operators.delete(operator);
        audit.record(reason == null ? event : event.with("reason", reason));
        log.info("운영자 신청을 반려했다 operatorId={} by operatorId={}", operator.getId(), actor.operatorId());
    }

    private OperatorEntity lock(String operatorId) {
        long id;
        try {
            id = Long.parseLong(operatorId);
        } catch (NumberFormatException e) {
            throw new OperatorNotFoundException();
        }
        return operators.findWithLockById(id).orElseThrow(OperatorNotFoundException::new);
    }

    private static void requireNotRoot(OperatorEntity operator) {
        if (operator.isRoot()) {
            throw new OperatorRootProtectedException();
        }
    }

    private OperatorView viewOf(OperatorEntity operator, Instant now) {
        return view(operator, roleQueries.rolesOfOperators(List.of(operator.getId())).getOrDefault(operator.getId(), List.of()), now);
    }

    private static OperatorView view(OperatorEntity operator, List<OperatorRoleRow> roles, Instant now) {
        List<OperatorRoleRef> refs = operator.isRoot() ? List.of() : new ArrayList<>(roles.stream().map(row -> new OperatorRoleRef(row.roleId(), row.roleName())).toList());
        return new OperatorView(Long.toString(operator.getId()), operator.getEmail(), operator.getOperatorName(), operator.isRoot(), refs,
                operator.getAccountStartAt(), operator.getAccountEndAt(), operator.isApproved(), operator.isLocked(), operator.isExpired(),
                operator.isEnabled(), operator.isPeriodOver(now), operator.getCreatedAt());
    }

    private static AuditEvent operatorEvent(AdminActor actor, String action, long operatorId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operatorId));
    }
}
