package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 프로필 저장의 닉네임 변경권(member-mypage-contract 「후속 작업: 닉네임 변경권」, 커머스 1-2 결정 1·2) — 무료가 남으면 변경권을 쓰지 않음 · 무료를 다 쓰고 없으면
 * {@code 409 NICKNAME_TICKET_REQUIRED}(최상위 {@code ticketCount}·{@code ticketPrice}) · {@code useTicket} 없으면 409 · 있으면 쓰고 바뀜 · 중복 닉네임·규칙 위반이면 안 씀 ·
 * 소개만 저장은 안 씀 · 동시 두 요청에 한 장이 두 번 쓰이지 않음 · {@code nicknameChange.ticketCount|ticketPrice} · {@code /api/me/points} 의 {@code ticketCount}. 실제 PostgreSQL.
 */
class NicknameTicketProfileHttpTest extends PointShopApiTestBase {

    private ResultActions save(long member, String nickname, Boolean useTicket) throws Exception {
        String json = "{\"nickname\":\"" + nickname + "\",\"bio\":\"\"" + (useTicket == null ? "" : ",\"useTicket\":" + useTicket) + "}";
        return mvc.perform(as(member, put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    private int unusedTickets(long member) {
        return count("SELECT count(*) FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE ui.user_id = ? AND i.kind = 'NICKNAME_CHANGE_TICKET' "
                + "AND ui.status = 'OWNED'", member);
    }

    private String nickname(long member) {
        return jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, member);
    }

    private static String fresh(String prefix, long member) {
        return prefix + nicknameOf(member).substring(4);
    }

    /** 무료 변경 1번을 이미 쓴 회원 */
    private long usedFreeChange() {
        long member = member();
        jdbc.update("UPDATE profiles SET nickname_change_count = 1 WHERE user_id = ?", member);
        return member;
    }

    @Test
    void 무료_변경이_남았으면_useTicket_을_보내도_변경권을_쓰지_않는다() throws Exception {
        long member = member();
        grant(member, 5_000);
        buy(member, seedTicket());
        String next = fresh("a", member);

        save(member, next, true).andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value(next))
                .andExpect(jsonPath("$.data.nicknameChange.changeCount").value(1)).andExpect(jsonPath("$.data.nicknameChange.allowed").value(false))
                .andExpect(jsonPath("$.data.nicknameChange.ticketCount").value(1)).andExpect(jsonPath("$.data.nicknameChange.ticketPrice").value(seedTicketPrice()))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty());
        assertThat(unusedTickets(member)).isEqualTo(1);
    }

    @Test
    void 무료를_다_쓰고_변경권이_없으면_409_NICKNAME_TICKET_REQUIRED_최상위_장수와_값() throws Exception {
        long member = usedFreeChange();
        String before = nickname(member);

        save(member, fresh("b", member), true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_TICKET_REQUIRED"))
                .andExpect(jsonPath("$.error.ticketCount").value(0)).andExpect(jsonPath("$.error.ticketPrice").value(seedTicketPrice()));
        save(member, fresh("b", member), null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_TICKET_REQUIRED"));
        assertThat(nickname(member)).isEqualTo(before);
    }

    @Test
    void 변경권이_있어도_useTicket_이_없으면_409_이고_쓰지_않는다() throws Exception {
        long member = usedFreeChange();
        grant(member, 5_000);
        buy(member, seedTicket());

        save(member, fresh("c", member), false).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_TICKET_REQUIRED"))
                .andExpect(jsonPath("$.error.ticketCount").value(1));
        assertThat(unusedTickets(member)).isEqualTo(1);
    }

    @Test
    void 변경권을_쓰면_한_장이_줄고_닉네임이_바뀐다_포인트_요약의_장수도_실제_값() throws Exception {
        long member = usedFreeChange();
        grant(member, 5_000);
        buy(member, seedTicket());
        buy(member, seedTicket());
        mvc.perform(as(member, get("/api/me/points"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.ticketCount").value(2));
        mvc.perform(as(member, get("/api/me/profile"))).andExpect(jsonPath("$.data.nicknameChange.ticketCount").value(2));
        String next = fresh("d", member);

        save(member, next, true).andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value(next))
                .andExpect(jsonPath("$.data.nicknameChange.ticketCount").value(1)).andExpect(jsonPath("$.data.nicknameChange.changeCount").value(2));
        assertThat(nickname(member)).isEqualTo(next);
        assertThat(unusedTickets(member)).isEqualTo(1);
        mvc.perform(as(member, get("/api/me/points"))).andExpect(jsonPath("$.data.ticketCount").value(1));
        assertThat(count("SELECT count(*) FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE ui.user_id = ? AND i.kind = 'NICKNAME_CHANGE_TICKET' "
                + "AND ui.status = 'USED' AND ui.used_at IS NOT NULL", member)).isEqualTo(1);
    }

    @Test
    void 소개만_저장_규칙_위반_중복_닉네임이면_변경권을_쓰지_않는다() throws Exception {
        long member = usedFreeChange();
        long other = member();
        grant(member, 5_000);
        buy(member, seedTicket());

        save(member, nickname(member), true).andExpect(status().isOk()).andExpect(jsonPath("$.data.nicknameChange.ticketCount").value(1));
        save(member, "a", true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.nickname").exists());
        save(member, nickname(other), true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_TAKEN"));
        save(member, nickname(other).toUpperCase(), true).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_TAKEN"));
        assertThat(unusedTickets(member)).as("실패한 저장은 변경권을 되돌린다").isEqualTo(1);
    }

    @Test
    void 정지_중이면_403_이고_변경권을_쓰지_않는다() throws Exception {
        long member = usedFreeChange();
        grant(member, 5_000);
        buy(member, seedTicket());
        suspend(member);

        save(member, fresh("e", member), true).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        assertThat(unusedTickets(member)).isEqualTo(1);
    }

    @Test
    void 동시에_두_요청이_와도_변경권_한_장은_한_번만_쓰인다() throws Exception {
        long member = usedFreeChange();
        grant(member, 5_000);
        buy(member, seedTicket());
        String first = fresh("f", member);
        String second = fresh("g", member);

        List<Integer> statuses = together(List.of(() -> save(member, first, true).andReturn().getResponse().getStatus(),
                () -> save(member, second, true).andReturn().getResponse().getStatus()));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(unusedTickets(member)).isZero();
        assertThat(nickname(member)).isIn(first, second);
        assertThat(count("SELECT nickname_change_count FROM profiles WHERE user_id = ?", member)).isEqualTo(2);
    }

    private static <T> List<T> together(List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return job.call();
                }));
            }
            start.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
