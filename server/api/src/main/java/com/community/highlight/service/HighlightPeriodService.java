package com.community.highlight.service;

import com.community.board.service.BoardCatalog;
import com.community.highlight.dto.response.HighlightPeriodItemView;
import com.community.highlight.dto.response.HighlightPeriodPageResponse;
import com.community.popular.entity.PopularSettingEntity;
import com.community.popular.repository.master.PopularSettingRepository;
import com.community.popular.service.PopularRules;
import com.community.post.service.FeedParams;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 「아추!」(아이로빗 추천) 페이지({@code GET /api/highlights}) — 운영자가 고른 홈 하이라이트를 일간·주간·월간으로(docs/features/achu-page-contract.md 「기간 정책」). 비회원도 본다.
 *
 * <ul>
 *   <li>대상 = 홈 하이라이트({@code place = home})만, 노출을 켠 것({@code visible}). 노출 기간({@code show_until})이 끝난 것도 남긴다.</li>
 *   <li>고른 시각 = {@code coalesce(show_from, created_at)}. 기간 = 서비스 시간대(한국시간, {@link ServiceTime}) 달력의 오늘 · 이번 주(시작 요일 = 인기글 설정 {@code weekStart}) ·
 *       이번 달이고 고른 시각이 {@code [from, to)} 이며 지금 이하인 것.</li>
 *   <li>지금 볼 수 없는 글(지움·가림·멤버 전용 소모임 글·꺼진 게시판·19금)은 뺀다 — 홈 하이라이트 가젯과 같은 판정({@link HighlightPicks}). 차단한 회원 글은 빼지 않고 줄에 표시만.</li>
 *   <li>틀린 값은 모아서 {@code 400 VALIDATION} — {@code fields.period}(DAY·WEEK·MONTH, 대소문자 무시, 비면 DAY) · {@code fields.page}(1~{@value #PAGE_MAX}) ·
 *       {@code fields.size}(1~50, 기본 20).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class HighlightPeriodService {

    public static final String DAY = "DAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";
    public static final List<String> PERIODS = List.of(DAY, WEEK, MONTH);
    /** 쪽 번호 최대 */
    public static final int PAGE_MAX = 100;

    private final HighlightPicks picks;
    private final BoardCatalog catalog;
    private final PopularSettingRepository settings;

    public HighlightPeriodService(HighlightPicks picks, BoardCatalog catalog, PopularSettingRepository settings) {
        this.picks = picks;
        this.catalog = catalog;
        this.settings = settings;
    }

    /** @param viewerId 로그인한 회원(비로그인 null) — 줄의 {@code blocked}·작성자 {@code memo} */
    public HighlightPeriodPageResponse list(String periodRaw, String pageRaw, String sizeRaw, Long viewerId) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String period = period(periodRaw);
        if (period == null) {
            fields.put("period", new MessageRef("highlight.period.invalid"));
        }
        int page = FeedParams.page(pageRaw, PAGE_MAX, fields);
        int size = FeedParams.size(sizeRaw, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = Instant.now();
        String weekStart = settings.findById(PopularSettingEntity.ROW_ID).map(PopularSettingEntity::getWeekStart).orElse(PopularRules.MONDAY);
        Range range = range(period, now, weekStart);
        HighlightPicks.PickPage found = picks.picked(HighlightPlaces.HOME, range.from(), range.to(), catalog.snapshot(), now, viewerId, (page - 1) * size, size);
        List<HighlightPeriodItemView> items = found.picks().stream()
                .map(p -> new HighlightPeriodItemView(Long.toString(p.highlight().getId()), p.post(), p.highlight().getTitle(), p.highlight().getSummary(),
                        p.imageUrl(), p.highlight().pickedAt()))
                .toList();
        int total = found.total();
        int totalPages = Math.max(1, (total + size - 1) / size);
        return new HighlightPeriodPageResponse(items, total == 0 ? 1 : page, totalPages, total, page < totalPages, period, range.from(), range.to(), weekStart);
    }

    /** 기간 표기(대소문자·앞뒤 공백 무시). 비면 일간, 모르는 값이면 null */
    static String period(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAY;
        }
        String period = raw.strip().toUpperCase(Locale.ROOT);
        return PERIODS.contains(period) ? period : null;
    }

    /** 그 기간의 범위 — 서비스 시간대 달력의 0시부터 다음 기간 0시 전까지. 주 시작 요일이 SUNDAY 가 아니면 월요일 */
    static Range range(String period, Instant now, String weekStart) {
        LocalDate today = ServiceTime.dateOf(now);
        LocalDate start = switch (period) {
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(PopularRules.SUNDAY.equals(weekStart) ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
            default -> today;
        };
        LocalDate end = switch (period) {
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
        return new Range(start.atStartOfDay(ServiceTime.ZONE).toInstant(), end.atStartOfDay(ServiceTime.ZONE).toInstant());
    }

    /** 기간의 범위 — {@code to} 는 미포함 */
    record Range(Instant from, Instant to) {
    }
}
