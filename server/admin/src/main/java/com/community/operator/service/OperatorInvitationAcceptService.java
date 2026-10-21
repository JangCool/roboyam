package com.community.operator.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.dto.request.InvitationAcceptRequest;
import com.community.operator.dto.response.InvitationCheckResponse;
import com.community.operator.dto.response.OperatorRoleRef;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.entity.OperatorInvitationEntity;
import com.community.operator.entity.OperatorInvitationRoleEntity;
import com.community.operator.exception.InvitationExpiredException;
import com.community.operator.exception.InvitationLinkNotFoundException;
import com.community.operator.exception.InvitationUsedException;
import com.community.operator.exception.OperatorEmailDuplicatedException;
import com.community.operator.repository.master.OperatorInvitationRepository;
import com.community.operator.repository.master.OperatorInvitationRoleRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.operator.repository.master.OperatorRepositoryQDSL;
import com.community.role.entity.OperatorRoleEntity;
import com.community.role.entity.RoleEntity;
import com.community.role.repository.master.OperatorRoleRepository;
import com.community.role.repository.master.RoleRepository;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크 확인·수락 — 로그인 전 공개 API 다 (docs/features/roles-contract.md 「5단계 구현 결과」).
 *
 * <ul>
 *   <li><b>확인</b>({@link #check}): 본문의 토큰을 SHA-256 해시로 바꿔 {@code operator_invitations.token_hash} 와 견준다(DB 에는 해시만 있다). 토큰은 256비트 무작위라 추측할 수 없고,
 *       해시로 찾으므로 비교 시간에서 토큰이 새지 않는다. 오류: 모르는·취소된(행이 지워진) 토큰 404 {@code INVITATION_NOT_FOUND} · 이미 수락 409 {@code INVITATION_USED}
 *       · 유효 시간 지남 410 {@code INVITATION_EXPIRED}. 수락한 초대는 만료 여부와 상관없이 「이미 수락」이다.</li>
 *   <li><b>수락</b>({@link #accept}): <b>한 트랜잭션</b>에서 초대 행을 잠가 중복 수락을 막고(두 번째는 {@code INVITATION_USED}) 운영자를 만든다 — 초대받은 이메일·이름(닉네임 규칙은 내 정보와 같고 대소문자 무시 중복이면 409
 *       {@code NICKNAME_TAKEN})·비밀번호(공개 사이트와 같은 규칙, {@code fields.password})·승인됨·사용 중·잠금 없음. 초대에 담긴 역할을 부여한다(그 사이 지워진 역할은 건너뛰고 감사에 남긴다).
 *       같은 이메일의 운영자가 이미 있으면 409 {@code OPERATOR_EMAIL_DUPLICATED}. 수락은 로그인시키지 않는다. 비밀번호 해시(느린 연산)는 모든 검사가 끝난 뒤에 한다.</li>
 * </ul>
 * 토큰·비밀번호·이메일을 로그와 감사 기록에 남기지 않는다.
 */
@Service
public class OperatorInvitationAcceptService {

    private static final Logger log = LoggerFactory.getLogger(OperatorInvitationAcceptService.class);

    /** 토큰은 43자(256비트 base64url). 이보다 훨씬 긴 값은 토큰이 아니다 */
    private static final int TOKEN_MAX_LENGTH = 200;

    private final OperatorInvitationRepository invitations;
    private final OperatorInvitationRoleRepository invitationRoles;
    private final OperatorRepository operators;
    private final OperatorRepositoryQDSL operatorQueries;
    private final OperatorRoleRepository operatorRoles;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final EntityManager entityManager;

    public OperatorInvitationAcceptService(OperatorInvitationRepository invitations, OperatorInvitationRoleRepository invitationRoles, OperatorRepository operators, OperatorRepositoryQDSL operatorQueries,
                                           OperatorRoleRepository operatorRoles, RoleRepository roles, PasswordEncoder encoder, SnowflakeIdGenerator ids,
                                           AuditLogService audit, EntityManager entityManager) {
        this.invitations = invitations;
        this.invitationRoles = invitationRoles;
        this.operators = operators;
        this.operatorQueries = operatorQueries;
        this.operatorRoles = operatorRoles;
        this.roles = roles;
        this.encoder = encoder;
        this.ids = ids;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    /** 초대 링크가 쓸 수 있는지 확인하고 초대받은 이메일·받을 역할·유효 기한을 돌려준다 */
    @Transactional(readOnly = true)
    public InvitationCheckResponse check(String token) {
        OperatorInvitationEntity invitation = invitations.findByTokenHash(hashOf(token)).orElseThrow(InvitationLinkNotFoundException::new);
        requireUsable(invitation, Instant.now());

        List<String> roleIds = invitationRoles.findByInvitationIdIn(List.of(invitation.getId())).stream().map(OperatorInvitationRoleEntity::getRoleId).sorted().toList();
        Map<String, RoleEntity> byId = roles.findAllById(roleIds).stream().collect(Collectors.toMap(RoleEntity::getRoleId, role -> role));
        List<OperatorRoleRef> refs = roleIds.stream().filter(byId::containsKey).map(id -> new OperatorRoleRef(id, byId.get(id).getRoleName())).toList();
        return new InvitationCheckResponse(invitation.getEmail(), refs, invitation.getExpiresAt());
    }

    /**
     * 초대를 수락해 운영자를 만든다. 검사 순서: 토큰(404·409·410) → 입력 형식(400 {@code fields.nickname|password}) → 이메일 중복(409) → 닉네임 중복(409).
     * 모르는 토큰이 아니면 {@link OperatorIdentityLock} 을 먼저 잡은 뒤 초대 행을 잠가(잠금 순서: 식별 잠금 → 행 잠금) 직접 추가·초대 만들기와 경합해도 중복이 생기지 않는다.
     *
     * @param ip 요청자 IP(감사 기록용)
     */
    @Transactional
    public void accept(InvitationAcceptRequest request, String ip) {
        Instant now = Instant.now();
        String tokenHash = hashOf(request.token());
        if (!invitations.existsByTokenHash(tokenHash)) {
            throw new InvitationLinkNotFoundException(); // 모르는 토큰은 전역 잠금을 잡기 전에 거른다(공개 API 라 아무나 부를 수 있다)
        }
        // 잠금 순서: 운영자 식별 잠금 → 초대 행 잠금. 초대 만들기·직접 추가·닉네임 변경이 같은 순서로 잡아 교착이 없다. 초대 행은 잠근 뒤에 상태를 다시 본다
        OperatorIdentityLock.acquire(entityManager);
        OperatorInvitationEntity invitation = invitations.findWithLockByTokenHash(tokenHash).orElseThrow(InvitationLinkNotFoundException::new);
        requireUsable(invitation, now);

        String nickname = request.nickname();
        String password = request.password();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        MessageRef nicknameViolation = nickname == null || nickname.isEmpty() ? new MessageRef("profile.nickname.required") : UserPolicy.nicknameViolation(nickname).orElse(null);
        if (nicknameViolation != null) {
            invalid.put("nickname", nicknameViolation);
        }
        MessageRef passwordViolation = password == null || password.isEmpty() ? new MessageRef("user.password.required") : UserPolicy.passwordViolation(password).orElse(null);
        if (passwordViolation != null) {
            invalid.put("password", passwordViolation);
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        String email = invitation.getEmail();
        if (operators.existsByEmail(email)) {
            throw new OperatorEmailDuplicatedException();
        }
        if (operatorQueries.existsOtherWithName(nickname, 0L)) { // 0 은 없는 ID — 「다른 운영자」 조건이 모든 운영자를 뜻하게 한다
            throw new UserNicknameTakenException();
        }

        OperatorEntity operator = OperatorEntity.createApproved(ids.nextId(), email, nickname, encoder.encode(password), now);
        try {
            operators.persistAndFlush(operator); // 아래 역할 행이 참조하므로 먼저 내보낸다. 위 잠금을 새는 경우의 마지막 방어는 유일 인덱스(이메일·이름)다
        } catch (DataIntegrityViolationException e) {
            throw OperatorIdentityLock.isNameViolation(e) ? new UserNicknameTakenException() : new OperatorEmailDuplicatedException();
        }

        Set<String> wanted = invitationRoles.findByInvitationIdIn(List.of(invitation.getId())).stream().map(OperatorInvitationRoleEntity::getRoleId).collect(Collectors.toCollection(TreeSet::new));
        List<String> granted = wanted.isEmpty() ? List.of() : roles.findWithShareLockByRoleIdIn(wanted).stream().map(RoleEntity::getRoleId).sorted().toList();
        for (String roleId : granted) {
            operatorRoles.persist(OperatorRoleEntity.create(ids.nextId(), operator.getId(), roleId, invitation.getInvitedBy(), now));
        }
        List<String> skipped = wanted.stream().filter(id -> !granted.contains(id)).toList(); // 역할은 지워지면 초대에서도 빠지므로 보통 비어 있다(경합 방어)

        invitation.consume(now);
        AuditEvent event = AuditEvent.byOperator(operator.getId(), ip, AuditActions.INVITATION_ACCEPT, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operator.getId()))
                .with("invitationId", Long.toString(invitation.getId())).with("roles", granted);
        audit.record(skipped.isEmpty() ? event : event.with("rolesSkipped", skipped));
        log.info("초대를 수락해 운영자를 만들었다 operatorId={} invitationId={} 역할={}개", operator.getId(), invitation.getId(), granted.size());
    }

    /** 수락했으면 409, 유효 시간이 지났으면 410 */
    private static void requireUsable(OperatorInvitationEntity invitation, Instant now) {
        if (invitation.getConsumedAt() != null) {
            throw new InvitationUsedException();
        }
        if (invitation.isExpired(now)) {
            throw new InvitationExpiredException();
        }
    }

    /** 토큰의 SHA-256 해시 — 비었거나 터무니없이 긴 값은 모르는 토큰이다 */
    private static String hashOf(String token) {
        if (token == null || token.isBlank() || token.length() > TOKEN_MAX_LENGTH) {
            throw new InvitationLinkNotFoundException();
        }
        return Digests.sha256Hex(token.strip());
    }
}
