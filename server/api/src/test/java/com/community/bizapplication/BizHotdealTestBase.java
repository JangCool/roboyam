package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.service.AdOperatorService;
import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.entity.BatchRequestKind;
import com.community.bizapplication.batch.BizHotdealPublishJob;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.service.BizHotdealPublisher;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.MyBizApplicationService;
import com.community.hotdeal.service.DealRuleBook;
import com.community.permission.entity.Permission;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 핫딜 게시(2차 — biz-hotdeal-contract 규칙 1~11) 시험의 바탕 — 예약된 핫딜 글 건 만들기 · 시계를 기간 안으로 · 게시 배치 · 운영자 · 링크 규칙.
 */
abstract class BizHotdealTestBase extends BizApplicationApiTestBase {

    @Autowired
    protected BizHotdealPublisher publisher;
    @Autowired
    protected BizHotdealPublishJob publishJob;
    @Autowired
    protected MyBizApplicationService view;
    @Autowired
    protected AdOperatorService ads;
    @Autowired
    protected DealRuleBook rules;

    private final List<String> domains = new ArrayList<>();

    @AfterEach
    void cleanDomains() {
        domains.forEach(d -> jdbc.update("DELETE FROM deal_domains WHERE domain = ?", d));
        rules.invalidate();
    }

    /** 예약된 건 — 사업자 · 신청 건 · 기간 */
    protected record Booked(long user, long id, BizPeriods.Period period) {
    }

    /** 사업자 핫딜 글 신청 → 먼 미래 기간으로 승인(온라인) → 결제(지금 — 시작 전). 글은 아직 없다 */
    protected Booked booked(String placement) {
        return booked(placement, deal(placement));
    }

    protected Booked booked(String placement, BizDealInfo deal) {
        return booked(approvedBusiness(), placement, deal);
    }

    protected Booked booked(long user, String placement, BizDealInfo deal) {
        LocalDate start = today().plusDays(2);
        boolean none = "NONE".equals(placement);
        long id = members.submitDeal(user, new BizDealApplyRequest("[시험상사] 텀블러 1+1", deal, none ? null : start.toString(), none ? null : start.plusDays(1).toString(),
                "ONLINE", POLICY, POLICY));
        BizPeriods.Period p = freshPeriod(3);
        approve(id, "ONLINE", p);
        pay(user, id);
        return new Booked(user, id, p);
    }

    /** 시계를 기간 시작 + {@code hours} 로 */
    protected void into(Booked b, long hours) {
        clock.set(b.period().start().plus(Duration.ofHours(hours)));
    }

    /** 예약 → 시작 1시간 뒤 → 게시(배치 한 건) — 글 ID */
    protected long posted(String placement) {
        Booked b = booked(placement);
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        return postOf(b.id());
    }

    protected Booked postedBooking(String placement) {
        Booked b = booked(placement);
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        return b;
    }

    protected long postOf(long applicationId) {
        return jdbc.queryForObject("SELECT post_id FROM biz_deal_posts WHERE application_id = ?", Long.class, applicationId);
    }

    protected Map<String, Object> result(long applicationId) {
        return jdbc.queryForMap("SELECT * FROM biz_deal_posts WHERE application_id = ?", applicationId);
    }

    protected int resultCount(long applicationId) {
        return jdbc.queryForObject("SELECT count(*) FROM biz_deal_posts WHERE application_id = ?", Integer.class, applicationId);
    }

    protected int businessPosts(long applicationId) {
        return jdbc.queryForObject("SELECT count(*) FROM post_deals WHERE biz_application_id = ?", Integer.class, applicationId);
    }

    protected Map<String, Object> dealRow(long postId) {
        return jdbc.queryForMap("SELECT * FROM post_deals WHERE post_id = ?", postId);
    }

    protected Map<String, Object> postRow(long postId) {
        return jdbc.queryForMap("SELECT * FROM posts WHERE id = ?", postId);
    }

    /** 처리 기록 action 들(차례대로) */
    protected List<String> events(long applicationId) {
        return jdbc.queryForList("SELECT action FROM biz_application_events WHERE application_id = ? ORDER BY created_at, id", String.class, applicationId);
    }

    protected String eventNote(long applicationId, String action) {
        List<String> notes = jdbc.queryForList("SELECT note FROM biz_application_events WHERE application_id = ? AND action = ? ORDER BY id DESC", String.class,
                applicationId, action);
        return notes.isEmpty() ? null : notes.get(0);
    }

    protected int refundRows(long applicationId) {
        return jdbc.queryForObject("SELECT count(*) FROM biz_refunds WHERE application_id = ?", Integer.class, applicationId);
    }

    /** 사업자 알림(BIZ_APPLICATION 덧붙임 kind) 수 */
    protected int memberNotices(long user, String kind) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'BIZ_APPLICATION' AND payload->>'kind' = ?", Integer.class, user,
                kind);
    }

    protected int adminNotices(long operatorId, String event) {
        return jdbc.queryForObject("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = ?", Integer.class, operatorId, event);
    }

    /** 그 권한을 가진 활성 운영자(공개 사이트 문맥이라 권한 표가 없으면 코드 값으로 넣는다) */
    protected long operatorWith(Permission... permissions) {
        long id = newOperator();
        String roleId = "ROLE_ZZ_BHD_" + id;
        jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name) VALUES (?, 'op_general', 'OPERATOR', ?)", roleId, "bhd-" + (id % 1_000_000_000L));
        for (Permission permission : permissions) {
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at) VALUES (?, 'OPERATOR', ?, ?, ?, now()) ON CONFLICT (code) DO NOTHING",
                    permission.code(), permission.type().id(), permission.unit().name(), permission.featureReady());
            jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?)", ids.nextId(), roleId, permission.code());
        }
        jdbc.update("INSERT INTO operator_roles (id, operator_id, role_id) VALUES (?, ?, ?)", ids.nextId(), id, roleId);
        return id;
    }

    /** 링크 규칙 한 줄 차단(시험이 끝나면 지운다) */
    protected void block(String domain) {
        domains.add(domain);
        jdbc.update("INSERT INTO deal_domains (id, domain, rule, shortener, created_at, updated_at) VALUES (?, ?, 'BLOCK', false, now(), now())", ids.nextId(), domain);
        rules.invalidate();
    }

    /** 겹치지 않는 상품 호스트의 핫딜 정보 */
    protected BizDealInfo dealAt(String host, String placement) {
        BizDealInfo d = deal(placement);
        return new BizDealInfo("https://" + host + "/products/1", d.shopName(), d.listPrice(), d.dealPrice(), d.shippingFee(), d.endsOn(), d.stock(), d.body(),
                d.placement(), d.boardId(), d.prefixId(), d.imageFileId(), null);
    }

    protected String uniqueHost() {
        return "s" + Long.toString(ids.nextId(), 36) + ".bizdeal-test.example";
    }

    protected String boardSlug(long boardId) {
        return jdbc.queryForObject("SELECT slug FROM boards WHERE id = ?", String.class, boardId);
    }

    /** 게시 배치를 「지금 실행」으로 한 번(운영과 같은 실행기 — OK·SKIP) */
    protected void runPublishJob() {
        BatchRunner runner = new BatchRunner(new BatchLockService(dataSource), new BatchRunRecorder(dataSource, transactions, ids, 954),
                new BatchFailureRecorder(dataSource, transactions, ids), BatchRegistry.forJobs(List.of(publishJob)), transactions);
        BatchRunner.RunReport report = runner.runRequested(new BatchRunner.RunRequest(publishJob.definition(), BatchRequestKind.NOW, null, BatchParameters.of(Map.of()),
                null, null, null));
        assertThat(report.runId()).as(String.valueOf(report)).isNotNull();
        Map<String, Object> row = jdbc.queryForMap("SELECT status, error_message FROM batch_runs WHERE id = ?", report.runId());
        assertThat(row.get("status")).as(String.valueOf(row)).isIn("OK", "SKIP");
    }
}
