package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.batch.DealAutoEndJob;
import com.community.hotdeal.batch.DealCleanupJob;
import com.community.hotdeal.batch.DealExpiryNoticeJob;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 핫딜 배치(hotdeal-contract 「배치」) — 의심 유예 지남 → 자동 종료 + 보상 + 작성자 알림(경계 · 0 = 안 함) · 마감 없는 딜 기간 종료 알림 한 번 · OG 사본 정리(종료·삭제 90일 · 공유 사본 ·
 * 붙지 않은 사본) · 수정 이력 6개월. 실행기로 「지금 실행」한다.
 */
class HotdealBatchTest extends HotdealStage2TestBase {

    @Autowired
    private DealAutoEndJob autoEndJob;
    @Autowired
    private DealExpiryNoticeJob expiryJob;
    @Autowired
    private DealCleanupJob cleanupJob;

    @Test
    void 유예가_지나야_자동_종료하고_보상과_알림() {
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 3);
        afterFlag(post, Duration.ofHours(12).minusSeconds(1));
        run(autoEndJob);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");

        afterFlag(post, Duration.ofHours(12));
        run(autoEndJob);
        assertThat(dealRow(post)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTO");
        assertThat(notices(author, "DEAL_AUTO_ENDED", post)).isEqualTo(1);
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        run(autoEndJob); // 다시 돌아도 같다
        assertThat(notices(author, "DEAL_AUTO_ENDED", post)).isEqualTo(1);
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        assertInvariants(requesters);
    }

    @Test
    void 유예_0_이면_자동_종료하지_않는다() {
        setting("end_grace_hours", 0);
        long post = newDeal(member());
        requestBy(post, 3);
        assertThat(postQueries.detail(Long.toString(post), null, "127.0.0.1").deal().autoEndAt()).isNull();
        afterFlag(post, Duration.ofDays(5));
        run(autoEndJob);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
    }

    @Test
    void keep_한_딜은_자동_종료하지_않는다() {
        long author = member();
        long post = newDeal(author);
        requestBy(post, 3);
        states.keep(Long.toString(post), author);
        clock.set(clock.instant().plus(Duration.ofDays(1)));
        run(autoEndJob);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
    }

    @Test
    void 마감_없는_딜의_기간_종료는_한_번만_알리고_상태는_그대로() {
        long author = member();
        long open = newDeal(author);
        setting("same_store_interval_minutes", 0);
        String endsAt = clock.instant().plus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC).toString();
        long dated = dealBy(author, withEnds(deal(url("s" + uniq() + ".shop.example.com")), endsAt));

        clock.set(clock.instant().plus(Duration.ofDays(30)).minusSeconds(1));
        run(expiryJob);
        assertThat(notices(author, "DEAL_EXPIRED", open)).isZero();

        clock.set(clock.instant().plusSeconds(2));
        run(expiryJob);
        assertThat(notices(author, "DEAL_EXPIRED", open)).isEqualTo(1);
        assertThat(notices(author, "DEAL_EXPIRED", dated)).isZero();
        assertThat(dealRow(open)).containsEntry("status", "ACTIVE");
        assertThat(dealRow(open).get("expiry_notified_at")).isNotNull();
        run(expiryJob);
        assertThat(notices(author, "DEAL_EXPIRED", open)).isEqualTo(1);
    }

    @Test
    void 기간_종료_0_이면_알리지_않는다() {
        setting("auto_expire_days", 0);
        long author = member();
        long open = newDeal(author);
        clock.set(clock.instant().plus(Duration.ofDays(400)));
        run(expiryJob);
        assertThat(notices(author, "DEAL_EXPIRED", open)).isZero();
    }

    // ── OG 사본 · 수정 이력 ──────────────────────────────────────────────────

    private long og(Instant createdAt) {
        long id = ids.nextId();
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO deal_og_images (id, storage_key, public_url, content_type, size_bytes, width, height, created_at) VALUES (?, ?, ?, 'image/jpeg', 10, 600, 400, ?)",
                id, "deal-og/test/" + id + ".jpg", "/media/deal-og/test/" + id + ".jpg", Timestamp.from(createdAt));
        return id;
    }

    private void attach(long postId, long ogId) {
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET image_source = 'OG', og_image_id = ?, image_id = NULL, image_url = ? WHERE post_id = ?", ogId, "/media/deal-og/test/" + ogId + ".jpg",
                postId);
        jdbc.update("UPDATE deal_og_images SET post_id = coalesce(post_id, ?), attached_at = coalesce(attached_at, now()) WHERE id = ?", postId, ogId);
    }

    private long endedDaysAgo(long days) {
        long post = newDeal(member());
        ends.endByManager(post, hotdealOperator(), null);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET ended_at = ? WHERE post_id = ?", Timestamp.from(clock.instant().minus(Duration.ofDays(days))), post);
        return post;
    }

    private boolean removed(long ogId) {
        return jdbc.queryForObject("SELECT removed_at IS NOT NULL FROM deal_og_images WHERE id = ?", Boolean.class, ogId);
    }

    @Test
    void OG_사본은_딜_종료_90일_뒤_다른_딜이_쓰지_않을_때만_지운다() {
        Instant old = clock.instant().minus(Duration.ofDays(120));
        long endedOld = endedDaysAgo(91);
        long ogOld = og(old);
        attach(endedOld, ogOld);
        long endedRecent = endedDaysAgo(89);
        long ogRecent = og(old);
        attach(endedRecent, ogRecent);
        long sharedEnded = endedDaysAgo(91);
        long ogShared = og(old);
        attach(sharedEnded, ogShared);
        long live = newDeal(member());
        attach(live, ogShared); // 끝난 딜에서 새 딜 올리기 — 같은 사본
        long activeOld = newDeal(member());
        long ogActive = og(old);
        attach(activeOld, ogActive);

        run(cleanupJob);
        assertThat(removed(ogOld)).isTrue();
        assertThat(dealRow(endedOld)).containsEntry("image_source", null).containsEntry("og_image_id", null).containsEntry("image_url", null);
        assertThat(removed(ogRecent)).isFalse();
        assertThat(removed(ogShared)).isFalse();
        assertThat(removed(ogActive)).isFalse();
        assertThat(dealRow(live)).containsEntry("og_image_id", ogShared);
    }

    @Test
    void OG_사본은_글_삭제_90일_뒤와_글에_붙지_않은_7일_뒤에_지운다() {
        Instant old = clock.instant().minus(Duration.ofDays(120));
        long deletedOld = newDeal(member());
        long ogDeletedOld = og(old);
        attach(deletedOld, ogDeletedOld);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE posts SET deleted_at = ? WHERE id = ?", Timestamp.from(clock.instant().minus(Duration.ofDays(91))), deletedOld);
        long deletedRecent = newDeal(member());
        long ogDeletedRecent = og(old);
        attach(deletedRecent, ogDeletedRecent);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE posts SET deleted_at = ? WHERE id = ?", Timestamp.from(clock.instant().minus(Duration.ofDays(10))), deletedRecent);
        long orphanOld = og(clock.instant().minus(Duration.ofDays(8)));
        long orphanNew = og(clock.instant().minus(Duration.ofDays(6)));

        run(cleanupJob);
        assertThat(removed(ogDeletedOld)).isTrue();
        assertThat(removed(ogDeletedRecent)).isFalse();
        assertThat(removed(orphanOld)).isTrue();
        assertThat(removed(orphanNew)).isFalse();
    }

    @Test
    void 수정_이력은_6개월_지난_줄만_지운다() {
        long post = newDeal(member());
        long oldRow = ids.nextId();
        long newRow = ids.nextId();
        String changes = "[{\"field\":\"dealPrice\",\"before\":\"1\",\"after\":\"2\"}]";
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO post_deal_revisions (id, post_id, actor_kind, changes, created_at) VALUES (?, ?, 'AUTHOR', ?::jsonb, ?)", oldRow, post, changes,
                Timestamp.from(clock.instant().atZone(ZoneOffset.UTC).minusMonths(7).toInstant()));
        jdbc.update("INSERT INTO post_deal_revisions (id, post_id, actor_kind, changes, created_at) VALUES (?, ?, 'AUTHOR', ?::jsonb, ?)", newRow, post, changes,
                Timestamp.from(clock.instant().atZone(ZoneOffset.UTC).minusMonths(5).toInstant()));
        run(cleanupJob);
        assertThat(jdbc.queryForList("SELECT id FROM post_deal_revisions WHERE post_id = ?", Long.class, post)).containsExactly(newRow);
    }
}
