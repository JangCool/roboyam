package com.community.point.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.point.dto.PointRules;
import com.community.pointshop.service.NicknameTicketService;
import com.community.point.dto.response.LikeBonusRuleView;
import com.community.point.dto.response.MyPointLedgerItemView;
import com.community.point.dto.response.MyPointLedgerPageView;
import com.community.point.dto.response.MyPointLedgerTargetView;
import com.community.point.dto.response.PointCountView;
import com.community.point.dto.response.PointRulesView;
import com.community.point.dto.response.PointSummaryView;
import com.community.point.dto.response.PointTodayView;
import com.community.point.dto.response.PointTotalView;
import com.community.point.dto.response.AttendanceStreakRuleView;
import com.community.point.dto.response.SignupPointRuleView;
import com.community.point.dto.response.PopularRewardRuleView;
import com.community.point.dto.PointExpiring;
import com.community.point.entity.PointBalanceEntity;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLedgerEntity;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.MyPointLedgerQueries;
import com.community.point.repository.master.PointBalanceRepository;
import com.community.point.repository.master.PointLotQueries;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 포인트 — 로그인한 회원 본인 것만(이메일 인증 전·정지 중이어도 읽는다).
 *
 * <ul>
 *   <li><b>요약</b>: 하루의 기준은 한국 시간 0시. 규칙은 정책 사본({@link PointPolicy}, 15초)에서 읽는다. 커머스 1-3: {@code attended} = 오늘 출석 줄, {@code attendanceAvailable=true},
 *       {@code today.total} = 오늘 활동 적립 합·하루 총 한도, {@code rules} 에 적립 조건·연속 출석 보너스·가입 포인트(받았나). 커머스 1-2: {@code ticketCount} = 안 쓴 닉네임 변경권 수, 변경권 가격 = 포인트 상점 변경권 품목 가격({@link NicknameTicketService}). 커머스 1-1: 무상·유상·미회수는 잔액 행, 소멸 예정은 남은 무상 묶음({@link PointLotQueries#expiring}), 충전 기능이 없어
 *       {@code chargeAvailable=false}.</li>
 *   <li><b>내역</b>({@code GET /api/me/points/ledger}): 원장의 본인 줄만, 최신이 위(id 내림차순 커서). {@code kind}(EARN·USE·REVOKE·CANCEL_USE·EXPIRE, 없으면 전체) ·
 *       {@code restoredUntil}(만료된 묶음 대신 새 묶음으로 돌려준 줄의 소멸 시각) · {@code size} 1~{@value #LEDGER_MAX_SIZE}
 *       (기본 {@value #LEDGER_DEFAULT_SIZE}) · {@code cursor}(앞 응답의 {@code nextCursor}) — 틀린 칸은 모아 {@code 400 VALIDATION fields.kind|size|cursor}.
 *       사유 이름은 관리자 원장과 같은 번들({@code point.reason.<코드>})을 요청 언어로. 글·댓글 대상({@code ref_type} POST·COMMENT)만 {@code target}(댓글이면 그 댓글이 달린 글) — 지움·숨김·꺼진
 *       게시판이면 {@code available=false}·{@code title=null}. 운영자 이름·회원 정보는 싣지 않는다.</li>
 * </ul>
 */
@Service
public class MyPointService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int LEDGER_DEFAULT_SIZE = 20;
    static final int LEDGER_MAX_SIZE = 50;
    /** 회원 내역의 종류 거르기 — 충전 환불({@code REFUND})은 충전 기능(3단계)과 함께 더한다(user-web {@code MyPointLedgerKind}) */
    private static final Set<String> KINDS = Set.of(PointKind.EARN.name(), PointKind.USE.name(), PointKind.REVOKE.name(), PointKind.CANCEL_USE.name(),
            PointKind.EXPIRE.name());
    /** 충전 기능이 열렸나 — 충전(유상 포인트 구매)은 3단계 기능이라 아직 없다. 설정 칸도 없으므로 상수다(충전 기능을 만들 때 그 설정에서 읽는다) */
    static final boolean CHARGE_AVAILABLE = false;
    /** 출석 기능이 있나 — 커머스 1-3 출석체크({@link AttendanceService}) */
    static final boolean ATTENDANCE_AVAILABLE = true;
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private final PointBalanceRepository balances;
    private final PointService points;
    private final PointPolicy policy;
    private final MyPointLedgerQueries ledger;
    private final PointLotQueries lots;
    private final BoardCatalog catalog;
    private final Messages messages;
    private final NicknameTicketService tickets;
    private final AttendanceService attendance;
    private final Clock clock;

    public MyPointService(PointBalanceRepository balances, PointService points, PointPolicy policy, MyPointLedgerQueries ledger, PointLotQueries lots,
                          BoardCatalog catalog, Messages messages, NicknameTicketService tickets, AttendanceService attendance, ObjectProvider<Clock> clocks) {
        this.attendance = attendance;
        this.balances = balances;
        this.points = points;
        this.policy = policy;
        this.ledger = ledger;
        this.lots = lots;
        this.catalog = catalog;
        this.messages = messages;
        this.tickets = tickets;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public PointSummaryView summary(long userId) {
        PointRules rules = policy.current();
        Optional<PointBalanceEntity> row = balances.findById(userId);
        long balance = row.map(PointBalanceEntity::getBalance).orElse(0L);
        long free = row.map(PointBalanceEntity::getFreeBalance).orElse(0L);
        long paid = row.map(PointBalanceEntity::getPaidBalance).orElse(0L);
        long debt = row.map(PointBalanceEntity::getRevokeDebt).orElse(0L);
        PointExpiring expiring = lots.expiring(userId, clock.instant()).orElse(null);
        PointTodayView today = new PointTodayView(LocalDate.ofInstant(clock.instant(), KST).toString(), attendance.attendedToday(userId),
                new PointCountView(points.countToday(userId, PointReason.POST), rules.postDailyCap()),
                new PointCountView(points.countToday(userId, PointReason.COMMENT), rules.commentDailyCap()),
                new PointCountView(points.countToday(userId, PointReason.LIKE_BONUS), rules.likeBonusDailyLimit()),
                new PointTotalView(points.earnedToday(userId), rules.dailyTotalCap()));
        PointRulesView view = new PointRulesView(rules.attendancePoints(), rules.postPoints(), rules.commentPoints(),
                new PopularRewardRuleView(rules.popularRewardTopN(), rules.popularRewardPoints()), tickets.price(),
                new LikeBonusRuleView(rules.likeBonusThreshold(), rules.likeBonusPoints(), rules.likeBonusDailyLimit()), rules.postMinLength(),
                rules.commentMinLength(), rules.commentOwnPostExcluded(), rules.commentOncePerPost(), rules.writeIntervalSeconds(), rules.dailyTotalCap(),
                new AttendanceStreakRuleView(rules.attendanceStreakDays(), rules.attendanceStreakBonus()),
                new SignupPointRuleView(rules.signupPoints(), points.signupReceived(userId)));
        return new PointSummaryView(balance, free, paid, CHARGE_AVAILABLE, expiring, debt, tickets.count(userId), ATTENDANCE_AVAILABLE, today, view);
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
        Map<Long, Instant> restored = ledger.restoredUntil(page.stream().filter(r -> PointKind.valueOf(r.getKind()).credit()).map(PointLedgerEntity::getId).toList());

        Locale locale = messages.locale();
        List<MyPointLedgerItemView> items = page.stream().map(r -> {
            MyPointLedgerTargetView target = null;
            if (contentRef(r)) {
                Long postId = postIdOf(r, commentPosts);
                String title = postId == null ? null : titles.get(postId);
                target = new MyPointLedgerTargetView(postId == null ? null : Long.toString(postId), title, title != null);
            }
            String reason = PointReason.of(r.getReason()).map(code -> messages.get(new MessageRef(code.labelKey()), locale)).orElse(r.getReason());
            return new MyPointLedgerItemView(Long.toString(r.getId()), r.getKind(), r.getReason(), reason, r.getNote(), r.getDelta(), r.getBalanceAfter(), target,
                    restored.get(r.getId()), r.getCreatedAt());
        }).toList();
        return new MyPointLedgerPageView(items, more, more ? items.get(items.size() - 1).entryId() : null);
    }

    /** 글·댓글을 가리키는 줄인가 — 소멸(묶음)·미회수 상계(원장 줄)·포인트 상점 구매 같은 다른 대상은 {@code target} 이 없다 */
    private static boolean contentRef(PointLedgerEntity row) {
        return PointService.REF_POST.equals(row.getRefType()) || PointService.REF_COMMENT.equals(row.getRefType());
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
