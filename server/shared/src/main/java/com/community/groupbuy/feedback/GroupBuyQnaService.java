package com.community.groupbuy.feedback;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
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
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 Q&A(2b-6 — 계약 「Q&A 규칙」, 사용자 결정 21·26). 회원(user-web)과 운영자(admin-web)가 같이 쓴다.
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

    private static final String ADMIN_SELECT = "SELECT q.id, q.group_buy_id, COALESCE(NULLIF(g.title, ''), p.name), g.round_no, q.user_id, pr.nickname, "
            + "(u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL), q.body, q.secret, q.forced_secret, q.status, q.hidden_at IS NOT NULL, "
            + "q.deleted_at IS NOT NULL, (SELECT count(*) FROM group_buy_ugc_reports r WHERE r.target_type = 'GROUP_BUY_QUESTION' AND r.target_id = q.id AND r.status = 'OPEN'), "
            + "q.created_at, a.answered_at FROM group_buy_questions q JOIN group_buys g ON g.id = q.group_buy_id JOIN group_buy_products p ON p.id = g.product_id "
            + "LEFT JOIN users u ON u.id = q.user_id LEFT JOIN profiles pr ON pr.user_id = q.user_id LEFT JOIN group_buy_answers a ON a.question_id = q.id ";

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;
    private final GroupBuyMemberStore members;
    private final GroupBuyUgcReportService reports;
    private final GroupBuyNotifications notifications;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyQnaService(DataSource dataSource, GroupBuyStore groups, GroupBuyMemberStore members, GroupBuyUgcReportService reports, GroupBuyNotifications notifications,
                              GroupBuyAudit audit, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.groups = groups;
        this.members = members;
        this.reports = reports;
        this.notifications = notifications;
        this.audit = audit;
        this.ids = ids;
        this.clocks = clocks;
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /** 한 줄 — 회원 목록·쓰기 응답이 같이 쓴다 */
    private record Row(long id, long userId, String nickname, String body, boolean secret, boolean forcedSecret, String status, boolean hidden, Instant createdAt,
                       Instant updatedAt, String answer, Instant answeredAt, boolean answerEdited) {
    }

    private static final String MEMBER_SELECT = "SELECT q.id, q.user_id, pr.nickname, q.body, q.secret, q.forced_secret, q.status, q.hidden_at IS NOT NULL, q.created_at, "
            + "q.updated_at, a.body, a.answered_at, a.updated_at IS NOT NULL, (u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL) "
            + "FROM group_buy_questions q LEFT JOIN group_buy_answers a ON a.question_id = q.id LEFT JOIN users u ON u.id = q.user_id "
            + "LEFT JOIN profiles pr ON pr.user_id = q.user_id ";

    private static Row row(ResultSet rs) throws SQLException {
        boolean gone = rs.getBoolean(14);
        return new Row(rs.getLong(1), rs.getLong(2), gone ? null : rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getString(7), rs.getBoolean(8),
                instant(rs, 9), instant(rs, 10), rs.getString(11), instant(rs, 12), rs.getBoolean(13));
    }

    /** 회원 목록 — 새 글이 위, {@value #PAGE_SIZE}개씩. {@code mine} 은 로그인했을 때만 */
    @Transactional(readOnly = true)
    public FeedbackView.QnaPage list(String groupBuyId, Long viewerId, boolean mine, int page) {
        GroupBuyRow g = visible(groupBuyId);
        int p = Math.max(0, page);
        boolean onlyMine = mine && viewerId != null;
        String where = "WHERE q.group_buy_id = ? AND q.deleted_at IS NULL" + (onlyMine ? " AND q.user_id = ?" : "");
        Object[] args = onlyMine ? new Object[] {g.id(), viewerId} : new Object[] {g.id()};
        long total = jdbc.queryForObject("SELECT count(*) FROM group_buy_questions q " + where, Long.class, args);
        List<Object> pageArgs = new ArrayList<>(List.of(args));
        pageArgs.add(PAGE_SIZE);
        pageArgs.add((long) p * PAGE_SIZE);
        List<FeedbackView.Question> items = jdbc.query(MEMBER_SELECT + where + " ORDER BY q.created_at DESC, q.id DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), pageArgs.toArray())
                .stream().map(r -> view(r, viewerId)).toList();
        String block = askBlock(g, viewerId);
        FeedbackView.Round previous = g.roundNo() <= 1 ? null
                : jdbc.query("SELECT id, round_no FROM group_buys WHERE round_root_id = ? AND round_no = ? AND published_at IS NOT NULL",
                        (rs, i) -> new FeedbackView.Round(Long.toString(rs.getLong(1)), rs.getInt(2)), g.roundRootId(), g.roundNo() - 1).stream().findFirst().orElse(null);
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
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "gbq:" + userId); // 같은 회원의 질문 쓰기를 줄 세운다(간격 검사)
        Timestamp lastAt = jdbc.queryForObject("SELECT max(created_at) FROM group_buy_questions WHERE user_id = ?", Timestamp.class, userId);
        Instant last = lastAt == null ? null : lastAt.toInstant();
        if (last != null) {
            long elapsedMs = Duration.between(last, now).toMillis();
            if (elapsedMs < WRITE_GAP_SECONDS * 1000L) {
                throw FeedbackException.questionTooFast(WRITE_GAP_SECONDS, Math.max(1, (WRITE_GAP_SECONDS * 1000L - elapsedMs + 999) / 1000));
            }
        }
        boolean forced = !Boolean.TRUE.equals(secret) && PersonalInfo.looksPersonal(text);
        long id = ids.nextId();
        jdbc.update("INSERT INTO group_buy_questions (id, group_buy_id, user_id, body, secret, forced_secret, status, created_at, retain_until) VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)",
                id, g.id(), userId, text, Boolean.TRUE.equals(secret) || forced, forced, Timestamp.from(now), Date.valueOf(retainFrom(now)));
        notifications.questionAsked(id);
        return memberView(id, userId);
    }

    /** 작성자가 고친다 — 답변 전, 숨기지 않은 것만. 비밀글 여부도 다시 고른다(개인정보가 보이면 다시 비밀글) */
    @Transactional
    public FeedbackView.Question update(long userId, String questionId, String body, Boolean secret) {
        String text = body(body);
        Row r = own(userId, questionId);
        requireWriter(userId);
        if ("ANSWERED".equals(r.status())) {
            throw FeedbackException.questionAnswered();
        }
        if (r.hidden()) {
            throw GroupBuyException.invalidState();
        }
        boolean forced = !Boolean.TRUE.equals(secret) && PersonalInfo.looksPersonal(text);
        jdbc.update("UPDATE group_buy_questions SET body = ?, secret = ?, forced_secret = ?, updated_at = ? WHERE id = ?", text, Boolean.TRUE.equals(secret) || forced, forced,
                Timestamp.from(now()), r.id());
        return memberView(r.id(), userId);
    }

    /** 공개 → 비밀글(되돌리지 않는다) — 답변 전후 모두 */
    @Transactional
    public FeedbackView.Question makeSecret(long userId, String questionId) {
        Row r = own(userId, questionId);
        jdbc.update("UPDATE group_buy_questions SET secret = true WHERE id = ?", r.id());
        return memberView(r.id(), userId);
    }

    /** 지우기 — 목록에서 빠지고, 보관 기간 동안 감춘 채 남는다 */
    @Transactional
    public void remove(long userId, String questionId) {
        Row r = own(userId, questionId);
        jdbc.update("UPDATE group_buy_questions SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL", Timestamp.from(now()), r.id());
    }

    private FeedbackView.Question memberView(long id, long viewerId) {
        return jdbc.query(MEMBER_SELECT + "WHERE q.id = ?", (rs, i) -> row(rs), id).stream().findFirst().map(r -> view(r, viewerId)).orElseThrow(FeedbackException::questionNotFound);
    }

    /** 회원이 보는 모양 — 볼 수 없는 것은 비우고 할 수 있는 일을 정한다 */
    static FeedbackView.Question view(Row r, Long viewerId) {
        boolean own = viewerId != null && viewerId == r.userId();
        boolean visible = own || (!r.secret() && !r.hidden());
        boolean answered = "ANSWERED".equals(r.status());
        FeedbackView.Answer answer = visible && r.answer() != null ? new FeedbackView.Answer(r.answer(), r.answeredAt(), r.answerEdited()) : null;
        FeedbackView.QuestionActions actions = new FeedbackView.QuestionActions(own && !answered && !r.hidden(), own, own && answered && !r.secret(),
                viewerId != null && !own && visible);
        return new FeedbackView.Question(Long.toString(r.id()), PersonalInfo.maskNickname(r.nickname()), own, visible ? r.body() : null, r.secret(), r.forcedSecret(),
                r.status(), r.hidden(), r.createdAt(), r.updatedAt(), answer, actions);
    }

    private Row own(long userId, String questionId) {
        long id = parse(questionId, FeedbackException::questionNotFound);
        return jdbc.query(MEMBER_SELECT + "WHERE q.id = ? AND q.user_id = ? AND q.deleted_at IS NULL", (rs, i) -> row(rs), id, userId).stream().findFirst()
                .orElseThrow(FeedbackException::questionNotFound);
    }

    private void requireWriter(long userId) {
        if (members.state(userId).map(s -> s.suspended() || s.gone()).orElse(true)) {
            throw FeedbackException.restricted();
        }
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    private record AdminRow(FeedbackView.QnaRow row, String body) {
    }

    private AdminRow adminRow(ResultSet rs, List<LocalDate> holidays, LocalDate today) throws SQLException {
        long userId = rs.getLong(5);
        boolean gone = rs.getBoolean(7);
        String body = rs.getString(8);
        String status = rs.getString(11);
        boolean hidden = rs.getBoolean(12);
        boolean deleted = rs.getBoolean(13);
        Instant created = instant(rs, 15);
        int elapsed = "OPEN".equals(status) ? GroupBuyBusinessDays.elapsed(ServiceTime.dateOf(created), today, holidays) : 0;
        boolean overdue = "OPEN".equals(status) && !hidden && !deleted && elapsed >= ANSWER_DUE_BUSINESS_DAYS;
        FeedbackView.QnaRow row = new FeedbackView.QnaRow(Long.toString(rs.getLong(1)), new FeedbackView.GroupBuyRef(Long.toString(rs.getLong(2)), rs.getString(3), rs.getInt(4)),
                new PaymentMemberView(gone ? null : Long.toString(userId), gone ? "" : nz(rs.getString(6)), gone), preview(body), rs.getBoolean(9), rs.getBoolean(10), status,
                hidden, deleted, rs.getInt(14), created, instant(rs, 16), elapsed, overdue);
        return new AdminRow(row, body);
    }

    /**
     * 관리자 목록 — 탭 OPEN(기본 — 오래 기다린 것 먼저)·ANSWERED·HIDDEN·ALL(지운 것 포함, 새 것 먼저) · {@code overdue}(답변 기한 지남) · 찾기(공구 제목·번호 · 닉네임 · 회원 번호).
     * 조회 자체를 개인정보 열람으로 남긴다(비밀글 본문이 실린다).
     */
    @Transactional
    public FeedbackView.QnaList adminList(String tab, boolean overdueOnly, String q, int page, int size, long operatorId, String ip) {
        String t = tab == null || tab.isBlank() ? "OPEN" : tab;
        StringBuilder where = new StringBuilder("WHERE TRUE");
        List<Object> args = new ArrayList<>();
        switch (t) {
            case "OPEN", "ANSWERED" -> {
                where.append(" AND q.status = ? AND q.hidden_at IS NULL AND q.deleted_at IS NULL");
                args.add(t);
            }
            case "HIDDEN" -> where.append(" AND q.hidden_at IS NOT NULL AND q.deleted_at IS NULL");
            case "ALL" -> {
            }
            default -> throw ValidationException.of("tab", new MessageRef("groupbuy.qna.tab.invalid"));
        }
        String needle = q == null ? "" : q.trim();
        if (!needle.isEmpty()) {
            Long n = parseOrNull(needle);
            where.append(" AND (g.title ILIKE ? OR p.name ILIKE ? OR pr.nickname ILIKE ?").append(n != null ? " OR q.user_id = ? OR q.group_buy_id = ?" : "").append(")");
            String like = "%" + needle.replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            if (n != null) {
                args.add(n);
                args.add(n);
            }
        }
        String order = "OPEN".equals(t) ? " ORDER BY q.created_at, q.id" : " ORDER BY q.created_at DESC, q.id DESC";
        List<LocalDate> holidays = groups.settings().holidays();
        LocalDate today = ServiceTime.today(clock());
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(0, page);
        List<FeedbackView.QnaRow> all = jdbc.query(ADMIN_SELECT + where + order, (rs, i) -> adminRow(rs, holidays, today).row(), args.toArray());
        List<FeedbackView.QnaRow> matched = overdueOnly ? all.stream().filter(FeedbackView.QnaRow::overdue).toList() : all;
        List<FeedbackView.QnaRow> items = matched.stream().skip((long) p * s).limit(s).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT CASE WHEN hidden_at IS NOT NULL THEN 'HIDDEN' ELSE status END, count(*) FROM group_buy_questions WHERE deleted_at IS NULL GROUP BY 1",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        for (String k : List.of("OPEN", "ANSWERED", "HIDDEN")) {
            counts.putIfAbsent(k, 0L);
        }
        long overdueCount = jdbc.queryForList("SELECT created_at FROM group_buy_questions WHERE status = 'OPEN' AND hidden_at IS NULL AND deleted_at IS NULL", Timestamp.class)
                .stream().filter(c -> GroupBuyBusinessDays.elapsed(ServiceTime.dateOf(c.toInstant()), today, holidays) >= ANSWER_DUE_BUSINESS_DAYS).count();
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_VIEW, GroupBuyAudit.TARGET_QUESTION, "LIST", ip, Map.of("tab", t, "count", items.size()));
        return new FeedbackView.QnaList(items, matched.size(), counts, overdueCount);
    }

    /** 관리자 상세 — 비밀글 본문 · 답변 · 수정 이력 · 숨김 · 열린 신고. 열람 감사 */
    @Transactional
    public FeedbackView.QnaDetail adminDetail(String questionId, long operatorId, String ip) {
        FeedbackView.QnaDetail d = detail(parse(questionId, FeedbackException::questionNotFound));
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_VIEW, GroupBuyAudit.TARGET_QUESTION, d.id(), ip, Map.of("groupBuyId", d.groupBuy().id()));
        return d;
    }

    private FeedbackView.QnaDetail detail(long id) {
        List<LocalDate> holidays = groups.settings().holidays();
        LocalDate today = ServiceTime.today(clock());
        AdminRow a = jdbc.query(ADMIN_SELECT + "WHERE q.id = ?", (rs, i) -> adminRow(rs, holidays, today), id).stream().findFirst()
                .orElseThrow(FeedbackException::questionNotFound);
        record Extra(Instant updatedAt, Instant deletedAt, LocalDate retainUntil, String hiddenReason, Instant hiddenAt, Long hiddenBy, String hiddenByName) {
        }
        Extra x = jdbc.queryForObject("SELECT q.updated_at, q.deleted_at, q.retain_until, q.hidden_reason, q.hidden_at, q.hidden_by, o.operator_name FROM group_buy_questions q "
                + "LEFT JOIN operators o ON o.id = q.hidden_by WHERE q.id = ?", (rs, i) -> new Extra(instant(rs, 1), instant(rs, 2), rs.getDate(3).toLocalDate(), rs.getString(4),
                        instant(rs, 5), (Long) rs.getObject(6), rs.getString(7)), id);
        FeedbackView.AdminAnswer answer = jdbc.query("SELECT a.body, a.answered_at, a.answered_by, o1.operator_name, a.updated_at, a.updated_by, o2.operator_name "
                + "FROM group_buy_answers a LEFT JOIN operators o1 ON o1.id = a.answered_by LEFT JOIN operators o2 ON o2.id = a.updated_by WHERE a.question_id = ?",
                (rs, i) -> new FeedbackView.AdminAnswer(rs.getString(1), instant(rs, 2), operator(rs.getLong(3), rs.getString(4)), instant(rs, 5),
                        rs.getObject(6) == null ? null : operator(rs.getLong(6), rs.getString(7))), id).stream().findFirst().orElse(null);
        List<FeedbackView.AnswerRevision> revisions = jdbc.query("SELECT r.id, r.body, r.written_at, r.written_by, o1.operator_name, r.replaced_at, r.replaced_by, o2.operator_name "
                + "FROM group_buy_answer_revisions r LEFT JOIN operators o1 ON o1.id = r.written_by LEFT JOIN operators o2 ON o2.id = r.replaced_by WHERE r.question_id = ? "
                + "ORDER BY r.replaced_at DESC, r.id DESC", (rs, i) -> new FeedbackView.AnswerRevision(Long.toString(rs.getLong(1)), rs.getString(2), instant(rs, 3),
                        operator(rs.getLong(4), rs.getString(5)), instant(rs, 6), operator(rs.getLong(7), rs.getString(8))), id);
        FeedbackView.HiddenInfo hidden = x.hiddenAt() == null ? null : new FeedbackView.HiddenInfo(x.hiddenReason(), x.hiddenAt(), operator(x.hiddenBy(), x.hiddenByName()));
        FeedbackView.QnaRow r = a.row();
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
        record Q(long userId, long groupBuyId, boolean deleted) {
        }
        Q q = jdbc.query("SELECT user_id, group_buy_id, deleted_at IS NOT NULL FROM group_buy_questions WHERE id = ? FOR UPDATE", (rs, i) -> new Q(rs.getLong(1), rs.getLong(2),
                rs.getBoolean(3)), id).stream().findFirst().orElseThrow(FeedbackException::questionNotFound);
        if (q.deleted()) {
            throw FeedbackException.questionDeleted();
        }
        Instant now = now();
        record Old(String body, Instant at, long by) {
        }
        Optional<Old> old = jdbc.query("SELECT body, COALESCE(updated_at, answered_at), COALESCE(updated_by, answered_by) FROM group_buy_answers WHERE question_id = ?",
                (rs, i) -> new Old(rs.getString(1), instant(rs, 2), rs.getLong(3)), id).stream().findFirst();
        if (old.isEmpty()) {
            jdbc.update("INSERT INTO group_buy_answers (question_id, body, answered_by, answered_at) VALUES (?, ?, ?, ?)", id, text, operatorId, Timestamp.from(now));
            jdbc.update("UPDATE group_buy_questions SET status = 'ANSWERED', retain_until = ? WHERE id = ?", Date.valueOf(retainFrom(now)), id);
            notifications.questionAnswered(q.userId(), q.groupBuyId());
        } else if (!old.get().body().equals(text)) {
            Old o = old.get();
            jdbc.update("INSERT INTO group_buy_answer_revisions (id, question_id, body, written_by, written_at, replaced_by, replaced_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    ids.nextId(), id, o.body(), o.by(), Timestamp.from(o.at()), operatorId, Timestamp.from(now));
            jdbc.update("UPDATE group_buy_answers SET body = ?, updated_by = ?, updated_at = ? WHERE question_id = ?", text, operatorId, Timestamp.from(now), id);
            jdbc.update("UPDATE group_buy_questions SET retain_until = ? WHERE id = ?", Date.valueOf(retainFrom(now)), id);
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
        jdbc.update("UPDATE group_buy_questions SET hidden_at = ?, hidden_by = ?, hidden_reason = ? WHERE id = ?", Timestamp.from(now()), operatorId, why, id);
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
        jdbc.update("UPDATE group_buy_questions SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL WHERE id = ?", id);
        audit.record(operatorId, AuditActions.GROUPBUY_QNA_HIDE, GroupBuyAudit.TARGET_QUESTION, id, ip, Map.of("action", "UNHIDE", "reason", why));
        return detail(id);
    }

    private void lockLive(long id) {
        Boolean deleted = jdbc.query("SELECT deleted_at IS NOT NULL FROM group_buy_questions WHERE id = ? FOR UPDATE", (rs, i) -> rs.getBoolean(1), id).stream().findFirst()
                .orElseThrow(FeedbackException::questionNotFound);
        if (deleted) {
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

    static Instant instant(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private Clock clock() {
        return clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock().instant();
    }
}
