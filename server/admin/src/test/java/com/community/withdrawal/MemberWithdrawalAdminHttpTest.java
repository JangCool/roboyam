package com.community.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.permission.entity.Permission;
import com.community.withdrawal.dto.WithdrawalApplication;
import com.community.withdrawal.dto.WithdrawalApplication.GroupPlan;
import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.dto.WithdrawalOutcome;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.withdrawal.service.WithdrawalRequestWriter;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 탈퇴 관리 API(실제 관리자 앱 문맥 + PostgreSQL, member-withdrawal-contract 「관리자」) — 규칙마다 막는다.
 *
 * <ul>
 *   <li>목록 — 상태·회원 번호·신청일 기간 거르기, 정렬(모르는 칸 400), 완료 건 닉네임 null(익명), no-store, 권한 {@code member.read}.</li>
 *   <li>요약 4칸 — 오늘 0시(서비스 하루)·이번 달 1일·10일 경계. 사유 분포 — 6종 + 고르지 않음, 신청일 기간.</li>
 *   <li>상세 — 남은 일 {@code refId}·고른 것(소모임 이름·넘길 사람·DISBAND)·포기 동의·강제/대리 취소 정보·처리 이력.</li>
 *   <li>강제 탈퇴 — 권한 403 · 검증 400 · 즉시 완료(세션 끊김·감사 FORCE 돈 관련·완료 감사 행위자 운영자) · 남은 일 있으면 SCHEDULED(로그인 유지) · 선택값 고정 · 이미 진행 중/탈퇴/없는 회원.</li>
 *   <li>대리 취소 — 회원 신청만(강제 건 409) · 끝난 건 409 · 검증 400 · 신청 전 상태 복귀 · 감사(운영자·경로·메모).</li>
 *   <li>회원 목록·상세가 {@code WITHDRAWING} 을 그대로 내린다(거르기 포함). 서버가 뜰 때 {@code permissions} 표에 {@code member.withdraw} 가 들어간다.</li>
 * </ul>
 * 남은 일은 공구 전체 없이 만들 수 있는 송금 대기 환불({@code commerce_bank_transfers})로 만든다(api 탈퇴 시험과 같다). 요약은 DB 를 같이 쓰므로 전후 차이로 본다.
 */
class MemberWithdrawalAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String ROOT = "/api/admin/member-withdrawals";
    private static final String MEMO = "고객센터 문의 #1234 — 본인이 요청";

    @Autowired
    private WithdrawalRequestWriter writer;

    // ── 재료 ───────────────────────────────────────────────────────────────

    private static String memberPath(long memberId) {
        return "/api/admin/members/" + memberId + "/withdrawal";
    }

    private JsonNode data(ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    /** 송금 대기 환불(남은 일 PAYMENT_CANCEL) — 송금 ID */
    private long pendingBankTransfer(long userId) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, reason, created_at) VALUES (?, 'GROUP_BUY_REFUND', ?, ?, 10000, "
                + "'AWAITING_ACCOUNT', 'test', now())", id, id, userId);
        return id;
    }

    private void finishBankTransfer(long id) {
        jdbc.update("UPDATE commerce_bank_transfers SET status = 'SENT', sent_at = now(), account_erased_at = now() WHERE id = ?", id);
    }

    /** 회원 본인 신청(회원 탈퇴와 같은 코드) */
    private WithdrawalOutcome memberRequest(long member, String reason, Map<Long, GroupPlan> plans) {
        return writer.apply(WithdrawalApplication.byMember(member, reason, "회원이 적은 메모", WithdrawalChoices.DEFAULT, plans, WithdrawalApplication.Consent.NONE, "10.0.0.9"));
    }

    /** 공개 사이트 로그인 세션 한 줄 */
    private void session(long member) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time, principal_name) "
                + "VALUES (?, ?, ?, ?, 1800, ?, ?)", id, id, now, now, now + 1_800_000, Long.toString(member));
    }

    private int sessions(long member) {
        return jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class, Long.toString(member));
    }

    private String userStatus(long member) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, member);
    }

    private Map<String, Object> row(long withdrawalId) {
        return jdbc.queryForMap("SELECT * FROM member_withdrawals WHERE id = ?", withdrawalId);
    }

    /** 신청 줄을 직접 넣는다(요약 경계 시험 — 시각을 마음대로) */
    private long insertRow(long member, WithdrawalStatus status, Instant requestedAt, Instant completedAt) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, source, prior_status, pending_count, requested_at, completed_at, canceled_at) "
                + "VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', 0, ?, ?, ?)", id, member, status.name(), ts(requestedAt), ts(completedAt),
                status == WithdrawalStatus.CANCELED ? ts(requestedAt.plusSeconds(60)) : null);
        return id;
    }

    private List<Map<String, Object>> audits(String action, long member) {
        return jdbc.queryForList("SELECT actor_type, actor_id, detail, retention_class FROM audit_logs WHERE action = ? AND target_type = 'MEMBER' AND target_id = ? ORDER BY id",
                action, Long.toString(member));
    }

    private long board() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 992)", id, "wa" + Long.toString(id, 36), "탈퇴 관리 시험 " + (id % 1000));
        return id;
    }

    private long group(long boardId, long leader, String name, long... members) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO groups (id, name, name_key, description, board_id, join_type, capacity, status, leader_id, creator_id, member_count, requested_at, opened_at) "
                + "VALUES (?, ?, ?, '시험', ?, 'OPEN', 50, 'ACTIVE', ?, ?, ?, now(), now())", id, name, name, boardId, leader, leader, 1 + members.length);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'LEADER', 'ACTIVE', now())", id, leader);
        for (long m : members) {
            jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'MEMBER', 'ACTIVE', now())", id, m);
        }
        return id;
    }

    private String nicknameOf(long member) {
        return jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, member);
    }

    // ── 목록·요약·분포 ──────────────────────────────────────────────────────

    @Nested
    class 목록 {

        @Test
        void 상태_회원_번호_신청일로_거르고_완료_건은_닉네임이_없고_권한은_member_read() throws Exception {
            long scheduled = newMember();
            long transfer = pendingBankTransfer(scheduled);
            long scheduledId = memberRequest(scheduled, "NOT_USING", Map.of()).withdrawalId();
            long completed = newMember();
            long completedId = memberRequest(completed, "PRIVACY", Map.of()).withdrawalId();
            long canceled = newMember();
            pendingBankTransfer(canceled);
            long canceledId = memberRequest(canceled, null, Map.of()).withdrawalId();
            writer.cancel(canceled, "10.0.0.9");
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));

            // 회원 번호(정확히) — 그 회원의 줄만
            JsonNode byMember = data(mvc.perform(get(ROOT).param("memberNo", Long.toString(scheduled)).cookie(reader)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")));
            assertThat(byMember.get("total").asLong()).isEqualTo(1);
            JsonNode row = byMember.get("items").get(0);
            assertThat(row.get("id").asText()).isEqualTo(Long.toString(scheduledId));
            assertThat(row.get("status").asText()).isEqualTo("SCHEDULED");
            assertThat(row.get("source").asText()).isEqualTo("MEMBER");
            assertThat(row.get("nickname").asText()).isEqualTo(nicknameOf(scheduled));
            assertThat(row.get("pendingCount").asInt()).isEqualTo(1);
            assertThat(row.get("pendingCounts").get("PAYMENT_CANCEL").asInt()).isEqualTo(1);
            assertThat(row.get("hasPaymentHistory").asBoolean()).isFalse();
            assertThat(row.get("completedAt").isNull()).isTrue();

            // 상태 탭 + 회원 번호 — 완료 건은 닉네임 null(익명)
            JsonNode done = data(mvc.perform(get(ROOT).param("status", "COMPLETED").param("memberNo", Long.toString(completed)).cookie(reader)).andExpect(status().isOk()));
            assertThat(done.get("total").asLong()).isEqualTo(1);
            assertThat(done.get("items").get(0).get("id").asText()).isEqualTo(Long.toString(completedId));
            assertThat(done.get("items").get(0).get("nickname").isNull()).isTrue();
            assertThat(done.get("items").get(0).get("completedAt").isNull()).isFalse();
            assertThat(done.get("items").get(0).get("pendingCount").asInt()).isZero();
            assertThat(data(mvc.perform(get(ROOT).param("status", "SCHEDULED").param("memberNo", Long.toString(completed)).cookie(reader))).get("total").asLong()).isZero();

            // 취소 건은 닉네임이 있다
            JsonNode cancels = data(mvc.perform(get(ROOT).param("status", "CANCELED").param("memberNo", Long.toString(canceled)).cookie(reader)));
            assertThat(cancels.get("items").get(0).get("id").asText()).isEqualTo(Long.toString(canceledId));
            assertThat(cancels.get("items").get(0).get("nickname").asText()).isEqualTo(nicknameOf(canceled));
            assertThat(cancels.get("items").get(0).get("canceledAt").isNull()).isFalse();

            // 신청일 기간 [from, to) — 미래부터면 0건, 과거부터 미래까지면 1건
            Instant now = Instant.now();
            assertThat(data(mvc.perform(get(ROOT).param("memberNo", Long.toString(scheduled)).param("requestedFrom", now.plusSeconds(60).toString()).cookie(reader)))
                    .get("total").asLong()).isZero();
            assertThat(data(mvc.perform(get(ROOT).param("memberNo", Long.toString(scheduled)).param("requestedFrom", now.minusSeconds(600).toString())
                    .param("requestedTo", now.plusSeconds(600).toString()).cookie(reader))).get("total").asLong()).isEqualTo(1);
            // 완료일 기간은 완료 건에만 걸린다
            assertThat(data(mvc.perform(get(ROOT).param("memberNo", Long.toString(completed)).param("completedFrom", now.minusSeconds(600).toString()).cookie(reader)))
                    .get("total").asLong()).isEqualTo(1);
            assertThat(data(mvc.perform(get(ROOT).param("memberNo", Long.toString(scheduled)).param("completedFrom", now.minusSeconds(600).toString()).cookie(reader)))
                    .get("total").asLong()).isZero();

            // 틀린 값 400 fields.<칸>, 뒤집힌 기간 400
            mvc.perform(get(ROOT).param("status", "DONE").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.status").isNotEmpty());
            mvc.perform(get(ROOT).param("memberNo", "abc").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.memberNo").isNotEmpty());
            mvc.perform(get(ROOT).param("requestedFrom", now.toString()).param("requestedTo", now.minusSeconds(1).toString()).cookie(reader)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.requestedTo").isNotEmpty());
            mvc.perform(get(ROOT).param("size", "1000").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.size").isNotEmpty());

            // 권한
            mvc.perform(get(ROOT).cookie(login(operatorWith()))).andExpect(status().isForbidden());
            mvc.perform(get(ROOT + "/summary").cookie(login(operatorWith()))).andExpect(status().isForbidden());
            finishBankTransfer(transfer);
        }

        @Test
        void 정렬은_허용한_칸만_기본은_신청일_내림_예상_완료일_오름은_모르는_것이_뒤() throws Exception {
            long a = newMember();
            pendingBankTransfer(a);
            long first = memberRequest(a, null, Map.of()).withdrawalId();
            long b = newMember();
            pendingBankTransfer(b);
            long second = memberRequest(b, null, Map.of()).withdrawalId();
            // 둘째 줄에 예상 완료일을 심어 오름차순에서 앞에 오게 한다(첫째는 모름 → 뒤)
            jdbc.update("UPDATE member_withdrawals SET expected_complete_at = now() + interval '3 days' WHERE id = ?", second);
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));
            Instant from = Instant.now().minusSeconds(600);

            // 기본 정렬 — 신청일 내림(나중 신청이 먼저). 두 회원만 보려고 전체 목록에서 ID 를 거른다
            List<String> order = idsOf(data(mvc.perform(get(ROOT).param("status", "SCHEDULED").param("requestedFrom", from.toString()).param("size", "100").cookie(reader))),
                    first, second);
            assertThat(order).containsExactly(Long.toString(second), Long.toString(first));

            List<String> asc = idsOf(data(mvc.perform(get(ROOT).param("status", "SCHEDULED").param("requestedFrom", from.toString()).param("sort", "expectedCompleteAt,asc")
                    .param("size", "100").cookie(reader))), first, second);
            assertThat(asc).containsExactly(Long.toString(second), Long.toString(first));

            List<String> byRequested = idsOf(data(mvc.perform(get(ROOT).param("status", "SCHEDULED").param("requestedFrom", from.toString()).param("sort", "requestedAt,asc")
                    .param("size", "100").cookie(reader))), first, second);
            assertThat(byRequested).containsExactly(Long.toString(first), Long.toString(second));

            mvc.perform(get(ROOT).param("sort", "nickname,asc").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.sort").isNotEmpty());
            mvc.perform(get(ROOT).param("sort", "requestedAt,sideways").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.sort").isNotEmpty());
        }

        private static List<String> idsOf(JsonNode page, long... keep) {
            List<String> wanted = java.util.Arrays.stream(keep).mapToObj(Long::toString).toList();
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            page.get("items").forEach(item -> {
                if (wanted.contains(item.get("id").asText())) {
                    out.add(item.get("id").asText());
                }
            });
            return out;
        }
    }

    @Nested
    class 요약과_분포 {

        /** 사유 6종 + 고르지 않음 + 합계 = 8칸(항목 차례대로) */
        private long[] counts(Cookie reader, Instant from, Instant to) throws Exception {
            JsonNode stats = data(mvc.perform(get(ROOT + "/reasons").param("requestedFrom", from.toString()).param("requestedTo", to.toString()).cookie(reader))
                    .andExpect(status().isOk()));
            long[] out = new long[8];
            for (int i = 0; i < 7; i++) {
                out[i] = stats.get("items").get(i).get("count").asLong();
            }
            out[7] = stats.get("total").asLong();
            return out;
        }

        private long[] summary(Cookie reader) throws Exception {
            JsonNode s = data(mvc.perform(get(ROOT + "/summary").cookie(reader)).andExpect(status().isOk()));
            return new long[] {s.get("scheduled").asLong(), s.get("completedToday").asLong(), s.get("completedThisMonth").asLong(), s.get("overdue").asLong()};
        }

        @Test
        void 요약_4칸은_서비스_하루_0시_이번_달_1일_10일_경계로_센다() throws Exception {
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            LocalDate today = ServiceTime.dateOf(now);
            Instant todayStart = DashboardGadgetQuery.startOf(today);
            Instant monthStart = DashboardGadgetQuery.startOf(today.withDayOfMonth(1));
            long[] before = summary(reader);

            // 오늘 0시 정각 완료 → 오늘·이번 달 모두 +1
            insertRow(newMember(), WithdrawalStatus.COMPLETED, todayStart.minus(Duration.ofHours(1)), todayStart);
            // 오늘 0시 1초 전 완료 → 오늘 아님, 이번 달인지는 어제가 같은 달일 때만
            insertRow(newMember(), WithdrawalStatus.COMPLETED, todayStart.minus(Duration.ofHours(2)), todayStart.minusSeconds(1));
            boolean yesterdaySameMonth = !todayStart.minusSeconds(1).isBefore(monthStart);
            // 이번 달 1일 0시 1초 전 완료 → 둘 다 아님
            insertRow(newMember(), WithdrawalStatus.COMPLETED, monthStart.minus(Duration.ofHours(2)), monthStart.minusSeconds(1));
            // 진행 중 — 10일 하고 1시간 전 신청(10일 넘김) · 9일 전 신청(아직)
            insertRow(newMember(), WithdrawalStatus.SCHEDULED, now.minus(Duration.ofDays(10)).minus(Duration.ofHours(1)), null);
            insertRow(newMember(), WithdrawalStatus.SCHEDULED, now.minus(Duration.ofDays(9)), null);
            // 취소 건은 어디에도 세지 않는다
            insertRow(newMember(), WithdrawalStatus.CANCELED, now.minus(Duration.ofDays(20)), null);

            long[] after = summary(reader);
            assertThat(after[0] - before[0]).as("진행 중").isEqualTo(2);
            assertThat(after[1] - before[1]).as("오늘 완료").isEqualTo(1);
            assertThat(after[2] - before[2]).as("이번 달 완료").isEqualTo(yesterdaySameMonth ? 2 : 1);
            assertThat(after[3] - before[3]).as("10일 넘긴 진행 중").isEqualTo(1);
        }

        @Test
        void 사유_분포는_6종_차례와_고르지_않음을_0건까지_싣고_신청일_기간을_따르며_상태와_무관하다() throws Exception {
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));
            // DB 를 다른 시험과 같이 쓰므로 같은 기간의 전후 차이로 본다
            Instant from = Instant.now().minusSeconds(5);
            Instant to = Instant.now().plus(Duration.ofMinutes(5));
            long[] before = counts(reader, from, to);
            long a = newMember();
            memberRequest(a, "NOT_USING", Map.of()); // 완료
            long b = newMember();
            pendingBankTransfer(b);
            memberRequest(b, "NOT_USING", Map.of()); // 진행 중
            long c = newMember();
            pendingBankTransfer(c);
            memberRequest(c, null, Map.of()); // 고르지 않음
            writer.cancel(c, "10.0.0.9"); // 취소

            JsonNode stats = data(mvc.perform(get(ROOT + "/reasons").param("requestedFrom", from.toString()).param("requestedTo", to.toString()).cookie(reader))
                    .andExpect(status().isOk()));
            List<String> reasons = new java.util.ArrayList<>();
            stats.get("items").forEach(item -> reasons.add(item.get("reason").isNull() ? null : item.get("reason").asText()));
            assertThat(reasons).containsExactly("NOT_USING", "PRIVACY", "INCONVENIENT", "OTHER_SERVICE", "REJOIN", "OTHER", null);
            long[] after = counts(reader, from, to);
            assertThat(after[7] - before[7]).as("total").isEqualTo(3);
            assertThat(after[0] - before[0]).as("NOT_USING — 완료·진행 중 모두(상태 무관)").isEqualTo(2);
            assertThat(after[6] - before[6]).as("고르지 않음").isEqualTo(1);
            for (int i = 1; i <= 5; i++) {
                assertThat(after[i] - before[i]).as(reasons.get(i)).isZero();
            }

            // 기간 밖(먼 미래)이면 모두 0, 칸은 7개 그대로
            JsonNode empty = data(mvc.perform(get(ROOT + "/reasons").param("requestedFrom", to.toString()).param("requestedTo", to.plusSeconds(60).toString()).cookie(reader)));
            assertThat(empty.get("total").asLong()).isZero();
            assertThat(empty.get("items")).hasSize(7);
            mvc.perform(get(ROOT + "/reasons").param("requestedFrom", "yesterday").cookie(reader)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.requestedFrom").isNotEmpty());
        }
    }

    // ── 상세 ─────────────────────────────────────────────────────────────────

    @Nested
    class 상세 {

        @Test
        void 남은_일_refId_고른_것_포기_동의_이력이_있고_없는_기록은_404_회원별_최근_기록도_같다() throws Exception {
            long leader = newMember();
            long m1 = newMember();
            long boardId = board();
            long transferGroup = group(boardId, leader, "넘길모임" + (leader % 1000), m1);
            long dissolveGroup = group(boardId, leader, "해산모임" + (leader % 1000));
            long transfer = pendingBankTransfer(leader);
            Map<Long, GroupPlan> plans = new java.util.LinkedHashMap<>();
            plans.put(transferGroup, new GroupPlan("TRANSFER", m1));
            plans.put(dissolveGroup, new GroupPlan("DISSOLVE", null));
            long withdrawalId = memberRequest(leader, "INCONVENIENT", plans).withdrawalId();
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));

            JsonNode d = data(mvc.perform(get(ROOT + "/" + withdrawalId).cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
            assertThat(d.get("memberNo").asText()).isEqualTo(Long.toString(leader));
            assertThat(d.get("nickname").asText()).isEqualTo(nicknameOf(leader));
            assertThat(d.get("priorStatus").asText()).isEqualTo("ACTIVE");
            assertThat(d.get("reason").asText()).isEqualTo("INCONVENIENT");
            assertThat(d.get("reasonNote").asText()).isEqualTo("회원이 적은 메모");
            assertThat(d.get("pending")).hasSize(1);
            assertThat(d.get("pending").get(0).get("code").asText()).isEqualTo("PAYMENT_CANCEL");
            assertThat(d.get("pending").get(0).get("refId").asText()).isEqualTo(Long.toString(transfer));
            assertThat(d.get("pending").get(0).get("label").asText()).isEqualTo("#" + transfer);
            assertThat(d.get("pendingCounts").get("PAYMENT_CANCEL").asInt()).isEqualTo(1);
            assertThat(d.get("choices").get("paidPointChoice").asText()).isEqualTo("REFUND");
            assertThat(d.get("choices").get("deliveredChoice").asText()).isEqualTo("WAIT");
            assertThat(d.get("choices").get("bizChoice").asText()).isEqualTo("WAIT");
            JsonNode groups = d.get("choices").get("groupChoices");
            assertThat(groups).hasSize(2);
            assertThat(groups.get(0).get("groupId").asText()).isEqualTo(Long.toString(transferGroup));
            assertThat(groups.get(0).get("groupName").asText()).startsWith("넘길모임");
            assertThat(groups.get(0).get("choice").asText()).isEqualTo("TRANSFER");
            assertThat(groups.get(0).get("transferTo").asText()).isEqualTo(nicknameOf(m1));
            assertThat(groups.get(1).get("choice").asText()).isEqualTo("DISBAND");
            assertThat(groups.get(1).get("transferTo").isNull()).isTrue();
            assertThat(d.get("forfeit").get("paidPoints").asLong()).isZero();
            assertThat(d.get("forfeit").get("consentedAt").isNull()).isTrue();
            assertThat(d.get("rejoinUntil").isNull()).as("진행 중은 완료 때 정한다").isTrue();
            assertThat(d.get("force").isNull()).isTrue();
            assertThat(d.get("cancel").isNull()).isTrue();
            assertThat(d.get("events")).hasSize(1);
            assertThat(d.get("events").get(0).get("kind").asText()).isEqualTo("REQUESTED");
            assertThat(d.get("events").get(0).get("operatorName").isNull()).isTrue();

            // 진행 안내를 보낸 뒤 → 이력에 PROGRESS_MAILED
            jdbc.update("UPDATE member_withdrawals SET progress_notified_at = now() WHERE id = ?", withdrawalId);
            JsonNode mailed = data(mvc.perform(get(ROOT + "/" + withdrawalId).cookie(reader)));
            assertThat(mailed.get("progressNotifiedAt").isNull()).isFalse();
            List<String> kinds = new java.util.ArrayList<>();
            mailed.get("events").forEach(e -> kinds.add(e.get("kind").asText()));
            assertThat(kinds).containsExactly("REQUESTED", "PROGRESS_MAILED");

            // 회원별 최근 기록 = 같은 상세
            JsonNode mine = data(mvc.perform(get(memberPath(leader)).cookie(reader)).andExpect(status().isOk()));
            assertThat(mine.get("id").asText()).isEqualTo(Long.toString(withdrawalId));

            mvc.perform(get(ROOT + "/" + ids.nextId()).cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_FOUND"));
            mvc.perform(get(ROOT + "/abc").cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_FOUND"));
            mvc.perform(get(memberPath(newMember())).cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_FOUND"));
            mvc.perform(get(memberPath(ids.nextId())).cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
            mvc.perform(get(ROOT + "/" + withdrawalId).cookie(login(operatorWith()))).andExpect(status().isForbidden());
            finishBankTransfer(transfer);
        }

        @Test
        void 완료_건은_닉네임이_없고_재가입_가능일과_완료_이력이_있다() throws Exception {
            long member = newMember();
            WithdrawalOutcome outcome = memberRequest(member, "OTHER", Map.of());
            assertThat(outcome.completed()).isTrue();
            Cookie reader = login(operatorWith(Permission.MEMBER_READ));

            JsonNode d = data(mvc.perform(get(ROOT + "/" + outcome.withdrawalId()).cookie(reader)).andExpect(status().isOk()));
            assertThat(d.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(d.get("nickname").isNull()).isTrue();
            assertThat(Instant.parse(d.get("rejoinUntil").asText())).isEqualTo(outcome.rejoinUntil());
            assertThat(d.get("completedAt").isNull()).isFalse();
            List<String> kinds = new java.util.ArrayList<>();
            d.get("events").forEach(e -> kinds.add(e.get("kind").asText()));
            assertThat(kinds).containsExactly("REQUESTED", "COMPLETED");
            // 회원 상세 블록 — 탈퇴 완료 회원도 최근 기록을 준다
            assertThat(data(mvc.perform(get(memberPath(member)).cookie(reader)).andExpect(status().isOk())).get("status").asText()).isEqualTo("COMPLETED");
        }
    }

    // ── 강제 탈퇴 ───────────────────────────────────────────────────────────────

    @Nested
    class 강제_탈퇴 {

        private String body(String kind, String memo) {
            return "{\"reasonKind\":" + (kind == null ? "null" : "\"" + kind + "\"") + ",\"memo\":" + (memo == null ? "null" : "\"" + memo + "\"") + "}";
        }

        @Test
        void 권한이_없으면_403_이고_상태도_감사도_그대로() throws Exception {
            long member = newMember();
            Cookie reader = login(operatorWith(Permission.MEMBER_READ, Permission.MEMBER_PII));
            mvc.perform(withCsrf(post(memberPath(member)), reader).content(body("OTHER", MEMO))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
            mvc.perform(get(memberPath(member) + "/preview").cookie(reader)).andExpect(status().isForbidden());
            assertThat(userStatus(member)).isEqualTo("ACTIVE");
            assertThat(audits(AuditActions.MEMBER_WITHDRAW_FORCE, member)).isEmpty();
        }

        @Test
        void 사유_종류와_메모_5_500자를_검사하고_틀리면_400_fields() throws Exception {
            long member = newMember();
            Cookie session = login(operatorWith(Permission.MEMBER_WITHDRAW));
            mvc.perform(withCsrf(post(memberPath(member)), session).content(body("BORED", MEMO))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.reasonKind").isNotEmpty());
            mvc.perform(withCsrf(post(memberPath(member)), session).content(body("OTHER", "짧음"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.memo").isNotEmpty());
            mvc.perform(withCsrf(post(memberPath(member)), session).content(body("OTHER", "가".repeat(501)))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.memo").isNotEmpty());
            mvc.perform(withCsrf(post(memberPath(member)), session).content("{}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.reasonKind").isNotEmpty()).andExpect(jsonPath("$.error.fields.memo").isNotEmpty());
            assertThat(userStatus(member)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_withdrawals WHERE user_id = ?", Integer.class, member)).isZero();
        }

        @Test
        void 남은_일이_없으면_즉시_완료_세션이_끊기고_감사_FORCE_는_돈_관련_완료_감사_행위자는_운영자() throws Exception {
            long member = newMember();
            session(member);
            session(member);
            long operator = operatorWith(Permission.MEMBER_WITHDRAW, Permission.MEMBER_READ);
            Cookie session = login(operator);
            Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

            JsonNode preview = data(mvc.perform(get(memberPath(member) + "/preview").cookie(session)).andExpect(status().isOk()));
            assertThat(preview.get("memberNo").asText()).isEqualTo(Long.toString(member));
            assertThat(preview.get("nickname").asText()).isEqualTo(nicknameOf(member));
            assertThat(preview.get("pending")).isEmpty();
            assertThat(preview.get("hasPaymentHistory").asBoolean()).isFalse();
            assertThat(Instant.parse(preview.get("rejoinUntil").asText())).isAfter(before.plus(Duration.ofDays(29)));

            JsonNode result = data(mvc.perform(withCsrf(post(memberPath(member)), session).content(body("POLICY_VIOLATION", MEMO))).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")));
            assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(result.get("pendingCount").asInt()).isZero();
            assertThat(result.get("completedAt").isNull()).isFalse();
            long withdrawalId = result.get("withdrawalId").asLong();

            assertThat(userStatus(member)).isEqualTo("WITHDRAWN");
            assertThat(nicknameOf(member)).isNull();
            assertThat(sessions(member)).as("완료면 회원 세션을 모두 끊는다").isZero();
            Map<String, Object> row = row(withdrawalId);
            assertThat(row.get("status")).isEqualTo("COMPLETED");
            assertThat(row.get("source")).isEqualTo("OPERATOR");
            assertThat(row.get("force_reason_kind")).isEqualTo("POLICY_VIOLATION");
            assertThat(row.get("force_memo")).isEqualTo(MEMO);
            assertThat(((Number) row.get("force_operator_id")).longValue()).isEqualTo(operator);
            assertThat(row.get("reason")).isNull();
            // 선택값 고정 — 충전 환불 · 배송 기다리기 · 광고 기다리기
            assertThat(row.get("paid_point_choice")).isEqualTo("REFUND");
            assertThat(row.get("delivered_choice")).isEqualTo("WAIT");
            assertThat(row.get("biz_choice")).isEqualTo("WAIT");
            assertThat(row.get("forfeit_consented_at")).isNull();
            assertThat(row.get("rejoin_until")).isNotNull();

            List<Map<String, Object>> force = audits(AuditActions.MEMBER_WITHDRAW_FORCE, member);
            assertThat(force).hasSize(1);
            assertThat(force.get(0).get("actor_type")).isEqualTo("OPERATOR");
            assertThat(((Number) force.get(0).get("actor_id")).longValue()).isEqualTo(operator);
            assertThat(force.get(0).get("retention_class")).isEqualTo("MONEY");
            assertThat(String.valueOf(force.get(0).get("detail"))).contains("\"reasonKind\":\"POLICY_VIOLATION\"").contains("\"result\":\"COMPLETED\"").contains(MEMO);
            List<Map<String, Object>> completed = audits(AuditActions.MEMBER_WITHDRAW, member);
            assertThat(completed).hasSize(1);
            assertThat(completed.get(0).get("actor_type")).isEqualTo("OPERATOR");
            assertThat(String.valueOf(completed.get(0).get("detail"))).contains("\"forced\":true").contains("\"source\":\"OPERATOR\"");
            assertThat(audits(AuditActions.MEMBER_WITHDRAW_REQUEST, member)).as("회원 신청 감사는 남지 않는다").isEmpty();

            // 상세 — 강제 정보·FORCED 이력(운영자 이름·메모), 닉네임 null
            JsonNode d = data(mvc.perform(get(ROOT + "/" + withdrawalId).cookie(session)).andExpect(status().isOk()));
            assertThat(d.get("source").asText()).isEqualTo("OPERATOR");
            assertThat(d.get("nickname").isNull()).isTrue();
            assertThat(d.get("force").get("reasonKind").asText()).isEqualTo("POLICY_VIOLATION");
            assertThat(d.get("force").get("memo").asText()).isEqualTo(MEMO);
            assertThat(d.get("force").get("operatorName").asText()).isEqualTo(operatorName(operator));
            assertThat(d.get("events").get(0).get("kind").asText()).isEqualTo("FORCED");
            assertThat(d.get("events").get(0).get("operatorName").asText()).isEqualTo(operatorName(operator));
            assertThat(d.get("events").get(0).get("note").asText()).isEqualTo(MEMO);
            assertThat(d.get("events").get(1).get("kind").asText()).isEqualTo("COMPLETED");

            // 이미 탈퇴한 회원 → 409, 미리보기도 409
            mvc.perform(withCsrf(post(memberPath(member)), session).content(body("OTHER", MEMO))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("MEMBER_ALREADY_WITHDRAWN"));
            mvc.perform(get(memberPath(member) + "/preview").cookie(session)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MEMBER_ALREADY_WITHDRAWN"));
            // 없는 회원 → 404
            mvc.perform(withCsrf(post(memberPath(ids.nextId())), session).content(body("OTHER", MEMO))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
        }

        @Test
        void 남은_일이_있으면_운영자도_건너뛰지_못하고_진행_중_로그인은_유지되고_이미_진행_중이면_409_강제_건은_대리_취소_불가() throws Exception {
            long member = newMember();
            long transfer = pendingBankTransfer(member);
            session(member);
            long operator = operatorWith(Permission.MEMBER_WITHDRAW, Permission.MEMBER_READ);
            Cookie session = login(operator);

            JsonNode preview = data(mvc.perform(get(memberPath(member) + "/preview").cookie(session)).andExpect(status().isOk()));
            assertThat(preview.get("pending")).hasSize(1);
            assertThat(preview.get("pending").get(0).get("refId").asText()).isEqualTo(Long.toString(transfer));

            JsonNode result = data(mvc.perform(withCsrf(post(memberPath(member)), session).content(body("ON_BEHALF", MEMO))).andExpect(status().isOk()));
            assertThat(result.get("status").asText()).isEqualTo("SCHEDULED");
            assertThat(result.get("pendingCount").asInt()).isEqualTo(1);
            assertThat(result.get("completedAt").isNull()).isTrue();
            long withdrawalId = result.get("withdrawalId").asLong();
            assertThat(userStatus(member)).isEqualTo("WITHDRAWING");
            assertThat(sessions(member)).as("진행 중이면 로그인은 유지된다(남은 일 처리·취소 요청)").isEqualTo(1);
            assertThat(nicknameOf(member)).isNotNull();
            assertThat(String.valueOf(audits(AuditActions.MEMBER_WITHDRAW_FORCE, member).get(0).get("detail"))).contains("\"result\":\"SCHEDULED\"");

            // 이미 진행 중 → 409(강제·미리보기 모두)
            mvc.perform(withCsrf(post(memberPath(member)), session).content(body("OTHER", MEMO))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("WITHDRAWAL_ALREADY_SCHEDULED"));
            mvc.perform(get(memberPath(member) + "/preview").cookie(session)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("WITHDRAWAL_ALREADY_SCHEDULED"));

            // 강제 건은 대리 취소할 수 없다
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session)
                            .content("{\"requestChannel\":\"PHONE\",\"memo\":\"" + MEMO + "\",\"memberConfirmed\":true}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_MEMBER_REQUESTED"));
            assertThat(userStatus(member)).isEqualTo("WITHDRAWING");

            // 회원 목록·상세가 WITHDRAWING 을 그대로 내린다(거르기 포함)
            JsonNode members = data(mvc.perform(get("/api/admin/members").param("statuses", "WITHDRAWING").param("searchField", "MEMBER_NO")
                    .param("keyword", Long.toString(member)).cookie(session)).andExpect(status().isOk()));
            assertThat(members.get("total").asLong()).isEqualTo(1);
            assertThat(members.get("items").get(0).get("status").asText()).isEqualTo("WITHDRAWING");
            assertThat(data(mvc.perform(get("/api/admin/members/" + member).cookie(session)).andExpect(status().isOk())).get("status").asText()).isEqualTo("WITHDRAWING");
            assertThat(data(mvc.perform(get("/api/admin/members").param("statuses", "ACTIVE").param("searchField", "MEMBER_NO").param("keyword", Long.toString(member))
                    .cookie(session))).get("total").asLong()).isZero();
            finishBankTransfer(transfer);
        }
    }

    // ── 대리 취소 ───────────────────────────────────────────────────────────────

    @Nested
    class 대리_취소 {

        private String body(String channel, String memo, String confirmed) {
            return "{\"requestChannel\":" + (channel == null ? "null" : "\"" + channel + "\"") + ",\"memo\":" + (memo == null ? "null" : "\"" + memo + "\"")
                    + ",\"memberConfirmed\":" + confirmed + "}";
        }

        @Test
        void 회원이_신청한_진행_중_탈퇴를_신청_전_상태로_되돌리고_감사에_운영자_경로_메모가_남는다() throws Exception {
            long member = newMember();
            jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member); // 정지 중 탈퇴 → 취소하면 정지로 돌아간다
            long transfer = pendingBankTransfer(member);
            long withdrawalId = memberRequest(member, "REJOIN", Map.of()).withdrawalId();
            assertThat(userStatus(member)).isEqualTo("WITHDRAWING");
            long operator = operatorWith(Permission.MEMBER_WITHDRAW, Permission.MEMBER_READ);
            Cookie session = login(operator);

            JsonNode d = data(mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content(body("CS_INQUIRY", MEMO, "true"))).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")));
            assertThat(d.get("status").asText()).isEqualTo("CANCELED");
            assertThat(d.get("source").asText()).isEqualTo("MEMBER");
            assertThat(d.get("nickname").asText()).isEqualTo(nicknameOf(member));
            assertThat(d.get("canceledAt").isNull()).isFalse();
            assertThat(d.get("pendingCount").asInt()).as("취소 때의 스냅샷(기다리던 일)").isEqualTo(1);
            assertThat(d.get("cancel").get("requestChannel").asText()).isEqualTo("CS_INQUIRY");
            assertThat(d.get("cancel").get("memo").asText()).isEqualTo(MEMO);
            assertThat(d.get("cancel").get("operatorName").asText()).isEqualTo(operatorName(operator));
            List<String> kinds = new java.util.ArrayList<>();
            d.get("events").forEach(e -> kinds.add(e.get("kind").asText()));
            assertThat(kinds).containsExactly("REQUESTED", "OPERATOR_CANCELED");
            assertThat(d.get("events").get(1).get("operatorName").asText()).isEqualTo(operatorName(operator));
            assertThat(d.get("events").get(1).get("note").asText()).isEqualTo(MEMO);

            assertThat(userStatus(member)).as("신청 전 상태로").isEqualTo("SUSPENDED");
            Map<String, Object> row = row(withdrawalId);
            assertThat(row.get("status")).isEqualTo("CANCELED");
            assertThat(row.get("cancel_channel")).isEqualTo("CS_INQUIRY");
            assertThat(((Number) row.get("cancel_operator_id")).longValue()).isEqualTo(operator);
            List<Map<String, Object>> cancels = audits(AuditActions.MEMBER_WITHDRAW_CANCEL, member);
            assertThat(cancels).hasSize(1);
            assertThat(cancels.get(0).get("actor_type")).isEqualTo("OPERATOR");
            assertThat(((Number) cancels.get(0).get("actor_id")).longValue()).isEqualTo(operator);
            assertThat(String.valueOf(cancels.get(0).get("detail"))).contains("\"requestChannel\":\"CS_INQUIRY\"").contains("\"onBehalf\":true").contains(MEMO);

            // 끝난 건은 409
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content(body("PHONE", MEMO, "true"))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_SCHEDULED"));
            finishBankTransfer(transfer);
        }

        @Test
        void 완료된_건은_409_없는_건은_404_권한_없으면_403() throws Exception {
            long member = newMember();
            long withdrawalId = memberRequest(member, null, Map.of()).withdrawalId();
            Cookie session = login(operatorWith(Permission.MEMBER_WITHDRAW));
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content(body("EMAIL", MEMO, "true"))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_SCHEDULED"));
            mvc.perform(withCsrf(post(ROOT + "/" + ids.nextId() + "/cancel"), session).content(body("EMAIL", MEMO, "true"))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("WITHDRAWAL_NOT_FOUND"));
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), login(operatorWith(Permission.MEMBER_READ))).content(body("EMAIL", MEMO, "true")))
                    .andExpect(status().isForbidden());
            assertThat(userStatus(member)).isEqualTo("WITHDRAWN");
        }

        @Test
        void 요청_경로_메모_본인_확인을_검사하고_틀리면_400_상태는_그대로() throws Exception {
            long member = newMember();
            long transfer = pendingBankTransfer(member);
            long withdrawalId = memberRequest(member, null, Map.of()).withdrawalId();
            Cookie session = login(operatorWith(Permission.MEMBER_WITHDRAW));

            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content(body("FAX", MEMO, "true"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.requestChannel").isNotEmpty());
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content(body("PHONE", "짧음", "true"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.memo").isNotEmpty());
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content(body("PHONE", MEMO, "false"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.memberConfirmed").isNotEmpty());
            mvc.perform(withCsrf(post(ROOT + "/" + withdrawalId + "/cancel"), session).content("{}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.requestChannel").isNotEmpty()).andExpect(jsonPath("$.error.fields.memo").isNotEmpty())
                    .andExpect(jsonPath("$.error.fields.memberConfirmed").isNotEmpty());
            assertThat(userStatus(member)).isEqualTo("WITHDRAWING");
            assertThat(row(withdrawalId).get("status")).isEqualTo("SCHEDULED");
            assertThat(audits(AuditActions.MEMBER_WITHDRAW_CANCEL, member)).isEmpty();
            finishBankTransfer(transfer);
        }
    }

    @Test
    void 서버가_뜰_때_permissions_표에_member_withdraw_가_민감_조치_기능_있음으로_들어간다() {
        Map<String, Object> row = jdbc.queryForMap("SELECT axis, type_id, category, feature_ready FROM permissions WHERE code = 'member.withdraw'");
        assertThat(row.get("axis")).isEqualTo("OPERATOR");
        assertThat(row.get("type_id")).isEqualTo("memberView");
        assertThat(row.get("category")).isEqualTo("SENSITIVE");
        assertThat(row.get("feature_ready")).isEqualTo(true);
    }
}
