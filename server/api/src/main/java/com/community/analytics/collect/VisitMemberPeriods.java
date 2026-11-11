package com.community.analytics.collect;

import com.community.analytics.store.AnalyticsWriteStore.MemberRow;
import com.community.analytics.store.VisitPeriods;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 이 서버가 <b>이번 기간에 이미 넣은 회원</b>(site-analytics-contract 「3단계 검토 보강 — 회원 표시 넣기 줄이기」) — 회원 주·월 표시 넣기를 서버당·회원당·기간당 한 번으로 줄인다.
 * 방문자는 「그날 하루 표시에 처음 들어간 때」라는 관문이 있지만 회원은 없어서, 이것이 없으면 로그인 회원의 1분마다 DB 넣기 시도가 생긴다.
 *
 * <ul>
 *   <li>정확성은 DB 가 맡는다(표시 표의 기본 키 + ON CONFLICT DO NOTHING) — 이것은 시도만 줄인다. 서버가 다시 뜨거나 상한에 닿아 잊어도 한 번 더 넣어 볼 뿐 숫자는 같다.</li>
 *   <li>몰아쓰기 트랜잭션이 <b>커밋된 뒤에만</b> 기억한다(되돌려진 줄은 다음 1분에 다시 넣어 본다).</li>
 *   <li>기간이 바뀌면(더 늦은 기간의 줄을 기억할 때) 비운다. 지난 기간의 늦은 줄(자정 직전 신호)은 기억하지 않고 늘 넣어 본다(드물다).</li>
 *   <li>상한 {@value #MAX_MEMBERS}명(종류마다) — 넘으면 더 기억하지 않는다(메모리가 터지지 않게, 그 뒤 회원은 1분마다 넣어 본다).</li>
 * </ul>
 * {@link VisitFlusher} 의 잠금 안에서만 쓴다(스레드 안전하지 않다).
 */
final class VisitMemberPeriods {

    static final int MAX_MEMBERS = 200_000;

    private LocalDate week;
    private final Set<Long> weekMembers = new HashSet<>();
    private LocalDate month;
    private final Set<Long> monthMembers = new HashSet<>();

    /** 이번에 넣어 볼 줄 — 그 날의 주·달 표시를 이미 넣었으면 그 칸은 false, 둘 다 넣었으면 빠진다 */
    List<MemberRow> rows(Collection<VisitBuffer.MemberKey> members) {
        return members.stream().map(m -> new MemberRow(m.day(), m.memberId(), !seen(week, weekMembers, VisitPeriods.weekStart(m.day()), m.memberId()),
                !seen(month, monthMembers, VisitPeriods.monthStart(m.day()), m.memberId()))).filter(r -> r.week() || r.month()).toList();
    }

    /** 커밋된 줄을 기억한다 */
    void committed(Collection<MemberRow> rows) {
        for (MemberRow r : rows) {
            if (r.week()) {
                week = remember(week, weekMembers, VisitPeriods.weekStart(r.day()), r.memberId());
            }
            if (r.month()) {
                month = remember(month, monthMembers, VisitPeriods.monthStart(r.day()), r.memberId());
            }
        }
    }

    private static boolean seen(LocalDate period, Set<Long> members, LocalDate start, long memberId) {
        return start.equals(period) && members.contains(memberId);
    }

    /** 기억한 기간(바뀌었으면 새 기간) */
    private static LocalDate remember(LocalDate period, Set<Long> members, LocalDate start, long memberId) {
        if (period == null || start.isAfter(period)) {
            members.clear();
            period = start;
        }
        if (start.equals(period) && members.size() < MAX_MEMBERS) {
            members.add(memberId);
        }
        return period;
    }
}
