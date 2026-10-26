package com.community.point.service;

import com.community.member.exception.MemberNotFoundException;
import com.community.point.dto.PointExpiring;
import com.community.point.dto.response.PointLotPageView;
import com.community.point.dto.response.PointLotView;
import com.community.point.dto.response.PointMemberLotsView;
import com.community.point.dto.response.PointRevokeShortfallView;
import com.community.point.entity.PointBalanceEntity;
import com.community.point.entity.PointLotEntity;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointBalanceRepository;
import com.community.point.repository.master.PointLotQueries;
import com.community.point.repository.master.PointMemberLotQueries;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 상세 「등급·포인트」의 포인트 묶음 — {@code GET /api/admin/members/{id}/point-lots}(권한 {@code point.read}, 컨트롤러가 선언). 읽기 전용(감사 기록 없음).
 * commerce-stage1-contract 「1-1 화면 확정과 서버 세부」 API.
 *
 * <ul>
 *   <li><b>검사</b>(칸을 모아 400 {@code VALIDATION}): {@code active} 는 {@code true}·{@code false}(없으면 true) · {@code page} 0 이상 정수(없으면 0) · {@code size} 1~{@value #MAX_SIZE}
 *       정수(없으면 {@value #DEFAULT_SIZE}). 그다음 회원 — 없는 회원·탈퇴한 회원·형식이 틀린 ID 는 404 {@code MEMBER_NOT_FOUND}.</li>
 *   <li>잔액(무상·유상·미회수)은 잔액 행(없으면 0), 소멸 예정은 {@link PointLotQueries#expiring}(회원 요약과 같은 셈), 묶음은 {@link PointMemberLotQueries}(남은 것만 = 차감 순서,
 *       전체 = 최근 받은 순), 미회수 기록은 최근 {@value #SHORTFALL_LIMIT}건.</li>
 *   <li>묶음 출처 이름은 요청 언어(번들 {@code point.reason.<코드>} — 전환 {@code MIGRATION} 포함). 「지금」은 앱 시계(시험은 옮길 수 있는 시계)다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PointMemberLotsService {

    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 50;
    static final int SHORTFALL_LIMIT = 5;

    private final UserRepository users;
    private final PointBalanceRepository balances;
    private final PointLotQueries lots;
    private final PointMemberLotQueries memberLots;
    private final Messages messages;
    private final Clock clock;

    public PointMemberLotsService(UserRepository users, PointBalanceRepository balances, PointLotQueries lots, PointMemberLotQueries memberLots, Messages messages,
                                  ObjectProvider<Clock> clocks) {
        this.users = users;
        this.balances = balances;
        this.lots = lots;
        this.memberLots = memberLots;
        this.messages = messages;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    public PointMemberLotsView lots(String memberId, String activeText, String pageText, String sizeText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Boolean active = activeOf(activeText);
        if (active == null) {
            fields.put("active", new MessageRef("point.lots.active.invalid"));
        }
        Integer page = intOf(pageText, 0);
        if (page == null || page < 0) {
            fields.put("page", new MessageRef("point.ledger.page.invalid"));
        }
        Integer size = intOf(sizeText, DEFAULT_SIZE);
        if (size == null || size < 1 || size > MAX_SIZE) {
            fields.put("size", new MessageRef("notification.size.range", String.valueOf(MAX_SIZE)));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long userId = parseId(memberId);
        users.findById(userId).filter(u -> !u.isGone()).orElseThrow(MemberNotFoundException::new);

        Instant now = clock.instant();
        Optional<PointBalanceEntity> balance = balances.findById(userId);
        PointExpiring expiring = lots.expiring(userId, now).orElse(null);
        long total = memberLots.count(userId, active, now);
        Locale locale = messages.locale();
        List<PointLotView> items = total == 0 || (long) page * size >= total ? List.of()
                : memberLots.page(userId, active, now, page, size).stream().map(lot -> view(lot, now, locale)).toList();
        List<PointRevokeShortfallView> shortfalls = memberLots.recentShortfalls(userId, SHORTFALL_LIMIT).stream()
                .map(s -> new PointRevokeShortfallView(Long.toString(s.getId()), s.getRefType(), Long.toString(s.getRefId()), s.getRequested(), s.getApplied(),
                        s.getCreatedAt()))
                .toList();
        return new PointMemberLotsView(balance.map(PointBalanceEntity::getFreeBalance).orElse(0L), balance.map(PointBalanceEntity::getPaidBalance).orElse(0L),
                balance.map(PointBalanceEntity::getRevokeDebt).orElse(0L), expiring, new PointLotPageView(items, total), shortfalls);
    }

    private PointLotView view(PointLotEntity lot, Instant now, Locale locale) {
        return new PointLotView(Long.toString(lot.getId()), lot.getKind().name(), lot.getOriginalAmount(), lot.getRemainingAmount(), lot.getExpiresAt(),
                lot.getSourceReason(), sourceLabel(lot.getSourceReason(), locale), lot.getRestoredFromLotId() != null, lot.getCreatedAt(), lot.expiredAt(now));
    }

    /** 출처 사유 → 요청 언어의 이름 — 원장 사유 또는 전환({@code MIGRATION}), 모르는 코드면 코드 그대로 */
    private String sourceLabel(String code, Locale locale) {
        if (PointReason.MIGRATION_LOT_SOURCE.equals(code)) {
            return messages.get(new MessageRef("point.reason." + PointReason.MIGRATION_LOT_SOURCE), locale);
        }
        return PointLedgerQueryService.reasonLabel(messages, code, locale);
    }

    /** {@code true}·{@code false}(없으면 true), 그 밖은 null */
    private static Boolean activeOf(String text) {
        if (text == null || text.isBlank()) {
            return Boolean.TRUE;
        }
        return switch (text.strip()) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** 부호가 있을 수 있는 정수(없으면 기본값), 정수가 아니면 null */
    private static Integer intOf(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String value = text.strip();
        if (!value.matches("^-?[0-9]{1,9}$")) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private static long parseId(String memberId) {
        if (memberId == null || memberId.isEmpty() || memberId.length() > 19 || !memberId.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new MemberNotFoundException();
        }
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException e) {
            throw new MemberNotFoundException();
        }
    }
}
