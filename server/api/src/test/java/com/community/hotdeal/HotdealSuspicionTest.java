package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.batch.DealSuspicionJob;
import com.community.hotdeal.service.DealSuspicionService;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 위장 홍보 의심 후보(확정 가정) — 회원 후보(7일 안 같은 비대형몰 3건 · 대형몰 제외 · 0 = 끔) · 판매처 후보(30일 안 서로 다른 회원 3명 · 다른 핫딜 비율 20% 미만 — 경계) ·
 * 사업자 글 제외 · 열린 후보 하나 · 무시한 뒤 새 글이 있어야 다시 · 관리자 알림. 자동 제재 없음.
 */
class HotdealSuspicionTest extends HotdealStage2TestBase {

    @Autowired
    private DealSuspicionService suspicions;
    @Autowired
    private DealSuspicionJob job;

    @BeforeEach
    void noRateLimits() {
        setting("same_store_interval_minutes", 0);
        setting("member_daily_post_limit", 0);
    }

    private static String store() {
        return "promo" + uniq() + ".example.kr";
    }

    private long post(long author, String host) {
        return dealBy(author, deal(url(host)));
    }

    private List<Map<String, Object>> open(String kind, String host) {
        return jdbc.queryForList("SELECT * FROM deal_suspicions WHERE kind = ? AND host = ? AND status = 'OPEN'", kind, host);
    }

    @Test
    void 같은_회원이_7일_안_같은_비대형몰_3건이면_회원_후보() {
        long operator = hotdealOperator();
        long m = member();
        String host = store();
        post(m, host);
        post(m, host);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).isEmpty();
        long third = post(m, host);
        DealSuspicionService.Tally t = suspicions.detectMembers(clock.instant());
        assertThat(t.opened()).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = open("MEMBER", host).get(0);
        assertThat(row).containsEntry("member_id", m).containsEntry("post_count", 3).containsEntry("window_days", 7);
        assertThat(adminNotices(operator, "HOTDEAL_SUSPECTED_PROMO")).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForList("SELECT link_path FROM admin_notifications WHERE operator_id = ? AND event_code = 'HOTDEAL_SUSPECTED_PROMO'", String.class, operator))
                .contains("/hotdeals/suspicions?suspicion=" + row.get("id"));
        assertThat(((java.sql.Array) row.get("post_ids")).toString()).contains(Long.toString(third));

        // 열린 후보는 하나 — 다시 계산해도 새로 열지 않는다
        int before = adminNotices(operator, "HOTDEAL_SUSPECTED_PROMO");
        post(m, host);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).hasSize(1);
        assertThat(open("MEMBER", host).get(0)).containsEntry("post_count", 4);
        assertThat(adminNotices(operator, "HOTDEAL_SUSPECTED_PROMO")).isEqualTo(before);
    }

    @Test
    void 기간_밖의_글은_세지_않는다() {
        long m = member();
        String host = store();
        long first = post(m, host);
        post(m, host);
        post(m, host);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(clock.instant().minus(Duration.ofDays(8))), first);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).isEmpty();
    }

    @Test
    void 대형몰은_빼고_0_이면_끈다() {
        long m = member();
        String sub = "s" + uniq() + ".coupang.com"; // 대형몰 coupang.com 의 하위 호스트(접미사 일치)
        post(m, sub);
        post(m, sub);
        post(m, sub);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", sub)).isEmpty();

        setting("suspicion_min_posts", 0);
        long m2 = member();
        String host = store();
        post(m2, host);
        post(m2, host);
        post(m2, host);
        assertThat(suspicions.memberCandidates(clock.instant())).isEmpty();
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).isEmpty();
    }

    @Test
    void 사업자_글은_셈에서_뺀다() {
        long m = member();
        String host = store();
        long a = post(m, host);
        post(m, host);
        post(m, host);
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET source = 'BUSINESS', biz_application_id = ?, business_name = '시험상사' WHERE post_id = ?", ids.nextId(), a);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).isEmpty();
    }

    @Test
    void 무시한_뒤에는_새_글이_있어야_다시_연다() {
        long m = member();
        String host = store();
        post(m, host);
        post(m, host);
        post(m, host);
        suspicions.detectMembers(clock.instant());
        clock.set(clock.instant().plusSeconds(10));
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE deal_suspicions SET status = 'DISMISSED', dismissed_at = ? WHERE kind = 'MEMBER' AND host = ?", Timestamp.from(clock.instant()), host);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).isEmpty();
        clock.set(clock.instant().plusSeconds(10));
        post(m, host);
        suspicions.detectMembers(clock.instant());
        assertThat(open("MEMBER", host)).hasSize(1);
    }

    @Test
    void 판매처_후보는_회원_3명이고_다른_핫딜_비율이_20퍼센트_미만() {
        setting("suspicion_min_posts", 0);
        String host = store();
        long a = member();
        long b = member();
        long c = member();
        post(a, host);
        post(b, host);
        suspicions.detectStores(clock.instant());
        assertThat(open("STORE", host)).isEmpty(); // 회원 2명
        post(c, host);
        post(a, host);
        post(b, host);
        post(c, store()); // 다른 핫딜 1 · 전체 6 → 16%
        suspicions.detectStores(clock.instant());
        List<Map<String, Object>> rows = open("STORE", host);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("member_count", 3).containsEntry("post_count", 5).containsEntry("other_ratio_percent", 16)
                .containsEntry("window_days", 30).containsEntry("member_id", null);
    }

    @Test
    void 다른_핫딜_비율이_정확히_20퍼센트면_후보가_아니다() {
        setting("suspicion_min_posts", 0);
        String host = store();
        long a = member();
        long b = member();
        long c = member();
        post(a, host);
        post(b, host);
        post(c, host);
        post(a, host);
        post(b, store()); // 다른 1 · 전체 5 → 20%
        suspicions.detectStores(clock.instant());
        assertThat(open("STORE", host)).isEmpty();
        setting("store_suspicion_other_ratio_percent", 21);
        suspicions.detectStores(clock.instant());
        assertThat(open("STORE", host)).hasSize(1);
    }

    @Test
    void 판매처_후보_0_이면_끈다() {
        setting("store_suspicion_members", 0);
        String host = store();
        post(member(), host);
        post(member(), host);
        post(member(), host);
        assertThat(suspicions.storeCandidates(clock.instant())).isEmpty();
    }

    @Test
    void 배치로_돈다() {
        long m = member();
        String host = store();
        post(m, host);
        post(m, host);
        post(m, host);
        run(job);
        assertThat(open("MEMBER", host)).hasSize(1);
    }
}
