package com.community.groupbuy.feedback;

import com.community.holiday.service.BusinessDayCalendar;
import com.community.audit.dto.AuditActions;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
import com.community.groupbuy.repository.master.GroupBuyQnaStore;
import com.community.groupbuy.repository.master.GroupBuyQnaStore.AdminFilter;
import com.community.groupbuy.repository.master.GroupBuyQnaStore.AdminQuestionRow;
import com.community.groupbuy.repository.master.GroupBuyQnaStore.QuestionRow;
import com.community.groupbuy.repository.master.GroupBuyRowNames;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.service.GroupBuyAudit;
import com.community.groupbuy.service.GroupBuyBusinessDays;
import com.community.groupbuy.service.GroupBuyNotifications;
import com.community.payment.dto.response.PaymentMemberView;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 Q&A(2b-6 — 계약 「Q&A 규칙」, 사용자 결정 21·26). 회원(user-web)과 운영자(admin-web)가 같이 쓴다. 읽기·쓰기는 {@link GroupBuyQnaStore}.
 *
 * <ul>
 *   <li><b>쓰기</b>: 로그인한 정상 회원(정지·탈퇴는 403 {@code ACCOUNT_RESTRICTED} — 공구 참여 제한은 막지 않는다). 공개된 공구 어디서나, 중단(CANCELED)은 409 {@code GROUP_BUY_QNA_CLOSED}.
 *       본문 1~1,000자. 같은 회원은 {@value #WRITE_GAP_SECONDS}초에 한 번(429 {@code GROUP_BUY_QUESTION_TOO_FAST} — 글쓰기 간격 모양).
 *       본문에 전화번호·이메일·주소 모양이 보이면 비밀글로 바꿔 저장하고 {@code forcedSecret} 을 싣는다.</li>
 *   <li><b>보기</b>: 공개 질문은 누구나, 비밀글·숨긴 질문은 본문 없이(작성자에게만 본문). 지운 질문은 회원 목록에 없다(보관 기간 동안 남는다).</li>
 *   <li><b>작성자</b>: 답변 전에는 고치기·지우기, 답변 뒤에는 비밀글로 바꾸기·지우기만(고치기는 409 {@code GROUP_BUY_QUESTION_ANSWERED}).</li>
 *   <li><b>운영자</b>: 답변·답변 수정(이전 내용은 이력 + 감사 {@code GROUPBUY_QNA_ANSWER_EDIT}) · 숨김·숨김 풀기(사유 5~200자, 감사 {@code GROUPBUY_QNA_HIDE}, 숨기면 열린 신고 처리) ·
 *       목록·상세 열람은 개인정보 열람 감사 {@code GROUPBUY_QNA_VIEW}. 첫 답변이면 작성자에게 앱 알림.</li>
 *   <li><b>보관</b>: 마지막 답변일(없으면 작성일) + 3년 — {@code retain_until}, 파기는 배치(R__batch_32 ④).</li>
 *   <li><b>관리자 목록의 「기한 지남」</b>: 지난 영업일 수는 작성일이 뒤로 갈수록 줄기만 하므로 「{@value #ANSWER_DUE_BUSINESS_DAYS}영업일 지남」 = 「작성 시각 &lt; 경계 날 0시(KST)」
 *       ({@link GroupBuyBusinessDays#elapsedCutoff}) — 거르기·쪽 나누기·수를 DB 에서 한다. 줄마다 보이는 경과 영업일은 그 쪽의 줄만 자바로 센다.</li>
 * </ul>
 */
@Service
public class GroupBuyQnaService {

    public static final int PAGE_SIZE = 10;
    public static final int BODY_MAX = 1000;
    public static final int ANSWER_MAX = 2000;
    public static final int WRITE_GAP_SECONDS = 30;
    public static final int RETAIN_YEARS = 3;
    /** 답변 기한(결정 60 — 2영업일) */
    public static final int ANSWER_DUE_BUSINESS_DAYS = 2;

    private final GroupBuyQnaStore store;
    private final GroupBuyStore groups;
    private final GroupBuyMemberStore members;
    private final GroupBuyUgcReportService reports;
    private final GroupBuyNotifications notifications;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyQnaService(GroupBuyQnaStore store, GroupBuyStore groups, GroupBuyMemberStore members, GroupBuyUgcReportService reports, GroupBuyNotifications notifications,
                              GroupBuyAudit audit, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.groups = groups;
        this.members = members;
        this.reports = reports;
        this.notifications = notifications;
        this.audit = audit;
        this.ids = ids;
        this.clocks = clocks;
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /** 회원 목록 — 새 글이 위, {@value #PAGE_SIZE}개씩. {@code mine} 은 로그인했을 때만 */
    @Transactional(readOnly = true)
    public FeedbackView.QnaPage list(String groupBuyId, Long viewerId, boolean mine, int page) {
        GroupBuyRow g = visible(groupBuyId);
        int p = Math.max(0, page);
        Long onlyUser = mine && viewerId != null ? viewerId : null;
        long total = store.memberCount(g.id(), onlyUser);
        List<FeedbackView.Question> items = store.memberPage(g.id(), onlyUser, (long) p * PAGE_SIZE, PAGE_SIZE).stream().map(r -> view(r, viewerId)).toList();
        String block = askBlock(g, viewerId);
        FeedbackView.Round previous = g.roundNo() <= 1 ? null
                : store.previousRound(g.roundRootId(), g.roundNo() - 1).map(r -> new FeedbackView.Round(Long.toString(r[0]), (int) r[1])).orElse(null);
        return new FeedbackView.QnaPage(items, total, p, (long) (p + 1) * PAGE_SIZE < total, block == null, block, previous);
    }

    /** 쓸 수 없는 까닭 — 로그인 안 함 · 정지·탈퇴 · 중단된 공구(없으면 null) */
    String askBlock(GroupBuyRow g, Long userId) {
        if (userId == null) {
            return "LOGIN_REQUIRED";
        }
        if (members.state(userId).map(s -> s.suspended() || s.gone()).orElse(true)) {
            return "RESTRICTED";
        }
        return g.status() == GroupBuyStatus.CANCELED ? "CLOSED" : null;
    }

    @Transactional
    public FeedbackView.Question ask(long userId, String groupBuyId, String body, Boolean secret) {
        String text = body(body);
        GroupBuyRow g = visible(groupBuyId);
        requireWriter(userId);
        if (g.status() == GroupBuyStatus.CANCELED) {
            throw FeedbackException.qnaClosed();
        }
        Instant now = now();
        store.lockWriter(userId); // 같은 회원의 질문 쓰기를 줄 세운다(간격 검사)
        Instant last = store.lastAskedAt(userId);
        if (last != null) {
            long elapsedMs = Duration.between(last, now).toMillis();
            if (elapsedMs < WRITE_GAP_SECONDS * 1000L) {
                throw FeedbackException.questionTooFast(WRITE_GAP_SECONDS, Math.max(1, (WRITE_GAP_SECONDS * 1000L - elapsedMs + 999) / 1000));
            }
        }
        boolean forced = !Boolean.TRUE.equals(secret) && PersonalInfo.looksPersonal(text);
        long id = ids.nextId();
        store.insertQuestion(id, g.id(), userId, text, Boolean.TRUE.equals(secret) || forced, forced, now, retainFrom(now));
        notifications.questionAsked(id);
        return memberView(id, userId);
    }

    /** 작성자가 고친다 — 답변 전, 숨기지 않은 것만. 비밀글 여부도 다시 고른다(개인정보가 보이면 다시 비밀글) */
    @Transactional
    public FeedbackView.Question update(long userId, String questionId, String body, Boolean secret) {
        String text = body(body);
        QuestionRow r = own(userId, questionId);
        requireWriter(userId);
        if ("ANSWERED".equals(r.status())) {
            throw FeedbackException.questionAnswered();
        }
        if (r.hidden()) {
            throw GroupBuyException.invalidState();
        }
        boolean forced = !Boolean.TRUE.equals(secret) && PersonalInfo.looksPersonal(text);
        store.updateBody(r.id(), text, Boolean.TRUE.equals(secret) || forced, forced, now());
        return memberView(r.id(), userId);
    }

    /** 공개 → 비밀글(되돌리지 않는다) — 답변 전후 모두 */
    @Transactional
    public FeedbackView.Question makeSecret(long userId, String questionId) {
        QuestionRow r = own(userId, questionId);
        store.makeSecret(r.id());
        return memberView(r.id(), userId);
    }

    /** 지우기 — 목록에서 빠지고, 보관 기간 동안 감춘 채 남는다 */
    @Transactional
    public void remove(long userId, String questionId) {
        QuestionRow r = own(userId, questionId);
        store.softDelete(r.id(), now());
    }

    private FeedbackView.Question memberView(long id, long viewerId) {
        return store.memberRow(id).map(r -> view(r, viewerId)).orElseThrow(FeedbackException::questionNotFound);
    }

    /** 회원이 보는 모양 — 볼 수 없는 것은 비우고 할 수 있는 일을 정한다 */
    static FeedbackView.Question view(QuestionRow r, Long viewerId) {
        boolean own = viewerId != null && viewerId == r.userId();
        boolean visible = own || (!r.secret() && !r.hidden());
        boolean answered = "ANSWERED".equals(r.status());
        FeedbackView.Answer answer = visible && r.answer() != null ? new FeedbackView.Answer(r.answer(), r.answeredAt(), r.answerEdited()) : null;
        FeedbackView.QuestionActions actions = new FeedbackView.QuestionActions(own && !answered && !r.hidden(), own, own && answered && !r.secret(),
                viewerId != null && !own && visible);
        return new FeedbackView.Question(Long.toString(r.id()), PersonalInfo.maskNickname(r.nickname()), own, visible ? r.body() : null, r.secret(), r.forcedSecret(),
                r.status(), r.hidden(), r.createdAt(), r.updatedAt(), answer, actions);
    }

    private QuestionRow own(long userId, String questionId) {
        long id = parse(questionId, FeedbackException::questionNotFound);
        return store.ownRow(id, userId).orElseThrow(FeedbackException::questionNotFound);
    }

    private void requireWriter(long userId) {
        if (members.state(userId).map(s -> s.suspended() || s.gone()).orElse(true)) {
            throw FeedbackException.restricted();
        }
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    private static FeedbackView.QnaRow adminRow(AdminQuestionRow q, BusinessDayCalendar holidays, LocalDate today) {
        int elapsed = "OPEN".equals(q.status()) ? GroupBuyBusinessDays.elapsed(ServiceTime.dateOf(q.createdAt()), today, holidays) : 0;
        boolean overdue = "OPEN".equals(q.status()) && !q.hidden() && !q.deleted() && elapsed >= ANSWER_DUE_BUSINESS_DAYS;
        return new FeedbackView.QnaRow(Long.toString(q.id()), new FeedbackView.GroupBuyRef(Long.toString(q.groupBuyId()), q.title(), q.roundNo()),
                new PaymentMemberView(q.gone() ? null : Long.toString(q.userId()), q.gone() ? "" : nz(q.nickname()), q.gone()), preview(q.body()), q.secret(), q.forcedSecret(),
                q.status(), q.hidden(), q.deleted(), q.reportCount(), q.createdAt(), q.answeredAt(), elapsed, overdue);
    }

    /** 답변 기한이 지난 질문을 고르는 시각 경계 — 이 시각 전에 쓴 답변 대기 질문이 「기한 지남」 */
    private static Instant overdueBefore(LocalDate today, BusinessDayCalendar holidays) {
        return GroupBuyBusinessDays.elapsedCutoff(today, ANSWER_DUE_BUSINESS_DAYS, holidays).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /**
     * 관리자 목록 — 탭 OPEN(기본 — 오래 기다린 것 먼저)·ANSWERED·HIDDEN·ALL(지운 것 포함, 새 것 먼저) · {@code overdue}(답변 기한 지남) · 찾기(공구 제목·번호 · 닉네임 · 회원 번호).
     * 조회 자체를 개인정보 열람으로 남긴다(비밀글 본문이 실린다).
     */
    @Transactional
    public FeedbackView.QnaList adminList(String tab, boolean overdueOnly, String q, int page, int size, long operatorId, String ip) {
        String t = tab == null || tab.isBlank() ? "OPEN" : tab;
        if (!List.of("OPEN", "ANSWERED", "HIDDEN", "ALL").contains(t)) {
            throw ValidationException.of("tab", new MessageRef("groupbuy.qna.tab.invalid"));
        }
        String needle = q == null ? "" : q.trim();
        BusinessDayCalendar holidays = groups.settings().calendar();
        LocalDate today = ServiceTime.today(clock());
        Instant cutoff = overdueBefore(today, holidays);
        AdminFilter filter = new AdminFilter(t, needle.isEmpty() ? null : GroupBuyRowNames.like(needle), needle.isEmpty() ? null : parseOrNull(needle),
                overdueOnly ? cutoff : null);
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(0, page);
        long total = store.adminCount(filter);
        List<FeedbackView.QnaRow> items = store.adminPage(filter, "OPEN".equals(t), (long) p * s, s).stream().map(r -> adminRow(r, holidays, today)).toList();
        Map<String, Long> counts = store.stateCounts();
        long overdueCount = store.overdueCount(cutoff);
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_VIEW, GroupBuyAudit.TARGET_QUESTION, "LIST", ip, Map.of("tab", t, "count", items.size()));
        return new FeedbackView.QnaList(items, total, counts, overdueCount);
    }

    /** 관리자 상세 — 비밀글 본문 · 답변 · 수정 이력 · 숨김 · 열린 신고. 열람 감사 */
    @Transactional
    public FeedbackView.QnaDetail adminDetail(String questionId, long operatorId, String ip) {
        FeedbackView.QnaDetail d = detail(parse(questionId, FeedbackException::questionNotFound));
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_VIEW, GroupBuyAudit.TARGET_QUESTION, d.id(), ip, Map.of("groupBuyId", d.groupBuy().id()));
        return d;
    }

    private FeedbackView.QnaDetail detail(long id) {
        BusinessDayCalendar holidays = groups.settings().calendar();
        LocalDate today = ServiceTime.today(clock());
        AdminQuestionRow a = store.adminRow(id).orElseThrow(FeedbackException::questionNotFound);
        GroupBuyQnaStore.QuestionExtra x = store.extra(id).orElseThrow(FeedbackException::questionNotFound);
        FeedbackView.AdminAnswer answer = store.answer(id).map(v -> new FeedbackView.AdminAnswer(v.body(), v.answeredAt(), operator(v.answeredBy(), v.answeredByName()),
                v.updatedAt(), v.updatedBy() == null ? null : operator(v.updatedBy(), v.updatedByName()))).orElse(null);
        List<FeedbackView.AnswerRevision> revisions = store.revisions(id).stream().map(v -> new FeedbackView.AnswerRevision(Long.toString(v.id()), v.body(), v.writtenAt(),
                operator(v.writtenBy(), v.writtenByName()), v.replacedAt(), operator(v.replacedBy(), v.replacedByName()))).toList();
        FeedbackView.HiddenInfo hidden = x.hiddenAt() == null ? null : new FeedbackView.HiddenInfo(x.hiddenReason(), x.hiddenAt(), operator(x.hiddenBy(), x.hiddenByName()));
        FeedbackView.QnaRow r = adminRow(a, holidays, today);
        return new FeedbackView.QnaDetail(r.id(), r.groupBuy(), r.member(), r.preview(), r.secret(), r.forcedSecret(), r.status(), r.hidden(), r.deleted(), r.reportCount(),
                r.createdAt(), r.answeredAt(), r.elapsedBusinessDays(), r.overdue(), a.body(), x.updatedAt(), x.deletedAt(), x.retainUntil(), answer, revisions, hidden,
                reports.openReasons(GroupBuyUgcReportService.QUESTION, id));
    }

    /** 답변(처음) · 답변 수정(이전 내용은 이력 + 감사). 작성자가 지운 질문은 409. 보관 만료일은 마지막 답변일부터 다시 3년 */
    @Transactional
    public FeedbackView.QnaDetail answer(String questionId, String body, long operatorId, String ip) {
        long id = parse(questionId, FeedbackException::questionNotFound);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > ANSWER_MAX) {
            throw ValidationException.of("body", new MessageRef("groupbuy.qna.answer.length", ANSWER_MAX));
        }
        GroupBuyQnaStore.LockedQuestion q = store.lock(id).orElseThrow(FeedbackException::questionNotFound);
        if (q.deleted()) {
            throw FeedbackException.questionDeleted();
        }
        Instant now = now();
        Optional<GroupBuyQnaStore.CurrentAnswer> old = store.currentAnswer(id);
        if (old.isEmpty()) {
            store.insertAnswer(id, text, operatorId, now);
            store.markAnswered(id, retainFrom(now));
            notifications.questionAnswered(q.userId(), q.groupBuyId());
        } else if (!old.get().body().equals(text)) {
            GroupBuyQnaStore.CurrentAnswer o = old.get();
            store.insertAnswerRevision(ids.nextId(), id, o.body(), o.by(), o.at(), operatorId, now);
            store.updateAnswer(id, text, operatorId, now);
            store.setRetainUntil(id, retainFrom(now));
            audit.record(operatorId, AuditActions.GROUPBUY_QNA_ANSWER_EDIT, GroupBuyAudit.TARGET_QUESTION, id, ip, Map.of("length", text.length()));
        }
        return detail(id);
    }

    /** 숨김(사유 5~200자) — 열린 신고도 처리. 감사 */
    @Transactional
    public FeedbackView.QnaDetail hide(String questionId, String reason, long operatorId, String ip) {
        long id = parse(questionId, FeedbackException::questionNotFound);
        String why = reason(reason);
        lockLive(id);
        store.hide(id, why, operatorId, now());
        int resolved = reports.resolve(GroupBuyUgcReportService.QUESTION, id, operatorId);
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_HIDE, GroupBuyAudit.TARGET_QUESTION, id, ip, Map.of("action", "HIDE", "reason", why, "resolvedReports", resolved));
        return detail(id);
    }

    /** 숨김 풀기(사유 5~200자) — 감사 */
    @Transactional
    public FeedbackView.QnaDetail unhide(String questionId, String reason, long operatorId, String ip) {
        long id = parse(questionId, FeedbackException::questionNotFound);
        String why = reason(reason);
        lockLive(id);
        store.unhide(id);
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_HIDE, GroupBuyAudit.TARGET_QUESTION, id, ip, Map.of("action", "UNHIDE", "reason", why));
        return detail(id);
    }

    private void lockLive(long id) {
        if (store.lock(id).orElseThrow(FeedbackException::questionNotFound).deleted()) {
            throw FeedbackException.questionDeleted();
        }
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 공개된 공구(모집 예정·중·종료·중단) — 공개 전·없음은 404 */
    private GroupBuyRow visible(String groupBuyId) {
        long id = parse(groupBuyId, GroupBuyException::notFound);
        return groups.find(id).filter(r -> r.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
    }

    private static String body(String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw ValidationException.of("body", new MessageRef("groupbuy.qna.body.required"));
        }
        if (text.codePointCount(0, text.length()) > BODY_MAX) {
            throw ValidationException.of("body", new MessageRef("groupbuy.qna.body.length", BODY_MAX));
        }
        return text;
    }

    static String reason(String reason) {
        String why = reason == null ? "" : reason.strip();
        int n = why.codePointCount(0, why.length());
        if (n < 5 || n > 200) {
            throw ValidationException.of("reason", new MessageRef("groupbuy.feedback.reason.length", 5, 200));
        }
        return why;
    }

    private LocalDate retainFrom(Instant at) {
        return ServiceTime.dateOf(at).plusYears(RETAIN_YEARS);
    }

    static String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.codePointCount(0, body.length()) > 60 ? body.substring(0, body.offsetByCodePoints(0, 60)) + "…" : body;
    }

    static FeedbackView.Operator operator(Long id, String name) {
        return id == null ? null : new FeedbackView.Operator(Long.toString(id), name == null ? "" : name);
    }

    static long parse(String id, java.util.function.Supplier<? extends RuntimeException> notFound) {
        Long n = parseOrNull(id);
        if (n == null) {
            throw notFound.get();
        }
        return n;
    }

    static Long parseOrNull(String id) {
        try {
            return id == null ? null : Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    private Clock clock() {
        return clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock().instant();
    }
}
