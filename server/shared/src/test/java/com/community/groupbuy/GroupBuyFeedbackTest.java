package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.feedback.FeedbackException;
import com.community.groupbuy.feedback.FeedbackView;
import com.community.groupbuy.feedback.GroupBuyUgcReportService;
import com.community.groupbuy.feedback.PersonalInfo;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 Q&A·리뷰(2b-6 — 계약 「Q&A 규칙」·「리뷰 규칙」, 테스트 G11·G11R).
 * Q&A: 쓰기 자격(정지·중단 공구)·본문 길이·쓰기 간격·개인정보 자동 비밀글·비밀글 열람·답변 전후 수정·지워도 보관·숨김(사유·감사·신고 처리)·열람 감사·답변 수정 이력·3년 파기.
 * 리뷰: 산 회원·배송완료 뒤 90일·줄마다 하나(지워도)·모두 취소·반품 줄 거절·별점·글·사진 검증·30일 수정·이력·숨김 사유 코드만·평균에서 빠짐·같은 상품 다른 차수 묶음·신고.
 */
class GroupBuyFeedbackTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private static String code(Throwable e) {
        return ((CommonException) e).getErrorCode();
    }

    private long openGroupBuy() {
        return kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(20_000).open();
    }

    private void nextWrite() {
        kit.advance(Duration.ofSeconds(31));
    }

    // ── Q&A ──────────────────────────────────────────────────────────────

    @Test
    void 질문은_정상_회원만_쓰고_정지_회원은_403_중단된_공구는_409() {
        long gb = openGroupBuy();
        long member = kit.newMember();
        FeedbackView.Question q = kit.qna.ask(member, Long.toString(gb), "  20kg 는 한 포대인가요?  ", false);
        assertThat(q.body()).as("앞뒤 공백은 자른다").isEqualTo("20kg 는 한 포대인가요?");
        assertThat(q.status()).isEqualTo("OPEN");
        assertThat(kit.operatorNotices).as("새 질문은 운영자 알림").contains(AdminNotificationEvent.COMMERCE_QNA_RECEIVED);

        long suspended = kit.newMember();
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        assertThatThrownBy(() -> kit.qna.ask(suspended, Long.toString(gb), "질문이에요", false)).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.ACCOUNT_RESTRICTED));
        assertThat(kit.qna.list(Long.toString(gb), suspended, false, 0).askBlock()).isEqualTo("RESTRICTED");
        assertThat(kit.qna.list(Long.toString(gb), null, false, 0).askBlock()).isEqualTo("LOGIN_REQUIRED");

        kit.admin.cancel(gb, "공급처 품절", kit.operator, "ip");
        nextWrite();
        assertThatThrownBy(() -> kit.qna.ask(member, Long.toString(gb), "환불은 언제?", false)).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.QNA_CLOSED));
        assertThat(kit.qna.list(Long.toString(gb), member, false, 0).askBlock()).isEqualTo("CLOSED");
    }

    @Test
    void 본문은_1에서_1000자이고_같은_회원은_30초에_한_번() {
        long gb = openGroupBuy();
        long member = kit.newMember();
        assertThatThrownBy(() -> kit.qna.ask(member, Long.toString(gb), "   ", false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.qna.ask(member, Long.toString(gb), "가".repeat(1001), false)).isInstanceOf(ValidationException.class);
        kit.qna.ask(member, Long.toString(gb), "가".repeat(1000), false);
        assertThatThrownBy(() -> kit.qna.ask(member, Long.toString(gb), "또 물어요", false)).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.QUESTION_TOO_FAST));
        nextWrite();
        kit.qna.ask(member, Long.toString(gb), "또 물어요", false);
    }

    @Test
    void 연락처_이메일_주소가_보이면_비밀글로_바꾸고_남에게는_본문을_주지_않는다() {
        assertThat(PersonalInfo.looksPersonal("제 번호는 010-1234-5678 이에요")).isTrue();
        assertThat(PersonalInfo.looksPersonal("메일 a.b@example.com")).isTrue();
        assertThat(PersonalInfo.looksPersonal("강남구 테헤란로 123")).isTrue();
        assertThat(PersonalInfo.looksPersonal("20kg 는 한 포대인가요?")).isFalse();

        long gb = openGroupBuy();
        long author = kit.newMember();
        long other = kit.newMember();
        FeedbackView.Question q = kit.qna.ask(author, Long.toString(gb), "받는 사람 010 1234 5678 로 바꿔 주세요", false);
        assertThat(q.secret()).isTrue();
        assertThat(q.forcedSecret()).isTrue();

        FeedbackView.Question seen = kit.qna.list(Long.toString(gb), other, false, 0).items().get(0);
        assertThat(seen.body()).as("남의 비밀글 본문은 비운다").isNull();
        assertThat(seen.actions().report()).as("볼 수 없는 글은 신고하지 못한다").isFalse();
        assertThat(kit.qna.list(Long.toString(gb), null, false, 0).items().get(0).body()).isNull();
        assertThat(kit.qna.list(Long.toString(gb), author, false, 0).items().get(0).body()).as("작성자는 본다").contains("010 1234 5678");
        assertThat(kit.qna.adminDetail(q.id(), kit.operator, "ip").body()).as("운영자는 본다").contains("010 1234 5678");
        assertThatThrownBy(() -> kit.ugcReports.report(GroupBuyUgcReportService.QUESTION, Long.parseLong(q.id()), other, "SPAM_AD", null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REPORT_TARGET_NOT_FOUND));
    }

    @Test
    void 답변_전에는_고치고_지우고_답변_뒤에는_비밀글로_바꾸기와_지우기만_지워도_보관한다() {
        long gb = openGroupBuy();
        long author = kit.newMember();
        FeedbackView.Question q = kit.qna.ask(author, Long.toString(gb), "현미도 하나요?", false);
        FeedbackView.Question edited = kit.qna.update(author, q.id(), "현미·흑미도 하나요?", false);
        assertThat(edited.body()).isEqualTo("현미·흑미도 하나요?");
        assertThat(edited.updatedAt()).isNotNull();
        assertThatThrownBy(() -> kit.qna.update(kit.newMember(), q.id(), "남의 글", false)).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.QUESTION_NOT_FOUND));

        kit.qna.answer(q.id(), "이번엔 백미만 해요.", kit.operator, "ip");
        FeedbackView.Question answered = kit.qna.list(Long.toString(gb), author, false, 0).items().get(0);
        assertThat(answered.status()).isEqualTo("ANSWERED");
        assertThat(answered.actions().edit()).isFalse();
        assertThat(answered.actions().makeSecret()).isTrue();
        assertThatThrownBy(() -> kit.qna.update(author, q.id(), "고쳐요", false)).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.QUESTION_ANSWERED));
        assertThat(kit.qna.makeSecret(author, q.id()).secret()).isTrue();

        kit.qna.remove(author, q.id());
        assertThat(kit.qna.list(Long.toString(gb), author, false, 0).items()).as("회원 목록에서 빠진다").isEmpty();
        assertThat(kit.count("SELECT count(*) FROM group_buy_questions WHERE id = ? AND deleted_at IS NOT NULL", Long.parseLong(q.id()))).as("행은 남는다").isEqualTo(1);
        assertThat(kit.qna.adminList("ALL", false, null, 0, 100, kit.operator, "ip").items()).anySatisfy(r -> {
            assertThat(r.id()).isEqualTo(q.id());
            assertThat(r.deleted()).isTrue();
        });
        assertThatThrownBy(() -> kit.qna.answer(q.id(), "다시 답해요", kit.operator, "ip")).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.QUESTION_DELETED));
    }

    @Test
    void 첫_답변은_작성자_알림_답변을_고치면_이전_내용은_이력과_감사_보관_만료일은_마지막_답변일부터_3년() {
        long gb = openGroupBuy();
        long author = kit.newMember();
        FeedbackView.Question q = kit.qna.ask(author, Long.toString(gb), "도정일은요?", false);
        long qid = Long.parseLong(q.id());
        kit.qna.answer(q.id(), "마감 다음 날 도정해요.", kit.operator, "ip");
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND payload::text LIKE '%QNA_ANSWERED%'", author)).isEqualTo(1);

        kit.advance(Duration.ofDays(10));
        FeedbackView.QnaDetail d = kit.qna.answer(q.id(), "마감 다음 날 도정해서 바로 보내요.", kit.operator, "ip");
        assertThat(d.revisions()).hasSize(1);
        assertThat(d.revisions().get(0).body()).isEqualTo("마감 다음 날 도정해요.");
        assertThat(d.answer().updatedAt()).isNotNull();
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_QNA_ANSWER_EDIT));
        assertThat(d.retainUntil()).isEqualTo(com.community.zconfiguration.time.ServiceTime.dateOf(kit.now()).plusYears(3));
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND payload::text LIKE '%QNA_ANSWERED%'", author)).as("고친 답변은 다시 알리지 않는다")
                .isEqualTo(1);

        kit.qna.answer(q.id(), "마감 다음 날 도정해서 바로 보내요.", kit.operator, "ip");
        assertThat(kit.count("SELECT count(*) FROM group_buy_answer_revisions WHERE question_id = ?", qid)).as("같은 내용이면 이력을 만들지 않는다").isEqualTo(1);
    }

    @Test
    void 숨김은_사유_5에서_200자_감사_남에게는_자리_글만_열린_신고는_처리된다_열람은_감사() {
        long gb = openGroupBuy();
        long author = kit.newMember();
        long reporter = kit.newMember();
        FeedbackView.Question q = kit.qna.ask(author, Long.toString(gb), "여기보다 다른 데가 싸요", false);
        kit.ugcReports.report(GroupBuyUgcReportService.QUESTION, Long.parseLong(q.id()), reporter, "SPAM_AD", null);
        assertThat(kit.operatorNotices).contains(AdminNotificationEvent.COMMERCE_QNA_REPORTED);
        assertThatThrownBy(() -> kit.ugcReports.report(GroupBuyUgcReportService.QUESTION, Long.parseLong(q.id()), reporter, "SPAM_AD", null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.ALREADY_REPORTED));
        assertThatThrownBy(() -> kit.ugcReports.report(GroupBuyUgcReportService.QUESTION, Long.parseLong(q.id()), author, "SPAM_AD", null))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.CANNOT_REPORT_OWN));
        assertThat(kit.qna.adminDetail(q.id(), kit.operator, "ip").reports()).extracting(FeedbackView.ReportCount::reason).containsExactly("SPAM_AD");

        assertThatThrownBy(() -> kit.qna.hide(q.id(), "광고", kit.operator, "ip")).isInstanceOf(ValidationException.class);
        FeedbackView.QnaDetail hidden = kit.qna.hide(q.id(), "다른 판매처 홍보", kit.operator, "ip");
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.reports()).as("숨기면 열린 신고는 처리").isEmpty();
        assertThat(kit.count("SELECT count(*) FROM group_buy_ugc_reports WHERE target_id = ? AND status = 'RESOLVED'", Long.parseLong(q.id()))).isEqualTo(1);
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_QNA_HIDE));

        FeedbackView.Question seen = kit.qna.list(Long.toString(gb), reporter, false, 0).items().get(0);
        assertThat(seen.hidden()).isTrue();
        assertThat(seen.body()).isNull();
        assertThat(kit.qna.list(Long.toString(gb), author, false, 0).items().get(0).body()).as("작성자에게는 본문").isNotNull();

        kit.audits.clear();
        kit.qna.adminList("HIDDEN", false, null, 0, 20, kit.operator, "ip");
        kit.qna.adminDetail(q.id(), kit.operator, "ip");
        assertThat(kit.audits).filteredOn(e -> AuditActions.GROUPBUY_QNA_VIEW.equals(e.action())).hasSize(2);

        assertThat(kit.qna.unhide(q.id(), "오해였어요 — 다시 공개", kit.operator, "ip").hidden()).isFalse();
    }

    @Test
    void 답변_대기는_2영업일이_지나면_기한_지남() {
        long gb = openGroupBuy();
        FeedbackView.Question q = kit.qna.ask(kit.newMember(), Long.toString(gb), "배송비는요?", false);
        kit.advance(Duration.ofDays(5));
        FeedbackView.QnaList list = kit.qna.adminList("OPEN", true, null, 0, 100, kit.operator, "ip");
        assertThat(list.items()).extracting(FeedbackView.QnaRow::id).contains(q.id());
        assertThat(list.overdueCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 보관_만료일이_지난_질문은_파기_배치가_지운다() {
        long gb = openGroupBuy();
        FeedbackView.Question old = kit.qna.ask(kit.newMember(), Long.toString(gb), "오래된 문의", false);
        FeedbackView.Question kept = kit.qna.ask(kit.newMember(), Long.toString(gb), "남는 문의", false);
        kit.qna.answer(old.id(), "답변", kit.operator, "ip");
        kit.jdbc.update("UPDATE group_buy_questions SET retain_until = current_date - 1 WHERE id = ?", Long.parseLong(old.id()));
        kit.jdbc.update("UPDATE group_buy_questions SET retain_until = current_date + 10 WHERE id = ?", Long.parseLong(kept.id()));

        kit.jdbc.execute("CALL batch_groupbuy_privacy_cleanup('MANUAL', NULL, NULL)");

        assertThat(kit.count("SELECT count(*) FROM group_buy_questions WHERE id = ?", Long.parseLong(old.id()))).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_answers WHERE question_id = ?", Long.parseLong(old.id()))).as("답변도 함께").isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_questions WHERE id = ?", Long.parseLong(kept.id()))).isEqualTo(1);
    }

    // ── 리뷰 ─────────────────────────────────────────────────────────────

    private record Bought(long groupBuyId, long userId, JoinResult order, long itemId) {
    }

    private Bought deliveredOrder(long gb) {
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 1));
        return new Bought(gb, member, order, kit.items(order.participationId()).get(0).id());
    }

    @Test
    void 리뷰는_배송완료된_줄만_줄마다_하나_지워도_다시_못_쓰고_90일이_지나면_못_쓴다() {
        long gb = openGroupBuy();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        String item = Long.toString(kit.items(paid.participationId()).get(0).id());
        assertThat(kit.reviews.targets(member, paid.orderNo())).extracting(FeedbackView.ReviewTarget::state).containsExactly("NONE");
        assertThatThrownBy(() -> kit.reviews.create(member, paid.orderNo(), item, 5, "아직 안 받았지만 좋아요", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_NOT_WRITABLE));
        kit.ship(paid.participationId());
        kit.deliver(paid.participationId());
        assertThat(kit.reviews.targets(member, paid.orderNo())).extracting(FeedbackView.ReviewTarget::state).containsExactly("WRITABLE");

        assertThatThrownBy(() -> kit.reviews.create(kit.newMember(), paid.orderNo(), item, 5, "남의 주문이에요 열 자", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.ORDER_NOT_FOUND));
        FeedbackView.Review r = kit.reviews.create(member, paid.orderNo(), item, 5, "밥맛이 정말 좋아요!", List.of());
        assertThat(r.skuName()).as("산 단품 이름 사본").isNotBlank();
        assertThat(kit.reviews.targets(member, paid.orderNo())).extracting(FeedbackView.ReviewTarget::state).containsExactly("WRITTEN");
        assertThatThrownBy(() -> kit.reviews.create(member, paid.orderNo(), item, 4, "한 번 더 쓰고 싶어요", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_EXISTS));
        kit.reviews.remove(member, r.id());
        assertThatThrownBy(() -> kit.reviews.create(member, paid.orderNo(), item, 4, "지우고 다시 쓸래요", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_EXISTS));
        assertThat(kit.reviews.targets(member, paid.orderNo())).extracting(FeedbackView.ReviewTarget::state).as("지운 줄은 대상 아님").containsExactly("NONE");

        Bought late = deliveredOrder(gb);
        kit.advance(Duration.ofDays(91));
        assertThat(kit.reviews.targets(late.userId(), late.order().orderNo())).extracting(FeedbackView.ReviewTarget::state).containsExactly("EXPIRED");
        assertThatThrownBy(() -> kit.reviews.create(late.userId(), late.order().orderNo(), Long.toString(late.itemId()), 5, "늦게 써요 열 자 넘게", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_NOT_WRITABLE));
    }

    @Test
    void 모두_취소_반품한_줄은_리뷰를_못_쓴다() {
        long gb = openGroupBuy();
        Bought b = deliveredOrder(gb);
        kit.jdbc.update("UPDATE group_buy_participation_items SET returned_qty = qty WHERE id = ?", b.itemId());
        assertThat(kit.reviews.targets(b.userId(), b.order().orderNo())).extracting(FeedbackView.ReviewTarget::state).containsExactly("NONE");
        assertThatThrownBy(() -> kit.reviews.create(b.userId(), b.order().orderNo(), Long.toString(b.itemId()), 1, "반품했어요 열 자 넘게", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_NOT_WRITABLE));
    }

    @Test
    void 별점_1에서_5_글_10에서_1000자_사진_5장까지_남의_사진은_붙이지_못한다() {
        long gb = openGroupBuy();
        Bought b = deliveredOrder(gb);
        String no = b.order().orderNo();
        String item = Long.toString(b.itemId());
        assertThatThrownBy(() -> kit.reviews.create(b.userId(), no, item, 0, "별점이 없어요 열 자", List.of())).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("rating"));
        assertThatThrownBy(() -> kit.reviews.create(b.userId(), no, item, 5, "짧아요", List.of())).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("body"));
        List<String> six = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(kit.reviews.uploadPhoto(b.userId(), new byte[] {1}).id());
        }
        assertThatThrownBy(() -> kit.reviews.create(b.userId(), no, item, 5, "사진이 너무 많아요", six)).isInstanceOf(ValidationException.class);
        String others = kit.reviews.uploadPhoto(kit.newMember(), new byte[] {1}).id();
        assertThatThrownBy(() -> kit.reviews.create(b.userId(), no, item, 5, "남의 사진을 붙여요", List.of(others))).isInstanceOf(ValidationException.class);

        FeedbackView.Review r = kit.reviews.create(b.userId(), no, item, 4, "사진 두 장 붙였어요", six.subList(0, 2));
        assertThat(r.photos()).extracting(FeedbackView.Photo::id).containsExactly(six.get(0), six.get(1));
    }

    @Test
    void 작성_30일_안에만_고치고_이전_내용은_이력으로() {
        long gb = openGroupBuy();
        Bought b = deliveredOrder(gb);
        String photo = kit.reviews.uploadPhoto(b.userId(), new byte[] {1}).id();
        FeedbackView.Review r = kit.reviews.create(b.userId(), b.order().orderNo(), Long.toString(b.itemId()), 2, "배송이 늦었어요 아쉬워요", List.of(photo));
        kit.advance(Duration.ofDays(3));
        FeedbackView.Review edited = kit.reviews.update(b.userId(), r.id(), 4, "늦었지만 쌀은 좋아요 만족", List.of());
        assertThat(edited.rating()).isEqualTo(4);
        assertThat(edited.photos()).as("뺀 사진은 떨어진다").isEmpty();
        FeedbackView.ReviewDetail d = kit.reviews.adminDetail(r.id());
        assertThat(d.revisions()).hasSize(1);
        assertThat(d.revisions().get(0).rating()).isEqualTo(2);
        assertThat(d.revisions().get(0).photoCount()).isEqualTo(1);

        assertThatThrownBy(() -> kit.reviews.update(kit.newMember(), r.id(), 5, "남의 리뷰를 고쳐요", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_NOT_FOUND));
        kit.advance(Duration.ofDays(28));
        assertThatThrownBy(() -> kit.reviews.update(b.userId(), r.id(), 5, "한 달 뒤에 고쳐요", List.of()))
                .satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_EDIT_EXPIRED));
        kit.reviews.remove(b.userId(), r.id());
    }

    @Test
    void 숨김은_사유_코드로만_낮은_별점은_사유가_아니고_숨긴_리뷰는_평균과_수에서_빠지고_작성자에게만_사유와_함께() {
        long gb = openGroupBuy();
        Bought low = deliveredOrder(gb);
        Bought high = deliveredOrder(gb);
        FeedbackView.Review bad = kit.reviews.create(low.userId(), low.order().orderNo(), Long.toString(low.itemId()), 1, "벌레가 나왔어요 실망이에요", List.of());
        kit.reviews.create(high.userId(), high.order().orderNo(), Long.toString(high.itemId()), 5, "정말 맛있어요 또 살게요", List.of());
        long viewer = kit.newMember();
        assertThat(kit.reviews.list(Long.toString(gb), viewer, "LATEST", false, 0).summary().average()).isEqualTo(3.0);

        assertThatThrownBy(() -> kit.reviews.hide(bad.id(), "LOW_RATING", null, kit.operator, "ip")).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("reasonCode"));
        assertThatThrownBy(() -> kit.reviews.hide(bad.id(), "OTHER", "짧", kit.operator, "ip")).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("memo"));

        kit.ugcReports.report(GroupBuyUgcReportService.REVIEW, Long.parseLong(bad.id()), viewer, "ABUSE", null);
        assertThat(kit.reviews.adminList("REPORTED", null, false, false, null, 0, 20).items()).extracting(FeedbackView.ReviewRow::id).contains(bad.id());
        FeedbackView.ReviewDetail hidden = kit.reviews.hide(bad.id(), "PRIVACY", "본문에 전화번호", kit.operator, "ip");
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.reports()).isEmpty();
        assertThat(kit.audits).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_REVIEW_HIDE);
            assertThat(e.detail()).containsEntry("reasonCode", "PRIVACY");
        });

        FeedbackView.ReviewPage seen = kit.reviews.list(Long.toString(gb), viewer, "LATEST", false, 0);
        assertThat(seen.summary().count()).isEqualTo(1);
        assertThat(seen.summary().average()).isEqualTo(5.0);
        assertThat(seen.items()).extracting(FeedbackView.Review::id).doesNotContain(bad.id());
        FeedbackView.Review mine = kit.reviews.list(Long.toString(gb), low.userId(), "LATEST", false, 0).items().stream().filter(r -> r.id().equals(bad.id())).findFirst()
                .orElseThrow();
        assertThat(mine.hidden()).isEqualTo(new FeedbackView.Hidden("PRIVACY", "본문에 전화번호"));

        assertThatThrownBy(() -> kit.reviews.unhide(bad.id(), "짧", kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThat(kit.reviews.unhide(bad.id(), "개인정보를 지웠어요", kit.operator, "ip").hidden()).isFalse();
    }

    @Test
    void 같은_상품의_다른_차수_리뷰도_함께_보이고_목록_카드는_평균과_수() {
        long first = openGroupBuy();
        long product = kit.productOf(first);
        long second = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).product(product).price(21_000).open();
        Bought b = deliveredOrder(first);
        kit.reviews.create(b.userId(), b.order().orderNo(), Long.toString(b.itemId()), 4, "1차에 샀는데 좋아요", List.of());

        FeedbackView.ReviewPage onSecond = kit.reviews.list(Long.toString(second), null, "LATEST", false, 0);
        assertThat(onSecond.items()).hasSize(1);
        assertThat(onSecond.summary().count()).isEqualTo(1);
        assertThat(kit.reviews.stats(List.of(product))).containsEntry(product, new FeedbackView.Stats(4.0, 1));
    }

    @Test
    void 첫_답글은_작성자_알림_답글을_고치면_감사_지운_리뷰에는_답글_불가() {
        long gb = openGroupBuy();
        Bought b = deliveredOrder(gb);
        FeedbackView.Review r = kit.reviews.create(b.userId(), b.order().orderNo(), Long.toString(b.itemId()), 3, "보통이에요 그럭저럭", List.of());
        kit.reviews.reply(r.id(), "의견 감사해요.", kit.operator, "ip");
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND payload::text LIKE '%REVIEW_REPLIED%'", b.userId())).isEqualTo(1);
        kit.reviews.reply(r.id(), "의견 감사해요. 다음엔 더 좋은 쌀로!", kit.operator, "ip");
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_REVIEW_REPLY_EDIT));
        assertThat(kit.reviews.list(Long.toString(gb), null, "LATEST", false, 0).items().get(0).reply().edited()).isTrue();

        kit.reviews.remove(b.userId(), r.id());
        assertThatThrownBy(() -> kit.reviews.reply(r.id(), "지운 뒤 답글", kit.operator, "ip")).satisfies(e -> assertThat(code(e)).isEqualTo(FeedbackException.REVIEW_DELETED));
    }
}
