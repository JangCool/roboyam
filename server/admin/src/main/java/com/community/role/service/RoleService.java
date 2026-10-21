package com.community.role.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.MemberPermission;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.exception.PermissionEscalationException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.role.dto.request.RoleCreateRequest;
import com.community.role.dto.request.RolePermissionsRequest;
import com.community.role.dto.request.RoleUpdateRequest;
import com.community.role.dto.response.RoleView;
import com.community.role.entity.RoleAxis;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RolePermissionEntity;
import com.community.role.entity.RoleScope;
import com.community.role.entity.RoleTypes;
import com.community.role.entity.RoleTypes.RoleTypeDef;
import com.community.role.exception.PermissionUnknownException;
import com.community.role.exception.RoleBuiltinException;
import com.community.role.exception.RoleDerivedException;
import com.community.role.exception.RoleDuplicatedException;
import com.community.role.exception.RoleGeneralSuperOnlyException;
import com.community.role.exception.RoleInUseException;
import com.community.role.exception.RoleNotFoundException;
import com.community.role.exception.RoleTypeDerivedException;
import com.community.role.exception.RoleTypeFixedException;
import com.community.role.exception.RoleTypeMismatchException;
import com.community.role.exception.RoleTypeNotFoundException;
import com.community.role.repository.master.RolePermissionRepository;
import com.community.role.repository.master.RoleRepository;
import com.community.role.repository.master.RoleRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 만들기·고치기·지우기·권한 바꾸기 (docs/features/roles-contract.md, docs/domain/operator-permissions.md).
 *
 * <ul>
 *   <li>역할은 <b>역할 유형</b> 하나에 속한다({@link RoleTypes}). 운영자 역할은 세부 유형의 권한만 후보로 고르고(종합 관리는 전부), 회원 역할은 사이트 범위면 이용 권한·게시판 범위면 게시판 운영 권한만 고른다.</li>
 *   <li>새로 만들 수 있는 것은 운영자 세부 유형 24개와 회원의 <b>사용자 정의</b>뿐이다. 등급(파생)은 409 {@code ROLE_TYPE_DERIVED}, 회원 유형·게시판 단위(제품 고정)는 409 {@code ROLE_TYPE_FIXED}.</li>
 *   <li><b>종합 관리</b> 유형의 운영자 역할은 최고 관리자만 만들고 고치고 지운다(403 {@code ROLE_GENERAL_SUPER_ONLY}).</li>
 *   <li><b>권한 상승 방지</b>: 운영자 역할에 권한을 새로 줄 때는 요청한 운영자가 <b>가진 권한 안에서만</b> 줄 수 있다(403 {@code PERMISSION_ESCALATION}). 이미 있던 권한은 그대로 둘 수 있다.
 *       회원 역할에는 이 규칙이 없다. 운영자 역할의 권한을 바꾸려면 {@code role.grant}, 회원 역할은 {@code role.manage} 가 필요하다.</li>
 *   <li>등급 역할(파생)은 이름·설명을 고치거나 직접 지울 수 없다(409 {@code ROLE_DERIVED}) — 권한만 고친다. 등급 설정이 만들고 지운다.</li>
 *   <li>내장 역할은 못 지운다(409 {@code ROLE_BUILTIN}), 쓰는 운영자·회원이 있으면 못 지운다(409 {@code ROLE_IN_USE}).</li>
 *   <li>변경은 감사 기록으로 남긴다(권한은 {@code granted}/{@code revoked}). 거부(권한 상승·종합 관리)도 따로 커밋되는 감사 기록으로 남는다.</li>
 * </ul>
 * 입구의 권한(role.read / role.manage / role.grant 중 하나)은 컨트롤러가 선언한다. 같은 역할을 동시에 바꾸는 요청은 행 잠금으로 줄 세운다.
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roles;
    private final RoleRepositoryQDSL roleQueries;
    private final RolePermissionRepository rolePermissions;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;
    private final AdminNotices notices;

    public RoleService(RoleRepository roles, RoleRepositoryQDSL roleQueries, RolePermissionRepository rolePermissions, SnowflakeIdGenerator ids,
                       AuditLogService audit, AccessDeniedRecorder denied, AdminNotices notices) {
        this.roles = roles;
        this.roleQueries = roleQueries;
        this.rolePermissions = rolePermissions;
        this.ids = ids;
        this.audit = audit;
        this.denied = denied;
        this.notices = notices;
    }

    /** 모든 유형의 역할(가진 권한·쓰는 운영자/회원 수 포함). 제품이 넣은 역할이 앞, 그다음 만든 순서 */
    @Transactional(readOnly = true)
    public List<RoleView> list() {
        List<RoleEntity> all = roleQueries.findAllOrdered();
        Map<String, Set<String>> codes = roleQueries.permissionCodesOf(all.stream().map(RoleEntity::getRoleId).toList());
        Map<String, Long> operatorCounts = roleQueries.operatorCountByRole();
        Map<String, Long> memberCounts = roleQueries.memberCountByRole();
        return all.stream()
                .map(role -> view(role, codes.getOrDefault(role.getRoleId(), Set.of()), operatorCounts.getOrDefault(role.getRoleId(), 0L),
                        memberCounts.getOrDefault(role.getRoleId(), 0L)))
                .toList();
    }

    @Transactional
    public RoleView create(AdminActor actor, RoleCreateRequest request) {
        RoleTypeDef type = RoleTypes.find(request.roleTypeId()).orElseThrow(RoleTypeNotFoundException::new);
        if (type.derived()) {
            throw new RoleTypeDerivedException();
        }
        if (!type.creatable()) {
            throw new RoleTypeFixedException();
        }
        if (type.isGeneral()) {
            requireSuper(actor, "ROLE", request.roleId());
        }
        if (roles.existsById(request.roleId())) {
            throw new RoleDuplicatedException();
        }

        Instant now = Instant.now();
        String name = request.roleName().strip();
        String description = blankToNull(request.description());
        RoleEntity role = type.axis() == RoleAxis.OPERATOR
                ? RoleEntity.createOperator(request.roleId(), type.id(), name, description, actor.operatorId(), now)
                : RoleEntity.createCustomMember(request.roleId(), name, description, actor.operatorId(), now);
        try {
            roles.persistAndFlush(role);
        } catch (DataIntegrityViolationException e) {
            throw new RoleDuplicatedException(); // 동시에 같은 아이디로 만든 요청이 먼저 들어갔다
        }
        audit.record(operatorEvent(actor, AuditActions.ROLE_CREATE, role.getRoleId()).with("roleTypeId", type.id()));
        log.info("역할을 만들었다 roleId={} type={} operatorId={}", role.getRoleId(), type.id(), actor.operatorId());
        return view(role, Set.of(), 0, 0);
    }

    @Transactional
    public RoleView update(AdminActor actor, String roleId, RoleUpdateRequest request) {
        RoleEntity role = lock(roleId);
        requireSuperIfGeneral(actor, role);
        if (RoleTypes.GRADE.equals(role.getRoleTypeId())) {
            throw new RoleDerivedException();
        }
        role.rename(request.roleName().strip(), blankToNull(request.description()), Instant.now());
        audit.record(operatorEvent(actor, AuditActions.ROLE_UPDATE, roleId));
        return viewOf(role);
    }

    /**
     * 권한을 통째로 바꾼다. 검사 순서: 없는 역할(404) → 필요한 권한(403) → 종합 관리는 최고 관리자만(403) → 알 수 없는 권한(400) → 이 역할의 후보 밖(400) →
     * 권한 상승(403, 운영자 역할만).
     */
    @Transactional
    public RoleView setPermissions(AdminActor actor, String roleId, RolePermissionsRequest request) {
        RoleEntity role = lock(roleId);
        boolean operatorRole = role.getAxis() == RoleAxis.OPERATOR;
        Permission needed = operatorRole ? Permission.ROLE_GRANT : Permission.ROLE_MANAGE;
        if (!actor.can(needed)) {
            denied.record(actor, "MISSING_PERMISSION", "ROLE", roleId, List.of(needed.code()));
            throw new PermissionDeniedException();
        }
        requireSuperIfGeneral(actor, role);

        Set<String> wanted = new LinkedHashSet<>();
        for (String code : request.permissionCodes()) {
            if (code != null && !code.isBlank()) {
                wanted.add(code.strip());
            }
        }
        checkCandidates(role, wanted);

        Set<String> current = roleQueries.permissionCodesOf(roleId);
        List<String> granted = wanted.stream().filter(code -> !current.contains(code)).toList();
        List<String> revoked = current.stream().filter(code -> !wanted.contains(code)).toList();
        if (operatorRole && granted.stream().anyMatch(code -> !actor.can(code))) {
            denied.record(actor, "ESCALATION", "ROLE", roleId, granted.stream().filter(code -> !actor.can(code)).toList());
            throw new PermissionEscalationException();
        }

        Instant now = Instant.now();
        roleQueries.deletePermissions(roleId, revoked);
        for (String code : granted) {
            rolePermissions.persist(RolePermissionEntity.create(ids.nextId(), roleId, code, now));
        }
        role.touch(now);
        audit.record(operatorEvent(actor, AuditActions.ROLE_PERMISSIONS_CHANGE, roleId).with("granted", granted).with("revoked", revoked));
        if (operatorRole && (!granted.isEmpty() || !revoked.isEmpty())) {
            notices.rolePermissionsChanged(actor, roleId, role.getRoleName(), !revoked.isEmpty()); // 그 역할을 받은 운영자에게
        }
        log.info("역할 권한을 바꿨다 roleId={} 준 것={}개 뺀 것={}개 operatorId={}", roleId, granted.size(), revoked.size(), actor.operatorId());
        return view(role, new TreeSet<>(wanted), operatorCountOf(role), memberCountOf(role));
    }

    @Transactional
    public void delete(AdminActor actor, String roleId) {
        RoleEntity role = lock(roleId);
        requireSuperIfGeneral(actor, role);
        if (RoleTypes.GRADE.equals(role.getRoleTypeId())) {
            throw new RoleDerivedException(); // 등급 역할은 등급 설정이 만들고 지운다 — 등급을 지우면 함께 사라진다
        }
        if (role.isBuiltin()) {
            throw new RoleBuiltinException();
        }
        long operatorCount = roleQueries.operatorCountOf(roleId);
        long memberCount = roleQueries.memberCountOf(roleId);
        if (operatorCount > 0 || memberCount > 0) {
            throw new RoleInUseException(operatorCount, memberCount);
        }
        roles.delete(role);
        audit.record(operatorEvent(actor, AuditActions.ROLE_DELETE, roleId).with("roleTypeId", role.getRoleTypeId()));
        log.info("역할을 지웠다 roleId={} operatorId={}", roleId, actor.operatorId());
    }

    /** 줄 권한이 모두 알려진 코드이고 이 역할이 고를 수 있는 후보 안인지 본다 — 알 수 없는 코드가 하나라도 있으면 먼저 그것을 알린다 */
    private void checkCandidates(RoleEntity role, Collection<String> codes) {
        if (role.getAxis() == RoleAxis.MEMBER) {
            for (String code : codes) {
                MemberPermission permission = MemberPermission.ofCode(code).orElseThrow(PermissionUnknownException::new);
                if (!permission.isCandidate(role.getScope() == RoleScope.BOARD, role.getRoleId())) {
                    throw new RoleTypeMismatchException();
                }
            }
            return;
        }
        RoleTypeDef type = RoleTypes.find(role.getRoleTypeId()).orElseThrow(RoleTypeNotFoundException::new);
        Set<Permission> candidates = Set.copyOf(Permission.candidatesOf(type.permissionType()));
        for (String code : codes) {
            Permission permission = Permission.ofCode(code).orElseThrow(PermissionUnknownException::new);
            if (!candidates.contains(permission)) {
                throw new RoleTypeMismatchException();
            }
        }
    }

    private RoleEntity lock(String roleId) {
        return roles.findWithLockByRoleId(roleId).orElseThrow(RoleNotFoundException::new);
    }

    private void requireSuperIfGeneral(AdminActor actor, RoleEntity role) {
        if (role.getAxis() == RoleAxis.OPERATOR && RoleTypes.find(role.getRoleTypeId()).map(RoleTypeDef::isGeneral).orElse(false)) {
            requireSuper(actor, "ROLE", role.getRoleId());
        }
    }

    private void requireSuper(AdminActor actor, String targetType, String targetId) {
        if (!actor.root()) {
            denied.record(actor, "SUPER_ONLY", targetType, targetId, List.of());
            throw new RoleGeneralSuperOnlyException();
        }
    }

    private RoleView viewOf(RoleEntity role) {
        return view(role, roleQueries.permissionCodesOf(role.getRoleId()), operatorCountOf(role), memberCountOf(role));
    }

    private long operatorCountOf(RoleEntity role) {
        return role.getAxis() == RoleAxis.OPERATOR ? roleQueries.operatorCountOf(role.getRoleId()) : 0;
    }

    private long memberCountOf(RoleEntity role) {
        return role.getAxis() == RoleAxis.MEMBER ? roleQueries.memberCountOf(role.getRoleId()) : 0;
    }

    private static RoleView view(RoleEntity role, Collection<String> codes, long operatorCount, long memberCount) {
        boolean operator = role.getAxis() == RoleAxis.OPERATOR;
        return new RoleView(role.getRoleId(), role.getRoleName(), role.getDescription(), role.getRoleTypeId(), role.getAxis().name(), role.getScope().name(),
                role.getGrantMode().name(), role.isBuiltin(), new ArrayList<>(codes), operator ? operatorCount : 0, operator ? 0 : memberCount);
    }

    private static AuditEvent operatorEvent(AdminActor actor, String action, String roleId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("ROLE", roleId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
