package com.community.profile.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.dto.response.OperatorRoleRef;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.request.ProfileUpdateRequest;
import com.community.profile.dto.response.ProfileResponse;
import com.community.role.dto.OperatorRoleRow;
import com.community.role.repository.master.RoleRepositoryQDSL;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 — 로그인한 운영자 <b>본인</b>의 계정 정보를 읽고 닉네임을 바꾼다. 대상은 늘 요청한 사람({@link AdminActor})이라 다른 사람의 정보에는 닿을 수 없다.
 *
 * <ul>
 *   <li>닉네임 규칙은 공개 사이트와 같은 {@link UserPolicy#nicknameViolation}(2~12자, 한글 완성형·영문·숫자·밑줄, 글자 사이의 공백 한 칸)이다. 비어 있으면 별도 문구.
 *       위반은 {@code 400 VALIDATION} + {@code fields.nickname}.</li>
 *   <li>다른 운영자와 같은 이름(대소문자 무시)이면 {@code 409 NICKNAME_TAKEN}(목업과 같은 코드). {@code operators} 표에는 이름 유일 제약이 없어 서비스가 확인하고,
 *       같은 이름으로 동시에 바꾸는 요청은 어드바이저리 잠금({@link ProfileNicknameLock})으로 줄 세운다.</li>
 *   <li>바뀐 이름은 다음 요청부터 {@code auth/me} 의 {@code user.nickname} 에 비친다(행위자를 요청마다 DB 에서 다시 읽는다). 바뀐 때만 감사 {@code PROFILE_UPDATE}(전후 이름).</li>
 * </ul>
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final OperatorRepository operators;
    private final RoleRepositoryQDSL roles;
    private final AuditLogService audit;
    private final EntityManager entityManager;

    public ProfileService(OperatorRepository operators, RoleRepositoryQDSL roles, AuditLogService audit, EntityManager entityManager) {
        this.operators = operators;
        this.roles = roles;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(AdminActor actor) {
        OperatorEntity operator = operators.findById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        return view(operator);
    }

    @Transactional
    public ProfileResponse updateNickname(AdminActor actor, ProfileUpdateRequest request) {
        String nickname = request == null ? null : request.nickname();
        MessageRef violation = nickname == null || nickname.isEmpty() ? new MessageRef("profile.nickname.required") : UserPolicy.nicknameViolation(nickname).orElse(null);
        if (violation != null) {
            throw ValidationException.of("nickname", violation);
        }

        ProfileNicknameLock.acquire(entityManager);
        OperatorEntity operator = operators.findWithLockById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        String before = operator.getOperatorName();
        if (!nickname.equals(before)) {
            if (operators.existsByOperatorNameIgnoreCaseAndIdNot(nickname, operator.getId())) {
                throw new UserNicknameTakenException();
            }
            operator.rename(nickname, Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PROFILE_UPDATE, AuditResult.SUCCESS)
                    .target("OPERATOR", Long.toString(actor.operatorId())).with("before", Map.of("nickname", before)).with("after", Map.of("nickname", nickname)));
            log.info("닉네임을 바꿨다 operatorId={}", actor.operatorId());
        }
        return view(operator);
    }

    private ProfileResponse view(OperatorEntity operator) {
        List<OperatorRoleRef> mine = roles.rolesOfOperators(List.of(operator.getId())).getOrDefault(operator.getId(), List.of()).stream()
                .map((OperatorRoleRow row) -> new OperatorRoleRef(row.roleId(), row.roleName())).toList();
        return new ProfileResponse(Long.toString(operator.getId()), operator.getEmail(), operator.getOperatorName(), operator.isRoot() ? "ADMIN" : "STAFF", operator.isRoot(), mine,
                "ACTIVE", operator.getCreatedAt(), operator.getLastLoginAt());
    }
}
