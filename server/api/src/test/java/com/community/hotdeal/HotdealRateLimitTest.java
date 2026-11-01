package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.community.hotdeal.service.DealWriter;
import com.community.zconfiguration.exception.CommonException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 하루 핫딜 글 수(하루 = 한국 0시)·같은 판매처 연속 간격(429 + extras) — 저장 트랜잭션 안의 같은 판정({@link DealWriter#checkRate})을 시각을 정해 부른다.
 * 실제 글쓰기 경로에서 429 가 나고 딜이 저장되지 않는 것도 본다.
 */
class HotdealRateLimitTest extends HotdealApiTestBase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private DealWriter writer;

    private void dealAt(long author, String host, String finalHost, Instant at) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO post_deals (post_id, board_id, author_id, store_name, product_url, product_host, final_host, product_url_key, deal_price, created_at, "
                        + "updated_at) VALUES (?, ?, ?, '시험몰', ?, ?, ?, ?, 1000, ?, ?)", id, boardId(board), author, "https://" + host + "/p/" + id, host, finalHost,
                String.format("%064x", id), Timestamp.from(at), Timestamp.from(at));
    }

    private static Instant kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(KST).toInstant();
    }

    @Test
    void 하루_한도는_한국_0시부터_센다() {
        long author = member();
        Instant now = kst(2026, 11, 3, 9, 0);
        dealAt(author, "a.example", null, kst(2026, 11, 2, 23, 59)); // 어제(한국) — 세지 않는다
        for (int i = 0; i < 4; i++) {
            dealAt(author, "b" + i + ".example", null, kst(2026, 11, 3, 0, i));
        }
        assertThatCode(() -> writer.checkRate(author, "new.example", now)).doesNotThrowAnyException();
        dealAt(author, "b9.example", null, kst(2026, 11, 3, 8, 0));
        CommonException e = error(() -> writer.checkRate(author, "new.example", now));
        assertThat(e.getErrorCode()).isEqualTo("DEAL_DAILY_POST_LIMIT");
        assertThat(e.getStatus().value()).isEqualTo(429);
        assertThat(e.getExtras()).containsEntry("limit", 5);

        assertThatCode(() -> writer.checkRate(author, "new.example", kst(2026, 11, 4, 0, 0))).as("다음 날 0시").doesNotThrowAnyException();
    }

    @Test
    void 한도_0_이면_없다() {
        long author = member();
        setting("member_daily_post_limit", 0);
        Instant now = kst(2026, 11, 5, 12, 0);
        for (int i = 0; i < 6; i++) {
            dealAt(author, "c" + i + ".example", null, kst(2026, 11, 5, 1, i));
        }
        assertThatCode(() -> writer.checkRate(author, "new.example", now)).doesNotThrowAnyException();
    }

    @Test
    void 같은_판매처는_10분_간격_남은_분과_다시_시도_초() {
        long author = member();
        Instant now = kst(2026, 11, 6, 10, 0);
        dealAt(author, "coupa.ng", "coupang.com", now.minusSeconds(4 * 60 + 30)); // 최종 호스트가 판매처
        CommonException e = error(() -> writer.checkRate(author, "coupang.com", now));
        assertThat(e.getErrorCode()).isEqualTo("DEAL_SAME_STORE_TOO_SOON");
        assertThat(e.getExtras()).containsEntry("intervalMinutes", 10).containsEntry("remainingMinutes", 6L);
        assertThat(e.getRetryAfterSeconds()).isBetween(329L, 331L);

        assertThatCode(() -> writer.checkRate(author, "other.example", now)).as("다른 판매처").doesNotThrowAnyException();
        assertThatCode(() -> writer.checkRate(author, "coupang.com", now.plusSeconds(6 * 60))).as("10분 지남").doesNotThrowAnyException();
        assertThatCode(() -> writer.checkRate(member(), "coupang.com", now)).as("다른 회원").doesNotThrowAnyException();
    }

    @Test
    void 실제_글쓰기에서_한도에_걸리면_429_이고_딜도_글도_남지_않는다() {
        long author = member();
        setting("member_daily_post_limit", 1);
        dealAt(author, "d.example", null, Instant.now().minusSeconds(60));
        CommonException e = error(() -> write(author, board, deal(url("shop.example.com"))));
        assertThat(e.getErrorCode()).isEqualTo("DEAL_DAILY_POST_LIMIT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_deals WHERE author_id = ?", Integer.class, author)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE author_id = ?", Integer.class, author)).isZero();
    }
}
