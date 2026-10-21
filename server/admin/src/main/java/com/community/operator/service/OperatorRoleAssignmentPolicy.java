package com.community.operator.service;

import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.exception.PermissionEscalationException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.operator.exception.OperatorRoleNotFoundException;
import com.community.role.entity.RoleAxis;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RoleTypes;
import com.community.role.exception.RoleGeneralSuperOnlyException;
import com.community.role.repository.master.RoleRepository;
import com.community.role.repository.master.RoleRepositoryQDSL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자(또는 초대)에게 역할을 줄 때의 검사를 한 곳에 모은다 — 운영자 만들기·고치기·초대가 같은 규칙을 쓴다.
 *
 * <ol>
 *   <li>역할이 모두 있고 <b>운영자 역할</b>이어야 한다(회원 역할은 운영자에게 줄 수 없다) — 아니면 400 {@code OPERATOR_ROLE_NOT_FOUND}.</li>
 *   <li>역할을 바꾸려면(새로 주거나 빼려면) {@code role.grant} 가 있어야 한다 — 아니면 403 {@code PERMISSION_DENIED}.</li>
 *   <li>새로 주는 <b>종합 관리</b> 역할은 최고 관리자만 줄 수 있다 — 아니면 403 {@code ROLE_GENERAL_SUPER_ONLY}.</li>
 *   <li><b>권한 상승 방지</b>: 새로 주는 역할의 권한이 모두 요청한 운영자의 권한 안이어야 한다 — 아니면 403 {@code PERMISSION_ESCALATION}.
 *       이미 가진 역할은 그대로 둘 수 있다.</li>
 * </ol>
 * 거부는 따로 커밋되는 감사 기록으로 남는다. 트랜잭션은 부르는 서비스의 것을 쓴다.
 */
@Service
public class OperatorRoleAssignmentPolicy {

    private final RoleRepository roles;
    private final RoleRepositoryQDSL roleQueries;
    private final AccessDeniedRecorder denied;

    public OperatorRoleAssignmentPolicy(RoleRepository roles, RoleRepositoryQDSL roleQueries, AccessDeniedRecorder denied) {
        this.roles = roles;
        this.roleQueries = roleQueries;
        this.denied = denied;
    }

    /**
     * 요청한 역할 아이디들을 검사하고, 중복을 없앤 역할 목록(요청 순서)을 돌려준다.
     *
     * @param requestedIds 줄 역할 아이디들(비어 있으면 역할 없음)
     * @param currentIds   지금 가진 역할 아이디들(새 운영자·초대는 빈 집합) — 이미 가진 것은 권한 상승 검사에서 뺀다
     */
    @Transactional
    public List<RoleEntity> resolve(AdminActor actor, Collection<String> requestedIds, Collection<String> currentIds) {
        Set<String> wanted = new LinkedHashSet<>();
        if (requestedIds != null) {
            requestedIds.stream().filter(id -> id != null && !id.isBlank()).map(String::strip).forEach(wanted::add);
        }
        Map<String, RoleEntity> found = roles.findAllById(wanted).stream().collect(Collectors.toMap(RoleEntity::getRoleId, Function.identity()));
        List<RoleEntity> result = new ArrayList<>();
        for (String id : wanted) {
            RoleEntity role = found.get(id);
            if (role == null || role.getAxis() != RoleAxis.OPERATOR) {
                throw new OperatorRoleNotFoundException();
            }
            result.add(role);
        }

        Set<String> current = Set.copyOf(currentIds);
        List<RoleEntity> added = result.stream().filter(role -> !current.contains(role.getRoleId())).toList();
        boolean removed = current.stream().anyMatch(id -> !wanted.contains(id));
        if ((!added.isEmpty() || removed) && !actor.can(Permission.ROLE_GRANT)) {
            denied.record(actor, "MISSING_PERMISSION", "OPERATOR_ROLES", "-", List.of(Permission.ROLE_GRANT.code()));
            throw new PermissionDeniedException();
        }

        Map<String, Set<String>> permissionsOf = roleQueries.permissionCodesOf(added.stream().map(RoleEntity::getRoleId).toList());
        for (RoleEntity role : added) {
            boolean general = RoleTypes.find(role.getRoleTypeId()).map(RoleTypes.RoleTypeDef::isGeneral).orElse(false);
            if (general && !actor.root()) {
                denied.record(actor, "SUPER_ONLY", "ROLE", role.getRoleId(), List.of());
                throw new RoleGeneralSuperOnlyException();
            }
            List<String> outside = permissionsOf.getOrDefault(role.getRoleId(), Set.of()).stream().filter(code -> !actor.can(code)).toList();
            if (!outside.isEmpty()) {
                denied.record(actor, "ESCALATION", "ROLE", role.getRoleId(), outside);
                throw new PermissionEscalationException();
            }
        }
        return result;
    }
}
