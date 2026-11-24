package com.community.operator.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.auth.exception.AuthRejoinBlockedException;
import com.community.auth.service.MemberSignupWriter;
import com.community.mail.outbox.dto.OutboxMail;
import com.community.mail.outbox.service.MailOutbox;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.operator.dto.request.OperatorMemberLinkRequest;
import com.community.member.exception.MemberNotFoundException;
import com.community.member.service.MemberMasking;
import com.community.operator.dto.response.OperatorMemberAccountView;
import com.community.operator.dto.response.OperatorMemberCandidates;
import com.community.operator.dto.response.OperatorMemberCreateResult;
import com.community.operator.repository.master.OperatorMemberAccountStore.CandidateSearch;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Locale;
import com.community.terms.service.TermsService;
import com.community.withdrawal.entity.WithdrawalKind;
import com.community.withdrawal.service.IdentityWithdrawalRecorder;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.exception.OperatorMemberAccountException;
import com.community.operator.exception.OperatorNotFoundException;
import com.community.operator.repository.master.OperatorMemberAccountStore;
import com.community.operator.repository.master.OperatorMemberAccountStore.Candidate;
import com.community.operator.repository.master.OperatorMemberAccountStore.Link;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.user.entity.ProfileVisibility;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.service.UserPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 회원 계정 연결·해제(docs/features/operator-member-account-contract.md 정책 1~5 · 「API > 관리자」) — 관리자 앱 운영자 하나에 사이트 회원 계정 하나를 1:1 로 잇는다.
 *
 * <ul>
 *   <li><b>대상</b>(정책 2 — 세션 정리 결정 2026-11-24): 관리자가 회원을 검색해 고른다({@link #searchCandidates}) — <b>같은 이메일이 아니어도 된다</b>. 운영자 이메일과 같은
 *       이메일(정규화한 소문자)로 가입한 회원은 추천 후보로 {@code GET} 의 {@code candidate} 에 준다.</li>
 *   <li><b>자격</b>(정책 3, 연결할 때만): 이메일 인증 · 회원 상태 정상(정지·탈퇴 진행 아님) · 사업자 계정 아님 · 다른 운영자에 이어져 있지 않음.</li>
 *   <li><b>연결</b>: 운영자 행 잠금 → 루트 보호 → 이미 이어짐 409 → <b>회원 행 잠금</b>(탈퇴 신청 · 게시판 담당 지정과 같은 잠금 — 없으면 404 {@code MEMBER_NOT_FOUND}) → 자격 판정 409
 *       (사유 {@code ineligibleReasons}) →
 *       그 회원의 게시판 매니저·스텝 지정을 모두 끝냄(정책 4 — 행 삭제, 감사 {@code releasedBoards}) → <b>프로필을 비공개로</b>(운영팀 계정은 기본 비공개 — 사용자 2026-11-08,
 *       회원 본인이 나중에 공개로 돌릴 수 있다, 감사 {@code profileMadePrivate}) → 연결 + 감사 {@code OPERATOR_MEMBER_LINK}, 한 트랜잭션.</li>
 *   <li><b>새 계정 만들기</b>({@link #createLinked} — 「새 계정 만들기(2026-11-24)」): 관리자가 회원 계정을 새로 만들어 바로 잇는다. 이메일은 운영자 이메일과 달라도 되고
 *       (정책 2 의 예외 — 본인 확인은 인증 메일·비밀번호 정하기 메일이 대신한다) 이메일 인증 전 계정을 잇는다(힘 판정에 인증 조건을 더하지 않는다). 감사는 {@code OPERATOR_MEMBER_CREATE} 하나.</li>
 *   <li><b>해제</b>: 언제든(정책 5) — 감사 {@code OPERATOR_MEMBER_UNLINK}. 끝난 게시판 담당은 되살리지 않는다.</li>
 *   <li><b>루트 보호</b>(정책 5): 대상이 최고 관리자면 최고 관리자 본인만 잇고 끊는다(409 {@code OPERATOR_ROOT_PROTECTED}).</li>
 * </ul>
 */
@Service
public class OperatorMemberAccountAdminService {

    private static final Logger log = LoggerFactory.getLogger(OperatorMemberAccountAdminService.class);

    /** 연결 자격 미달 사유(정책 3) — 화면과 같은 차례 */
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String MEMBER_RESTRICTED = "MEMBER_RESTRICTED";
    public static final String BUSINESS_ACCOUNT = "BUSINESS_ACCOUNT";
    public static final String LINKED_TO_OTHER = "LINKED_TO_OTHER";

    /** 힘 목록(키 · 필요한 관리자 권한) — 권한과 상관없는 힘은 null */
    private static final List<String[]> POWERS = List.of(new String[] {"ROBOTICON_ALL", null}, new String[] {"OPERATOR_BADGE", null},
            new String[] {"POST_MODERATE", Permission.POST_MANAGE.code()}, new String[] {"COMMENT_DELETE", Permission.POST_MANAGE.code()},
            new String[] {"AUTHOR_SANCTION", Permission.SANCTION_MANAGE.code()}, new String[] {"ACTION_READ", Permission.KEEPER_ACTION_READ.code()},
            new String[] {"ACTION_RESTORE", Permission.KEEPER_ACTION_RESTORE.code()});

    private static final String AUDIT_TARGET = "OPERATOR";

    /** 후보 검색 — 검색어 길이(앞뒤 공백 자른 글자 수)와 최대 건수 */
    static final int SEARCH_MIN_LENGTH = 2;
    static final int SEARCH_MAX_LENGTH = 100;
    static final int SEARCH_LIMIT = 20;

    /** 새 계정 만들기가 보낸 메일(응답 {@code mailsSent}) */
    public static final String MAIL_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String MAIL_PASSWORD_SETUP = "PASSWORD_SETUP";

    private final OperatorRepository operators;
    private final OperatorMemberAccountStore store;
    private final OperatorMemberAccountService accounts;
    private final AuditLogService audit;
    private final ProfileRepository profiles;
    private final MemberSignupWriter signupWriter;
    private final IdentityWithdrawalRecorder withdrawals;
    private final TermsService terms;
    private final MailOutbox outbox;

    public OperatorMemberAccountAdminService(OperatorRepository operators, OperatorMemberAccountStore store, OperatorMemberAccountService accounts, AuditLogService audit,
                                             ProfileRepository profiles, MemberSignupWriter signupWriter, IdentityWithdrawalRecorder withdrawals, TermsService terms,
                                             MailOutbox outbox) {
        this.operators = operators;
        this.store = store;
        this.accounts = accounts;
        this.audit = audit;
        this.profiles = profiles;
        this.signupWriter = signupWriter;
        this.withdrawals = withdrawals;
        this.terms = terms;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public OperatorMemberAccountView get(String operatorId) {
        OperatorEntity operator = operators.findById(parseId(operatorId)).orElseThrow(OperatorNotFoundException::new);
        return view(operator);
    }

    @Transactional
    public OperatorMemberAccountView link(AdminActor actor, String operatorId, OperatorMemberLinkRequest body) {
        OperatorEntity operator = operators.findWithLockById(parseId(operatorId)).orElseThrow(OperatorNotFoundException::new);
        requireRootSelf(actor, operator);
        if (accounts.linkOfOperator(operator.getId()).isPresent()) {
            throw OperatorMemberAccountException.alreadyLinked();
        }
        Long userId = body == null ? null : parseUserId(body.userId());
        if (userId == null || !store.lockUser(userId)) {
            throw new MemberNotFoundException();
        }
        // 회원 행을 잠근 뒤 본다 — 그새 정지·탈퇴 신청·다른 연결이 들어왔을 수 있다. 같은 이메일이 아니어도 된다(세션 정리 결정 2026-11-24 — 정책 3 자격만)
        Candidate candidate = store.candidateById(userId).orElseThrow(MemberNotFoundException::new);
        List<String> reasons = ineligible(candidate);
        if (!reasons.isEmpty()) {
            throw OperatorMemberAccountException.notEligible(reasons);
        }
        String operatorEmail = UserPolicy.normalizeEmail(operator.getEmail());
        boolean sameEmail = operatorEmail != null && !operatorEmail.isBlank() && operatorEmail.equals(candidate.email());
        List<Long> released = store.releaseBoardStaff(userId); // 정책 4 — 그 회원의 게시판 매니저·스텝 지정이 모두 끝난다
        Instant now = accounts.now();
        // 운영팀 계정은 기본 비공개 프로필(사용자 2026-11-08) — 이을 때 한 번만 바꾼다(회원 본인이 나중에 공개로 돌리면 그대로 둔다)
        boolean madePrivate = profiles.findWithLockByUserId(userId).filter(p -> !p.isPrivate()).map(p -> {
            p.changeVisibility(ProfileVisibility.PRIVATE, now);
            return true;
        }).orElse(false);
        store.insert(operator.getId(), userId, now, actor.operatorId());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OPERATOR_MEMBER_LINK, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, Long.toString(operator.getId())).with("userId", Long.toString(userId))
                .with("releasedBoards", released.stream().map(String::valueOf).toList()).with("profileMadePrivate", madePrivate).with("sameEmail", sameEmail));
        log.info("운영자 회원 계정을 이었다 operatorId={} userId={} sameEmail={} releasedBoards={} by operatorId={}", operator.getId(), userId, sameEmail, released.size(),
                actor.operatorId());
        return view(operator);
    }

    /**
     * 관리자 후보 검색(세션 정리 결정 2026-11-24 — 같은 이메일이 아니어도 고를 수 있다). {@code q} 는 앞뒤 공백을 자르고 2자 이상(넘치면 400 {@code VALIDATION fields.q}).
     * 숫자만이면 회원번호 일치(숫자만인 닉네임의 부분 일치도 함께), {@code @} 가 있으면 이메일 전체 주소 일치(대소문자 무시 — 관리자 회원 목록이 개인정보 권한 없이 찾는 방식과 같다),
     * 그 밖은 닉네임 부분 일치(대소문자 무시). 탈퇴를 마친 회원 · 이 운영자에 이미 이어진 회원은 뺀다. 이메일은 늘 가려서 준다({@link MemberMasking}).
     */
    @Transactional(readOnly = true)
    public OperatorMemberCandidates searchCandidates(String operatorId, String q) {
        String keyword = q == null ? "" : q.strip();
        int length = keyword.codePointCount(0, keyword.length());
        if (length < SEARCH_MIN_LENGTH) {
            throw ValidationException.of("q", new MessageRef("search.q.tooShort", SEARCH_MIN_LENGTH));
        }
        if (length > SEARCH_MAX_LENGTH) {
            throw ValidationException.of("q", new MessageRef("search.q.tooLong", SEARCH_MAX_LENGTH));
        }
        OperatorEntity operator = operators.findById(parseId(operatorId)).orElseThrow(OperatorNotFoundException::new);
        CandidateSearch search;
        String value;
        if (keyword.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            search = CandidateSearch.MEMBER_NO;
            value = keyword;
        } else if (keyword.indexOf('@') >= 0) {
            search = CandidateSearch.EMAIL;
            value = UserPolicy.normalizeEmail(keyword);
        } else {
            search = CandidateSearch.NICKNAME;
            value = keyword.toLowerCase(Locale.ROOT);
        }
        List<OperatorMemberCandidates.Item> items = store.searchCandidates(search, value, operator.getId(), SEARCH_LIMIT).stream().map(c -> {
            List<String> reasons = ineligible(c);
            String id = Long.toString(c.userId());
            return new OperatorMemberCandidates.Item(id, id, nz(c.nickname()), MemberMasking.email(c.email()), c.gradeKey(), c.joinedAt(), c.managerCount(),
                    c.staffCount(), reasons.isEmpty(), reasons, reasons.contains(LINKED_TO_OTHER) ? c.linkedOperatorName() : null);
        }).toList();
        return new OperatorMemberCandidates(items);
    }

    /**
     * 새 회원 계정을 만들고 이 운영자에 바로 잇는다(「새 계정 만들기」 — 정책 2 「같은 이메일」의 예외). 입력 형식 검사와 비밀번호 해시는 부르는 쪽({@link OperatorMemberCreateService})이 끝낸다.
     * 판정 순서: 운영자 없음 404 → 루트 보호 409 → 이미 이어짐 409 → 이메일 중복 409 → 닉네임 중복 409(대소문자 무시) → 탈퇴 뒤 재가입 제한 409(공개 가입과 같은 차례).
     * 한 트랜잭션: 회원(이메일 인증 전, 약관 버전 = 지금 약관 — 공개 가입과 같다) + 프로필(<b>비공개</b>로 시작) + 연결 + 메일 대기열 한 통 + 감사 {@code OPERATOR_MEMBER_CREATE}.
     *
     * @param email        정규화한 이메일
     * @param passwordMode {@code DIRECT} · {@code MAIL}
     * @param passwordHash bcrypt 해시 — MAIL 이면 아무도 모르는 무작위 비밀번호의 해시(링크에서 정할 때까지 로그인할 수 없다)
     */
    @Transactional
    public OperatorMemberCreateResult createLinked(AdminActor actor, String operatorId, String email, String nickname, String passwordMode, String passwordHash) {
        OperatorEntity operator = operators.findWithLockById(parseId(operatorId)).orElseThrow(OperatorNotFoundException::new);
        requireRootSelf(actor, operator);
        if (accounts.linkOfOperator(operator.getId()).isPresent()) {
            throw OperatorMemberAccountException.alreadyLinked();
        }
        signupWriter.assertAvailable(email, nickname);
        Instant now = accounts.now();
        // 탈퇴 뒤 재가입 제한(commerce.md 17-2) — 공개 가입과 같다
        withdrawals.blockedUntil(WithdrawalKind.EMAIL, email, now).ifPresent(until -> {
            throw new AuthRejoinBlockedException(until, now);
        });
        long userId = signupWriter.create(email, passwordHash, nickname, terms.currentAgreementVersion());
        // 운영팀 계정은 기본 비공개 프로필(사용자 2026-11-08 — 이을 때와 같다)
        profiles.findById(userId).ifPresent(p -> p.changeVisibility(ProfileVisibility.PRIVATE, now));
        store.insert(operator.getId(), userId, now, actor.operatorId());

        boolean byMail = OperatorMemberCreateService.MODE_MAIL.equals(passwordMode);
        RequiredMail kind = byMail ? RequiredMail.MEMBER_PASSWORD_SETUP : RequiredMail.MEMBER_EMAIL_VERIFY;
        // 커밋 뒤 대기열 워커가 보낸다(실패해도 계정은 남고 재시도) — 링크는 보낼 때 만든다(대기열에 토큰 원문을 두지 않는다)
        outbox.enqueue(new OutboxMail("OPERATOR_MEMBER_CREATE:" + userId, userId, kind, Map.of()));
        String mailSent = byMail ? MAIL_PASSWORD_SETUP : MAIL_EMAIL_VERIFICATION;

        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OPERATOR_MEMBER_CREATE, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, Long.toString(operator.getId())).with("userId", Long.toString(userId)).with("passwordMode", passwordMode));
        log.info("운영자 회원 계정을 새로 만들어 이었다 operatorId={} userId={} passwordMode={} by operatorId={}", operator.getId(), userId, passwordMode, actor.operatorId());
        return new OperatorMemberCreateResult(Long.toString(userId), email, nickname, passwordMode, List.of(mailSent), view(operator));
    }

    @Transactional
    public OperatorMemberAccountView unlink(AdminActor actor, String operatorId) {
        OperatorEntity operator = operators.findWithLockById(parseId(operatorId)).orElseThrow(OperatorNotFoundException::new);
        requireRootSelf(actor, operator);
        long userId = store.delete(operator.getId()).orElseThrow(OperatorMemberAccountException::notLinked);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OPERATOR_MEMBER_UNLINK, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, Long.toString(operator.getId())).with("userId", Long.toString(userId)));
        log.info("운영자 회원 계정 연결을 끊었다 operatorId={} userId={} by operatorId={}", operator.getId(), userId, actor.operatorId());
        return view(operator);
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private OperatorMemberAccountView view(OperatorEntity operator) {
        Set<String> permissions = accounts.permissionsOf(operator.getId(), operator.isRoot());
        List<OperatorMemberAccountView.Power> items = POWERS.stream()
                .map(p -> new OperatorMemberAccountView.Power(p[0], p[1], p[1] == null || permissions.contains(p[1]))).toList();
        OperatorMemberAccountView.Powers powers = new OperatorMemberAccountView.Powers(items, null);
        Optional<Link> link = accounts.linkOfOperator(operator.getId());
        if (link.isPresent()) {
            Link l = link.get();
            OperatorMemberAccountView.Actor by = l.linkedById() == null ? null : new OperatorMemberAccountView.Actor(Long.toString(l.linkedById()), l.linkedByName());
            return new OperatorMemberAccountView(new OperatorMemberAccountView.Linked(Long.toString(l.userId()), nz(l.nickname()), l.gradeKey(), l.linkedAt(), by, l.active(),
                    OperatorMemberAccountService.pausedReasons(l)), null, powers);
        }
        OperatorMemberAccountView.Candidate candidate = store.candidateByEmail(UserPolicy.normalizeEmail(operator.getEmail())).filter(c -> !c.withdrawn())
                .map(c -> {
                    List<String> reasons = ineligible(c);
                    return new OperatorMemberAccountView.Candidate(Long.toString(c.userId()), nz(c.nickname()), c.gradeKey(), c.joinedAt(), c.managerCount(), c.staffCount(),
                            reasons.isEmpty(), reasons, reasons.contains(LINKED_TO_OTHER) ? c.linkedOperatorName() : null);
                }).orElse(null);
        return new OperatorMemberAccountView(null, candidate, powers);
    }

    /** 자격 미달 사유(정책 3 — 여럿 가능, 화면과 같은 차례) */
    static List<String> ineligible(Candidate c) {
        List<String> out = new ArrayList<>();
        if (!c.emailVerified()) {
            out.add(EMAIL_NOT_VERIFIED);
        }
        if (c.deleted() || !"ACTIVE".equals(c.status())) {
            out.add(MEMBER_RESTRICTED);
        }
        if (c.business()) {
            out.add(BUSINESS_ACCOUNT);
        }
        if (c.linkedOperatorId() != null) {
            out.add(LINKED_TO_OTHER);
        }
        return List.copyOf(out);
    }

    /** 대상이 최고 관리자면 최고 관리자 본인만 */
    private static void requireRootSelf(AdminActor actor, OperatorEntity operator) {
        if (operator.isRoot() && actor.operatorId() != operator.getId()) {
            throw OperatorMemberAccountException.rootProtected();
        }
    }

    private static long parseId(String raw) {
        Long id = parseUserId(raw);
        if (id == null) {
            throw new OperatorNotFoundException();
        }
        return id;
    }

    private static Long parseUserId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String text) {
        return text == null ? "" : text;
    }
}
