package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.board.service.BoardCatalog;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessProfileRepositoryQDSL;
import com.community.business.repository.master.BusinessServiceRepository;
import com.community.business.service.BusinessEligibility;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 사업자 뱃지({@code business}) — 회원의 사업자 신원이 {@code APPROVED} 일 때만 true(재심사 중 {@code PENDING}·정지·반려·일반 회원·비회원은 false).
 * 글 목록·글 보기·댓글 작성자({@code PostAuthorView}), 공개 프로필, 쪽지 받는 사람 찾기(쪽지 상대 {@code DmPartnerView}). 목록 하나에 일괄 조회 한 번(줄마다 읽지 않는다).
 * 실제 공개 사이트 앱 문맥 + PostgreSQL(사업자 신원 줄은 SQL 로 넣는다 — {@link BizApplicationApiTestBase#business}).
 */
@Import(BusinessBadgeHttpTest.CountingConfig.class)
class BusinessBadgeHttpTest extends BizApplicationApiTestBase {

    /** 일괄 조회 횟수·마지막 인자를 세는 자격 판정(실제 판정 그대로) */
    static class CountingEligibility extends BusinessEligibility {

        private final AtomicInteger calls = new AtomicInteger();
        private final List<Collection<Long>> arguments = new ArrayList<>();

        CountingEligibility(BusinessProfileRepository profiles, BusinessServiceRepository services, BusinessProfileRepositoryQDSL queries) {
            super(profiles, services, queries);
        }

        @Override
        public Set<Long> approvedUserIds(Collection<Long> userIds) {
            calls.incrementAndGet();
            synchronized (arguments) {
                arguments.add(List.copyOf(userIds.stream().filter(java.util.Objects::nonNull).toList()));
            }
            return super.approvedUserIds(userIds);
        }

        // 트랜잭션 프록시(CGLIB)라 칸은 메서드로 읽는다
        public int calls() {
            return calls.get();
        }

        public Collection<Long> lastArgument() {
            synchronized (arguments) {
                return arguments.getLast();
            }
        }
    }

    @TestConfiguration
    static class CountingConfig {

        @Bean
        @Primary
        CountingEligibility countingEligibility(BusinessProfileRepository profiles, BusinessServiceRepository services, BusinessProfileRepositoryQDSL queries) {
            return new CountingEligibility(profiles, services, queries);
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private BoardCatalog boards;
    @Autowired
    private CountingEligibility eligibility;

    private MockMvc mvc;
    private long board;
    private String slug;

    @BeforeEach
    void setUpBoard() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        board = ids.nextId();
        slug = "bb" + Long.toString(board, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 900)", board, slug, "뱃지 " + slug.substring(slug.length() - 6));
        boards.invalidate();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    /** 사업자 신원이 없는 일반 회원(프로필 있음) */
    private long plainMember() {
        long user = newMember();
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", user, "p" + Long.toString(user, 36).substring(Math.max(0, Long.toString(user, 36).length() - 11)));
        return user;
    }

    private long post(Long authorId, String guestName) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO posts (id, board_id, author_id, guest_name, guest_password_hash, title, content) VALUES (?, ?, ?, ?, ?, '뱃지 시험', '본문')", id, board,
                authorId, guestName, guestName == null ? null : "x");
        return id;
    }

    private void comment(long postId, long authorId) {
        jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, content) VALUES (?, ?, ?, ?, '댓글')", ids.nextId(), postId, board, authorId);
        jdbc.update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?", postId);
    }

    private String nickname(long userId) {
        return jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, userId);
    }

    @Test
    void 일괄_조회는_승인된_사업자만_돌려준다_재심사_정지_반려_일반은_빠진다() {
        long approved = approvedBusiness();
        long reReview = business("PENDING", true);
        long pending = business("PENDING", false);
        long suspended = business("SUSPENDED", false);
        long rejected = business("PENDING", false);
        jdbc.update("UPDATE business_profiles SET status = 'REJECTED', reject_code = 'OTHER', reject_text = '시험' WHERE user_id = ?", rejected);
        long plain = plainMember();

        Set<Long> out = eligibility.approvedUserIds(Arrays.asList(approved, reReview, pending, suspended, rejected, plain, null, approved));

        assertThat(out).containsExactly(approved);
        assertThat(eligibility.approvedUserIds(List.of())).isEmpty();
    }

    @Test
    void 글_목록_글_보기_댓글_공개_프로필_쪽지_상대에_승인된_사업자만_뱃지가_붙는다() throws Exception {
        long approved = approvedBusiness();
        long reReview = business("PENDING", true);
        long suspended = business("SUSPENDED", false);
        long plain = plainMember();
        long viewer = plainMember();
        Map<Long, Boolean> expected = Map.of(approved, true, reReview, false, suspended, false, plain, false);

        List<Long> postIds = new ArrayList<>();
        for (long author : List.of(approved, reReview, suspended, plain)) {
            long p = post(author, null);
            comment(p, author);
            postIds.add(p);
        }
        long guestPost = post(null, "손님");

        // 글 목록 — 작성자 다섯 줄(회원 넷 + 비회원)에 일괄 조회 한 번, 회원 넷을 한 번에 넘긴다
        int before = eligibility.calls();
        String list = mvc.perform(as(viewer, get("/api/boards/" + slug + "/posts"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(eligibility.calls() - before).isEqualTo(1);
        assertThat(eligibility.lastArgument()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        List<Map<String, Object>> authors = JsonPath.read(list, "$.data.items[*].author");
        assertThat(authors).hasSize(5);
        for (Map<String, Object> author : authors) {
            Object memberId = author.get("memberId");
            boolean want = memberId != null && expected.get(Long.parseLong((String) memberId));
            assertThat(author.get("business")).as("목록 작성자 %s", memberId).isEqualTo(want);
        }

        for (int i = 0; i < postIds.size(); i++) {
            long author = List.of(approved, reReview, suspended, plain).get(i);
            boolean want = expected.get(author);
            mvc.perform(as(viewer, get("/api/posts/" + postIds.get(i)))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.author.memberId").value(Long.toString(author))).andExpect(jsonPath("$.data.author.business").value(want));
            mvc.perform(as(viewer, get("/api/posts/" + postIds.get(i) + "/comments"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.threads[0].author.business").value(want));
            mvc.perform(get("/api/users/" + author + "/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.data.business").value(want));
            mvc.perform(as(viewer, get("/api/dm/recipients").param("q", nickname(author)))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].user.id").value(Long.toString(author))).andExpect(jsonPath("$.data.items[0].user.business").value(want));
        }

        mvc.perform(get("/api/posts/" + guestPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.kind").value("GUEST"))
                .andExpect(jsonPath("$.data.author.business").value(false));
    }
}
