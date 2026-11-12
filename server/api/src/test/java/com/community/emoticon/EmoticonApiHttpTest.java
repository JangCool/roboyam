package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.permission.entity.MemberPermission;
import com.community.role.entity.MemberRoleIds;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 로보티콘 공개 사이트 API(emoticon-contract S1) — 앱 문맥 전체 + 실제 PostgreSQL + MockMvc(로그인 주체를 요청에 싣는다 — 보안 필터 없이, 401 은 컨트롤러가 낸다).
 * 카탈로그 캐시 머리글·ETag/304 · 받기 · 그림 올리기의 이유 코드 · <b>토큰 검사가 걸린 다섯 지점</b>(글쓰기 · 글 고치기 · 소모임 글쓰기 · 댓글 쓰기 · 고치기)과 오류 차례
 * (입력 오류 400 → 자격 409 → 금지어·속도 제한).
 */
class EmoticonApiHttpTest extends CommerceApiTestBase {

    /** 일반 회원 기본 권한 — 운영 시드({@code BuiltinRoleSeeder})와 같은 정본(먼저 도는 시험이 역할을 만들면 뒤의 공구 주문 시험도 그 권한을 쓴다) */
    private static final List<MemberPermission> GENERAL = MemberRoleIds.GENERAL_DEFAULT_PERMISSIONS;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private BoardCatalog boards;

    private MockMvc mvc;
    private String slug;
    private long board;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (MemberPermission p : MemberPermission.values()) {
            // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready) VALUES (?, 'MEMBER', ?, ?, ?) ON CONFLICT (code) DO NOTHING", p.code(),
                    p.typeId(), p.group().name(), p.featureReady());
        }
        // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
        if (jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name, scope, grant_mode, builtin) VALUES (?, 'memberKind', 'MEMBER', ?, 'SITE', 'AUTO', true) "
                + "ON CONFLICT (role_id) DO NOTHING", MemberRoleIds.GENERAL, MemberRoleIds.GENERAL) == 1) {
            for (MemberPermission p : GENERAL) {
                // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
                jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ids.nextId(), MemberRoleIds.GENERAL, p.code());
            }
        }
        board = ids.nextId();
        slug = "emo" + Long.toString(board, 36);
        boardFixture.board(board, slug, "로보티콘 " + slug.substring(slug.length() - 5), 950);
        boards.invalidate();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String base36 = Long.toString(id, 36);
        memberFixture.profile(id, "r" + base36.substring(Math.max(0, base36.length() - 11)), "REGULAR");
        return id;
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B as(long memberId, B request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private long official(String name) {
        return jdbc.queryForObject("SELECT p.id FROM emoticon_packs p JOIN emoticon_pack_revisions r ON r.id = p.live_revision_id WHERE p.source = 'OFFICIAL' AND r.name = ? "
                + "ORDER BY p.id LIMIT 1", Long.class, name);
    }

    private List<Long> emoticons(long pack) {
        return jdbc.queryForList("SELECT i.emoticon_id FROM emoticon_packs p JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id WHERE p.id = ? ORDER BY i.position",
                Long.class, pack);
    }

    private static String token(long id) {
        return ":emo-" + id + ":";
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void acquire(long member, long pack) throws Exception {
        mvc.perform(as(member, post("/api/emoticons/packs/" + pack + "/acquire"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.acquired").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions writePost(long member, String content) throws Exception {
        return mvc.perform(as(member, post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardSlug\":\"" + slug + "\",\"title\":\"로보티콘 시험 글\",\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    private org.springframework.test.web.servlet.ResultActions writeComment(long member, long postId, String content) throws Exception {
        return mvc.perform(as(member, post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    private long postOf(long member) {
        long id = ids.nextId();
        boardFixture.post(id, board, member, "로보티콘 시험", "본문이에요");
        return id;
    }

    private long commentOf(long postId, long member) {
        long id = ids.nextId();
        // 한 시간 전 댓글 — 댓글 작성 간격(10초)에 걸리지 않게
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, content, created_at) VALUES (?, ?, ?, ?, '댓글이에요', now() - interval '1 hour')", id, postId, board,
                member);
        jdbc.update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?", postId);
        return id;
    }

    // ── 카탈로그 · 규칙 · 받기 ──────────────────────────────────────────────

    @Test
    void 카탈로그는_public_max_age_300_과_ETag_같으면_304_에도_두_머리글() throws Exception {
        MvcResult first = mvc.perform(get("/api/emoticons/catalog")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "public, max-age=300"))
                .andExpect(jsonPath("$.data.items[0].id").isString()).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        assertThat(etag).matches("^\"[0-9a-f]{32}\"$");
        long robot = official("아이로빗콘");
        String body = first.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> packIds = JsonPath.read(body, "$.data.items[*].packId");
        assertThat(packIds).contains(Long.toString(robot));
        mvc.perform(get("/api/emoticons/catalog").header("If-None-Match", etag)).andExpect(status().isNotModified()).andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "public, max-age=300"));
        mvc.perform(get("/api/emoticons/catalog").header("If-None-Match", "W/" + etag)).andExpect(status().isNotModified());
        mvc.perform(get("/api/emoticons/catalog").header("If-None-Match", "\"other\", " + etag)).andExpect(status().isNotModified());
        mvc.perform(get("/api/emoticons/catalog").header("If-None-Match", "\"other\"")).andExpect(status().isOk());
    }

    @Test
    void 규칙과_상점_목록_상세는_로그인_없이_읽고_받기는_로그인이_필요하다() throws Exception {
        mvc.perform(get("/api/emoticons/rules")).andExpect(status().isOk()).andExpect(jsonPath("$.data.imageSize").value(200)).andExpect(jsonPath("$.data.maxFrames").value(24))
                .andExpect(jsonPath("$.data.rewardPerAcquire").value(10)).andExpect(jsonPath("$.data.maxPending").value(3)).andExpect(header().string("Cache-Control", "no-cache"));
        long robot = official("아이로빗콘");
        mvc.perform(get("/api/emoticons/packs/" + robot)).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("아이로빗콘"))
                .andExpect(jsonPath("$.data.count").value(32)).andExpect(jsonPath("$.data.acquired").value(false)).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/emoticons/packs").param("source", "OFFICIAL")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].source").value("OFFICIAL"));
        mvc.perform(get("/api/emoticons/packs/abc")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_NOT_FOUND"));
        mvc.perform(post("/api/emoticons/packs/" + robot + "/acquire")).andExpect(status().isUnauthorized());
        long member = member();
        acquire(member, robot);
        mvc.perform(as(member, get("/api/me/emoticons"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.packs[0].id").value(Long.toString(robot)))
                .andExpect(jsonPath("$.data.packs[0].emoticons.length()").value(32));
        mvc.perform(as(member, delete("/api/me/emoticons/" + robot))).andExpect(status().isNoContent());
        mvc.perform(as(member, get("/api/me/emoticons"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.packs.length()").value(0));
    }

    // ── 그림 올리기 ─────────────────────────────────────────────────────────

    @Test
    void 그림_올리기는_201_규격을_어기면_400_fields_file_이유_코드와_한_줄() throws Exception {
        long member = member();
        byte[] gif = TestGif.gif(200, 200, 2, "api-upload-" + ids.nextId());
        mvc.perform(as(member, multipart("/api/me/emoticon-studio/images").file(new MockMultipartFile("file", "a.gif", "image/gif", gif)).param("kind", "EMOTICON")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.imageId").isString()).andExpect(jsonPath("$.data.format").value("GIF"))
                .andExpect(jsonPath("$.data.frames").value(2)).andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/media/emoticons/")));
        mvc.perform(as(member, multipart("/api/me/emoticon-studio/images").file(new MockMultipartFile("file", "big.gif", "image/gif", TestGif.gif(220, 220, 1, "x")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.file").value("DIMENSION"))
                .andExpect(jsonPath("$.error.message").value("크기가 200×200px 이어야 해요. (지금 220×220px)"));
        mvc.perform(as(member, multipart("/api/me/emoticon-studio/images").file(new MockMultipartFile("file", "a.gif", "image/gif", gif)).param("kind", "COVER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").value("COVER_TYPE"));
        mvc.perform(as(member, multipart("/api/me/emoticon-studio/images"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").value("TYPE"));
        mvc.perform(as(member, get("/api/me/emoticon-studio"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.pendingCount").value(0))
                .andExpect(jsonPath("$.data.sales.salesAmount").value(0));
    }

    // ── 토큰 검사(다섯 지점) ────────────────────────────────────────────────

    @Test
    void 글쓰기_가지지_않은_팩은_409_21개부터_400_가진_팩은_201_자격이_금지어보다_먼저() throws Exception {
        long member = member();
        List<Long> pink = emoticons(official("아이로빗콘핑크"));
        List<Long> robot = emoticons(official("아이로빗콘"));
        writePost(member, "안녕하세요 " + token(pink.get(2)) + " 반가워요").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"))
                .andExpect(jsonPath("$.error.emoticonId").value(Long.toString(pink.get(2))));
        // 개수가 자격보다 먼저 — 21개(가지지 않은 것이라도)는 400 fields.content
        writePost(member, "많이 " + token(pink.get(0)).repeat(21)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.content")
                .value("로보티콘은 한 번에 20개까지 쓸 수 있어요."));
        // 자격이 금지어보다 먼저
        writePost(member, "지랄 " + token(pink.get(0))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
        acquire(member, official("아이로빗콘"));
        // 가진 팩 20개 · 없는 ID 는 글자
        writePost(member, "좋아요 " + token(robot.get(18)).repeat(19) + " :emo-1: 끝").andExpect(status().isCreated());
    }

    @Test
    void 자격이_속도_제한보다_먼저() throws Exception {
        long member = member();
        List<Long> pink = emoticons(official("아이로빗콘핑크"));
        writePost(member, "첫 글이에요 반가워요").andExpect(status().isCreated());
        // 바로 다음 글 — 작성 간격(429)보다 자격(409)이 먼저
        writePost(member, "둘째 글 " + token(pink.get(0))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
    }

    @Test
    void 글_고치기도_본문_전체를_다시_본다() throws Exception {
        long member = member();
        long postId = postOf(member);
        List<Long> robot = emoticons(official("아이로빗콘"));
        String update = "{\"title\":\"고친 제목\",\"contentFormat\":\"TEXT\",\"content\":" + json("고친 본문 " + token(robot.get(0))) + "}";
        mvc.perform(as(member, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content(update))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.emoticonId").value(Long.toString(robot.get(0))));
        String many = "{\"title\":\"고친 제목\",\"contentFormat\":\"TEXT\",\"content\":" + json("x " + token(robot.get(0)).repeat(21)) + "}";
        mvc.perform(as(member, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content(many))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.content").exists());
        acquire(member, official("아이로빗콘"));
        mvc.perform(as(member, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content(update))).andExpect(status().isOk());
        // 팩을 빼면 그 토큰이 든 글을 다시 고칠 수 없다(예전에 쓴 토큰이어도)
        mvc.perform(as(member, delete("/api/me/emoticons/" + official("아이로빗콘")))).andExpect(status().isNoContent());
        mvc.perform(as(member, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content(update))).andExpect(status().isConflict());
    }

    @Test
    void 댓글_쓰기_고치기() throws Exception {
        long writer = member();
        long member = member();
        long postId = postOf(writer);
        List<Long> robot = emoticons(official("아이로빗콘"));
        writeComment(member, postId, "댓글 " + token(robot.get(3))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
        writeComment(member, postId, token(robot.get(3)).repeat(21)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.content").exists());
        long commentId = commentOf(postId, member);
        String update = "{\"contentFormat\":\"TEXT\",\"content\":" + json("고친 댓글 " + token(robot.get(3))) + "}";
        mvc.perform(as(member, put("/api/comments/" + commentId).contentType(MediaType.APPLICATION_JSON).content(update))).andExpect(status().isConflict());
        acquire(member, official("아이로빗콘"));
        writeComment(member, postId, "댓글 " + token(robot.get(3))).andExpect(status().isCreated());
        mvc.perform(as(member, put("/api/comments/" + commentId).contentType(MediaType.APPLICATION_JSON).content(update))).andExpect(status().isOk());
    }

    @Test
    void 소모임_글쓰기() throws Exception {
        long leader = member();
        long groupId = ids.nextId();
        jdbc.update("INSERT INTO groups (id, name, name_key, description, board_id, join_type, capacity, status, leader_id, creator_id, member_count, requested_at, opened_at) "
                + "VALUES (?, ?, ?, '시험', ?, 'OPEN', 50, 'ACTIVE', ?, ?, 1, now(), now())", groupId, "e" + Long.toString(groupId, 36), "e" + Long.toString(groupId, 36), board,
                leader, leader);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'LEADER', 'ACTIVE', now())", groupId, leader);
        List<Long> robot = emoticons(official("아이로빗콘"));
        String body = "{\"title\":\"소모임 글\",\"contentFormat\":\"TEXT\",\"content\":" + json("모임 " + token(robot.get(7))) + "}";
        mvc.perform(as(leader, post("/api/groups/" + groupId + "/posts").contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
        String many = "{\"title\":\"소모임 글\",\"contentFormat\":\"TEXT\",\"content\":" + json("x " + token(robot.get(7)).repeat(21)) + "}";
        mvc.perform(as(leader, post("/api/groups/" + groupId + "/posts").contentType(MediaType.APPLICATION_JSON).content(many))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.content").exists());
        acquire(leader, official("아이로빗콘"));
        mvc.perform(as(leader, post("/api/groups/" + groupId + "/posts").contentType(MediaType.APPLICATION_JSON).content(body))).andExpect(status().is2xxSuccessful());
    }

    /** 시험용 GIF(손으로 짠 — shared 시험의 TestImages 와 같은 모양) */
    static final class TestGif {

        static byte[] gif(int w, int h, int frames, String comment) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
            le16(out, w);
            le16(out, h);
            out.write(0x80);
            out.write(0);
            out.write(0);
            out.writeBytes(new byte[] {0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff});
            out.writeBytes(new byte[] {0x21, (byte) 0xff, 11});
            out.writeBytes("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(new byte[] {3, 1, 0, 0, 0});
            byte[] text = comment.getBytes(StandardCharsets.US_ASCII);
            out.writeBytes(new byte[] {0x21, (byte) 0xfe, (byte) text.length});
            out.writeBytes(text);
            out.write(0);
            for (int f = 0; f < frames; f++) {
                out.writeBytes(new byte[] {0x21, (byte) 0xf9, 4, 0, 10, 0, 0, 0, 0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0});
            }
            out.write(0x3b);
            return out.toByteArray();
        }

        private static void le16(ByteArrayOutputStream out, int v) {
            out.write(v & 0xff);
            out.write((v >> 8) & 0xff);
        }
    }
}
