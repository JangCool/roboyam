package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonCreatorBlockedException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.exception.EmoticonPendingLimitException;
import com.community.emoticon.exception.EmoticonUploadLimitException;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * 작가 스튜디오 · 심사(emoticon-contract 가정 7·8·9, S1) — 실제 PostgreSQL. 임시 저장 · 심사 요청(새 판 — 신청할 때마다 판, 판이 바뀌어도 같은 로보티콘은 같은 ID) · 잠금 · 심사 대기 상한(동시 요청 포함)
 * · 유료 희망(유료 판매 꺼짐 400 · 정산 정보 등록 전 409 — 유료 조건 전체는 {@code EmoticonPaidTest}) · 자격 · 승인·반려·새 판 승인·반려·판매 중단 팩의 새 판 · 작가 알림·운영자 알림·감사.
 */
class EmoticonStudioReviewTest extends EmoticonTestBase {

    private static Map<String, ?> fields(Executable call) {
        try {
            call.execute();
        } catch (ValidationException e) {
            return e.getFields();
        } catch (Throwable e) {
            throw new AssertionError("400 VALIDATION 이 아니다: " + e, e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private static String code(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getErrorCode();
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private EmoticonStudioView.Pack save(long creator, String id, EmoticonStudioSaveRequest body) {
        return kit.inTx(() -> kit.studio.save(creator, id, body));
    }

    private EmoticonStudioView.Pack submit(long creator, String id, EmoticonStudioSaveRequest body) {
        return kit.inTx(() -> kit.studio.submit(creator, id, body));
    }

    private static EmoticonStudioSaveRequest with(EmoticonStudioSaveRequest b, String name, List<EmoticonStudioSaveRequest.Item> items, String cover) {
        return new EmoticonStudioSaveRequest(name, b.description(), b.tags(), cover, items, b.saleWish(), b.priceRequestMemo(), b.agreeOwnership(), b.agreeLicense(),
                b.agreePolicy(), null);
    }

    private static List<EmoticonStudioSaveRequest.Item> items(List<ImageRow> images) {
        List<EmoticonStudioSaveRequest.Item> out = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            out.add(new EmoticonStudioSaveRequest.Item(Long.toString(images.get(i).id()), "표" + (i + 1)));
        }
        return out;
    }

    @Test
    void 새_팩_임시_저장은_이름이_비어도_되고_DRAFT_로_남는다_반려된_팩을_고치면_다시_DRAFT() {
        long creator = kit.member();
        EmoticonStudioView.Pack draft = save(creator, null, new EmoticonStudioSaveRequest("", "", List.of(), null, List.of(), "FREE", null, null, null, null, null));
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.count()).isZero();
        assertThat(kit.count("SELECT count(*) FROM emoticon_pack_revisions WHERE pack_id = ? AND version IS NULL", Long.parseLong(draft.id()))).isEqualTo(1);
        // 다시 저장해도 임시 판은 하나
        List<ImageRow> images = kit.uploads(creator, 3);
        EmoticonStudioView.Pack again = save(creator, draft.id(), EmoticonTestKit.body("임시 팩", null, images));
        assertThat(again.count()).isEqualTo(3);
        assertThat(again.name()).isEqualTo("임시 팩");
        assertThat(kit.count("SELECT count(*) FROM emoticon_pack_revisions WHERE pack_id = ?", Long.parseLong(draft.id()))).isEqualTo(1);
        // 상점·카탈로그에는 나오지 않는다
        assertThat(kit.shop.catalog().catalog().items()).extracting(EmoticonPublicView.CatalogItem::packId).doesNotContain(draft.id());
        assertThatThrownBy(() -> kit.shop.pack(creator, draft.id())).isInstanceOf(EmoticonPackNotFoundException.class);
    }

    @Test
    void 칸_검사는_목업_차례로_모은다() {
        long creator = kit.member();
        long other = kit.member();
        ImageRow mine = kit.upload(creator);
        ImageRow theirs = kit.upload(other);
        ImageRow cover = kit.uploadCover(creator);
        EmoticonStudioSaveRequest base = EmoticonTestKit.body("좋은 이름", cover, List.of(mine));
        assertThat(fields(() -> save(creator, null, with(base, "가", base.emoticons(), base.coverImageId())))).containsKey("name");
        assertThat(fields(() -> save(creator, null, with(base, "이름!!", base.emoticons(), base.coverImageId())))).containsKey("name");
        assertThat(fields(() -> save(creator, null, new EmoticonStudioSaveRequest("좋은 이름", "", List.of("a", "b", "c", "d", "e", "f"), null, List.of(), "FREE", null, null,
                null, null, null)))).containsKey("tags");
        assertThat(fields(() -> save(creator, null, new EmoticonStudioSaveRequest("좋은 이름", "", List.of("같음", "#같음"), null, List.of(), "FREE", null, null, null, null,
                null)))).containsKey("tags");
        assertThat(fields(() -> save(creator, null, new EmoticonStudioSaveRequest("좋은 이름", "가".repeat(201), List.of(), null, List.of(), "FREE", null, null, null, null,
                null)))).containsKey("description");
        assertThat(fields(() -> save(creator, null, new EmoticonStudioSaveRequest("좋은 이름", "", List.of(), null, List.of(), "FREE", "메".repeat(201), null, null, null,
                null)))).containsKey("priceRequestMemo");
        assertThat(fields(() -> save(creator, null, with(base, "좋은 이름", List.of(new EmoticonStudioSaveRequest.Item(Long.toString(mine.id()), "열한글자가넘는긴이름표")),
                null)))).containsKey("emoticons[0].label");
        List<EmoticonStudioSaveRequest.Item> many = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            many.add(new EmoticonStudioSaveRequest.Item(Long.toString(mine.id()), "표"));
        }
        assertThat(fields(() -> save(creator, null, with(base, "좋은 이름", many, null)))).containsKey("emoticons");
        // 남이 올린 그림 · 같은 그림 두 번 · 로보티콘 그림을 대표로
        assertThat(fields(() -> save(creator, null, with(base, "좋은 이름", items(List.of(theirs)), null))).get("emoticons")).hasFieldOrPropertyWithValue("key",
                "emoticon.studio.image.unknown");
        assertThat(fields(() -> save(creator, null, with(base, "좋은 이름", items(List.of(mine, mine)), null))).get("emoticons")).hasFieldOrPropertyWithValue("key",
                "emoticon.studio.image.duplicate");
        assertThat(fields(() -> save(creator, null, with(base, "좋은 이름", items(List.of(mine)), Long.toString(mine.id()))))).containsKey("coverImageId");
        // 심사 요청은 더 본다 — 대표 필수 · 최소 개수 · 이름표 1자 이상 · 동의 셋 · 판매 희망 값
        Map<String, ?> submit = fields(() -> submit(creator, null, new EmoticonStudioSaveRequest("좋은 이름", "", List.of(), null,
                List.of(new EmoticonStudioSaveRequest.Item(Long.toString(mine.id()), " ")), "MAYBE", null, false, null, true, null)));
        assertThat(submit.keySet()).containsExactly("emoticons[0].label", "emoticons", "coverImageId", "saleWish", "agreeOwnership", "agreeLicense");
    }

    @Test
    void 심사_요청하면_판_1_을_내고_심사_건_FIRST_대기_팩은_PENDING_운영자_알림_그동안_고치지_못한다() {
        long creator = kit.member();
        EmoticonStudioView.Pack pack = kit.submitNew(creator, "처음 신청");
        long packId = Long.parseLong(pack.id());
        assertThat(pack.status()).isEqualTo("PENDING");
        Map<String, Object> review = kit.jdbc.queryForMap("SELECT r.id, r.kind, r.status, r.consent_version, v.version, v.submitted_at FROM emoticon_reviews r "
                + "JOIN emoticon_pack_revisions v ON v.id = r.revision_id WHERE r.pack_id = ?", packId);
        assertThat(review.get("kind")).isEqualTo("FIRST");
        assertThat(review.get("status")).isEqualTo("PENDING");
        assertThat(review.get("version")).isEqualTo(1);
        assertThat(review.get("submitted_at")).isNotNull();
        assertThat(review.get("consent_version")).isEqualTo("2026-11-05");
        assertThat(kit.operatorNotices).hasSize(1);
        assertThat(kit.operatorNotices.get(0).getKey()).isEqualTo(AdminNotificationEvent.EMOTICON_REVIEW_PENDING);
        assertThat(kit.operatorNotices.get(0).getValue().linkArgs()).containsEntry("reviewId", review.get("id").toString());
        assertThat(AdminNotificationEvent.EMOTICON_REVIEW_PENDING.linkPath()).isEqualTo("/robotikon/reviews?id={reviewId}");

        EmoticonStudioSaveRequest again = EmoticonTestKit.body("다시 고침", kit.uploadCover(creator), kit.uploads(creator, 8));
        assertThat(code(() -> save(creator, pack.id(), again))).isEqualTo(EmoticonConflictException.PACK_LOCKED);
        assertThat(code(() -> submit(creator, pack.id(), again))).isEqualTo(EmoticonConflictException.PACK_LOCKED);
        assertThat(kit.studio.overview(creator).pendingCount()).isEqualTo(1);
    }

    @Test
    void 유료_판매_희망은_유료_판매가_꺼져_있으면_400_정산_정보가_없으면_409() {
        long creator = kit.member();
        EmoticonStudioSaveRequest b = EmoticonTestKit.body("유료 희망", kit.uploadCover(creator), kit.uploads(creator, 8));
        EmoticonStudioSaveRequest paid = new EmoticonStudioSaveRequest(b.name(), b.description(), b.tags(), b.coverImageId(), b.emoticons(), "PAID", null, true, true, true, 1);
        kit.withPaid(false, List.of(1500, 2500, 3500), () -> assertThat(fields(() -> submit(creator, null, paid))).containsKey("saleWish"));
        kit.withPaid(() -> assertThat(code(() -> submit(creator, null, paid))).isEqualTo(EmoticonConflictException.PAYOUT_REQUIRED));
        // 임시 저장은 된다(판매 희망만 남긴다 — 유료 판매가 꺼져 있어도)
        assertThat(save(creator, null, paid).saleWish()).isEqualTo("PAID");
    }

    @Test
    void 심사_대기는_한_사람_3개까지_새_판_심사도_센다() {
        long creator = kit.member();
        long approved = kit.approvedPack(creator);
        kit.submitNew(creator, "대기 하나");
        kit.submitNew(creator, "대기 둘");
        submit(creator, Long.toString(approved), EmoticonTestKit.body("새 판", kit.uploadCover(creator), kit.uploads(creator, 8))); // 새 판 대기 = 세 번째
        assertThatThrownBy(() -> kit.submitNew(creator, "대기 넷")).isInstanceOfSatisfying(EmoticonPendingLimitException.class,
                e -> assertThat(e.getMessageRef().args()).containsExactly(3));
        // 다른 작가는 상관없다
        kit.submitNew(kit.member(), "다른 작가");
    }

    @Test
    void 동시에_심사_요청해도_상한을_넘지_않는다() throws Exception {
        long creator = kit.member();
        kit.submitNew(creator, "대기 하나");
        kit.submitNew(creator, "대기 둘");
        List<EmoticonStudioSaveRequest> bodies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            bodies.add(EmoticonTestKit.body("동시 신청 " + i, kit.uploadCover(creator), kit.uploads(creator, 8)));
        }
        List<Callable<Object>> jobs = new ArrayList<>();
        for (EmoticonStudioSaveRequest body : bodies) {
            jobs.add(() -> submit(creator, null, body));
        }
        List<Object> results = EmoticonTestKit.runTogether(jobs);
        assertThat(results.stream().filter(r -> r instanceof EmoticonStudioView.Pack)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof EmoticonPendingLimitException)).hasSize(2);
        assertThat(kit.count("SELECT count(*) FROM emoticon_reviews WHERE creator_id = ? AND status = 'PENDING'", creator)).isEqualTo(3);
    }

    @Test
    void 승인하면_판매_중_상점_카탈로그에_나오고_작가_알림_감사() {
        long creator = kit.member();
        long packId = Long.parseLong(kit.submitNew(creator, "승인 시험").id());
        long reviewId = kit.pendingReview(packId);
        kit.inTx(() -> kit.reviews.approve(kit.operator, "10.0.0.1", Long.toString(reviewId), new EmoticonReviewDecisionRequest(0, null)));
        assertThat(kit.status(packId)).isEqualTo("APPROVED");
        assertThat(kit.shop.catalog().catalog().items()).filteredOn(i -> i.packId().equals(Long.toString(packId))).hasSize(8);
        EmoticonPublicView.PackDetail detail = kit.shop.pack(null, Long.toString(packId));
        assertThat(detail.saleState()).isEqualTo("ON_SALE");
        assertThat(detail.name()).isEqualTo("승인 시험");
        assertThat(detail.creator().memberId()).isEqualTo(Long.toString(creator));
        assertThat(detail.price()).isZero();
        assertThat(kit.audits).extracting(a -> a.action()).contains("EMOTICON_REVIEW_APPROVE");
        assertThat(kit.lastNotice(creator)).containsEntry("type", "EMOTICON_PACK").containsEntry("kind", "APPROVED").containsEntry("pack_id", Long.toString(packId));
        // 이미 처리된 건 · 없는 건
        assertThat(code(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, Long.toString(reviewId), new EmoticonReviewDecisionRequest(0, null)))))
                .isEqualTo(EmoticonConflictException.REVIEW_NOT_PENDING);
        assertThat(code(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, "x", new EmoticonReviewDecisionRequest(0, null))))).isEqualTo("EMOTICON_REVIEW_NOT_FOUND");
    }

    @Test
    void 무료_신청의_승인_가격은_0_만_가격_없음도_400_반려_사유는_5_200자() {
        long creator = kit.member();
        long packId = Long.parseLong(kit.submitNew(creator, "가격 시험").id());
        String review = Long.toString(kit.pendingReview(packId));
        assertThat(fields(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, review, new EmoticonReviewDecisionRequest(1500, null))))).containsKey("price");
        assertThat(fields(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, review, new EmoticonReviewDecisionRequest(null, null))))).containsKey("price");
        assertThat(fields(() -> kit.inTx(() -> kit.reviews.reject(kit.operator, null, review, new EmoticonReviewDecisionRequest(null, "짧음"))))).containsKey("reason");
        assertThat(fields(() -> kit.inTx(() -> kit.reviews.reject(kit.operator, null, review, new EmoticonReviewDecisionRequest(null, "가".repeat(201)))))).containsKey("reason");
        assertThat(kit.status(packId)).isEqualTo("PENDING");
    }

    @Test
    void 처음_신청을_반려하면_팩이_반려_사유가_작가에게_보이고_고쳐_다시_내면_판_2() {
        long creator = kit.member();
        long packId = Long.parseLong(kit.submitNew(creator, "반려 시험").id());
        String review = Long.toString(kit.pendingReview(packId));
        kit.inTx(() -> kit.reviews.reject(kit.operator, null, review, new EmoticonReviewDecisionRequest(null, "  상표가 들어 있어요  ")));
        EmoticonStudioView.Pack rejected = kit.studio.pack(creator, Long.toString(packId));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.rejectReason()).isEqualTo("상표가 들어 있어요");
        assertThat(kit.lastNotice(creator)).containsEntry("kind", "REJECTED").containsEntry("note", "상표가 들어 있어요");
        EmoticonStudioSaveRequest fixed = EmoticonTestKit.body("반려 시험 고침", kit.uploadCover(creator), kit.uploads(creator, 8));
        assertThat(save(creator, Long.toString(packId), fixed).status()).isEqualTo("DRAFT");
        EmoticonStudioView.Pack resubmitted = submit(creator, Long.toString(packId), fixed);
        assertThat(resubmitted.status()).isEqualTo("PENDING");
        assertThat(kit.jdbc.queryForList("SELECT v.version FROM emoticon_reviews r JOIN emoticon_pack_revisions v ON v.id = r.revision_id WHERE r.pack_id = ? "
                + "ORDER BY r.submitted_at, r.id", Integer.class, packId)).containsExactly(1, 2);
        // 반려된 건의 상세는 그 판 그대로(처리가 끝난 건도 비교가 보인다)
        EmoticonAdminView.ReviewDetail old = kit.reviews.detail(review);
        assertThat(old.status()).isEqualTo("REJECTED");
        assertThat(old.next().name()).isEqualTo("반려 시험");
        assertThat(old.decision().reason()).isEqualTo("상표가 들어 있어요");
        assertThat(old.consents()).extracting(EmoticonAdminView.ConsentRecord::kind).containsExactly("ORIGINAL", "LICENSE", "POLICY");
    }

    @Test
    void 판매_중인_팩의_새_판_로보티콘_ID_는_그대로_새_그림만_새_ID_승인_전에는_지금_판이_나온다() {
        long creator = kit.member();
        ImageRow cover = kit.uploadCover(creator);
        List<ImageRow> images = kit.uploads(creator, 8);
        long packId = Long.parseLong(submit(creator, null, EmoticonTestKit.body("판 시험", cover, images)).id());
        kit.approve(packId);
        List<Long> v1 = kit.liveEmoticons(packId);
        assertThat(code(() -> save(creator, Long.toString(packId), EmoticonTestKit.body("판 시험", cover, images)))).as("판매 중 팩은 임시 저장이 없다")
                .isEqualTo(EmoticonConflictException.PACK_LOCKED);

        // 새 판: 차례를 뒤집고 1번째를 빼고 새 그림 하나 + 같은 파일을 다시 올린 그림(지문이 같다 — 2번째 로보티콘)
        ImageRow fresh = kit.upload(creator);
        ImageRow sameFile = reupload(creator, images.get(1));
        List<ImageRow> next = new ArrayList<>();
        for (int i = images.size() - 1; i >= 2; i--) {
            next.add(images.get(i));
        }
        next.add(sameFile);
        next.add(fresh);
        EmoticonStudioView.Pack pending = submit(creator, Long.toString(packId), EmoticonTestKit.body("판 시험 둘", cover, next));
        assertThat(pending.status()).isEqualTo("APPROVED");
        assertThat(pending.revision().status()).isEqualTo("PENDING");
        assertThat(pending.name()).as("스튜디오는 낸 새 판을 보인다").isEqualTo("판 시험 둘");
        assertThat(kit.liveEmoticons(packId)).as("승인 전에는 상점 판 그대로").isEqualTo(v1);
        assertThat(kit.shop.pack(null, Long.toString(packId)).name()).isEqualTo("판 시험");
        assertThat(kit.reviews.list("UPDATE").items()).extracting(EmoticonAdminView.ReviewRow::packId).contains(Long.toString(packId));

        kit.approve(packId);
        List<Long> v2 = kit.liveEmoticons(packId);
        List<Long> expected = new ArrayList<>();
        for (int i = 7; i >= 2; i--) {
            expected.add(v1.get(i));
        }
        expected.add(v1.get(1)); // 같은 파일을 다시 올려도 같은 로보티콘
        assertThat(v2.subList(0, 7)).isEqualTo(expected);
        assertThat(v1).doesNotContain(v2.get(7)); // 새 그림은 새 ID
        assertThat(kit.shop.pack(null, Long.toString(packId)).name()).isEqualTo("판 시험 둘");
        // 뺀 1번째 로보티콘은 카탈로그에서 빠진다(이미 쓴 토큰은 글자로 남는다)
        assertThat(kit.shop.catalog().catalog().items()).extracting(EmoticonPublicView.CatalogItem::id).doesNotContain(Long.toString(v1.get(0)))
                .contains(Long.toString(v1.get(7)));
        assertThat(kit.lastNotice(creator)).containsEntry("kind", "UPDATE_APPROVED");
        // 승인된 새 판 심사 건의 상세 — 신청 순간의 상점 판(v1)과 새 판(v2) 비교
        long reviewId = kit.jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ? AND kind = 'UPDATE'", Long.class, packId);
        EmoticonAdminView.ReviewDetail detail = kit.reviews.detail(Long.toString(reviewId));
        assertThat(detail.kind()).isEqualTo("UPDATE");
        assertThat(detail.current().version()).isEqualTo(1);
        assertThat(detail.next().version()).isEqualTo(2);
        assertThat(detail.current().items()).extracting(EmoticonAdminView.ItemView::id).containsExactlyElementsOf(v1.stream().map(String::valueOf).toList());
        assertThat(detail.next().items().get(6).contentHash()).isEqualTo(detail.current().items().get(1).contentHash());
    }

    /** 같은 파일을 다시 올린다(새 그림 ID · 같은 지문) */
    private ImageRow reupload(long creator, ImageRow original) {
        String hash = original.contentHash();
        // 시험 그림은 글자로 지문을 가르므로 같은 글자의 GIF 를 다시 만들 수 없다 — 원본 바이트를 저장소에서 읽는 대신 같은 지문의 줄을 장부에 하나 더 넣는다
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, storage_key, uploaded_by_user, created_at) "
                + "SELECT ?, kind, format, width, height, bytes, frames, loop_forever, content_hash, storage_key || '.copy', ?, now() FROM emoticon_images WHERE id = ?", id, creator,
                original.id());
        return new ImageRow(id, original.kind(), original.format(), original.width(), original.height(), original.bytes(), original.frames(), original.loopForever(), hash,
                original.storageKey() + ".copy", null, creator, null, original.createdAt());
    }

    @Test
    void 새_판을_반려하면_지금_판이_그대로_나오고_스튜디오에_반려_사유() {
        long creator = kit.member();
        long packId = kit.approvedPack(creator);
        List<Long> v1 = kit.liveEmoticons(packId);
        submit(creator, Long.toString(packId), EmoticonTestKit.body("새 판 반려", kit.uploadCover(creator), kit.uploads(creator, 8)));
        kit.inTx(() -> kit.reviews.reject(kit.operator, null, Long.toString(kit.pendingReview(packId)), new EmoticonReviewDecisionRequest(null, "그림이 흐려요 다시")));
        assertThat(kit.status(packId)).isEqualTo("APPROVED");
        assertThat(kit.liveEmoticons(packId)).isEqualTo(v1);
        EmoticonStudioView.Pack pack = kit.studio.pack(creator, Long.toString(packId));
        assertThat(pack.revision().status()).isEqualTo("REJECTED");
        assertThat(pack.revision().rejectReason()).isEqualTo("그림이 흐려요 다시");
        assertThat(pack.rejectReason()).isNull();
        assertThat(kit.lastNotice(creator)).containsEntry("kind", "UPDATE_REJECTED").containsEntry("note", "그림이 흐려요 다시");
        // 반려된 새 판은 다시 고쳐 낼 수 있다
        assertThat(submit(creator, Long.toString(packId), EmoticonTestKit.body("새 판 다시", kit.uploadCover(creator), kit.uploads(creator, 8))).revision().status())
                .isEqualTo("PENDING");
    }

    @Test
    void 판매_중인_팩의_새_판은_판매_희망을_바꾸지_않는다() {
        long creator = kit.member();
        long packId = kit.approvedPack(creator);
        EmoticonStudioSaveRequest b = EmoticonTestKit.body("희망 그대로", kit.uploadCover(creator), kit.uploads(creator, 8));
        EmoticonStudioSaveRequest paid = new EmoticonStudioSaveRequest(b.name(), b.description(), b.tags(), b.coverImageId(), b.emoticons(), "PAID", "3,500원으로 바꿔 주세요",
                true, true, true, null);
        EmoticonStudioView.Pack pack = submit(creator, Long.toString(packId), paid);
        assertThat(pack.saleWish()).isEqualTo("FREE");
        assertThat(pack.priceRequestMemo()).isEqualTo("3,500원으로 바꿔 주세요");
    }

    @Test
    void 판매_중단_팩은_임시_저장_뒤_새_판으로_다시_내고_승인되면_다시_판매() {
        long creator = kit.member();
        long packId = kit.approvedPack(creator);
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(packId), "다른 작가 그림과 비슷해요"));
        assertThat(kit.lastNotice(creator)).containsEntry("kind", "SUSPENDED").containsEntry("note", "다른 작가 그림과 비슷해요");
        EmoticonStudioSaveRequest fixed = EmoticonTestKit.body("다시 그림", kit.uploadCover(creator), kit.uploads(creator, 8));
        assertThat(save(creator, Long.toString(packId), fixed).status()).isEqualTo("SUSPENDED");
        EmoticonStudioView.Pack pending = submit(creator, Long.toString(packId), fixed);
        assertThat(pending.status()).isEqualTo("SUSPENDED");
        assertThat(pending.revision().status()).isEqualTo("PENDING");
        assertThat(kit.shop.catalog().catalog().items()).as("판매 중단 동안에도 카탈로그에 남는다").anyMatch(i -> i.packId().equals(Long.toString(packId)));
        kit.approve(packId);
        assertThat(kit.status(packId)).isEqualTo("APPROVED");
        assertThat(kit.studio.pack(creator, Long.toString(packId)).suspendReason()).isNull();
    }

    @Test
    void 자격_정지_이메일_미인증은_403_남의_팩은_404() {
        long creator = kit.member();
        long unverified = kit.member();
        kit.unverify(unverified);
        long suspended = kit.member();
        kit.suspend(suspended);
        EmoticonStudioSaveRequest empty = new EmoticonStudioSaveRequest("", "", List.of(), null, List.of(), "FREE", null, null, null, null, null);
        assertThat(code(() -> save(unverified, null, empty))).isEqualTo(EmoticonCreatorBlockedException.EMAIL_NOT_VERIFIED);
        assertThat(code(() -> save(suspended, null, empty))).isEqualTo(EmoticonCreatorBlockedException.ACCOUNT_RESTRICTED);
        assertThat(code(() -> submit(suspended, null, empty))).isEqualTo(EmoticonCreatorBlockedException.ACCOUNT_RESTRICTED);
        assertThat(code(() -> kit.studio.upload(unverified, null, "EMOTICON"))).isEqualTo(EmoticonCreatorBlockedException.EMAIL_NOT_VERIFIED);
        String mine = save(creator, null, empty).id();
        long other = kit.member();
        assertThat(code(() -> kit.studio.pack(other, mine))).isEqualTo("EMOTICON_PACK_NOT_FOUND");
        assertThat(code(() -> save(other, mine, empty))).isEqualTo("EMOTICON_PACK_NOT_FOUND");
        assertThat(code(() -> submit(other, mine, empty))).isEqualTo("EMOTICON_PACK_NOT_FOUND");
    }

    @Test
    void 그림_올리기는_하루_한도를_넘지_못한다() {
        long creator = kit.member();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            rows.add(new Object[] {kit.ids.nextId(), "limit-" + i, creator});
        }
        kit.jdbc.batchUpdate("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, storage_key, uploaded_by_user, created_at) "
                + "VALUES (?, 'EMOTICON', 'GIF', 200, 200, 10, 1, true, repeat('b', 64), ?, ?, now())", rows);
        assertThatThrownBy(() -> kit.images.uploadByMember(creator, TestImages.animated("limit"), EmoticonImageKind.EMOTICON)).isInstanceOf(EmoticonUploadLimitException.class);
    }

    @Test
    void 요약은_내_팩_받은_수_보상_최근_보상_심사_대기_수() {
        long creator = kit.member();
        long packId = kit.approvedPack(creator);
        kit.submitNew(creator, "대기 팩");
        kit.inTx(() -> kit.acquire.acquire(kit.member(), Long.toString(packId)));
        kit.inTx(() -> kit.acquire.acquire(kit.member(), Long.toString(packId)));
        EmoticonStudioView.Overview overview = kit.studio.overview(creator);
        assertThat(overview.packs()).hasSize(2);
        assertThat(overview.acquireTotal()).isEqualTo(2);
        assertThat(overview.rewardTotal()).isEqualTo(20);
        assertThat(overview.rewardToday()).isEqualTo(20);
        assertThat(overview.recentRewards()).hasSize(2).allMatch(r -> r.packId().equals(Long.toString(packId)) && r.points() == 10 && r.packName() != null);
        assertThat(overview.pendingCount()).isEqualTo(1);
        assertThat(overview.sales().salesAmount()).isZero();
        EmoticonStudioView.PackRow row = overview.packs().stream().filter(p -> p.id().equals(Long.toString(packId))).findFirst().orElseThrow();
        assertThat(row.rewardTotal()).isEqualTo(20);
        assertThat(row.acquireCount()).isEqualTo(2);
    }
}
