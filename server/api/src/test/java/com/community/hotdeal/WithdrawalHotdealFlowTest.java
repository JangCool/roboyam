package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.withdrawal.WithdrawalTestSupport;
import com.community.withdrawal.service.WithdrawalService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원 탈퇴 × 회원 핫딜(서버 단계 고정 규칙 6) — 탈퇴 완료 트랜잭션이 {@code DealWithdrawal.withdraw} 를 불러 작성자의 진행 중 딜은 작성자 종료되고 내 종료 요청 줄은 지워진다(의심 상태는 그대로).
 * 재료는 {@link HotdealStage2TestBase}(핫딜 게시판·판매처·종료 요청).
 */
class WithdrawalHotdealFlowTest extends HotdealStage2TestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private WithdrawalService service;
    @Autowired
    private PasswordEncoder encoder;

    private void ready(long userId) {
        WithdrawalTestSupport.withPassword(jdbc, encoder, userId, clock.instant());
    }

    @Test
    void 탈퇴_완료_때_작성자의_진행_중_딜은_작성자_종료되고_의심이었으면_요청자_보상() {
        long author = member();
        long flagged = newDeal(author);
        List<Long> requesters = requestBy(flagged, 3);
        setting("same_store_interval_minutes", 0);
        long plain = newDeal(author);
        long alreadyEnded = newDeal(author);
        ends.endByManager(alreadyEnded, hotdealOperator(), null);
        ready(author);

        assertThat(service.request(author, WithdrawalTestSupport.plain(), IP).state()).isEqualTo("COMPLETED");

        assertThat(dealRow(flagged)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
        assertThat(dealRow(plain)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
        assertThat(dealRow(alreadyEnded)).containsEntry("ended_by", "MANAGER");
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        assertThat(memberFixture.status(author)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT author_id FROM posts WHERE id = ?", Long.class, plain)).isEqualTo(author); // 글은 「탈퇴한 회원」으로 남는다
        assertInvariants(requesters);
    }

    @Test
    void 탈퇴_요청자의_종료_요청_줄은_지워지지만_이미_의심인_딜은_그대로() {
        long post = newDeal(member());
        List<Long> requesters = requestBy(post, 3);
        Object flaggedAt = dealRow(post).get("flagged_at");
        assertThat(flaggedAt).isNotNull();
        long leaving = requesters.get(0);
        ready(leaving);

        assertThat(service.request(leaving, WithdrawalTestSupport.plain(), IP).state()).isEqualTo("COMPLETED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, post)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND member_id = ?", Integer.class, post, leaving)).isZero();
        assertThat(dealRow(post).get("flagged_at")).isEqualTo(flaggedAt);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
    }
}
