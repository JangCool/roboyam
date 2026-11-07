package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.feedback.FeedbackView;
import com.community.groupbuy.feedback.GroupBuyQnaService;
import com.community.groupbuy.service.GroupBuyBusinessDays;
import com.community.holiday.service.BusinessDayCalendar;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 관리자 문의 목록(3단계 2차) — 예전에는 조건에 맞는 질문을 <b>모두 읽어</b> 줄마다 지난 영업일을 자바로 세고 「기한 지남」을 거른 뒤 메모리에서 쪽을 나눴다. 지금은 영업일 경계 시각
 * ({@link GroupBuyBusinessDays#elapsedCutoff})으로 DB 에서 거르고 나눈다 — 예전 문장과 예전 자바 셈을 그대로 돌려 <b>결과(줄 차례·쪽·건수·탭 수·기한 지남 수)가 같은지</b>
 * 실제 PostgreSQL 에서 견준다. 여러 날(주말·사이트 휴일 포함)에 걸쳐 시계를 옮기며 본다.
 */
class GroupBuyQnaAdminListParityTest {

    /** 옮기기 전 {@code GroupBuyQnaService.ADMIN_SELECT} 그대로 */
    private static final String OLD_SELECT = "SELECT q.id, q.status, q.hidden_at IS NOT NULL, q.deleted_at IS NOT NULL, q.created_at FROM group_buy_questions q "
            + "JOIN group_buys g ON g.id = q.group_buy_id JOIN group_buy_products p ON p.id = g.product_id LEFT JOIN users u ON u.id = q.user_id "
            + "LEFT JOIN profiles pr ON pr.user_id = q.user_id LEFT JOIN group_buy_answers a ON a.question_id = q.id ";

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    @Test
    void 기한_지남_거르기와_쪽_나누기가_예전_메모리_셈과_같다() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(20_000).open();
        String title = kit.jdbc.queryForObject("SELECT COALESCE(NULLIF(g.title, ''), p.name) FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?",
                String.class, gb);
        List<String> made = new ArrayList<>();
        // 열흘에 걸쳐 하루 반나절 간격으로 질문 — 몇은 답하고, 몇은 숨기고, 몇은 지운다
        for (int i = 0; i < 12; i++) {
            long member = kit.newMember();
            FeedbackView.Question q = kit.qna.ask(member, Long.toString(gb), "질문 " + i + " 배송은요?", false);
            made.add(q.id());
            if (i % 4 == 1) {
                kit.qna.answer(q.id(), "답변 " + i, kit.operator, "ip");
            } else if (i % 4 == 2) {
                kit.qna.hide(q.id(), "시험 숨김 사유", kit.operator, "ip");
            } else if (i % 5 == 3) {
                kit.qna.remove(member, q.id());
            }
            kit.advance(Duration.ofHours(19));
        }
        for (int day = 0; day < 6; day++) {
            BusinessDayCalendar holidays = kit.groupStore.settings().calendar();
            LocalDate today = ServiceTime.today(kit.clock);
            for (String tab : new String[] {"OPEN", "ANSWERED", "HIDDEN", "ALL"}) {
                for (String q : new String[] {null, title, "질문"}) {
                    for (boolean overdueOnly : new boolean[] {false, true}) {
                        List<String> expected = old(tab, q, overdueOnly, holidays, today);
                        for (int page = 0; page * 4 <= expected.size() && page < 3; page++) {
                            FeedbackView.QnaList list = kit.qna.adminList(tab, overdueOnly, q, page, 4, kit.operator, "ip");
                            assertThat(list.items()).extracting(FeedbackView.QnaRow::id).as("day=%d tab=%s q=%s overdue=%s page=%d", day, tab, q, overdueOnly, page)
                                    .containsExactlyElementsOf(expected.stream().skip(page * 4L).limit(4).toList());
                            assertThat(list.total()).isEqualTo(expected.size());
                            assertThat(list.counts()).isEqualTo(oldCounts());
                            assertThat(list.overdueCount()).isEqualTo(oldOverdueCount(holidays, today));
                        }
                    }
                }
            }
            kit.advance(Duration.ofDays(1));
        }
        assertThat(made).hasSize(12);
        assertThat(oldOverdueCount(kit.groupStore.settings().calendar(), ServiceTime.today(kit.clock))).as("시험 자료에 기한 지난 질문이 있다").isPositive();
        assertThat(old("OPEN", title, true, kit.groupStore.settings().calendar(), ServiceTime.today(kit.clock))).as("이 공구의 기한 지난 질문").isNotEmpty();
    }

    /** 예전 셈 — 조건에 맞는 줄을 다 읽어 지난 영업일로 「기한 지남」을 거른다(쪽은 부르는 쪽이 나눈다) */
    private List<String> old(String tab, String q, boolean overdueOnly, BusinessDayCalendar holidays, LocalDate today) {
        StringBuilder where = new StringBuilder("WHERE TRUE");
        List<Object> args = new ArrayList<>();
        switch (tab) {
            case "OPEN", "ANSWERED" -> {
                where.append(" AND q.status = ? AND q.hidden_at IS NULL AND q.deleted_at IS NULL");
                args.add(tab);
            }
            case "HIDDEN" -> where.append(" AND q.hidden_at IS NOT NULL AND q.deleted_at IS NULL");
            default -> {
            }
        }
        String needle = q == null ? "" : q.trim();
        if (!needle.isEmpty()) {
            Long n = parse(needle);
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
        String order = "OPEN".equals(tab) ? " ORDER BY q.created_at, q.id" : " ORDER BY q.created_at DESC, q.id DESC";
        List<String> out = new ArrayList<>();
        kit.jdbc.query(OLD_SELECT + where + order, rs -> {
            String status = rs.getString(2);
            boolean hidden = rs.getBoolean(3);
            boolean deleted = rs.getBoolean(4);
            int elapsed = "OPEN".equals(status) ? GroupBuyBusinessDays.elapsed(ServiceTime.dateOf(rs.getTimestamp(5).toInstant()), today, holidays) : 0;
            boolean overdue = "OPEN".equals(status) && !hidden && !deleted && elapsed >= GroupBuyQnaService.ANSWER_DUE_BUSINESS_DAYS;
            if (!overdueOnly || overdue) {
                out.add(Long.toString(rs.getLong(1)));
            }
        }, args.toArray());
        return out;
    }

    private Map<String, Long> oldCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        kit.jdbc.query("SELECT CASE WHEN hidden_at IS NOT NULL THEN 'HIDDEN' ELSE status END, count(*) FROM group_buy_questions WHERE deleted_at IS NULL GROUP BY 1", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        for (String k : List.of("OPEN", "ANSWERED", "HIDDEN")) {
            counts.putIfAbsent(k, 0L);
        }
        return counts;
    }

    private long oldOverdueCount(BusinessDayCalendar holidays, LocalDate today) {
        return kit.jdbc.queryForList("SELECT created_at FROM group_buy_questions WHERE status = 'OPEN' AND hidden_at IS NULL AND deleted_at IS NULL", Timestamp.class).stream()
                .filter(c -> GroupBuyBusinessDays.elapsed(ServiceTime.dateOf(c.toInstant()), today, holidays) >= GroupBuyQnaService.ANSWER_DUE_BUSINESS_DAYS).count();
    }

    private static Long parse(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
