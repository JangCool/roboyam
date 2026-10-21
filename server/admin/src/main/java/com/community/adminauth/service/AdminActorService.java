package com.community.adminauth.service;

import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.role.repository.master.RoleRepositoryQDSL;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 운영자의 <b>현재</b> 상태를 읽어 이 요청의 행위자({@link AdminActor})를 만든다. 요청마다 DB 에서 다시 읽는다 — 캐시하지 않으므로
 * 잠금·사용 중지·기간 만료·역할/권한 변경이 다음 요청부터 바로 반영되고 낡은 권한이 남지 않는다.
 * 최고 관리자(루트)는 모든 운영자 권한을 가진다.
 */
@Service
@Transactional(readOnly = true)
public class AdminActorService {

    private final OperatorRepository operators;
    private final RoleRepositoryQDSL roles;

    public AdminActorService(OperatorRepository operators, RoleRepositoryQDSL roles) {
        this.operators = operators;
        this.roles = roles;
    }

    /**
     * 그 운영자가 지금도 로그인할 수 있는 상태(승인됨·잠기지 않음·만료 표시 없음·사용 중·기간 안)이면 행위자를, 아니면(지워졌거나 막혔으면) 비어 있다 —
     * 부르는 쪽은 그 세션을 낡은 것으로 본다.
     */
    public Optional<AdminActor> load(long operatorId, String ip) {
        Optional<OperatorEntity> found = operators.findById(operatorId).filter(operator -> operator.canLogin(Instant.now()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OperatorEntity operator = found.get();
        Set<String> permissions = operator.isRoot() ? Set.copyOf(Permission.allCodes()) : roles.permissionCodesOfOperator(operatorId);
        return Optional.of(new AdminActor(operator.getId(), operator.getOperatorName(), operator.getEmail(), operator.isRoot(), permissions, ip));
    }
}
