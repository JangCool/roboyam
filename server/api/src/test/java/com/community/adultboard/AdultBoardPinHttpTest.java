package com.community.adultboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.repository.master.IdentityStore;
import com.community.installation.service.InstallationState;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.withdrawal.WithdrawalTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * 19금 게시판 접근 설정·핀번호(docs/features/adult-board-pin-contract.md) 규칙 시험 — 앱 전체 + 실제 PostgreSQL, 진짜 이메일 로그인 + Spring Session JDBC 로
 * 「기기(로그인 세션)마다 따로」인 핀 확인을 그대로 확인한다. 기기 하나 = {@link Device}(쿠키 항아리). 시계는 {@code MutableClock}.
 */
class AdultBoardPinHttpTest extends CommerceApiTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String XSRF = "test-xsrf-token";
    private static final String PIN = "482915";
    private static final String PIN2 = "739104";
    private static final String PW = WithdrawalTestSupport.PASSWORD;

    @Autowired
    private WebApplicationContext web;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter sessionFilter;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private IdentityStore identityStore;
    @Autowired
    private BoardRepository boards;
    @Autowired
    private PostRepository posts;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private CacheManager caches;

    private MockMvc mvc;
    private long author;
    private long adultBoard;
    private String slug;
    private long adultPost;

    @BeforeEach
    void setUp() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(sessionFilter, securityFilterChain).build();
        author = newMember();
        memberFixture.profileIfAbsent(author, "a" + Long.toString(author, 36));
        adultBoard = ids.nextId();
        slug = "ab" + Long.toString(adultBoard, 36);
        boardFixture.board(adultBoard, slug, "성인 시험 " + Long.toString(adultBoard, 36), 9900);
        tx.executeWithoutResult(s -> boards.findById(adultBoard).orElseThrow().changeAdultOnly(true));
        adultPost = ids.nextId();
        boardFixture.post(adultPost, adultBoard, author, "성인 글", "성인 글 본문");
        catalog.invalidate();
    }

    @AfterEach
    void tearDown() {
        // 시험 게시판은 다른 시험의 목록에 남지 않게 끈다
        boardFixture.active(adultBoard, false);
        catalog.invalidate();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 기기 하나 — 쿠키 항아리. 같은 항아리로 보내면 같은 로그인 세션이다 */
    private final class Device {
        private final Map<String, Cookie> jar = new LinkedHashMap<>();

        MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
            request.cookie(new Cookie("XSRF-TOKEN", XSRF));
            jar.values().forEach(request::cookie);
            request.header("X-XSRF-TOKEN", XSRF).contentType(MediaType.APPLICATION_JSON).secure(true);
            MvcResult result = mvc.perform(request).andReturn();
            for (Cookie c : result.getResponse().getCookies()) {
                if ("XSRF-TOKEN".equals(c.getName())) {
                    continue;
                }
                if (c.getMaxAge() == 0 || c.getValue() == null || c.getValue().isEmpty()) {
                    jar.remove(c.getName());
                } else {
                    jar.put(c.getName(), c);
                }
            }
            return result;
        }

        MvcResult get(String url) throws Exception {
            return send(MockMvcRequestBuilders.get(url));
        }

        MvcResult post(String url, Object body) throws Exception {
            return send(MockMvcRequestBuilders.post(url).content(body == null ? "{}" : MAPPER.writeValueAsString(body)));
        }

        MvcResult put(String url, Object body) throws Exception {
            return send(MockMvcRequestBuilders.put(url).content(MAPPER.writeValueAsString(body)));
        }

        Device login(long userId) throws Exception {
            MvcResult r = post("/api/auth/login", Map.of("email", memberFixture.email(userId), "password", PW));
            assertThat(r.getResponse().getStatus()).as("로그인 " + r.getResponse().getContentAsString()).isEqualTo(200);
            return this;
        }

        // 자주 쓰는 호출
        MvcResult status() throws Exception {
            return get("/api/me/adult-board");
        }

        MvcResult enable(String password, String pin) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("enabled", true);
            if (password != null) {
                body.put("password", password);
            }
            if (pin != null) {
                body.put("pin", pin);
            }
            return put("/api/me/adult-board", body);
        }

        MvcResult disable() throws Exception {
            return put("/api/me/adult-board", Map.of("enabled", false));
        }

        MvcResult changePin(String password, String pin) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            if (password != null) {
                body.put("password", password);
            }
            if (pin != null) {
                body.put("pin", pin);
            }
            return put("/api/me/adult-board/pin", body);
        }

        MvcResult unlock(String pin) throws Exception {
            return post("/api/me/adult-board/unlock", Map.of("pin", pin));
        }

        MvcResult lock() throws Exception {
            return post("/api/me/adult-board/lock", null);
        }

        MvcResult relockMode(String mode) throws Exception {
            return put("/api/me/adult-board/relock-mode", Map.of("relockMode", mode));
        }

        MvcResult board() throws Exception {
            return get("/api/boards/" + slug);
        }

        MvcResult boardPosts() throws Exception {
            return get("/api/boards/" + slug + "/posts");
        }

        MvcResult postDetail() throws Exception {
            return get("/api/posts/" + adultPost);
        }

        MvcResult comments() throws Exception {
            return get("/api/posts/" + adultPost + "/comments");
        }
    }

    /** 성인(만 19세 이상 본인인증) + 계정 비밀번호가 있는 회원 */
    private long adultMember() {
        long id = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, id, clock.instant());
        verifyIdentity(id, LocalDate.of(1990, 1, 1));
        return id;
    }

    private void verifyIdentity(long id, LocalDate birth) {
        identityStore.insertVerification(id, String.format("%064x", id), "f" + String.format("%063x", id), birth, IdentityProviderKind.FAKE, "fake:" + id, Instant.now());
    }

    private static int code(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private static String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    private static <T> T read(MvcResult r, String path) throws Exception {
        return JsonPath.read(body(r), path);
    }

    private static String errorCode(MvcResult r) throws Exception {
        return read(r, "$.error.code");
    }

    private void expectError(MvcResult r, int status, String code) throws Exception {
        assertThat(code(r)).as(body(r)).isEqualTo(status);
        assertThat(errorCode(r)).as(body(r)).isEqualTo(code);
    }

    private void expectHidden(Device d) throws Exception {
        expectError(d.board(), 404, "BOARD_NOT_FOUND");
        expectError(d.boardPosts(), 404, "BOARD_NOT_FOUND");
        expectError(d.postDetail(), 404, "POST_NOT_FOUND");
        expectError(d.comments(), 404, "POST_NOT_FOUND");
    }

    private void expectPinRequired(Device d) throws Exception {
        expectError(d.board(), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(d.boardPosts(), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(d.postDetail(), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(d.comments(), 403, "ADULT_BOARD_PIN_REQUIRED");
    }

    private void expectOpen(Device d) throws Exception {
        assertThat(code(d.board())).isEqualTo(200);
        assertThat(code(d.boardPosts())).isEqualTo(200);
        assertThat(code(d.postDetail())).isEqualTo(200);
        assertThat(code(d.comments())).isEqualTo(200);
    }

    private boolean listedInBoards(Device d) throws Exception {
        MvcResult r = d.get("/api/boards");
        assertThat(code(r)).isEqualTo(200);
        List<String> slugs = read(r, "$.data.boards[*].slug");
        return slugs.contains(slug);
    }

    private Map<String, Object> settingRow(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM member_adult_board_access WHERE user_id = ?", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── 1. 기본 꺼짐 ────────────────────────────────────────────────────────

    @Test
    void 기본은_꺼짐_본인인증_성인이어도_성인_전용_게시판은_없는_것과_같고_목록에도_없다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);

        assertThat(settingRow(member)).isNull(); // 설정 행이 없다 = 꺼짐
        expectHidden(d);
        assertThat(listedInBoards(d)).isFalse();
        // 쓰기·태그 추천·게시판 레이아웃·검색 게시판 고르기도 없는 게시판
        expectError(d.get("/api/boards/" + slug + "/tags"), 404, "BOARD_NOT_FOUND");
        expectError(d.get("/api/boards/" + slug + "/layout"), 404, "BOARD_NOT_FOUND");
        expectError(d.post("/api/posts", Map.of("boardSlug", slug, "title", "제목", "contentFormat", "TEXT", "content", "본문")), 404, "BOARD_NOT_FOUND");
        MvcResult search = d.get("/api/search?q=" + "성인글&board=" + slug);
        assertThat(code(search)).isEqualTo(400);
        assertThat((Map<String, Object>) read(search, "$.error.fields")).containsKey("board");
        // 쓰기 게시판 고르기에도 없다
        List<String> writable = read(d.get("/api/boards/writable"), "$.data.boards[*].slug");
        assertThat(writable).doesNotContain(slug);

        MvcResult status = d.status();
        assertThat(code(status)).isEqualTo(200);
        assertThat((Boolean) read(status, "$.data.enabled")).isFalse();
        assertThat((Boolean) read(status, "$.data.unlocked")).isFalse();
        assertThat((Boolean) read(status, "$.data.passwordAvailable")).isTrue();
    }

    // ── 2. 켜기 ─────────────────────────────────────────────────────────────

    @Test
    void 켜기_입력_검사_숫자_6자리가_아니면_400_fields_pin_이고_행도_안_생긴다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);

        for (String bad : new String[] {"12345", "1234567", "12345a", "12 456", "１２３４５６", ""}) {
            MvcResult r = d.enable(PW, bad);
            assertThat(code(r)).as("pin=" + bad).isEqualTo(400);
            assertThat(errorCode(r)).isEqualTo("VALIDATION");
            assertThat((Map<String, Object>) read(r, "$.error.fields")).containsKey("pin");
        }
        MvcResult noPin = d.enable(PW, null);
        assertThat(code(noPin)).isEqualTo(400);
        assertThat((Map<String, Object>) read(noPin, "$.error.fields")).containsKey("pin");
        MvcResult noPassword = d.enable(null, PIN);
        assertThat(code(noPassword)).isEqualTo(400);
        assertThat((Map<String, Object>) read(noPassword, "$.error.fields")).containsKey("password");
        assertThat(settingRow(member)).isNull();
    }

    @Test
    void 켜기_비밀번호가_틀리면_401_CURRENT_PASSWORD_WRONG_이고_켜지지_않는다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);

        expectError(d.enable("Wrong1!password", PIN), 401, "CURRENT_PASSWORD_WRONG");
        assertThat(settingRow(member)).isNull();
        expectHidden(d);
    }

    @Test
    void 켜기_성인이_아니면_거절_미인증_IDENTITY_REQUIRED_미성년_IDENTITY_UNDERAGE() throws Exception {
        long unverified = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, unverified, clock.instant());
        expectError(new Device().login(unverified).enable(PW, PIN), 403, "IDENTITY_REQUIRED");
        assertThat(settingRow(unverified)).isNull();

        long minor = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, minor, clock.instant());
        verifyIdentity(minor, LocalDate.now().minusYears(15));
        expectError(new Device().login(minor).enable(PW, PIN), 403, "IDENTITY_UNDERAGE");
        assertThat(settingRow(minor)).isNull();
    }

    @Test
    void 켜기_성공하면_enabled_unlocked_true_이고_이_세션은_바로_열리며_핀_평문은_응답과_DB_어디에도_없다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);

        MvcResult on = d.enable(PW, PIN);
        assertThat(code(on)).as(body(on)).isEqualTo(200);
        assertThat((Boolean) read(on, "$.data.enabled")).isTrue();
        assertThat((Boolean) read(on, "$.data.unlocked")).isTrue();
        assertThat((String) read(on, "$.data.relockMode")).isEqualTo("IDLE");
        assertThat(on.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(body(on)).doesNotContain(PIN).doesNotContain("pinHash").doesNotContain("pin_hash");
        expectOpen(d);
        assertThat(listedInBoards(d)).isTrue();

        Map<String, Object> row = settingRow(member);
        assertThat(row.get("enabled")).isEqualTo(true);
        assertThat((String) row.get("pin_hash")).isNotEqualTo(PIN).doesNotContain(PIN);
        assertThat(encoder.matches(PIN, (String) row.get("pin_hash"))).isTrue(); // 계정 비밀번호와 같은 PasswordEncoder 해시
        assertThat(row.values().stream().filter(v -> v != null && v.toString().contains(PIN)).count()).isZero();
        // 상태 조회에도 핀·해시는 없다
        assertThat(body(d.status())).doesNotContain(PIN).doesNotContain("pin_hash").doesNotContain("pinHash").doesNotContain("failed");
    }

    @Test
    void 켜기_핀_000000_과_123456_도_막지_않는다() throws Exception {
        long a = adultMember();
        assertThat(code(new Device().login(a).enable(PW, "000000"))).isEqualTo(200);
        long b = adultMember();
        assertThat(code(new Device().login(b).enable(PW, "123456"))).isEqualTo(200);
    }

    // ── 3. 켜짐 + 핀 미확인(다른 기기) ───────────────────────────────────────

    @Test
    void 다른_로그인_세션은_핀_미확인_모든_성인_전용_게시판_API_가_403_ADULT_BOARD_PIN_REQUIRED_이고_목록에는_보인다() throws Exception {
        long member = adultMember();
        Device a = new Device().login(member);
        assertThat(code(a.enable(PW, PIN))).isEqualTo(200);

        Device b = new Device().login(member);
        MvcResult status = b.status();
        assertThat((Boolean) read(status, "$.data.enabled")).isTrue();
        assertThat((Boolean) read(status, "$.data.unlocked")).isFalse();

        expectPinRequired(b);
        // 잠기지 않았으니 lockedUntil 은 없다
        assertThat(body(b.board())).doesNotContain("lockedUntil");
        expectError(b.get("/api/boards/" + slug + "/tags"), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(b.get("/api/boards/" + slug + "/layout"), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(b.post("/api/posts", Map.of("boardSlug", slug, "title", "제목", "contentFormat", "TEXT", "content", "본문")), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(b.post("/api/posts/" + adultPost + "/comments", Map.of("contentFormat", "TEXT", "content", "댓글")), 403, "ADULT_BOARD_PIN_REQUIRED");
        expectError(b.post("/api/posts/" + adultPost + "/like", null), 403, "ADULT_BOARD_PIN_REQUIRED");
        MvcResult search = b.get("/api/search?q=" + "성인글&board=" + slug);
        assertThat(code(search)).isEqualTo(400); // 핀 확인 전에는 게시판 안 검색에서 없는 게시판과 같다
        // 게시판 목록 · 글쓰기 게시판 고르기에는 보인다(메뉴와 같은 listed)
        assertThat(listedInBoards(b)).isTrue();
        // 첫 번째 기기는 계속 열려 있다
        expectOpen(a);
    }

    // ── 4. unlock ───────────────────────────────────────────────────────────

    @Test
    void unlock_맞으면_이_기기가_열린다() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        expectPinRequired(b);

        MvcResult ok = b.unlock(PIN);
        assertThat(code(ok)).as(body(ok)).isEqualTo(200);
        assertThat((Boolean) read(ok, "$.data.unlocked")).isTrue();
        assertThat((Object) read(ok, "$.data.unlockedUntil")).isNotNull();
        assertThat(body(ok)).doesNotContain(PIN);
        expectOpen(b);
        // 핀 확인 상태는 세션 속성(DB 의 Spring Session)에 있다 — 직렬화가 된다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes WHERE attribute_name = 'irobit.adultBoard.unlock'", Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void unlock_형식이_틀리면_400_fields_pin_이고_틀린_횟수로_세지_않는다() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);

        MvcResult r = b.unlock("12ab");
        assertThat(code(r)).isEqualTo(400);
        assertThat(errorCode(r)).isEqualTo("VALIDATION");
        assertThat(((Number) settingRow(member).get("failed_count")).intValue()).isZero();
    }

    @Test
    void unlock_틀리면_400_attemptsLeft_4_3_2_1_다섯_번째는_423_lockedUntil_잠긴_동안은_맞는_핀도_423_10분_뒤_다시_가능_맞으면_횟수_0() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        String wrong = "000001";

        for (int expectedLeft = 4; expectedLeft >= 1; expectedLeft--) {
            MvcResult r = b.unlock(wrong);
            expectError(r, 400, "ADULT_BOARD_PIN_WRONG");
            assertThat(((Number) read(r, "$.error.attemptsLeft")).intValue()).isEqualTo(expectedLeft);
        }
        Instant lockStart = clock.instant();
        MvcResult fifth = b.unlock(wrong);
        expectError(fifth, 423, "ADULT_BOARD_PIN_LOCKED");
        Instant lockedUntil = Instant.parse(read(fifth, "$.error.lockedUntil"));
        assertThat(lockedUntil).isEqualTo(lockStart.plus(Duration.ofMinutes(10)));
        assertThat(fifth.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(((Number) read(fifth, "$.error.retryAfterSeconds")).longValue()).isEqualTo(600);
        assertThat(((Number) settingRow(member).get("failed_count")).intValue()).isEqualTo(5);

        // 잠긴 동안 — 맞는 핀도 423, 횟수는 더하지 않는다
        MvcResult rightButLocked = b.unlock(PIN);
        expectError(rightButLocked, 423, "ADULT_BOARD_PIN_LOCKED");
        assertThat(Instant.parse(read(rightButLocked, "$.error.lockedUntil"))).isEqualTo(lockedUntil);
        assertThat(((Number) settingRow(member).get("failed_count")).intValue()).isEqualTo(5);
        // 게시판 접근의 403 에 lockedUntil 이 실린다 · 상태에도 lockedUntil
        MvcResult board = b.board();
        expectError(board, 403, "ADULT_BOARD_PIN_REQUIRED");
        assertThat(Instant.parse(read(board, "$.error.lockedUntil"))).isEqualTo(lockedUntil);
        assertThat(Instant.parse(read(b.status(), "$.data.lockedUntil"))).isEqualTo(lockedUntil);
        // 회원 단위 — 다른 기기에서도 잠겨 있다
        Device c = new Device().login(member);
        expectError(c.unlock(PIN), 423, "ADULT_BOARD_PIN_LOCKED");

        // 9분 59초 — 아직 잠김, 10분 — 풀림
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        expectError(b.unlock(PIN), 423, "ADULT_BOARD_PIN_LOCKED");
        clock.advance(Duration.ofSeconds(1));
        MvcResult after = b.unlock(PIN);
        assertThat(code(after)).as(body(after)).isEqualTo(200);
        assertThat((Boolean) read(after, "$.data.unlocked")).isTrue();
        Map<String, Object> row = settingRow(member);
        assertThat(((Number) row.get("failed_count")).intValue()).isZero();
        expectOpen(b);
        // 처음부터 다시 센다 — 틀리면 attemptsLeft 4
        b.lock();
        MvcResult again = b.unlock(wrong);
        assertThat(((Number) read(again, "$.error.attemptsLeft")).intValue()).isEqualTo(4);
    }

    @Test
    void unlock_중간에_맞게_넣으면_틀린_횟수가_0_으로_돌아간다() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);

        b.unlock("000001");
        b.unlock("000002");
        assertThat(((Number) settingRow(member).get("failed_count")).intValue()).isEqualTo(2);
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
        assertThat(((Number) settingRow(member).get("failed_count")).intValue()).isZero();
        b.lock();
        MvcResult r = b.unlock("000003");
        assertThat(((Number) read(r, "$.error.attemptsLeft")).intValue()).isEqualTo(4);
    }

    @Test
    void unlock_접근이_꺼져_있으면_409_ADULT_BOARD_DISABLED() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);
        expectError(d.unlock(PIN), 409, "ADULT_BOARD_DISABLED");
    }

    // ── 5. 유지 1시간 ───────────────────────────────────────────────────────

    @Test
    void FIXED_는_핀을_넣은_뒤_1시간이_지나면_보는_중이어도_다시_403() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
        MvcResult mode = b.relockMode("FIXED");
        assertThat(code(mode)).isEqualTo(200);
        assertThat((String) read(mode, "$.data.relockMode")).isEqualTo("FIXED");
        assertThat((Boolean) read(mode, "$.data.unlocked")).isTrue();

        clock.advance(Duration.ofMinutes(30));
        expectOpen(b); // 보는 중
        clock.advance(Duration.ofMinutes(29)); // 59분
        expectOpen(b);
        clock.advance(Duration.ofMinutes(2)); // 61분 — 마지막으로 본 지 2분밖에 안 됐어도 닫힌다
        expectPinRequired(b);
        // 다시 넣으면 열린다
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
        expectOpen(b);
    }

    @Test
    void IDLE_은_접근할_때마다_연장되고_접근_없이_1시간이_넘으면_403() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
        assertThat((String) read(b.status(), "$.data.relockMode")).isEqualTo("IDLE");

        clock.advance(Duration.ofMinutes(50));
        expectOpen(b); // 50분 뒤 접근 → 연장
        clock.advance(Duration.ofMinutes(50)); // 처음부터 100분 — 연장이 없었다면 이미 닫혔다
        expectOpen(b);
        clock.advance(Duration.ofMinutes(59)); // 마지막 접근 뒤 59분
        expectOpen(b);
        // 접근 없이 1시간 넘게
        clock.advance(Duration.ofMinutes(61));
        expectPinRequired(b);
    }

    @Test
    void IDLE_연장_기록은_5분_단위_마지막_접근_뒤_55분에서_60분_사이에_닫힌다() throws Exception {
        long member = adultMember();
        assertThat(code(new Device().login(member).enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        assertThat(code(b.unlock(PIN))).isEqualTo(200);

        clock.advance(Duration.ofMinutes(10));
        expectOpen(b); // 연장 → 끝 70분
        clock.advance(Duration.ofMinutes(2));
        expectOpen(b); // 12분 — 남은 시간이 5분 넘게 줄지 않아 세션에 다시 쓰지 않는다(끝 70분 그대로)
        clock.advance(Duration.ofMinutes(57)); // 69분 — 마지막 접근(12분) 뒤 57분
        expectOpen(b);
        // 상태 조회는 연장하지 않는다 — 끝 시각 이후엔 닫힌다
        MvcResult st = b.status();
        Instant until = Instant.parse(read(st, "$.data.unlockedUntil"));
        clock.set(until.plusSeconds(1));
        assertThat((Boolean) read(b.status(), "$.data.unlocked")).isFalse();
        expectPinRequired(b);
    }

    @Test
    void 다시_묻기_기준_바꾸기_값_검사_꺼져_있어도_바꿀_수_있다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);
        MvcResult bad = d.relockMode("SOMETIMES");
        assertThat(code(bad)).isEqualTo(400);
        assertThat((Map<String, Object>) read(bad, "$.error.fields")).containsKey("relockMode");

        MvcResult off = d.relockMode("FIXED"); // 접근이 꺼진 상태 — 값만 남는다
        assertThat(code(off)).isEqualTo(200);
        assertThat((Boolean) read(off, "$.data.enabled")).isFalse();
        assertThat((String) read(off, "$.data.relockMode")).isEqualTo("FIXED");
        assertThat(code(d.enable(PW, PIN))).isEqualTo(200);
        assertThat((String) read(d.status(), "$.data.relockMode")).isEqualTo("FIXED");
    }

    // ── 6. 지금 잠그기 ──────────────────────────────────────────────────────

    @Test
    void lock_이_세션만_잠기고_다른_기기는_그대로() throws Exception {
        long member = adultMember();
        Device a = new Device().login(member);
        assertThat(code(a.enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
        expectOpen(a);
        expectOpen(b);

        MvcResult locked = b.lock();
        assertThat(code(locked)).isEqualTo(200);
        assertThat((Boolean) read(locked, "$.data.unlocked")).isFalse();
        assertThat((Boolean) read(locked, "$.data.enabled")).isTrue();
        expectPinRequired(b);
        expectOpen(a);
        // 다시 핀을 넣으면 열린다 · 틀린 횟수는 늘지 않았다
        assertThat(((Number) settingRow(member).get("failed_count")).intValue()).isZero();
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
    }

    @Test
    void 로그아웃하면_풀린다_다시_로그인하면_핀_필요() throws Exception {
        long member = adultMember();
        Device a = new Device().login(member);
        assertThat(code(a.enable(PW, PIN))).isEqualTo(200);
        expectOpen(a);
        assertThat(code(a.post("/api/auth/logout", null))).isEqualTo(204);
        a.login(member);
        expectPinRequired(a);
    }

    // ── 7. 다른 기기 무효 ───────────────────────────────────────────────────

    @Test
    void 한_기기가_핀을_바꾸면_다른_기기는_403_다시_핀을_넣어야_하고_끄면_404() throws Exception {
        long member = adultMember();
        Device a = new Device().login(member);
        assertThat(code(a.enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        assertThat(code(b.unlock(PIN))).isEqualTo(200);
        expectOpen(a);
        expectOpen(b);

        // 시계가 멈춰 있으면 pinSetAt 이 같아진다(시험용 시계 때문 — 실제로는 다른 시각) — 1초 뒤에 바꾼다
        clock.advance(Duration.ofSeconds(1));
        MvcResult changed = a.changePin(PW, PIN2);
        assertThat(code(changed)).as(body(changed)).isEqualTo(200);
        assertThat((Boolean) read(changed, "$.data.unlocked")).isTrue();
        expectOpen(a); // 바꾼 기기는 열림
        expectError(b.board(), 403, "ADULT_BOARD_PIN_REQUIRED"); // 다른 기기는 무효
        expectError(b.postDetail(), 403, "ADULT_BOARD_PIN_REQUIRED");
        // 옛 핀은 더 이상 맞지 않고 새 핀으로 열린다
        expectError(b.unlock(PIN), 400, "ADULT_BOARD_PIN_WRONG");
        assertThat(code(b.unlock(PIN2))).isEqualTo(200);
        expectOpen(b);

        // A 가 끄면 B 도 404
        clock.advance(Duration.ofSeconds(1));
        MvcResult off = a.disable();
        assertThat(code(off)).isEqualTo(200);
        expectHidden(a);
        expectHidden(b);
        assertThat(listedInBoards(b)).isFalse();
    }

    // ── 8. 끄기 ─────────────────────────────────────────────────────────────

    @Test
    void 끄기는_확인_없이_꺼지고_다시_404_다시_켜려면_비밀번호가_필요하다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);
        assertThat(code(d.enable(PW, PIN))).isEqualTo(200);
        expectOpen(d);

        MvcResult off = d.disable(); // 비밀번호·핀 없이
        assertThat(code(off)).as(body(off)).isEqualTo(200);
        assertThat((Boolean) read(off, "$.data.enabled")).isFalse();
        assertThat((Boolean) read(off, "$.data.unlocked")).isFalse();
        expectHidden(d);
        assertThat(listedInBoards(d)).isFalse();
        Map<String, Object> row = settingRow(member);
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(row.get("pin_hash")).isNull(); // 핀 해시를 지운다
        // 한 번 더 꺼도 오류 없이 그대로
        assertThat(code(d.disable())).isEqualTo(200);

        // 다시 켜려면 비밀번호 · 핀
        MvcResult noPw = d.enable(null, PIN);
        assertThat(code(noPw)).isEqualTo(400);
        expectError(d.enable("Wrong1!password", PIN), 401, "CURRENT_PASSWORD_WRONG");
        expectHidden(d);
        assertThat(code(d.enable(PW, PIN))).isEqualTo(200);
        expectOpen(d);
    }

    @Test
    void 켠_적_없는_회원이_끄기를_불러도_행을_만들지_않고_200() throws Exception {
        long member = adultMember();
        MvcResult off = new Device().login(member).disable();
        assertThat(code(off)).isEqualTo(200);
        assertThat((Boolean) read(off, "$.data.enabled")).isFalse();
        assertThat(settingRow(member)).isNull();
    }

    @Test
    void enabled_값이_없으면_400_fields_enabled() throws Exception {
        long member = adultMember();
        MvcResult r = new Device().login(member).put("/api/me/adult-board", Map.of());
        assertThat(code(r)).isEqualTo(400);
        assertThat((Map<String, Object>) read(r, "$.error.fields")).containsKey("enabled");
    }

    // ── 9. 핀 바꾸기 ────────────────────────────────────────────────────────

    @Test
    void 핀_바꾸기_꺼진_상태면_409_ADULT_BOARD_DISABLED() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);
        expectError(d.changePin(PW, PIN2), 409, "ADULT_BOARD_DISABLED");
        assertThat(settingRow(member)).isNull();
        // 켰다 끈 뒤에도 같다
        assertThat(code(d.enable(PW, PIN))).isEqualTo(200);
        d.disable();
        expectError(d.changePin(PW, PIN2), 409, "ADULT_BOARD_DISABLED");
    }

    @Test
    void 핀_바꾸기_입력_검사와_비밀번호_확인() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);
        assertThat(code(d.enable(PW, PIN))).isEqualTo(200);

        MvcResult badPin = d.changePin(PW, "12345");
        assertThat(code(badPin)).isEqualTo(400);
        assertThat((Map<String, Object>) read(badPin, "$.error.fields")).containsKey("pin");
        expectError(d.changePin("Wrong1!password", PIN2), 401, "CURRENT_PASSWORD_WRONG");
        // 실패한 변경은 핀을 바꾸지 않는다
        assertThat(encoder.matches(PIN, (String) settingRow(member).get("pin_hash"))).isTrue();
    }

    @Test
    void 핀_바꾸기_성공하면_틀린_횟수와_잠금이_풀리고_잠겨_있던_다른_기기가_새_핀으로_바로_열린다() throws Exception {
        long member = adultMember();
        Device a = new Device().login(member);
        assertThat(code(a.enable(PW, PIN))).isEqualTo(200);
        Device b = new Device().login(member);
        for (int i = 0; i < 5; i++) {
            b.unlock("000009");
        }
        expectError(b.unlock(PIN), 423, "ADULT_BOARD_PIN_LOCKED");
        assertThat(settingRow(member).get("locked_until")).isNotNull();

        clock.advance(Duration.ofSeconds(1));
        MvcResult changed = a.changePin(PW, PIN2); // 잊었을 때 다시 정하기와 같은 API
        assertThat(code(changed)).isEqualTo(200);
        assertThat((Object) read(changed, "$.data.lockedUntil")).isNull();
        Map<String, Object> row = settingRow(member);
        assertThat(((Number) row.get("failed_count")).intValue()).isZero();
        assertThat(row.get("locked_until")).isNull();

        MvcResult unlocked = b.unlock(PIN2);
        assertThat(code(unlocked)).as(body(unlocked)).isEqualTo(200);
        expectOpen(b);
    }

    // ── 10. 비밀번호 없는 회원 ──────────────────────────────────────────────

    @Test
    void 비밀번호가_없는_회원은_최근_10분_안_로그인이면_password_없이_켜지고_지났으면_401_REAUTH_REQUIRED() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member); // 이메일로 로그인한 뒤 소셜 전용 회원 모양으로 바꾼다(세션은 그대로)
        jdbc.update("UPDATE users SET password_hash = NULL, last_login_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofMinutes(5))), member);

        assertThat((Boolean) read(d.status(), "$.data.passwordAvailable")).isFalse();
        MvcResult on = d.enable(null, PIN);
        assertThat(code(on)).as(body(on)).isEqualTo(200);
        assertThat((Boolean) read(on, "$.data.enabled")).isTrue();
        expectOpen(d);

        d.disable();
        jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofMinutes(11))), member);
        expectError(d.enable(null, PIN), 401, "REAUTH_REQUIRED");
        expectHidden(d);
        // 경계 — 정확히 10분 전은 통과
        jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofMinutes(10))), member);
        assertThat(code(d.enable(null, PIN))).isEqualTo(200);
        // 핀 바꾸기도 같은 규칙
        jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofMinutes(30))), member);
        expectError(d.changePin(null, PIN2), 401, "REAUTH_REQUIRED");
    }

    // ── 11~12. 범위·회귀 ────────────────────────────────────────────────────

    @Test
    void 일반_게시판_안의_19금_글은_접근_설정이_꺼져_있어도_본인인증_성인이면_예전처럼_보인다() throws Exception {
        long general = ids.nextId();
        String generalSlug = "gn" + Long.toString(general, 36);
        boardFixture.board(general, generalSlug, "일반 시험 " + Long.toString(general, 36), 9901, "GENERAL", true, false, true);
        long adultPostInGeneral = ids.nextId();
        tx.executeWithoutResult(s -> posts.persist(PostEntity.create(adultPostInGeneral, general, null, author, null, null, null, "19금 글", ContentFormat.TEXT, "본문", null,
                true, false, Instant.now())));
        catalog.invalidate();
        try {
            long adult = adultMember();
            Device d = new Device().login(adult);
            assertThat(settingRow(adult)).isNull();
            MvcResult detail = d.get("/api/posts/" + adultPostInGeneral);
            assertThat(code(detail)).as(body(detail)).isEqualTo(200);
            assertThat(code(d.get("/api/boards/" + generalSlug + "/posts"))).isEqualTo(200);

            // 본인인증 안 한 회원·비회원은 예전처럼 막힌다(성인 전용 게시판의 404 가 아니라 19금 글의 403)
            long unverified = newMember();
            WithdrawalTestSupport.withPassword(jdbc, encoder, unverified, clock.instant());
            MvcResult blocked = new Device().login(unverified).get("/api/posts/" + adultPostInGeneral);
            assertThat(code(blocked)).isEqualTo(403);
            assertThat(errorCode(blocked)).isEqualTo("POST_ADULT_ONLY");
        } finally {
            boardFixture.active(general, false);
            catalog.invalidate();
        }
    }

    @Test
    void 비회원과_미성년과_미인증_회원은_성인_전용_게시판이_404_그대로_목록에도_없다() throws Exception {
        Device guest = new Device();
        expectHidden(guest);
        assertThat(listedInBoards(guest)).isFalse();

        long minor = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, minor, clock.instant());
        verifyIdentity(minor, LocalDate.now().minusYears(15));
        Device m = new Device().login(minor);
        expectHidden(m);
        assertThat(listedInBoards(m)).isFalse();

        long unverified = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, unverified, clock.instant());
        Device u = new Device().login(unverified);
        expectHidden(u);
        assertThat(listedInBoards(u)).isFalse();

        // 미성년이 설정 행을 갖고 있어도(예: 데이터가 어긋나 있어도) 열리지 않는다 — 성인 판정을 같이 본다
        jdbc.update("INSERT INTO member_adult_board_access (user_id, enabled, pin_hash, pin_set_at) VALUES (?, true, ?, now())", minor, encoder.encode(PIN));
        expectHidden(m);
        assertThat(listedInBoards(m)).isFalse();
    }

    @Test
    void 핀_없이_켜진_행은_DB_제약이_막는다() {
        long member = adultMember();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("INSERT INTO member_adult_board_access (user_id, enabled) VALUES (?, true)", member))
                .hasMessageContaining("ck_member_adult_board_access_pin");
    }

    // ── 메뉴·가젯 ───────────────────────────────────────────────────────────

    @Test
    void 열림_상태에서_성인_전용_글을_쓸_수_있다() throws Exception {
        long member = adultMember();
        Device d = new Device().login(member);
        assertThat(code(d.enable(PW, PIN))).isEqualTo(200);
        MvcResult r = d.post("/api/posts", Map.of("boardSlug", slug, "title", "새 글", "contentFormat", "TEXT", "content", "본문 내용입니다"));
        // 핀 판정은 통과한다 — 쓰기 권한·등급 때문에 다른 오류가 날 수는 있지만 404·403 PIN_REQUIRED 는 아니어야 한다
        assertThat(code(r)).as(body(r)).isIn(201, 400, 403);
        if (code(r) == 403) {
            assertThat(errorCode(r)).isNotEqualTo("ADULT_BOARD_PIN_REQUIRED");
        }
        assertThat(code(r)).as(body(r)).isNotEqualTo(404);
    }

    @Test
    void 메뉴는_성인_더하기_접근_켜짐이면_핀_확인_전에도_보이고_꺼짐_비회원이면_빠진다_캐시가_보는_사람을_섞지_않는다() throws Exception {
        String setKey = "apm-" + Long.toString(ids.nextId(), 36);
        jdbc.update("INSERT INTO menu_sets (set_key, name, placement) VALUES (?, '핀 시험 메뉴', 'CUSTOM')", setKey);
        jdbc.update("INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_id) VALUES (?, ?, NULL, 'ITEM', 0, 'BOARD', ?)", ids.nextId(), setKey,
                Long.toString(adultBoard));
        try {
            String url = "/api/menu?set=" + setKey;
            String needle = "/boards/" + slug;
            Device guest = new Device();
            assertThat(body(guest.get(url))).as("비회원").doesNotContain(needle); // 비회원이 먼저 불러 캐시를 데운다

            long member = adultMember();
            Device a = new Device().login(member);
            assertThat(body(a.get(url))).as("성인 + 꺼짐").doesNotContain(needle);
            assertThat(code(a.enable(PW, PIN))).isEqualTo(200);
            assertThat(body(a.get(url))).as("켜짐 + 열림").contains(needle);
            Device b = new Device().login(member);
            assertThat(body(b.get(url))).as("켜짐 + 핀 미확인 — 메뉴에는 보인다").contains(needle);
            assertThat(body(guest.get(url))).as("비회원은 여전히").doesNotContain(needle);
            assertThat(code(a.disable())).isEqualTo(200);
            assertThat(body(a.get(url))).as("끈 뒤").doesNotContain(needle);
            assertThat(body(b.get(url))).as("끈 뒤 다른 기기").doesNotContain(needle);
        } finally {
            jdbc.update("DELETE FROM menu_sets WHERE set_key = ?", setKey);
        }
    }

    @Test
    void 가젯_19금_전용_가젯은_핀_확인_전에는_숨김_표시_열리면_데이터가_나온다() throws Exception {
        long member = adultMember();
        Device a = new Device().login(member);
        MvcResult first = a.get("/api/home/gadgets");
        assertThat(code(first)).as(body(first)).isEqualTo(200);
        String theme = read(first, "$.data.theme");
        long gadget = ids.nextId();
        List<Map<String, Object>> previous = jdbc.queryForList("SELECT * FROM home_layouts WHERE theme = ?", theme);
        jdbc.update("INSERT INTO home_gadgets (id, theme, type, title, config) VALUES (?, ?, 'ADULT_POSTS', '19금 시험', '{}'::jsonb)", gadget, theme);
        jdbc.update("INSERT INTO home_layouts (theme, columns, items) VALUES (?, 12, ?::jsonb) ON CONFLICT (theme) DO UPDATE SET items = EXCLUDED.items, columns = 12", theme,
                "[{\"gadgetId\":\"" + gadget + "\",\"x\":0,\"y\":0,\"w\":12,\"h\":4}]");
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        try {
            // 접근 꺼짐 — 숨김
            assertThat(gadgetHidden(a, gadget)).as("꺼짐").isTrue();
            assertThat(code(a.enable(PW, PIN))).isEqualTo(200);
            assertThat(gadgetHidden(a, gadget)).as("켜짐 + 열림").isFalse();
            Device b = new Device().login(member);
            assertThat(gadgetHidden(b, gadget)).as("켜짐 + 핀 미확인").isTrue();
            assertThat(gadgetHidden(new Device(), gadget)).as("비회원").isTrue();
            assertThat(code(b.unlock(PIN))).isEqualTo(200);
            assertThat(gadgetHidden(b, gadget)).as("핀 확인 뒤").isFalse();
            b.lock();
            assertThat(gadgetHidden(b, gadget)).as("지금 잠근 뒤").isTrue();
        } finally {
            jdbc.update("DELETE FROM home_gadgets WHERE id = ?", gadget);
            if (previous.isEmpty()) {
                jdbc.update("DELETE FROM home_layouts WHERE theme = ?", theme);
            } else {
                jdbc.update("UPDATE home_layouts SET columns = ?, items = ?::jsonb WHERE theme = ?", previous.get(0).get("columns"), previous.get(0).get("items").toString(), theme);
            }
            caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        }
    }

    private boolean gadgetHidden(Device d, long gadget) throws Exception {
        MvcResult r = d.get("/api/home/gadgets");
        assertThat(code(r)).as(body(r)).isEqualTo(200);
        List<Boolean> hidden = read(r, "$.data.gadgets[?(@.id=='" + gadget + "')].hidden");
        assertThat(hidden).as("가젯이 응답에 있다").hasSize(1);
        return hidden.get(0);
    }
}
