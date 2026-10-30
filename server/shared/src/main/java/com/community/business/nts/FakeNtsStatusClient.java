package com.community.business.nts;

import com.community.business.entity.NtsBizStatus;
import java.time.LocalDate;

/**
 * 가짜 국세청 조회 — <b>local·시험 전용</b>(dev·prod 프로필에서 고르면 기동을 멈춘다 — {@link NtsConfig}). 끝에서 둘째 자리 숫자로 결과를 고른다
 * (체크섬은 마지막 자리라 둘째 자리는 마음대로 고를 수 있다): {@code 6} 국세청에 없는 번호 · {@code 7} 휴업 · {@code 8} 폐업 · {@code 9} 조회 불가 · 그 밖 계속사업자.
 */
public class FakeNtsStatusClient implements NtsStatusClient {

    @Override
    public NtsProviderKind kind() {
        return NtsProviderKind.FAKE;
    }

    @Override
    public NtsLookup lookup(String bizNo) {
        char mark = bizNo == null || bizNo.length() != 10 ? '0' : bizNo.charAt(8);
        return switch (mark) {
            case '6' -> new NtsLookup(NtsBizStatus.NOT_FOUND, null, null, null);
            case '7' -> new NtsLookup(NtsBizStatus.PAUSED, "부가가치세 일반과세자", LocalDate.of(2026, 9, 1), null);
            case '8' -> new NtsLookup(NtsBizStatus.CLOSED, "부가가치세 일반과세자", LocalDate.of(2026, 6, 30), null);
            case '9' -> NtsLookup.unavailable("FAKE_DOWN");
            default -> new NtsLookup(NtsBizStatus.CONTINUING, "부가가치세 일반과세자", null, null);
        };
    }
}
