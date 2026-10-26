package com.community.profile.service;

import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.service.BoardAccessPolicy;
import com.community.pointshop.dto.NicknameTicketInfo;
import com.community.pointshop.service.NicknameTicketService;
import com.community.pointshop.service.ProfileAvatarService;
import com.community.profile.dto.request.AvatarSelectRequest;
import com.community.profile.dto.request.MyProfileRequest;
import com.community.profile.dto.response.MyProfileView;
import com.community.profile.dto.response.NicknameChangeView;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.ProfileNicknameHistoryEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.repository.master.ProfileNicknameHistoryRepository;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
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
 * 내 프로필(닉네임·한 줄 소개·프로필 아바타) — {@code GET}·{@code PUT /api/me/profile}·{@code PUT /api/me/profile/avatar}. docs/features/member-mypage-contract.md 「프로필」·「결정」·
 * 「후속 작업: 닉네임 변경권」, commerce-stage1-contract 「1-2 화면 목업」.
 *
 * <ul>
 *   <li><b>닉네임 변경</b>: 가입 뒤 {@value #NICKNAME_FREE_LIMIT}번은 무료(날짜 대기 없음). 그 뒤로는 바꿀 때마다 <b>닉네임 변경권 1장</b>({@code useTicket: true}) — 없거나
 *       {@code useTicket} 을 보내지 않으면 {@code 409 NICKNAME_TICKET_REQUIRED}(최상위 {@code ticketCount}·{@code ticketPrice}). 규칙은 가입과 같은 형식 + 예약어
 *       ({@link UserPolicy#memberNicknameViolation}), 중복은 대소문자 무시({@code ux_profiles_nickname}) {@code 409 NICKNAME_TAKEN}. 바꾸면 이력 한 줄(비공개)을 남긴다.</li>
 *   <li><b>닉네임이 그대로면</b>(소개만 저장) 형식·횟수·변경권·중복 검사를 하지 않고 아무것도 쓰지 않는다. 무료 변경이 남았으면 {@code useTicket} 을 보내도 변경권을 쓰지 않는다.
 *       대소문자만 바꾸는 것은 변경이다.</li>
 *   <li><b>한 줄 소개</b>: 앞뒤 공백을 자른 뒤 0~{@value #BIO_MAX}자, 빈 값은 null 로 저장한다.</li>
 *   <li><b>검사 순서</b>: 로그인(401) → 정지·영구 정지 {@code 403 ACCOUNT_RESTRICTED}(글쓰기와 같은 예외 — 읽기는 정지 중에도 된다) → 프로필 행 쓰기 잠금 →
 *       칸 검사 {@code 400 VALIDATION fields.nickname|bio} → 변경권 {@code 409 NICKNAME_TICKET_REQUIRED}(쓰면 이 트랜잭션 안에서) → 중복 {@code 409 NICKNAME_TAKEN}.
 *       실패하면 트랜잭션째 되돌아가 변경권도 그대로다. 같은 회원의 동시 요청은 프로필 행 잠금에서 줄을 서고, 변경권 쓰기는 조건부 UPDATE({@code SKIP LOCKED})라
 *       <b>한 장이 두 번 쓰이지 않는다</b>.</li>
 *   <li><b>아바타 고르기</b>({@link #selectAvatar}): 무료는 누구나(사업자 전용 계정 포함), 유료는 가진 것만 — 판정은 shared {@link ProfileAvatarService#select}. 응답은 저장 뒤 내 프로필.</li>
 *   <li>로그인 정보({@code GET /api/auth/me})는 세션이 아니라 DB 에서 닉네임을 읽으므로 따로 고칠 것이 없다(세션에는 회원 ID·역할만 있다).</li>
 * </ul>
 */
@Service
public class MyProfileService {

    private static final Logger log = LoggerFactory.getLogger(MyProfileService.class);

    /** 가입 뒤 무료로 닉네임을 바꿀 수 있는 횟수(서버 상수) */
    public static final int NICKNAME_FREE_LIMIT = ProfileEntity.NICKNAME_FREE_LIMIT;
    /** 한 줄 소개 최대 글자 수 */
    public static final int BIO_MAX = 100;

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileRepositoryQDSL profileQueries;
    private final ProfileNicknameHistoryRepository history;
    private final SnowflakeIdGenerator ids;
    private final NicknameTicketService tickets;
    private final ProfileAvatarService avatars;

    public MyProfileService(UserRepository users, ProfileRepository profiles, ProfileRepositoryQDSL profileQueries, ProfileNicknameHistoryRepository history,
                            SnowflakeIdGenerator ids, NicknameTicketService tickets, ProfileAvatarService avatars) {
        this.users = users;
        this.profiles = profiles;
        this.profileQueries = profileQueries;
        this.history = history;
        this.ids = ids;
        this.tickets = tickets;
        this.avatars = avatars;
    }

    @Transactional(readOnly = true)
    public MyProfileView get(long userId) {
        UserEntity user = requireUser(userId);
        ProfileEntity profile = profiles.findById(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        return view(user, profile);
    }

    @Transactional
    public MyProfileView update(long userId, MyProfileRequest body) {
        UserEntity user = requireUser(userId);
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.ACCOUNT_RESTRICTED);
        }
        ProfileEntity profile = profiles.findWithLockByUserId(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));

        String nickname = body == null ? null : body.nickname();
        String bio = body == null || body.bio() == null ? "" : body.bio().strip();
        boolean useTicket = body != null && Boolean.TRUE.equals(body.useTicket());
        boolean changing = nickname == null || !nickname.equals(profile.getNickname());

        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (changing) {
            MessageRef violation = nickname == null || nickname.isEmpty() ? new MessageRef("profile.nickname.required")
                    : UserPolicy.memberNicknameViolation(nickname).orElse(null);
            if (violation != null) {
                invalid.put("nickname", violation);
            }
        }
        if (bio.codePointCount(0, bio.length()) > BIO_MAX) {
            invalid.put("bio", new MessageRef("profile.bio.length", BIO_MAX));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        Instant now = Instant.now();
        if (changing) {
            if (profile.getNicknameChangeCount() >= NICKNAME_FREE_LIMIT) {
                if (!useTicket) {
                    throw tickets.required(userId);
                }
                long used = tickets.useOne(userId); // 이 트랜잭션 안 — 아래 중복·저장이 실패하면 함께 되돌아간다
                log.info("닉네임 변경권을 썼다 userId={} userItemId={}", userId, used);
            }
            if (profileQueries.existsByNicknameExcept(nickname, userId)) {
                throw new UserNicknameTakenException();
            }
            String previous = profile.changeNickname(nickname, now);
            history.persist(ProfileNicknameHistoryEntity.create(ids.nextId(), userId, previous, nickname, now));
        }
        profile.changeBio(bio, now);
        if (changing) {
            try {
                profiles.flush();
            } catch (DataIntegrityViolationException e) {
                // 동시에 다른 회원이 같은 닉네임을 가졌다(유일 제약). DB 문구에는 입력값이 실려 남기지 않는다
                if (String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage()).contains("ux_profiles_nickname")) {
                    throw new UserNicknameTakenException();
                }
                throw e;
            }
            log.info("회원 닉네임을 바꿨다 userId={} count={}", userId, profile.getNicknameChangeCount());
        }
        return view(user, profile);
    }

    /**
     * 프로필 아바타를 고른다 — 판정·저장은 {@link ProfileAvatarService#select}(401 → 403 정지 → 403 이메일 인증 → 400 형식 → 404 카탈로그에 없음 → 409 유료 미보유).
     *
     * @return 저장 뒤 내 프로필({@code avatarUrl} 포함)
     */
    @Transactional
    public MyProfileView selectAvatar(long userId, AvatarSelectRequest body) {
        avatars.select(userId, body == null ? null : body.avatarId());
        UserEntity user = requireUser(userId);
        ProfileEntity profile = profiles.findById(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        return view(user, profile);
    }

    private UserEntity requireUser(long userId) {
        return users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }

    private MyProfileView view(UserEntity user, ProfileEntity profile) {
        int count = profile.getNicknameChangeCount();
        NicknameTicketInfo ticket = tickets.info(user.getId());
        return new MyProfileView(profile.getNickname(), profile.getBio() == null ? "" : profile.getBio(), user.getCreatedAt(),
                new NicknameChangeView(count < NICKNAME_FREE_LIMIT, count, NICKNAME_FREE_LIMIT, ticket.count(), ticket.price()), avatars.avatarUrl(user.getId()));
    }
}
