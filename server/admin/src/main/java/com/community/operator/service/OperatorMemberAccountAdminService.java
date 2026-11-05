package com.community.operator.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.dto.request.OperatorMemberLinkRequest;
import com.community.operator.dto.response.OperatorMemberAccountView;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.exception.OperatorMemberAccountException;
import com.community.operator.exception.OperatorNotFoundException;
import com.community.operator.repository.master.OperatorMemberAccountStore;
import com.community.operator.repository.master.OperatorMemberAccountStore.Candidate;
import com.community.operator.repository.master.OperatorMemberAccountStore.Link;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.user.service.UserPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 *   <li><b>후보</b>(정책 2): 운영자 이메일과 같은 이메일(정규화한 소문자)로 가입한 회원 하나 — 서버가 찾아 준다(관리자가 아무 회원이나 고르지 못한다).</li>
 *   <li><b>자격</b>(정책 3, 연결할 때만): 이메일 인증 · 회원 상태 정상(정지·탈퇴 진행 아님) · 사업자 계정 아님 · 다른 운영자에 이어져 있지 않음.</li>
 *   <li><b>연결</b>: 운영자 행 잠금 → 루트 보호 → 이미 이어짐 409 → 후보 409 → <b>회원 행 잠금</b>(탈퇴 신청 · 게시판 담당 지정과 같은 잠금) → 자격 다시 판정 409 →
 *       그 회원의 게시판 매니저·스텝 지정을 모두 끝냄(정책 4 — 행 삭제, 감사 {@code releasedBoards}) → 연결 + 감사 {@code OPERATOR_MEMBER_LINK}, 한 트랜잭션.</li>
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

    private final OperatorRepository operators;
    private final OperatorMemberAccountStore store;
    private final OperatorMemberAccountService accounts;
    private final AuditLogService audit;

    public OperatorMemberAccountAdminService(OperatorRepository operators, OperatorMemberAccountStore store, OperatorMemberAccountService accounts, AuditLogService audit) {
        this.operators = operators;
        this.store = store;
        this.accounts = accounts;
        this.audit = audit;
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
        String email = UserPolicy.normalizeEmail(operator.getEmail());
        Candidate seen = store.candidateByEmail(email).orElse(null);
        if (userId == null || seen == null || seen.userId() != userId || !store.lockUser(userId)) {
            throw OperatorMemberAccountException.notCandidate();
        }
        // 회원 행을 잠근 뒤 다시 본다 — 그새 이메일이 바뀌었거나 정지·탈퇴 신청·다른 연결이 들어왔을 수 있다
        Candidate candidate = store.candidateByEmail(email).filter(c -> c.userId() == userId).orElseThrow(OperatorMemberAccountException::notCandidate);
        if (!ineligible(candidate).isEmpty()) {
            throw OperatorMemberAccountException.notEligible();
        }
        List<Long> released = store.releaseBoardStaff(userId); // 정책 4 — 그 회원의 게시판 매니저·스텝 지정이 모두 끝난다
        Instant now = accounts.now();
        store.insert(operator.getId(), userId, now, actor.operatorId());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OPERATOR_MEMBER_LINK, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, Long.toString(operator.getId())).with("userId", Long.toString(userId))
                .with("releasedBoards", released.stream().map(String::valueOf).toList()));
        log.info("운영자 회원 계정을 이었다 operatorId={} userId={} releasedBoards={} by operatorId={}", operator.getId(), userId, released.size(), actor.operatorId());
        return view(operator);
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
        OperatorMemberAccountView.Candidate candidate = store.candidateByEmail(UserPolicy.normalizeEmail(operator.getEmail())).filter(c -> !c.deleted() && !"WITHDRAWN".equals(c.status()))
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
