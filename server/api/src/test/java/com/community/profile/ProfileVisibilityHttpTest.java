package com.community.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 프로필 공개 범위(docs/features/profile-privacy-contract.md 「정책」·「API」) — 내 프로필 저장의 {@code visibility}(저장·틀린 값 400·정지 중 403·닉네임 변경 횟수 안 씀),
 * 비공개 공개 프로필의 숨긴 칸(남·비회원은 {@code bio}·{@code joinedAt}·{@code stats} null, 본인은 다 봄), 작성글·댓글·소모임 목록 {@code 403 PROFILE_PRIVATE}(본인은 200,
 * 400·404 가 먼저), 글 상세의 작성자 소개 {@code author.bio}(공개·소개 있음만 — 비공개·소개 없음·비회원·탈퇴 진행 중은 null, 목록·댓글은 늘 null). 실제 PostgreSQL.
 * 게시판·글·댓글은 픽스처로 넣는다(쓰기 정책과 무관하게 표시만 본다).
 */
class ProfileVisibilityHttpTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext web;
    @Autowired
    private BoardCatalog boards;

    private MockMvc mvc;
    private long board;
    private String slug;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(web).build();
        board = ids.nextId();
        slug = "v" + Long.toString(board, 36);
        boardFixture.board(board, slug, "공개 범위 " + slug.substring(slug.length() - 6), 900);
        boards.invalidate();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String base36 = Long.toString(id, 36);
        memberFixture.profile(id, "v" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    /** 닉네임은 그대로, 소개·공개 범위만 — {@code visibility} 가 null 이면 칸을 보내지 않는다 */
    private ResultActions save(long member, String bio, String visibility) throws Exception {
        String json = "{\"nickname\":\"" + memberFixture.nickname(member) + "\",\"bio\":\"" + bio + "\""
                + (visibility == null ? "" : ",\"visibility\":\"" + visibility + "\"") + "}";
        return mvc.perform(as(member, put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String visibility(long member) {
        return jdbc.queryForObject("SELECT profile_visibility FROM profiles WHERE user_id = ?", String.class, member);
    }

    /** 소개를 두고 공개 범위를 정한 회원 */
    private long member(String bio, String visibility) throws Exception {
        long id = member();
        save(id, bio, visibility).andExpect(status().isOk());
        return id;
    }

    private long post(Long authorId, String guestName) {
        long id = ids.nextId();
        boardFixture.post(id, board, authorId, guestName, guestName == null ? null : "x", "공개 범위 시험", "본문");
        return id;
    }

    // ── 내 프로필 ───────────────────────────────────────────────────────────

    @Test
    void 공개_범위를_저장하면_GET_PUT_응답과_DB_에_남고_칸을_빼면_그대로다() throws Exception {
        long me = member();
        mvc.perform(as(me, get("/api/me/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PUBLIC"));

        save(me, "안녕", "PRIVATE").andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.bio").value("안녕"));
        assertThat(visibility(me)).isEqualTo("PRIVATE");
        mvc.perform(as(me, get("/api/me/profile"))).andExpect(jsonPath("$.data.visibility").value("PRIVATE"));

        save(me, "소개만", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
        assertThat(visibility(me)).as("칸이 없으면 그대로").isEqualTo("PRIVATE");

        save(me, "소개만", "PUBLIC").andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
        assertThat(visibility(me)).isEqualTo("PUBLIC");
    }

    @Test
    void 틀린_공개_범위는_400_VALIDATION_fields_visibility_이고_저장하지_않는다() throws Exception {
        long me = member();
        for (String wrong : new String[] {"private", "HIDDEN", "", " PUBLIC"}) {
            save(me, "바뀌면 안 됨", wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.visibility").isNotEmpty());
        }
        assertThat(visibility(me)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT bio FROM profiles WHERE user_id = ?", String.class, me)).as("같은 저장의 다른 칸도 남지 않는다").isNull();
    }

    @Test
    void 정지_중이면_공개_범위만_바꾸는_저장도_403_ACCOUNT_RESTRICTED() throws Exception {
        long me = member();
        // SQL 로 남김: 회원 상태를 바로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", me);

        save(me, "", "PRIVATE").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        assertThat(visibility(me)).isEqualTo("PUBLIC");
        mvc.perform(as(me, get("/api/me/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
    }

    @Test
    void 공개_범위만_바꾸는_저장은_닉네임_변경_횟수와_변경권을_쓰지_않는다() throws Exception {
        long fresh = member();
        save(fresh, "", "PRIVATE").andExpect(status().isOk()).andExpect(jsonPath("$.data.nicknameChange.changeCount").value(0))
                .andExpect(jsonPath("$.data.nicknameChange.allowed").value(true));

        long used = member();
        // SQL 로 남김: 무료 변경을 이미 쓴 상태를 바로 둔다(도메인 메서드는 닉네임을 함께 바꾼다)
        jdbc.update("UPDATE profiles SET nickname_change_count = 1 WHERE user_id = ?", used);
        save(used, "", "PRIVATE").andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.nicknameChange.changeCount").value(1));
        assertThat(count("SELECT nickname_change_count FROM profiles WHERE user_id = ?", fresh)).isZero();
        assertThat(count("SELECT nickname_change_count FROM profiles WHERE user_id = ?", used)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM profile_nickname_history WHERE user_id IN (?, ?)", fresh, used)).isZero();
    }

    // ── 공개 프로필 ─────────────────────────────────────────────────────────

    @Test
    void 비공개_프로필을_남과_비회원이_보면_소개_가입일_수치가_null_이고_머리는_그대로() throws Exception {
        long owner = member("비밀 소개", "PRIVATE");
        long other = member();
        post(owner, null);

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {get("/api/users/" + owner + "/profile"),
                as(other, get("/api/users/" + owner + "/profile"))}) {
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.data.nickname").value(memberFixture.nickname(owner))).andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                    .andExpect(jsonPath("$.data.grade").isNotEmpty()).andExpect(jsonPath("$.data.isMe").value(false))
                    .andExpect(jsonPath("$.data.bio").isEmpty()).andExpect(jsonPath("$.data.joinedAt").isEmpty()).andExpect(jsonPath("$.data.stats").isEmpty());
        }
    }

    @Test
    void 본인은_비공개_프로필도_다_보고_공개_프로필은_남도_다_본다() throws Exception {
        long owner = member("내 소개", "PRIVATE");
        post(owner, null);
        mvc.perform(as(owner, get("/api/users/" + owner + "/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.isMe").value(true)).andExpect(jsonPath("$.data.bio").value("내 소개"))
                .andExpect(jsonPath("$.data.joinedAt").isNotEmpty()).andExpect(jsonPath("$.data.stats.posts").value(1));

        long open = member("공개 소개", "PUBLIC");
        mvc.perform(get("/api/users/" + open + "/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.bio").value("공개 소개")).andExpect(jsonPath("$.data.joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.stats.posts").value(0));
    }

    @Test
    void 비공개_프로필의_작성글_댓글_소모임_목록은_남과_비회원에게_403_PROFILE_PRIVATE_본인은_200() throws Exception {
        long owner = member("", "PRIVATE");
        long other = member();
        post(owner, null);

        for (String tab : new String[] {"posts", "comments", "groups"}) {
            String path = "/api/users/" + owner + "/" + tab;
            mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PROFILE_PRIVATE"))
                    .andExpect(jsonPath("$.error.message").value("비공개 프로필이에요."));
            mvc.perform(as(other, get(path))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PROFILE_PRIVATE"));
            mvc.perform(as(owner, get(path))).andExpect(status().isOk());
        }
        mvc.perform(as(owner, get("/api/users/" + owner + "/posts"))).andExpect(jsonPath("$.data.items.length()").value(1));

        long open = member();
        mvc.perform(get("/api/users/" + open + "/posts")).andExpect(status().isOk());
    }

    @Test
    void 비공개여도_size_cursor_형식_400_과_없는_회원_404_가_먼저다() throws Exception {
        long owner = member("", "PRIVATE");
        mvc.perform(get("/api/users/" + owner + "/posts?size=0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.size").exists());
        mvc.perform(get("/api/users/" + owner + "/comments?cursor=abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.cursor").exists());
        mvc.perform(get("/api/users/" + ids.nextId() + "/groups")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
    }

    // ── 글 상세 작성자 소개 ─────────────────────────────────────────────────

    @Test
    void 글_상세의_작성자_소개는_공개이고_소개가_있을_때만_목록_댓글에는_없다() throws Exception {
        long open = member("로봇을 좋아해요", "PUBLIC");
        long hidden = member("숨긴 소개", "PRIVATE");
        long blank = member("", "PUBLIC");
        long openPost = post(open, null);
        long hiddenPost = post(hidden, null);
        long blankPost = post(blank, null);
        long guestPost = post(null, "손님");
        long commentId = ids.nextId();
        boardFixture.comment(commentId, openPost, board, open, null, null, "댓글");
        // SQL 로 남김: 글 카운터 칸 — 엔티티 메서드가 없다(운영은 PostCounterQueries)
        jdbc.update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?", openPost);

        mvc.perform(get("/api/posts/" + openPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.bio").value("로봇을 좋아해요"));
        mvc.perform(as(hidden, get("/api/posts/" + hiddenPost))).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.bio").isEmpty());
        mvc.perform(get("/api/posts/" + hiddenPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.bio").isEmpty());
        mvc.perform(get("/api/posts/" + blankPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.bio").isEmpty());
        mvc.perform(get("/api/posts/" + guestPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.kind").value("GUEST"))
                .andExpect(jsonPath("$.data.author.bio").isEmpty());

        mvc.perform(get("/api/boards/" + slug + "/posts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + openPost + "')].author.bio").value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(get("/api/posts/" + openPost + "/comments")).andExpect(status().isOk()).andExpect(jsonPath("$.data.threads[0].author.bio").isEmpty());
        mvc.perform(get("/api/users/" + open + "/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].author.bio").isEmpty());
    }

    @Test
    void 탈퇴_진행_중인_작성자의_글_상세에는_소개가_없다() throws Exception {
        long leaving = member("곧 떠나요", "PUBLIC");
        long postId = post(leaving, null);
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.author.bio").value("곧 떠나요"));

        // SQL 로 남김: 탈퇴 예약 상태를 바로 둔다(탈퇴 신청 흐름은 남은 일 점검까지 함께 돈다)
        jdbc.update("UPDATE users SET status = 'WITHDRAWING' WHERE id = ?", leaving);
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.bio").isEmpty());
    }
}
