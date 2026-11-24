package com.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.service.EmoticonShopService;
import com.community.media.service.ImageStorage;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 글·신고 서랍의 본문 토큰 그림({@code emoticons}·{@code jjals}) — 실제 필터 체인·세션·권한·PostgreSQL.
 * 글 상세는 본문에 나온 로보티콘을 한 번씩 공개 카탈로그와 같은 {@code url}·{@code label} 로(카탈로그에 없는 ID 는 빠짐), 짤은 공개 조회와 같은 칸(내려간 짤 {@code url null}·
 * {@code blocked true}, 없는 ID 는 빠짐). 신고 상세는 지금 본문과 신고 당시 내용을 합친다. 토큰이 없으면 빈 목록.
 */
class AdminBodyTokenImagesHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private EmoticonShopService shop;
    @Autowired
    private ImageStorage storage;

    private long board() {
        long id = ids.nextId();
        String slug = "tk" + Long.toString(id, 36);
        boardFixture.board(id, slug, "토큰 " + slug.substring(slug.length() - 6), 952);
        catalog.invalidate();
        return id;
    }

    /** 짤 파일 한 줄 — {@code blocked} 면 운영자가 내려 비공개 위치로 옮긴 짤 */
    private String jjal(long uploader, boolean blocked) {
        long id = ids.nextId();
        String publicId = "t" + Long.toString(id, 36);
        // SQL 로 남김: 내려간 상태·비공개 키를 바로 세운다 — 엔티티 팩터리는 ACTIVE·공개 키로만 만든다
        jdbc.update("INSERT INTO jjal_files (id, public_id, sha256, storage_key, content_type, size_bytes, width, height, animated, uploaded_by, status, blocked_reason, "
                        + "blocked_at) VALUES (?, ?, ?, ?, 'image/gif', 1000, 120, 90, true, ?, ?, ?, ?)",
                id, publicId, String.format("%064x", id), (blocked ? "private/jjal/" : "jjal/") + publicId + ".gif", uploader, blocked ? "BLOCKED" : "ACTIVE",
                blocked ? "STAFF" : null, blocked ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return publicId;
    }

    @Test
    void 글_상세와_신고_상세에_본문_토큰의_그림이_실린다() throws Exception {
        Map<String, Object> seed = jdbc.queryForMap("SELECT i.emoticon_id, i.label FROM emoticon_packs p JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id "
                + "WHERE p.status IN ('APPROVED', 'SUSPENDED') ORDER BY p.id, i.position LIMIT 1");
        String emo = seed.get("emoticon_id").toString();
        EmoticonPublicView.CatalogItem inCatalog = shop.catalog().catalog().items().stream().filter(i -> i.id().equals(emo)).findFirst().orElseThrow();

        long board = board();
        long author = newMember();
        String live = jjal(author, false);
        String down = jjal(author, true);
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "제목 :emo-1:", "앞 :emo-" + emo + ": 또 :emo-" + emo + ": :emo-9999999999999: :jjal-" + live + ": :jjal-" + down
                + ": :jjal-zzNoSuch1:");
        long plainId = ids.nextId();
        boardFixture.post(plainId, board, author, "토큰 없음", "그냥 글자");

        Cookie posts = login(operatorWith(Permission.POST_READ));
        mvc.perform(get("/api/admin/posts/" + postId).cookie(posts)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emoticons.length()").value(1))
                .andExpect(jsonPath("$.data.emoticons[0].id").value(emo))
                .andExpect(jsonPath("$.data.emoticons[0].label").value(inCatalog.label()))
                .andExpect(jsonPath("$.data.emoticons[0].url").value(inCatalog.url()))
                .andExpect(jsonPath("$.data.jjals.length()").value(2))
                .andExpect(jsonPath("$.data.jjals[0].id").value(live))
                .andExpect(jsonPath("$.data.jjals[0].url").isString())
                .andExpect(jsonPath("$.data.jjals[0].width").value(120))
                .andExpect(jsonPath("$.data.jjals[0].height").value(90))
                .andExpect(jsonPath("$.data.jjals[0].animated").value(true))
                .andExpect(jsonPath("$.data.jjals[0].blocked").value(false))
                .andExpect(jsonPath("$.data.jjals[0].adminUrl").value("/api/admin/jjals/" + live + "/image"))
                .andExpect(jsonPath("$.data.jjals[1].id").value(down))
                .andExpect(jsonPath("$.data.jjals[1].url").isEmpty())
                .andExpect(jsonPath("$.data.jjals[1].blocked").value(true))
                .andExpect(jsonPath("$.data.jjals[1].adminUrl").value("/api/admin/jjals/" + down + "/image"));
        mvc.perform(get("/api/admin/posts/" + plainId).cookie(posts)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emoticons.length()").value(0)).andExpect(jsonPath("$.data.jjals.length()").value(0));
        assertThat(inCatalog.url()).isNotBlank();

        // 신고 상세 — 지금 본문(로보티콘만)과 신고 당시 내용(짤만)을 합친다
        long reported = ids.nextId();
        boardFixture.post(reported, board, author, "신고된 글", "지금 :emo-" + emo + ":");
        long caseId = ids.nextId();
        jdbc.update("INSERT INTO report_cases (id, target_type, target_id, post_id, board_id, target_author_id, status, report_count, first_reported_at, last_reported_at) "
                + "VALUES (?, 'POST', ?, ?, ?, ?, 'PENDING', 1, now(), now())", caseId, reported, reported, board, author);
        jdbc.update("INSERT INTO reports (id, case_id, target_type, target_id, post_id, board_id, reporter_id, reason, counted, snapshot_title, snapshot_content) "
                + "VALUES (?, ?, 'POST', ?, ?, ?, ?, 'SPAM_AD', true, '신고된 글', ?)", ids.nextId(), caseId, reported, reported, board, newMember(),
                "그때 :jjal-" + live + ": :emo-" + emo + ":");
        Cookie reports = login(operatorWith(Permission.REPORT_READ));
        mvc.perform(get("/api/admin/reports/" + caseId).cookie(reports)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emoticons.length()").value(1))
                .andExpect(jsonPath("$.data.emoticons[0].url").value(inCatalog.url()))
                .andExpect(jsonPath("$.data.jjals.length()").value(1))
                .andExpect(jsonPath("$.data.jjals[0].id").value(live))
                .andExpect(jsonPath("$.data.jjals[0].blocked").value(false))
                .andExpect(jsonPath("$.data.jjals[0].adminUrl").value("/api/admin/jjals/" + live + "/image"));
    }

    /** 그 운영자가 그 짤 원본을 연 감사 기록 수 */
    private int originalViews(long operatorId, String publicId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'JJAL_ORIGINAL_VIEW' AND actor_id = ? AND target_type = 'JJAL' AND target_id = ?",
                Integer.class, operatorId, publicId);
    }

    /** 불법촬영물로 확정해 지운 짤 — 파일을 지우고 해시 행만 남는다(저장소 키 없음) */
    private String purgedJjal(long uploader) {
        long id = ids.nextId();
        String publicId = "p" + Long.toString(id, 36);
        jdbc.update("INSERT INTO jjal_files (id, public_id, sha256, storage_key, content_type, size_bytes, width, height, animated, uploaded_by, status, blocked_reason, "
                        + "blocked_at, purged_at) VALUES (?, ?, ?, NULL, 'image/gif', 1000, 120, 90, true, ?, 'BLOCKED', 'ILLEGAL', now(), now())",
                id, publicId, String.format("%064x", id), uploader);
        return publicId;
    }

    @Test
    void 운영자는_내린_짤의_원본을_본다() throws Exception {
        long author = newMember();
        String down = jjal(author, true);
        String live = jjal(author, false);
        String purged = purgedJjal(author);
        String missing = jjal(author, true); // 장부에는 키가 있지만 저장소에 파일이 없다
        byte[] downBytes = "GIF89a-down".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] liveBytes = "GIF89a-live".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String downKey = "private/jjal/" + down + ".gif";
        String liveKey = "jjal/" + live + ".gif";
        storage.put(downKey, downBytes);
        storage.put(liveKey, liveBytes);
        try {
            // 글 서랍에서 지운 짤은 adminUrl null
            long board = board();
            long postId = ids.nextId();
            boardFixture.post(postId, board, author, "짤 글", ":jjal-" + down + ": :jjal-" + purged + ":");
            long postOperator = operatorWith(Permission.POST_READ);
            Cookie posts = login(postOperator);
            mvc.perform(get("/api/admin/posts/" + postId).cookie(posts)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.jjals.length()").value(2))
                    .andExpect(jsonPath("$.data.jjals[0].adminUrl").value("/api/admin/jjals/" + down + "/image"))
                    .andExpect(jsonPath("$.data.jjals[1].id").value(purged))
                    .andExpect(jsonPath("$.data.jjals[1].blocked").value(true))
                    .andExpect(jsonPath("$.data.jjals[1].adminUrl").isEmpty());

            // post.read 만 — 내린 짤 원본 200, 형식 그대로, 캐시 금지
            mvc.perform(get("/api/admin/jjals/" + down + "/image").cookie(posts)).andExpect(status().isOk())
                    .andExpect(content().contentType("image/gif"))
                    .andExpect(content().bytes(downBytes))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("no-store"),
                            org.hamcrest.Matchers.containsString("private"))))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
            // 내려간 짤을 보면 감사 기록(대상 JJAL + 공개 ID, 상태·사유)
            assertThat(originalViews(postOperator, down)).isEqualTo(1);
            Map<String, Object> row = jdbc.queryForMap("SELECT detail::jsonb ->> 'status' AS status, detail::jsonb ->> 'blockedReason' AS reason, (detail::jsonb -> 'caseId') IS NOT NULL AS has_case "
                    + "FROM audit_logs WHERE action = 'JJAL_ORIGINAL_VIEW' AND actor_id = ? AND target_type = 'JJAL' AND target_id = ?", postOperator, down);
            assertThat(row).containsEntry("status", "BLOCKED").containsEntry("reason", "STAFF").containsEntry("has_case", false);
            // 살아 있는 짤도 같은 경로로 — 공개 중인 그림이라 감사 기록은 없다
            mvc.perform(get("/api/admin/jjals/" + live + "/image").cookie(posts)).andExpect(status().isOk()).andExpect(content().bytes(liveBytes));
            assertThat(originalViews(postOperator, live)).isZero();

            // report.read 만 — 200
            long reportOperator = operatorWith(Permission.REPORT_READ);
            Cookie reports = login(reportOperator);
            mvc.perform(get("/api/admin/jjals/" + down + "/image").cookie(reports)).andExpect(status().isOk()).andExpect(content().bytes(downBytes));
            assertThat(originalViews(reportOperator, down)).isEqualTo(1);

            // 짤 신고 사건의 그림도 같은 기록(caseId 덧붙임)
            long jjalCase = ids.nextId();
            jdbc.update("INSERT INTO report_cases (id, target_type, target_id, post_id, board_id, target_author_id, status, report_count, first_reported_at, last_reported_at) "
                    + "VALUES (?, 'JJAL', (SELECT id FROM jjal_files WHERE public_id = ?), NULL, NULL, ?, 'PENDING', 1, now(), now())", jjalCase, down, author);
            mvc.perform(get("/api/admin/reports/" + jjalCase + "/jjal-image").cookie(reports)).andExpect(status().isOk()).andExpect(content().bytes(downBytes));
            assertThat(originalViews(reportOperator, down)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'JJAL_ORIGINAL_VIEW' AND actor_id = ? AND target_id = ? AND detail::jsonb ->> 'caseId' = ?",
                    Integer.class, reportOperator, down, Long.toString(jjalCase))).isEqualTo(1);

            // 파일 없는 짤(지움 · 저장소에 없음) · 없는 ID — 404 JJAL_NOT_FOUND
            for (String id : new String[] {purged, missing, "zzNoSuch1"}) {
                mvc.perform(get("/api/admin/jjals/" + id + "/image").cookie(reports)).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error.code").value("JJAL_NOT_FOUND"));
                // 그림을 못 내보냈으면 기록도 없다
                assertThat(originalViews(reportOperator, id)).isZero();
            }

            // 둘 다 없음 — 403
            Cookie neither = login(operatorWith(Permission.MEMBER_READ));
            mvc.perform(get("/api/admin/jjals/" + down + "/image").cookie(neither)).andExpect(status().isForbidden());
        } finally {
            storage.delete(downKey);
            storage.delete(liveKey);
        }
    }
}
