package com.community.member.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.member.dto.request.MemberNicknameUpdateRequest;
import com.community.member.dto.response.MemberNicknameUpdateView;
import com.community.member.exception.MemberNotFoundException;
import com.community.permission.dto.AdminActor;
import com.community.restriction.exception.MemberWithdrawnException;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.ProfileNicknameHistoryEntity;
import com.community.user.entity.UserEntity;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.repository.master.ProfileNicknameHistoryRepository;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 회원 닉네임을 바꾼다(관리자 회원 상세 — 회원이 오류로 못 바꿨거나 변경권을 샀는데 적용이 안 된 사연 등, 사용자 2026-11-15). 권한 {@code member.nickname} 은 컨트롤러 선언.
 *
 * <ul>
 *   <li><b>검사 순서</b>: 경로 번호·회원 없음 404 {@code MEMBER_NOT_FOUND} → 탈퇴(익명화) 회원 409 {@code MEMBER_WITHDRAWN} → 프로필 행 쓰기 잠금 →
 *       칸 검사 400 {@code VALIDATION fields.nickname|reason}(빈 값 · 지금과 똑같은 닉네임 · 형식 {@link UserPolicy#nicknameViolation} — 예약어는 보지 않는다
 *       (「아이로빗운영자」 같은 운영팀 이름도 가능) · 사유 {@value #REASON_MIN}~{@value #REASON_MAX}자) → 중복(대소문자 무시, 자기 자신 제외) 409 {@code NICKNAME_TAKEN}.
 *       대소문자만 바꾸는 것은 변경이다(회원 쪽 {@code MyProfileService} 와 같다).</li>
 *   <li><b>무료 변경 횟수·변경권은 쓰지 않는다</b> — {@link ProfileEntity#changeNicknameByOperator}(회원의 남은 횟수 그대로). 닉네임 이력 한 줄은 회원 변경과 같은 표에 남긴다
 *       (운영자 표시 칸은 없어 누가 바꿨는지는 감사 {@code MEMBER_NICKNAME_CHANGE}({@code from}·{@code to}·{@code reason})가 맡는다).</li>
 *   <li>동시에 다른 회원이 같은 닉네임을 가지면 유일 제약({@code ux_profiles_nickname}) 위반을 409 {@code NICKNAME_TAKEN} 으로 바꾼다(DB 문구에는 입력값이 실려 남기지 않는다).</li>
 * </ul>
 */
@Service
public class MemberNicknameAdminService {

    private static final Logger log = LoggerFactory.getLogger(MemberNicknameAdminService.class);

    /** 사유 최소·최대 글자 수(앞뒤 공백 뺀 뒤) */
    public static final int REASON_MIN = 2;
    public static final int REASON_MAX = 200;

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileRepositoryQDSL profileQueries;
    private final ProfileNicknameHistoryRepository history;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;

    public MemberNicknameAdminService(UserRepository users, ProfileRepository profiles, ProfileRepositoryQDSL profileQueries, ProfileNicknameHistoryRepository history,
                                      SnowflakeIdGenerator ids, AuditLogService audit) {
        this.users = users;
        this.profiles = profiles;
        this.profileQueries = profileQueries;
        this.history = history;
        this.ids = ids;
        this.audit = audit;
    }

    @Transactional
    public MemberNicknameUpdateView change(AdminActor actor, String memberId, MemberNicknameUpdateRequest body) {
        long userId = MemberLookup.parseId(memberId);
        UserEntity user = users.findById(userId).orElseThrow(MemberNotFoundException::new);
        if (user.isGone()) {
            throw new MemberWithdrawnException();
        }
        ProfileEntity profile = profiles.findWithLockByUserId(userId).orElseThrow(MemberNotFoundException::new);

        String nickname = body == null ? null : body.nickname();
        String reason = body == null || body.reason() == null ? "" : body.reason().strip();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (nickname == null || nickname.isEmpty()) {
            invalid.put("nickname", new MessageRef("profile.nickname.required"));
        } else if (nickname.equals(profile.getNickname())) {
            invalid.put("nickname", new MessageRef("member.nickname.same"));
        } else {
            UserPolicy.nicknameViolation(nickname).ifPresent(violation -> invalid.put("nickname", violation));
        }
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            invalid.put("reason", new MessageRef("member.nickname.reason.length", REASON_MIN, REASON_MAX));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (profileQueries.existsByNicknameExcept(nickname, userId)) {
            throw new UserNicknameTakenException();
        }
        Instant now = Instant.now();
        String previous = profile.changeNicknameByOperator(nickname, now);
        history.persist(ProfileNicknameHistoryEntity.create(ids.nextId(), userId, previous, nickname, now));
        try {
            profiles.flush();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage()).contains("ux_profiles_nickname")) {
                throw new UserNicknameTakenException();
            }
            throw e;
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MEMBER_NICKNAME_CHANGE, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("from", previous).with("to", nickname).with("reason", reason));
        log.info("운영자가 회원 닉네임을 바꿨다 memberId={} operatorId={}", userId, actor.operatorId());
        return new MemberNicknameUpdateView(Long.toString(userId), nickname, previous, now);
    }
}
