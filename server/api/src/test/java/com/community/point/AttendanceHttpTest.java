package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.testsupport.PointInvariants;
import com.community.user.entity.Role;
import com.community.zconfiguration.time.ServiceTime;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 출석체크 API·내 포인트 새 칸(커머스 1-3 — commerce-stage1-contract 「1-3 출석체크」·「1-3 화면 목업」 API) — {@code POST/GET /api/me/attendance} 의 모양·409·403·400,
 * {@code GET /api/me/points} 의 {@code attendanceAvailable}·{@code today.attended}·{@code today.total}·{@code rules} 새 칸. 공개 사이트 앱 문맥 + PostgreSQL + MockMvc.
 */
class AttendanceHttpTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PointPolicy policy;
    @Autowired
    private PointService points;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        policy.invalidate();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private long verifiedMember() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        return id;
    }

    private String today() {
        return ServiceTime.today(clock).toString();
    }

    @Test
    void 출석하면_200_모양이고_두_번째는_409_ATTENDANCE_ALREADY_DONE() throws Exception {
        long member = verifiedMember();
        mvc.perform(as(member, post("/api/me/attendance"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.date").value(today()))
                .andExpect(jsonPath("$.data.earned").value(10))
                .andExpect(jsonPath("$.data.streakBonus").value(0))
                .andExpect(jsonPath("$.data.streak").value(1))
                .andExpect(jsonPath("$.data.balance").value(10));
        mvc.perform(as(member, post("/api/me/attendance"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATTENDANCE_ALREADY_DONE"))
                .andExpect(jsonPath("$.error.message").value("오늘은 이미 출석했어요."));
    }

    @Test
    void 동결_중이어도_출석은_200이고_earned_0() throws Exception {
        long member = verifiedMember();
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', '시험 동결', now() - "
                + "interval '1 minute', 'AUTO', 'POINT_TEST', now())", ids.nextId(), member);
        mvc.perform(as(member, post("/api/me/attendance"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.earned").value(0))
                .andExpect(jsonPath("$.data.streakBonus").value(0)).andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    void 이메일_인증_전은_403_EMAIL_NOT_VERIFIED_정지는_403_ACCOUNT_RESTRICTED_로그인_없으면_401() throws Exception {
        mvc.perform(as(newMember(), post("/api/me/attendance"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        long suspended = verifiedMember();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        mvc.perform(as(suspended, post("/api/me/attendance"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        mvc.perform(post("/api/me/attendance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/attendance")).andExpect(status().isUnauthorized());
    }

    @Test
    void 달력은_month_days_monthCount_streak_today_모양이고_틀린_달은_400_fields_month() throws Exception {
        long member = verifiedMember();
        mvc.perform(as(member, post("/api/me/attendance"))).andExpect(status().isOk());
        LocalDate day = ServiceTime.today(clock);
        String month = day.toString().substring(0, 7);
        mvc.perform(as(member, get("/api/me/attendance").param("month", month))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.month").value(month))
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0]").value(day.getDayOfMonth()))
                .andExpect(jsonPath("$.data.monthCount").value(1))
                .andExpect(jsonPath("$.data.streak").value(1))
                .andExpect(jsonPath("$.data.today.date").value(today()))
                .andExpect(jsonPath("$.data.today.attended").value(true));
        mvc.perform(as(member, get("/api/me/attendance").param("month", day.plusMonths(1).toString().substring(0, 7)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(0)).andExpect(jsonPath("$.data.today.attended").value(true));
        mvc.perform(as(member, get("/api/me/attendance").param("month", "2026-13"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.month").exists());
    }

    @Test
    void 내_포인트에_출석_가능_오늘_출석_오늘_총_적립과_새_규칙_칸이_있다() throws Exception {
        long member = verifiedMember();
        mvc.perform(as(member, get("/api/me/points"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendanceAvailable").value(true))
                .andExpect(jsonPath("$.data.today.attended").value(false))
                .andExpect(jsonPath("$.data.today.total.amount").value(0))
                .andExpect(jsonPath("$.data.today.total.cap").value(200))
                .andExpect(jsonPath("$.data.rules.postMinLength").value(50))
                .andExpect(jsonPath("$.data.rules.commentMinLength").value(10))
                .andExpect(jsonPath("$.data.rules.commentOwnPostExcluded").value(true))
                .andExpect(jsonPath("$.data.rules.commentOncePerPost").value(true))
                .andExpect(jsonPath("$.data.rules.writeIntervalSeconds").value(30))
                .andExpect(jsonPath("$.data.rules.dailyTotalCap").value(200))
                .andExpect(jsonPath("$.data.rules.attendanceStreak.days").value(7))
                .andExpect(jsonPath("$.data.rules.attendanceStreak.bonus").value(20))
                .andExpect(jsonPath("$.data.rules.signup.points").value(0))
                .andExpect(jsonPath("$.data.rules.signup.received").value(false));

        mvc.perform(as(member, post("/api/me/attendance"))).andExpect(status().isOk());
        Integer before = jdbc.queryForObject("SELECT signup_points FROM point_settings WHERE id = 1", Integer.class);
        jdbc.update("UPDATE point_settings SET signup_points = 300 WHERE id = 1");
        policy.invalidate();
        try {
            mvc.perform(as(member, get("/api/me/points"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.today.attended").value(true))
                    .andExpect(jsonPath("$.data.today.total.amount").value(10))
                    .andExpect(jsonPath("$.data.rules.signup.points").value(300))
                    .andExpect(jsonPath("$.data.rules.signup.received").value(false));
            tx.execute(s -> points.signup(member));
            // 가입 포인트는 받았다고 보이고, 오늘 활동 적립 합(하루 총 한도)에는 들지 않는다
            mvc.perform(as(member, get("/api/me/points"))).andExpect(jsonPath("$.data.rules.signup.received").value(true))
                    .andExpect(jsonPath("$.data.today.total.amount").value(10)).andExpect(jsonPath("$.data.balance").value(310));
        } finally {
            jdbc.update("UPDATE point_settings SET signup_points = ? WHERE id = 1", before);
            policy.invalidate();
        }
        assertThat(PointInvariants.violations(jdbc, member)).isEmpty();
    }
}
