package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.repository.master.DealCleanupRepositoryQDSL;
import com.community.hotdeal.service.DealCleanupService;
import com.community.media.service.ImageStorage;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 핫딜 정리 배치(OG 사본 · 수정 이력)의 질의가 운영과 같은 JPA·QueryDSL 로 돈다(잠금 고르기 {@code SKIP LOCKED} · 지움 표시 · 이력 지우기). 실제 PostgreSQL.
 * DB 를 다른 시험과 같이 쓰므로 이 시험의 줄은 「오래된」 시각으로 만들고(다른 시험의 새 줄은 대상이 아니다) 끝나면 스스로 지운다.
 */
class DealCleanupServiceTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(922);

    private static JdbcTemplate jdbc;
    private static DealCleanupService cleanup;
    private static final List<String> deletedFiles = new ArrayList<>();
    private final List<Long> ogIds = new ArrayList<>();
    private final List<Long> revisionIds = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        ImageStorage storage = new ImageStorage() {
            @Override
            public void put(String key, byte[] data) {
            }

            @Override
            public void delete(String key) {
                deletedFiles.add(key);
            }

            @Override
            public String publicUrl(String key) {
                return "/media/" + key;
            }
        };
        cleanup = new DealCleanupService(new DealCleanupRepositoryQDSL(JpaTestContext.query()), storage, JpaTestContext.transactions());
    }

    @AfterEach
    void cleanUp() {
        ogIds.forEach(id -> jdbc.update("DELETE FROM deal_og_images WHERE id = ?", id));
        revisionIds.forEach(id -> jdbc.update("DELETE FROM post_deal_revisions WHERE id = ?", id));
        deletedFiles.clear();
    }

    private long og(Instant createdAt) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO deal_og_images (id, storage_key, public_url, content_type, size_bytes, width, height, created_at) VALUES (?, ?, ?, 'image/webp', 1, 1, 1, ?)",
                id, "test/og-" + id, "/media/test/og-" + id, Timestamp.from(createdAt));
        ogIds.add(id);
        return id;
    }

    private long revision(Instant createdAt) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO post_deal_revisions (id, post_id, actor_kind, changes, created_at) VALUES (?, ?, 'AUTHOR', '[{\"field\":\"dealPrice\"}]'::jsonb, ?)",
                id, IDS.nextId(), Timestamp.from(createdAt));
        revisionIds.add(id);
        return id;
    }

    @Test
    void 아무_딜도_지키지_않는_오래된_사본은_지움_표시하고_파일을_지운다_새_사본은_남긴다() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long old = og(now.minus(Duration.ofDays(DealCleanupService.ORPHAN_DAYS + 3)));
        long young = og(now.minus(Duration.ofDays(1)));

        DealCleanupService.OgTally tally = cleanup.cleanOgImages(now);

        assertThat(tally.removed()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT removed_at FROM deal_og_images WHERE id = ?", Timestamp.class, old).toInstant()).isEqualTo(now);
        assertThat(jdbc.queryForObject("SELECT removed_at FROM deal_og_images WHERE id = ?", Timestamp.class, young)).isNull();
        assertThat(deletedFiles).contains("test/og-" + old).doesNotContain("test/og-" + young);
    }

    @Test
    void 보존_기간이_지난_수정_이력만_지운다() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long old = revision(now.minus(Duration.ofDays(400)));
        long recent = revision(now.minus(Duration.ofDays(10)));

        long deleted = cleanup.cleanRevisions(now);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_deal_revisions WHERE id = ?", Integer.class, old)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_deal_revisions WHERE id = ?", Integer.class, recent)).isOne();
    }
}
