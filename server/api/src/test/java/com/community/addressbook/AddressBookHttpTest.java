package com.community.addressbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.exception.AddressBookFullException;
import com.community.addressbook.repository.master.MemberAddressStore;
import com.community.addressbook.service.AddressBookService;
import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.service.GroupBuyAdminService;
import com.community.groupbuy.service.GroupBuyJoinService;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.groupbuy.service.ShippingPolicyService;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.repository.master.IdentityStore;
import com.community.installation.service.InstallationState;
import com.community.user.entity.Role;
import com.community.withdrawal.WithdrawalTestSupport;
import com.community.withdrawal.service.WithdrawalService;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.CommonException;
import jakarta.servlet.Filter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 주소록(배송지 관리 — docs/features/address-book-contract.md)과 공구 주문 연결 — 실제 api 앱 문맥 + PostgreSQL(commerce.md 18절).
 * 칸 규칙 · 10개 한도(동시 포함) · 첫 배송지 기본 · 기본 하나 · 기본 삭제 시 넘김 · 기본 끄기 불가 · 차례 · 남의 배송지 404 · 암호문 저장 · 키 없음 503 ·
 * 주문 {@code addressId} 복사·메모·남의 ID 400 · {@code saveAddress} 저장·10개면 건너뜀·멱등 재전송 한 번·주문 실패면 저장 없음 · 탈퇴 즉시 삭제.
 */
class AddressBookHttpTest extends CommerceApiTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AddressBookService addressBook;
    @Autowired
    private MemberAddressStore store;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private ObjectProvider<Clock> clocks;
    @Autowired
    private GroupBuyProductService products;
    @Autowired
    private GroupBuyAdminService admin;
    @Autowired
    private ShippingPolicyService policies;
    @Autowired
    private GroupBuyNoticeService notices;
    @Autowired
    private GroupBuyNoticeCatalog catalog;
    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    private static String body(String label, String recipient, String phone, String postcode, String address1, String address2, String memo, boolean makeDefault) {
        return "{\"label\":" + q(label) + ",\"recipient\":" + q(recipient) + ",\"phone\":" + q(phone) + ",\"postcode\":" + q(postcode) + ",\"address1\":" + q(address1)
                + ",\"address2\":" + q(address2) + ",\"memo\":" + q(memo) + ",\"makeDefault\":" + makeDefault + "}";
    }

    private static String q(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String valid(String recipient, boolean makeDefault) {
        return body("집", recipient, "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 1", "101호", "문 앞", makeDefault);
    }

    /** 배송지를 더하고 글자 ID 를 돌려준다(시계를 1초 넘겨 updated_at 이 겹치지 않게) */
    private String add(long member, String recipient, boolean makeDefault) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        String json = mvc.perform(as(member, post("/api/me/addresses")).content(valid(recipient, makeDefault))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(json).path("data").path("id").asString();
    }

    private JsonNode list(long member) throws Exception {
        return MAPPER.readTree(mvc.perform(as(member, get("/api/me/addresses"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private List<String> recipientsInOrder(long member) throws Exception {
        List<String> out = new ArrayList<>();
        list(member).path("items").forEach(i -> out.add(i.path("recipient").asString()));
        return out;
    }

    private String defaultRecipient(long member) throws Exception {
        for (JsonNode i : list(member).path("items")) {
            if (i.path("isDefault").asBoolean()) {
                return i.path("recipient").asString();
            }
        }
        return null;
    }

    private int defaults(long member) {
        return jdbc.queryForObject("SELECT count(*) FROM member_addresses WHERE user_id = ? AND is_default", Integer.class, member);
    }

    private int count(long member) {
        return jdbc.queryForObject("SELECT count(*) FROM member_addresses WHERE user_id = ?", Integer.class, member);
    }

    private static AddressInput input(String recipient) {
        return new AddressInput(null, recipient, "01012345678", "06236", "서울 강남구 1", null, null, false);
    }

    // ── 주소록 API ──────────────────────────────────────────────────────────

    @Nested
    class 주소록 {

        @Test
        void 응답_모양_ID_는_글자_빈_별칭은_null_빈_상세_메모는_빈_글자_max_10() throws Exception {
            long m = newMember();
            mvc.perform(as(m, post("/api/me/addresses")).content(body("  ", "홍길동", "01012345678", "06236", "서울 1", "", null, false)))
                    .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.id").isString()).andExpect(jsonPath("$.data.label").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.data.recipient").value("홍길동")).andExpect(jsonPath("$.data.phone").value("01012345678"))
                    .andExpect(jsonPath("$.data.postcode").value("06236")).andExpect(jsonPath("$.data.address1").value("서울 1"))
                    .andExpect(jsonPath("$.data.address2").value("")).andExpect(jsonPath("$.data.memo").value(""))
                    .andExpect(jsonPath("$.data.isDefault").value(true)).andExpect(jsonPath("$.data.updatedAt").isString());
            JsonNode page = list(m);
            assertThat(page.path("max").asInt()).isEqualTo(10);
            assertThat(page.path("items")).hasSize(1);
            assertThat(page.path("items").get(0).has("label")).as("label 은 null 로 나간다(화면 타입 string | null)").isTrue();
            assertThat(jdbc.queryForObject("SELECT label FROM member_addresses WHERE user_id = ?", String.class, m)).isNull();
        }

        @Test
        void 칸_검사는_틀린_칸을_모두_모아_400_VALIDATION_경계값은_통과() throws Exception {
            long m = newMember();
            mvc.perform(as(m, post("/api/me/addresses")).content(body("가".repeat(21), "가".repeat(31), "010-12", "1234", "", "나".repeat(101), "다".repeat(51), false)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.label").exists()).andExpect(jsonPath("$.error.fields.recipient").exists())
                    .andExpect(jsonPath("$.error.fields.phone").exists()).andExpect(jsonPath("$.error.fields.postcode").exists())
                    .andExpect(jsonPath("$.error.fields.address1").exists()).andExpect(jsonPath("$.error.fields.address2").exists())
                    .andExpect(jsonPath("$.error.fields.memo").exists());
            mvc.perform(as(m, post("/api/me/addresses")).content("{}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.recipient").exists()).andExpect(jsonPath("$.error.fields.label").doesNotExist());
            assertThat(count(m)).isZero();
            mvc.perform(as(m, post("/api/me/addresses")).content(body("가".repeat(20), "가".repeat(30), "010-1234-5678", "06236", "가".repeat(100), "나".repeat(100),
                    "다".repeat(50), false))).andExpect(status().isCreated());
            // 고치기도 같은 규칙
            String id = list(m).path("items").get(0).path("id").asString();
            mvc.perform(as(m, put("/api/me/addresses/" + id)).content(body(null, "홍길동", "0101234567890", "06236", "서울", null, null, false)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.phone").exists());
        }

        @Test
        void 첫_배송지는_makeDefault_와_상관없이_기본_기본은_늘_하나() throws Exception {
            long m = newMember();
            add(m, "첫째", false);
            assertThat(defaultRecipient(m)).isEqualTo("첫째");
            add(m, "둘째", false);
            assertThat(defaultRecipient(m)).as("기본으로 하지 않은 둘째는 기본이 아니다").isEqualTo("첫째");
            add(m, "셋째", true);
            assertThat(defaultRecipient(m)).isEqualTo("셋째");
            assertThat(defaults(m)).isEqualTo(1);
            // 기본으로 정하기
            String second = list(m).path("items").get(2).path("id").asString();
            assertThat(list(m).path("items").get(2).path("recipient").asString()).isEqualTo("첫째");
            mvc.perform(as(m, put("/api/me/addresses/" + second + "/default"))).andExpect(status().isNoContent());
            assertThat(defaultRecipient(m)).isEqualTo("첫째");
            assertThat(defaults(m)).isEqualTo(1);
            // 고치며 기본으로
            String third = list(m).path("items").get(1).path("id").asString();
            mvc.perform(as(m, put("/api/me/addresses/" + third)).content(valid("셋째 고침", true))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isDefault").value(true));
            assertThat(defaultRecipient(m)).isEqualTo("셋째 고침");
            assertThat(defaults(m)).isEqualTo(1);
        }

        @Test
        void 기본을_makeDefault_false_로_고쳐도_기본은_꺼지지_않는다() throws Exception {
            long m = newMember();
            String only = add(m, "하나", false);
            add(m, "둘", false);
            mvc.perform(as(m, put("/api/me/addresses/" + only)).content(valid("하나 고침", false))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isDefault").value(true));
            assertThat(defaultRecipient(m)).isEqualTo("하나 고침");
            assertThat(defaults(m)).isEqualTo(1);
        }

        @Test
        void 목록_차례는_기본_다음_최근_고친_순() throws Exception {
            long m = newMember();
            String a = add(m, "가", false); // 기본
            add(m, "나", false);
            String c = add(m, "다", false);
            assertThat(recipientsInOrder(m)).containsExactly("가", "다", "나");
            clock.advance(Duration.ofSeconds(1));
            mvc.perform(as(m, put("/api/me/addresses/" + list(m).path("items").get(2).path("id").asString())).content(valid("나 고침", false))).andExpect(status().isOk());
            assertThat(recipientsInOrder(m)).containsExactly("가", "나 고침", "다");
            assertThat(a).isNotEqualTo(c);
        }

        @Test
        void 기본을_지우면_남은_것_중_가장_최근_것이_기본_기본이_아닌_것을_지우면_그대로() throws Exception {
            long m = newMember();
            String first = add(m, "가", false); // 기본
            String older = add(m, "나", false);
            add(m, "다", false); // 가장 최근
            clock.advance(Duration.ofSeconds(1));
            mvc.perform(as(m, put("/api/me/addresses/" + older)).content(valid("나 고침", false))).andExpect(status().isOk()); // 이제 「나 고침」이 가장 최근
            mvc.perform(as(m, delete("/api/me/addresses/" + first))).andExpect(status().isNoContent());
            assertThat(defaultRecipient(m)).isEqualTo("나 고침");
            assertThat(defaults(m)).isEqualTo(1);
            String notDefault = list(m).path("items").get(1).path("id").asString();
            mvc.perform(as(m, delete("/api/me/addresses/" + notDefault))).andExpect(status().isNoContent());
            assertThat(defaultRecipient(m)).isEqualTo("나 고침");
            mvc.perform(as(m, delete("/api/me/addresses/" + older))).andExpect(status().isNoContent());
            assertThat(count(m)).isZero();
            assertThat(list(m).path("items")).isEmpty();
        }

        @Test
        void 남의_배송지와_없는_ID_는_404_ADDRESS_NOT_FOUND_값은_그대로() throws Exception {
            long owner = newMember();
            long other = newMember();
            String id = add(owner, "주인", false);
            add(other, "남", false);
            for (ResultActions r : List.of(mvc.perform(as(other, put("/api/me/addresses/" + id)).content(valid("가로채기", true))),
                    mvc.perform(as(other, delete("/api/me/addresses/" + id))),
                    mvc.perform(as(other, put("/api/me/addresses/" + id + "/default"))),
                    mvc.perform(as(owner, delete("/api/me/addresses/" + ids.nextId()))),
                    mvc.perform(as(owner, put("/api/me/addresses/abc")).content(valid("x", false))))) {
                r.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ADDRESS_NOT_FOUND"));
            }
            assertThat(recipientsInOrder(owner)).containsExactly("주인");
            assertThat(recipientsInOrder(other)).as("남의 목록에 보이지 않는다").containsExactly("남");
            assertThat(defaultRecipient(other)).isEqualTo("남");
        }

        @Test
        void 한_회원_10개까지_11번째는_409_ADDRESS_BOOK_FULL() throws Exception {
            long m = newMember();
            for (int i = 0; i < 10; i++) {
                add(m, "받는이" + i, false);
            }
            mvc.perform(as(m, post("/api/me/addresses")).content(valid("열한째", false))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ADDRESS_BOOK_FULL"));
            assertThat(count(m)).isEqualTo(10);
            assertThat(defaults(m)).isEqualTo(1);
        }

        @Test
        void 동시에_더해도_10개를_넘지_않고_첫_배송지_기본도_하나() throws Exception {
            long full = newMember();
            for (int i = 0; i < 9; i++) {
                addressBook.create(full, input("앞" + i));
            }
            assertThat(race(full, 6)).as("남은 한 자리만").isEqualTo(1);
            assertThat(count(full)).isEqualTo(10);

            long empty = newMember();
            assertThat(race(empty, 6)).isEqualTo(6);
            assertThat(count(empty)).isEqualTo(6);
            assertThat(defaults(empty)).as("동시에 들어온 첫 배송지들 중 하나만 기본").isEqualTo(1);
        }

        /** {@code n} 개를 동시에 더한다 — 성공 수 */
        private int race(long member, int n) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    String name = "동시" + i;
                    Callable<Boolean> work = () -> {
                        start.await();
                        try {
                            addressBook.create(member, input(name));
                            return true;
                        } catch (AddressBookFullException e) {
                            return false;
                        }
                    };
                    results.add(pool.submit(work));
                }
                start.countDown();
                int ok = 0;
                for (Future<Boolean> f : results) {
                    ok += f.get(30, TimeUnit.SECONDS) ? 1 : 0;
                }
                return ok;
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void 받는_분_전화_주소_상세_메모는_암호문으로_저장되고_우편번호_별칭만_평문() throws Exception {
            long m = newMember();
            mvc.perform(as(m, post("/api/me/addresses")).content(body("회사", "홍길동", "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 1", "101호", "문 앞", false)))
                    .andExpect(status().isCreated());
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM member_addresses WHERE user_id = ?", m);
            for (String column : List.of("recipient_enc", "phone_enc", "address1_enc", "address2_enc", "memo_enc")) {
                assertThat((String) row.get(column)).as(column).startsWith("d" + cipher.activeVersion() + ":");
            }
            String all = row.values().toString();
            for (String plain : List.of("홍길동", "010-1234-5678", "테헤란로", "101호", "문 앞")) {
                assertThat(all).as("평문이 표에 없다").doesNotContain(plain);
            }
            assertThat(row.get("postcode")).isEqualTo("06236");
            assertThat(row.get("label")).isEqualTo("회사");
            assertThat(cipher.decrypt(AddressBookService.RECIPIENT, (String) row.get("recipient_enc"))).isEqualTo("홍길동");
            // 용도(AAD)가 주문 배송지와 달라 주문 칸으로는 풀리지 않는다
            assertThatThrownBy(() -> cipher.decrypt(GroupBuyJoinService.RECIPIENT, (String) row.get("recipient_enc"))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 데이터_키가_없으면_주소록은_503_DATA_KEY_MISSING() {
            AddressBookService keyless = new AddressBookService(store, new DataCipher("", ""), ids, clocks);
            long m = newMember();
            for (Runnable call : List.<Runnable>of(() -> keyless.list(m), () -> keyless.create(m, input("홍길동")), () -> keyless.delete(m, 1L),
                    () -> keyless.makeDefault(m, 1L), () -> keyless.update(m, 1L, input("홍길동")), () -> keyless.forOrder(m, 1L))) {
                assertThatThrownBy(call::run).isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("DATA_KEY_MISSING");
                    assertThat(e.getStatus().value()).isEqualTo(503);
                });
            }
        }

        @Test
        void 로그인하지_않으면_401() throws Exception {
            installation.markCompleted();
            MockMvc secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
            secured.perform(get("/api/me/addresses")).andExpect(status().isUnauthorized());
        }

        @Test
        void 탈퇴하면_주소록은_즉시_삭제되고_남의_주소록은_그대로() throws Exception {
            long leaving = newMember();
            WithdrawalTestSupport.withPassword(jdbc, encoder, leaving, clock.instant());
            long other = newMember();
            add(leaving, "떠나는이", false);
            add(leaving, "떠나는이2", false);
            add(other, "남는이", false);
            assertThat(withdrawals.request(leaving, WithdrawalTestSupport.plain(), "127.0.0.1").state()).isEqualTo("COMPLETED");
            assertThat(count(leaving)).isZero();
            assertThat(count(other)).isEqualTo(1);
        }
    }

    // ── 공구 주문 연결 ────────────────────────────────────────────────────────

    @Nested
    class 공구_주문 {

        @BeforeEach
        void openSales() {
            jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        }

        @AfterEach
        void closeSales() {
            jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        }

        /** 이메일 인증·본인인증(만 19세 이상)을 마친 회원 */
        private long buyer() {
            long id = newMember();
            memberFixture.verifyEmail(id);
            memberFixture.profileIfAbsent(id, "a" + Long.toString(id, 36));
            new IdentityStore(dataSource).insertVerification(id, String.format("%064x", id), "f" + String.format("%063x", id), LocalDate.of(1990, 1, 1),
                    IdentityProviderKind.FAKE, "fake:" + id, Instant.now());
            return id;
        }

        /** 모집 중 공구(선착순·즉시 발송·단품 하나·25,000원·배송비 3,000) — {공구, 단품} */
        private long[] recruiting() {
            long op = newOperator();
            String policy = "A" + Long.toString(ids.nextId(), 36).toUpperCase();
            policies.schedule(new ShippingPolicyService.PolicyInput(policy, "시험 배송", 3_000, null, 3_000, "ONE_WAY", null), op, "ip");
            long category = products.createCategory("주소록 " + ids.nextId(), null, 0, op, "ip");
            long product = products.create(new ProductDraft(category, "주소록 시험 상품", null, null, "<p>설명</p>", 30_000L, "OFFICIAL_STORE", "시험", null, List.of(),
                    List.of(new ProductDraft.SkuDraft(null, "기본", List.of(), 10_000L, true)), List.<GroupBuyDraft.ImageRef>of()), op, "ip");
            List<NoticeDraft.Value> values = catalog.current().category("OTHER_GOODS").orElseThrow().items().stream()
                    .map(i -> new NoticeDraft.Value(i.key(), "시험 값", false, null)).toList();
            notices.save(product, List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(), values)), op, "ip");
            Instant now = clock.instant();
            long gb = admin.create(new GroupBuyDraft(product, "주소록 시험 공구", 25_000, policy, 0, 100, 5, now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofDays(2)),
                    "DEMAND", "IMMEDIATE", null, 3, null, false, List.of(), List.of(), true), op, "ip");
            int version = jdbc.queryForObject("SELECT version FROM group_buys WHERE id = ?", Integer.class, gb);
            admin.publish(gb, version, true, op, "ip");
            long sku = jdbc.queryForObject("SELECT id FROM group_buy_skus WHERE group_buy_id = ? AND kind = 'MAIN'", Long.class, gb);
            return new long[] {gb, sku};
        }

        private static final String AGREE = "\"agreements\":{\"purchaseTerms\":true,\"refundPolicy\":true,\"privacy\":true}";

        /** 입력 모드 — 배송지 전체 + saveAddress */
        private String typed(long sku, long expected, String recipient, Boolean saveAddress) {
            return "{\"items\":[{\"skuId\":\"" + sku + "\",\"qty\":1}],\"address\":{\"recipient\":\"" + recipient + "\",\"phone\":\"010-2222-3333\",\"postcode\":\"04524\","
                    + "\"address1\":\"서울특별시 중구 세종대로 110\",\"address2\":\"3층\",\"memo\":\"경비실\"}," + AGREE + ",\"expectedAmount\":" + expected
                    + (saveAddress == null ? "" : ",\"saveAddress\":" + saveAddress) + "}";
        }

        /** 카드 모드 — addressId + 메모만 */
        private String picked(long sku, String addressId, String memo) {
            return "{\"items\":[{\"skuId\":\"" + sku + "\",\"qty\":1}],\"addressId\":\"" + addressId + "\",\"address\":{\"memo\":" + q(memo) + "}," + AGREE
                    + ",\"expectedAmount\":28000}";
        }

        private ResultActions order(long member, long groupBuy, String key, String content) throws Exception {
            return mvc.perform(as(member, post("/api/group-buys/" + groupBuy + "/orders")).header("Idempotency-Key", key).content(content));
        }

        private Map<String, Object> participation(long member) {
            return jdbc.queryForMap("SELECT recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc FROM group_buy_participations WHERE user_id = ?", member);
        }

        private String dec(com.community.zconfiguration.crypto.DataPurpose purpose, Object value) {
            return value == null ? null : cipher.decrypt(purpose, (String) value);
        }

        @Test
        void addressId_면_주소록에서_풀어_복사하고_메모만_이번_요청_값() throws Exception {
            long[] g = recruiting();
            long m = buyer();
            String id = add(m, "주소록 받는이", false); // 저장된 메모 「문 앞」
            order(m, g[0], "ab-" + ids.nextId(), picked(g[1], id, "이번엔 경비실")).andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(28000));
            Map<String, Object> p = participation(m);
            assertThat(dec(GroupBuyJoinService.RECIPIENT, p.get("recipient_enc"))).isEqualTo("주소록 받는이");
            assertThat(dec(GroupBuyJoinService.PHONE, p.get("phone_enc"))).isEqualTo("010-1234-5678");
            assertThat(p.get("postcode")).isEqualTo("06236");
            assertThat(dec(GroupBuyJoinService.ADDRESS1, p.get("address1_enc"))).isEqualTo("서울특별시 강남구 테헤란로 1");
            assertThat(dec(GroupBuyJoinService.ADDRESS2, p.get("address2_enc"))).isEqualTo("101호");
            assertThat(dec(GroupBuyJoinService.MEMO, p.get("memo_enc"))).isEqualTo("이번엔 경비실");
            assertThat(count(m)).as("주소록은 그대로").isEqualTo(1);

            // 메모를 비우면 주문 메모도 없다(저장된 메모를 쓰지 않는다)
            long m2 = buyer();
            String id2 = add(m2, "둘째", false);
            order(m2, g[0], "ab-" + ids.nextId(), picked(g[1], id2, "")).andExpect(status().isOk());
            assertThat(participation(m2).get("memo_enc")).isNull();

            // 주소록을 고치거나 지워도 이미 낸 주문의 배송지는 그대로(주문마다 사본)
            mvc.perform(as(m, delete("/api/me/addresses/" + id))).andExpect(status().isNoContent());
            assertThat(dec(GroupBuyJoinService.RECIPIENT, participation(m).get("recipient_enc"))).isEqualTo("주소록 받는이");
        }

        @Test
        void 남의_없는_틀린_addressId_는_400_fields_addressId_주문은_만들지_않는다() throws Exception {
            long[] g = recruiting();
            long m = buyer();
            long other = newMember();
            String others = add(other, "남의 주소", false);
            for (String bad : List.of(others, Long.toString(ids.nextId()), "abc")) {
                order(m, g[0], "ab-" + ids.nextId(), picked(g[1], bad, "")).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.addressId").exists());
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM group_buy_participations WHERE user_id = ?", Integer.class, m)).isZero();
        }

        @Test
        void saveAddress_면_주문_트랜잭션에서_저장_주소록이_비었으면_기본_멱등_재전송은_한_번() throws Exception {
            long[] g = recruiting();
            long m = buyer();
            String key = "ab-" + ids.nextId();
            String content = typed(g[1], 28_000, "새 받는이", true);
            order(m, g[0], key, content).andExpect(status().isOk());
            order(m, g[0], key, content).andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"));
            assertThat(count(m)).as("다시 보낸 요청은 업무가 돌지 않는다").isEqualTo(1);
            JsonNode item = list(m).path("items").get(0);
            assertThat(item.path("recipient").asString()).isEqualTo("새 받는이");
            assertThat(item.path("phone").asString()).isEqualTo("010-2222-3333");
            assertThat(item.path("postcode").asString()).isEqualTo("04524");
            assertThat(item.path("address1").asString()).isEqualTo("서울특별시 중구 세종대로 110");
            assertThat(item.path("address2").asString()).isEqualTo("3층");
            assertThat(item.path("memo").asString()).isEqualTo("경비실");
            assertThat(item.path("label").isNull()).isTrue();
            assertThat(item.path("isDefault").asBoolean()).isTrue();

            // 이미 있는 회원이 저장하면 기본은 그대로
            long m2 = buyer();
            add(m2, "원래 기본", false);
            order(m2, g[0], "ab-" + ids.nextId(), typed(g[1], 28_000, "추가", true)).andExpect(status().isOk());
            assertThat(count(m2)).isEqualTo(2);
            assertThat(defaultRecipient(m2)).isEqualTo("원래 기본");
        }

        @Test
        void saveAddress_가_없거나_false_면_저장하지_않는다() throws Exception {
            long[] g = recruiting();
            long a = buyer();
            long b = buyer();
            order(a, g[0], "ab-" + ids.nextId(), typed(g[1], 28_000, "저장 안 함", null)).andExpect(status().isOk());
            order(b, g[0], "ab-" + ids.nextId(), typed(g[1], 28_000, "저장 안 함", false)).andExpect(status().isOk());
            assertThat(count(a)).isZero();
            assertThat(count(b)).isZero();
        }

        @Test
        void 주소록이_10개면_저장만_건너뛰고_주문은_계속() throws Exception {
            long[] g = recruiting();
            long m = buyer();
            for (int i = 0; i < 10; i++) {
                addressBook.create(m, input("앞" + i));
            }
            order(m, g[0], "ab-" + ids.nextId(), typed(g[1], 28_000, "열한째", true)).andExpect(status().isOk()).andExpect(jsonPath("$.data.orderNo").isString());
            assertThat(count(m)).isEqualTo(10);
            assertThat(recipientsInOrder(m)).doesNotContain("열한째");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM group_buy_participations WHERE user_id = ?", Integer.class, m)).isEqualTo(1);
        }

        @Test
        void 주문이_실패하면_saveAddress_도_저장되지_않는다() throws Exception {
            long[] g = recruiting();
            long m = buyer();
            order(m, g[0], "ab-" + ids.nextId(), typed(g[1], 27_000, "실패", true)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("GROUP_BUY_AMOUNT_CHANGED"));
            assertThat(count(m)).isZero();
        }

        @Test
        void 주문_배송지도_주소록과_같은_칸_규칙() throws Exception {
            long[] g = recruiting();
            long m = buyer();
            order(m, g[0], "ab-" + ids.nextId(), typed(g[1], 28_000, "가".repeat(31), true)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields['address.recipient']").exists());
            order(m, g[0], "ab-" + ids.nextId(), typed(g[1], 28_000, "가".repeat(30), true)).andExpect(status().isOk());
        }
    }
}
