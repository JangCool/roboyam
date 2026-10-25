package com.community.point.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.point.dto.PointRules;
import com.community.point.dto.response.LikeBonusRuleView;
import com.community.point.dto.response.MyPointLedgerItemView;
import com.community.point.dto.response.MyPointLedgerPageView;
import com.community.point.dto.response.MyPointLedgerTargetView;
import com.community.point.dto.response.PointCountView;
import com.community.point.dto.response.PointRulesView;
import com.community.point.dto.response.PointSummaryView;
import com.community.point.dto.response.PointTodayView;
import com.community.point.dto.response.PopularRewardRuleView;
import com.community.point.entity.PointBalanceEntity;
import com.community.point.entity.PointLedgerEntity;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.MyPointLedgerQueries;
import com.community.point.repository.master.PointBalanceRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 포인트 — 로그인한 회원 본인 것만(이메일 인증 전·정지 중이어도 읽는다).
 *
 * <ul>
 *   <li><b>요약</b>: 하루의 기준은 한국 시간 0시. 규칙은 정책 사본({@link PointPolicy}, 15초)에서 읽는다. 출석·교환 기능이 아직 없어 {@code attended=false}·{@code ticketCount=0}·
 *       {@code attendanceAvailable=false} 다.</li>
 *   <li><b>내역</b>({@code GET /api/me/points/ledger}): 원장의 본인 줄만, 최신이 위(id 내림차순 커서). {@code kind}(EARN·USE·REVOKE, 없으면 전체) · {@code size} 1~{@value #LEDGER_MAX_SIZE}
 *       (기본 {@value #LEDGER_DEFAULT_SIZE}) · {@code cursor}(앞 응답의 {@code nextCursor}) — 틀린 칸은 모아 {@code 400 VALIDATION fields.kind|size|cursor}.
 *       사유 이름은 관리자 원장과 같은 번들({@code point.reason.<코드>})을 요청 언어로. 글·댓글 사유({@code ref_type})만 {@code target}(댓글이면 그 댓글이 달린 글) — 지움·숨김·꺼진
 *       게시판이면 {@code available=false}·{@code title=null}. 운영자 이름·회원 정보는 싣지 않는다.</li>
 * </ul>
 */
@Service
public class MyPointService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int LEDGER_DEFAULT_SIZE = 20;
    static final int LEDGER_MAX_SIZE = 50;
    private static final Set<String> KINDS = Set.of("EARN", "USE", "REVOKE");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private final PointBalanceRepository balances;
    private final PointService points;
    private final PointPolicy policy;
    private final MyPointLedgerQueries ledger;
    private final BoardCatalog catalog;
    private final Messages messages;

    public MyPointService(PointBalanceRepository balances, PointService points, PointPolicy policy, MyPointLedgerQueries ledger, BoardCatalog catalog,
                          Messages messages) {
        this.balances = balances;
        this.points = points;
        this.policy = policy;
        this.ledger = ledger;
        this.catalog = catalog;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public PointSummaryView summary(long userId) {
        PointRules rules = policy.current();
        long balance = balances.findById(userId).map(PointBalanceEntity::getBalance).orElse(0L);
        PointTodayView today = new PointTodayView(LocalDate.now(KST).toString(), false,
                new PointCountView(points.countToday(userId, PointReason.POST), rules.postDailyCap()),
                new PointCountView(points.countToday(userId, PointReason.COMMENT), rules.commentDailyCap()),
                new PointCountView(points.countToday(userId, PointReason.LIKE_BONUS), rules.likeBonusDailyLimit()));
        PointRulesView view = new PointRulesView(rules.attendancePoints(), rules.postPoints(), rules.commentPoints(),
                new PopularRewardRuleView(rules.popularRewardTopN(), rules.popularRewardPoints()), rules.nicknameTicketPrice(),
                new LikeBonusRuleView(rules.likeBonusThreshold(), rules.likeBonusPoints(), rules.likeBonusDailyLimit()));
        return new PointSummaryView(balance, 0, false, today, view);
    }

    /** 내 포인트 내역 한 쪽 */
    @Transactional(readOnly = true)
    public MyPointLedgerPageView ledger(long userId, String kindText, String sizeText, String cursorText) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String kind = kindText == null || kindText.isBlank() ? null : kindText.strip();
        if (kind != null && !KINDS.contains(kind)) {
            invalid.put("kind", new MessageRef("point.ledger.kind.invalid"));
        }
        int size = LEDGER_DEFAULT_SIZE;
        if (sizeText != null && !sizeText.isBlank()) {
            String text = sizeText.strip();
            size = DIGITS.matcher(text).matches() && text.length() <= 3 ? Integer.parseInt(text) : -1;
            if (size < 1 || size > LEDGER_MAX_SIZE) {
                invalid.put("size", new MessageRef("notification.size.range", String.valueOf(LEDGER_MAX_SIZE)));
            }
        }
        Long before = null;
        if (cursorText != null && !cursorText.isBlank()) {
            String text = cursorText.strip();
            before = DIGITS.matcher(text).matches() ? parsePositive(text) : null;
            if (before == null) {
                invalid.put("cursor", new MessageRef("notification.cursor.invalid"));
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        List<PointLedgerEntity> rows = ledger.page(userId, kind, before, size);
        boolean more = rows.size() > size;
        List<PointLedgerEntity> page = more ? rows.subList(0, size) : rows;

        // 줄이 가리키는 글 — 댓글이면 그 댓글이 달린 글. 볼 수 있는 글만 제목이 있다
        Map<Long, Long> commentPosts = ledger.postIdsOfComments(page.stream()
                .filter(r -> PointService.REF_COMMENT.equals(r.getRefType()) && r.getRefId() != null).map(PointLedgerEntity::getRefId).collect(Collectors.toSet()));
        Set<Long> postIds = page.stream().map(r -> postIdOf(r, commentPosts)).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> activeBoards = catalog.snapshot().boards().stream().filter(EffectiveBoard::active).map(EffectiveBoard::id).collect(Collectors.toSet());
        Map<Long, String> titles = ledger.visibleTitles(postIds, activeBoards, Instant.now());

        Locale locale = messages.locale();
        List<MyPointLedgerItemView> items = page.stream().map(r -> {
            MyPointLedgerTargetView target = null;
            if (r.getRefType() != null) {
                Long postId = postIdOf(r, commentPosts);
                String title = postId == null ? null : titles.get(postId);
                target = new MyPointLedgerTargetView(postId == null ? null : Long.toString(postId), title, title != null);
            }
            String reason = PointReason.of(r.getReason()).map(code -> messages.get(new MessageRef(code.labelKey()), locale)).orElse(r.getReason());
            return new MyPointLedgerItemView(Long.toString(r.getId()), r.getKind(), r.getReason(), reason, r.getNote(), r.getDelta(), r.getBalanceAfter(), target,
                    r.getCreatedAt());
        }).toList();
        return new MyPointLedgerPageView(items, more, more ? items.get(items.size() - 1).entryId() : null);
    }

    private static Long postIdOf(PointLedgerEntity row, Map<Long, Long> commentPosts) {
        if (row.getRefId() == null) {
            return null;
        }
        if (PointService.REF_POST.equals(row.getRefType())) {
            return row.getRefId();
        }
        return PointService.REF_COMMENT.equals(row.getRefType()) ? commentPosts.get(row.getRefId()) : null;
    }

    private static Long parsePositive(String text) {
        try {
            long value = Long.parseLong(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
