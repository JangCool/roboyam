package com.community.rewardevent.service;

import static com.community.rewardevent.service.RewardEventTestKit.manual;
import static com.community.rewardevent.service.RewardEventTestKit.points;
import static com.community.rewardevent.service.RewardEventTestKit.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.rewardevent.dto.RewardTierDto;
import com.community.rewardevent.dto.request.RewardCommentPreviewRequest;
import com.community.rewardevent.dto.request.RewardDrawRequest;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.dto.request.RewardRecipientAddRequest;
import com.community.rewardevent.dto.response.RewardCommentPreviewView;
import com.community.rewardevent.dto.response.RewardDrawView;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.dto.response.RewardRecipientResolveView;
import com.community.rewardevent.exception.RewardEventConflictException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * 보상 이벤트 — 대상 규칙·상태·고치기 범위(event-admin-contract 「대상 규칙」·「상태·방식」): 이벤트당 1인 1회(등수끼리도) · 정지·탈퇴·운영자 회원 계정 제외 · 등수 자리 ·
 * 댓글 추첨(1인 1회 · 지운 댓글·비회원·글 작성자 제외 · seed 재현 · 후보 부족) · 상태 전이 409 · 고치기 범위 409 · 지급 있으면 취소 불가 · 직접 전달 완료.
 */
class RewardSelectionTest extends RewardEventTestBase {

    private static final UnaryOperator<String> RAW = UnaryOperator.identity();

    private static String code(Throwable e) {
        return ((CommonException) e).getErrorCode();
    }

    private RewardEventDetailView add(long event, String tierId, long... members) {
        List<String> ids = new ArrayList<>();
        for (long m : members) {
            ids.add(Long.toString(m));
        }
        return kit.selection.addRecipients(kit.op(), event, new RewardRecipientAddRequest(tierId, ids));
    }

    @Test
    void 같은_회원은_이벤트당_한_번_등수끼리도_정지_탈퇴_운영자_회원_계정은_넣지_않는다() {
        long event = kit.create(kit.selection(tier("1등", 5, points(100)), tier("2등", 5, points(50))));
        RewardEventDetailView d = kit.queries.detail(event);
        String first = d.tiers().get(0).id();
        String second = d.tiers().get(1).id();
        long a = kit.member();
        long suspended = kit.member();
        long withdrawn = kit.member();
        long operatorAccount = kit.member();
        kit.status(suspended, "SUSPENDED");
        kit.status(withdrawn, "WITHDRAWN");
        kit.operatorAccount(operatorAccount);

        add(event, first, a, a, suspended, withdrawn, operatorAccount);
        RewardEventDetailView after = add(event, second, a);

        assertThat(after.recipientsByTier()).containsEntry(first, 1L).containsEntry(second, 0L);
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM reward_event_recipients WHERE event_id = ?", Integer.class, event)).isEqualTo(1);
        // 유일 (event_id, user_id) 가 끝까지 막는다
        long rid = kit.ids.nextId();
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO reward_event_recipients (id, event_id, tier_id, user_id, method, selected_at) VALUES (?, ?, ?, ?, 'MANUAL', now())",
                rid, event, Long.parseLong(second), a)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        RewardRecipientResolveView resolved = kit.selection.resolve(event, List.of(Long.toString(a), Long.toString(suspended), Long.toString(withdrawn),
                Long.toString(operatorAccount), "없는회원닉네임", "  "), RAW);
        assertThat(resolved.alreadyIn()).extracting(r -> r.id()).containsExactly(Long.toString(a));
        assertThat(resolved.excluded()).extracting(x -> x.reason()).containsExactly("SUSPENDED", "WITHDRAWN", "OPERATOR");
        assertThat(resolved.notFound()).containsExactly("없는회원닉네임");
    }

    @Test
    void 등수_자리를_넘으면_409_TIER_FULL() {
        long event = kit.create(kit.selection(tier("1등", 2, points(100))));
        String t = kit.queries.detail(event).tiers().get(0).id();
        assertThatThrownBy(() -> add(event, t, kit.member(), kit.member(), kit.member())).satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.TIER_FULL));
    }

    @Test
    void 댓글_추첨은_1인_1회_후보에서_seed_로_재현되고_지운_댓글_비회원_글_작성자는_빠진다_후보가_모자라면_409() {
        long author = kit.member();
        List<Long> commenters = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            commenters.add(kit.member());
        }
        long[] all = new long[commenters.size() + 2];
        for (int i = 0; i < commenters.size(); i++) {
            all[i] = commenters.get(i);
        }
        all[commenters.size()] = commenters.get(0); // 같은 회원의 두 번째 댓글
        all[commenters.size() + 1] = author; // 글 작성자 댓글
        long post = kit.post(author, all);
        long deletedOnly = kit.member();
        kit.boards.comment(kit.ids.nextId(), post, kit.boardOf(post), deletedOnly, "지울 댓글");
        kit.jdbc.update("UPDATE comments SET deleted_at = now() WHERE post_id = ? AND author_id = ?", post, deletedOnly);
        kit.boards.comment(kit.ids.nextId(), post, kit.boardOf(post), null, "손님", "x", "비회원 댓글");

        RewardCommentPreview preview = new RewardCommentPreview(kit.selection.previewComments(new RewardCommentPreviewRequest(Long.toString(post),
                kit.clock.instant().minus(Duration.ofHours(1)), kit.clock.instant().plus(Duration.ofHours(1)), true, true)));
        assertThat(preview.view().candidateCount()).isEqualTo(6);
        assertThat(preview.view().excluded().author()).isEqualTo(1);
        assertThat(preview.view().commentCount()).isEqualTo(9); // 회원 8 + 비회원 1(지운 댓글은 빼고)

        long event = kit.create(kit.commentDraw(post, tier("1등", 4, points(100))));
        String t = kit.queries.detail(event).tiers().get(0).id();
        kit.edits.start(kit.op(), event);
        assertThatThrownBy(() -> kit.selection.draw(kit.op(), event, new RewardDrawRequest(t, 2), RAW))
                .satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.STATE)); // 마감 뒤에만
        kit.edits.close(kit.op(), event);

        RewardDrawView draw = kit.selection.draw(kit.op(), event, new RewardDrawRequest(t, 3), RAW);

        assertThat(draw.candidateCount()).isEqualTo(6);
        assertThat(draw.picked()).isEqualTo(3);
        long seed = Long.parseUnsignedLong(draw.seed(), 16);
        List<Long> expected = RewardEventRules.draw(commenters.stream().sorted().toList(), seed, 3);
        assertThat(draw.members()).extracting(m -> Long.parseLong(m.id())).containsExactlyElementsOf(expected);
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM reward_event_recipients WHERE event_id = ? AND method = 'COMMENT_DRAW' AND draw_id = ?", Integer.class,
                event, Long.parseLong(draw.id()))).isEqualTo(3);
        // 같은 후보·번호면 같은 결과
        assertThat(RewardEventRules.draw(commenters.stream().sorted().toList(), seed, 3)).isEqualTo(expected);
        assertThat(kit.queries.draws(event, RAW)).singleElement().satisfies(d -> assertThat(d.seed()).isEqualTo(draw.seed()));

        // 이미 뽑힌 3명은 후보에서 빠진다 — 남은 자리 1, 후보 3
        RewardDrawView second = kit.selection.draw(kit.op(), event, new RewardDrawRequest(t, 1), RAW);
        assertThat(second.candidateCount()).isEqualTo(3);
        assertThat(second.members()).extracting(m -> Long.parseLong(m.id())).doesNotContainAnyElementsOf(expected);
        assertThat(kit.audits(event)).contains(AuditActions.REWARD_EVENT_DRAW);
    }

    @Test
    void 추첨_후보가_모자라면_409_DRAW_NOT_ENOUGH() {
        long author = kit.member();
        long one = kit.member();
        long post = kit.post(author, one);
        long event = kit.create(kit.commentDraw(post, tier("1등", 5, points(100))));
        String t = kit.queries.detail(event).tiers().get(0).id();
        kit.edits.start(kit.op(), event);
        kit.edits.close(kit.op(), event);
        assertThatThrownBy(() -> kit.selection.draw(kit.op(), event, new RewardDrawRequest(t, 2), RAW))
                .satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.DRAW_NOT_ENOUGH));
    }

    @Test
    void 상태_전이는_맞는_상태에서만_끝_시각이_지나면_시작할_수_없다() {
        long event = kit.create(kit.selection(tier("1등", 5, points(100))));
        assertThatThrownBy(() -> kit.edits.close(kit.op(), event)).satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.STATE));
        kit.edits.start(kit.op(), event);
        assertThatThrownBy(() -> kit.edits.start(kit.op(), event)).satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.STATE));
        kit.edits.close(kit.op(), event);
        assertThat(kit.eventStatus(event)).isEqualTo("CLOSED");

        long late = kit.create(kit.selection(tier("1등", 5, points(100))));
        kit.jdbc.update("UPDATE reward_events SET start_at = now() - interval '2 day', end_at = now() - interval '1 day' WHERE id = ?", late);
        assertThatThrownBy(() -> kit.edits.start(kit.op(), late)).satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.PERIOD_PASSED));
    }

    @Test
    void 고치기_범위_밖의_칸이_바뀌면_409_LOCKED_FIELD_진행_중은_이름_끝_시각을_마감은_등수_인원을_고칠_수_있다() {
        long event = kit.create(kit.selection(tier("1등", 5, points(100))));
        kit.edits.start(kit.op(), event);
        RewardEventDetailView d = kit.queries.detail(event);
        // 진행 중: 이름·끝 시각은 된다, 시작 시각·보상 줄은 잠겼다
        RewardEventDetailView renamed = kit.edits.update(kit.op(), event, save(d, "새 이름", d.startAt(), d.endAt().plus(Duration.ofDays(1)), d.tiers()));
        assertThat(renamed.name()).isEqualTo("새 이름");
        assertThatThrownBy(() -> kit.edits.update(kit.op(), event, save(d, "새 이름", d.startAt().minus(Duration.ofHours(1)), renamed.endAt(), d.tiers())))
                .satisfies(e -> {
                    assertThat(code(e)).isEqualTo(RewardEventConflictException.LOCKED_FIELD);
                    assertThat(((CommonException) e).getFields()).containsKey("startAt");
                });
        List<RewardTierDto> changedLines = List.of(new RewardTierDto(d.tiers().get(0).id(), d.tiers().get(0).name(), 5, List.of(points(999))));
        assertThatThrownBy(() -> kit.edits.update(kit.op(), event, save(renamed, renamed.name(), renamed.startAt(), renamed.endAt(), changedLines)))
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsKey("tiers"));
        // 마감: 이름은 잠겼고 등수 인원은 된다
        kit.edits.close(kit.op(), event);
        RewardEventDetailView closed = kit.queries.detail(event);
        assertThatThrownBy(() -> kit.edits.update(kit.op(), event, save(closed, "또 바꿈", closed.startAt(), closed.endAt(), closed.tiers())))
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsKey("name"));
        List<RewardTierDto> moreQuota = List.of(new RewardTierDto(closed.tiers().get(0).id(), closed.tiers().get(0).name(), 9, closed.tiers().get(0).rewards()));
        assertThat(kit.edits.update(kit.op(), event, save(closed, closed.name(), closed.startAt(), closed.endAt(), moreQuota)).tiers().get(0).quota()).isEqualTo(9);
        assertThat(kit.audits(event)).contains(AuditActions.REWARD_EVENT_UPDATE);
    }

    @Test
    void 당첨자_수_아래로_등수_인원을_줄이면_400() {
        long event = kit.create(kit.selection(tier("1등", 5, points(100))));
        RewardEventDetailView d = kit.queries.detail(event);
        add(event, d.tiers().get(0).id(), kit.member(), kit.member());
        List<RewardTierDto> less = List.of(new RewardTierDto(d.tiers().get(0).id(), d.tiers().get(0).name(), 1, d.tiers().get(0).rewards()));
        assertThatThrownBy(() -> kit.edits.update(kit.op(), event, save(d, d.name(), d.startAt(), d.endAt(), less))).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("tiers[0].quota"));
    }

    @Test
    void 준비_상태의_고치기는_보상_줄을_다시_만들고_당첨자의_줄_결과도_다시_만든다() {
        long event = kit.create(kit.selection(tier("1등", 5, points(100))));
        RewardEventDetailView d = kit.queries.detail(event);
        long m = kit.member();
        add(event, d.tiers().get(0).id(), m);
        List<RewardTierDto> two = List.of(new RewardTierDto(d.tiers().get(0).id(), "1등", 5, List.of(points(300), manual("커피"))));
        RewardEventDetailView after = kit.edits.update(kit.op(), event, save(d, d.name(), d.startAt(), d.endAt(), two));
        assertThat(after.tiers().get(0).rewards()).hasSize(2);
        assertThat(kit.results(event, m)).hasSize(2).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("PENDING"));
    }

    @Test
    void 지급이_있으면_취소할_수_없고_지급_전이면_사유와_함께_취소한다() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(100))), m);
        kit.grants.grant(kit.op(), event, "k-" + event);
        assertThatThrownBy(() -> kit.edits.cancel(kit.op(), event, "이벤트를 접어요")).satisfies(e -> assertThat(code(e)).isIn(RewardEventConflictException.HAS_GRANTS,
                RewardEventConflictException.STATE));
        assertThat(kit.queries.detail(event).actions().cancelBlockedReason()).isNull(); // 지급 끝(DONE)은 열린 상태가 아니다

        long other = kit.closedSelection(kit.selection(tier("1등", 5, points(100))), kit.member());
        assertThatThrownBy(() -> kit.edits.cancel(kit.op(), other, "짧음")).isInstanceOf(ValidationException.class);
        RewardEventDetailView canceled = kit.edits.cancel(kit.op(), other, "조건을 잘못 잡았어요");
        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.cancelReason()).isEqualTo("조건을 잘못 잡았어요");
        assertThat(kit.audits(other)).contains(AuditActions.REWARD_EVENT_CANCEL);
    }

    @Test
    void 일부_지급이_남아_열린_이벤트는_취소가_HAS_GRANTS_로_막힌다() {
        long m = kit.member();
        long n = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(100), manual("커피"))), m, n);
        kit.grants.grant(kit.op(), event, "k-" + event);
        assertThat(kit.eventStatus(event)).isEqualTo("CLOSED"); // 직접 전달 대기가 남아 지급 끝이 아니다
        assertThat(kit.queries.detail(event).actions().cancelBlockedReason()).isEqualTo("HAS_GRANTS");
        assertThatThrownBy(() -> kit.edits.cancel(kit.op(), event, "이벤트를 접어요")).satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.HAS_GRANTS));
    }

    @Test
    void 지급한_받는_사람은_뺄_수_없다_직접_전달을_모두_마치면_지급_끝() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, manual("커피 기프티콘"))), m);
        kit.grants.grant(kit.op(), event, "k-" + event);
        long rid = kit.jdbc.queryForObject("SELECT id FROM reward_event_recipients WHERE event_id = ?", Long.class, event);
        assertThat(kit.results(event, m).get(0)).containsEntry("status", "DELIVERY_PENDING");
        assertThat(kit.recipientStatus(event, m)).isEqualTo("GRANTED");
        assertThatThrownBy(() -> kit.selection.removeRecipient(kit.op(), event, rid))
                .satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.RECIPIENT_GRANTED));
        String lineId = kit.queries.detail(event).tiers().get(0).rewards().get(0).id();
        var view = kit.grants.markDelivered(kit.op(), event, rid, lineId, "카카오톡 선물 보냄", RAW);
        assertThat(view.results().get(0).status()).isEqualTo("DELIVERED");
        assertThat(view.results().get(0).delivered().memo()).isEqualTo("카카오톡 선물 보냄");
        assertThatThrownBy(() -> kit.grants.markDelivered(kit.op(), event, rid, lineId, "", RAW))
                .satisfies(e -> assertThat(code(e)).isEqualTo(RewardEventConflictException.DELIVERY_STATE));
        assertThat(kit.eventStatus(event)).isEqualTo("DONE");
        assertThat(kit.audits(event)).contains(AuditActions.REWARD_EVENT_DELIVERED);
    }

    private static RewardEventSaveRequest save(RewardEventDetailView d, String name, java.time.Instant start, java.time.Instant end, List<RewardTierDto> tiers) {
        return new RewardEventSaveRequest(name, d.memo(), start, end, d.noticeUrl(), d.mode(), d.firstCome(), d.selection(), d.bulk(), d.excludeOperators(), tiers);
    }

    private record RewardCommentPreview(RewardCommentPreviewView view) {
    }
}
