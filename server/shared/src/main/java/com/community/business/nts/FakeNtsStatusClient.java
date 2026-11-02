package com.community.business.nts;

import com.community.business.entity.NtsBizStatus;
import com.community.business.entity.NtsValidity;
import java.time.LocalDate;

/**
 * 가짜 국세청 조회 — <b>local·시험 전용</b>(dev·prod 프로필에서 고르면 기동을 멈춘다 — {@link NtsConfig}). 등록번호 끝에서 둘째 자리 숫자로 결과를 고른다
 * (체크섬은 마지막 자리라 둘째 자리는 마음대로 고를 수 있다). 개업일·대표자명·상호는 보지 않는다.
 * <ul>
 *   <li>{@code 5} 진위 불일치(상태조회는 계속사업자)</li>
 *   <li>{@code 6} 국세청에 없는 번호 — 상태 {@code NOT_FOUND}, 진위 불일치(실제 국세청도 없는 번호는 02)</li>
 *   <li>{@code 7} 휴업 · {@code 8} 폐업 — 진위는 일치</li>
 *   <li>{@code 9} 조회 불가(상태·진위 모두)</li>
 *   <li>그 밖 계속사업자 + 진위 일치</li>
 * </ul>
 * 진위 불일치는 실제 응답처럼 상태 없이 돌려준다({@link NtsLookup#mismatch()}) — 상태가 필요하면 부르는 쪽이 상태조회를 따로 한다.
 */
public class FakeNtsStatusClient implements NtsStatusClient {

    @Override
    public NtsProviderKind kind() {
        return NtsProviderKind.FAKE;
    }

    @Override
    public NtsLookup lookup(String bizNo) {
        return switch (mark(bizNo)) {
            case '6' -> new NtsLookup(NtsBizStatus.NOT_FOUND, null, null, null);
            case '7' -> new NtsLookup(NtsBizStatus.PAUSED, "부가가치세 일반과세자", LocalDate.of(2026, 9, 1), null);
            case '8' -> new NtsLookup(NtsBizStatus.CLOSED, "부가가치세 일반과세자", LocalDate.of(2026, 6, 30), null);
            case '9' -> NtsLookup.unavailable("FAKE_DOWN");
            default -> new NtsLookup(NtsBizStatus.CONTINUING, "부가가치세 일반과세자", null, null);
        };
    }

    @Override
    public NtsLookup validate(String bizNo, LocalDate openedOn, String ownerName, String companyName) {
        return switch (mark(bizNo)) {
            case '5', '6' -> NtsLookup.mismatch();
            case '9' -> NtsLookup.validationUnavailable("FAKE_DOWN");
            default -> lookup(bizNo).withValidity(NtsValidity.MATCH);
        };
    }

    private static char mark(String bizNo) {
        return bizNo == null || bizNo.length() != 10 ? '0' : bizNo.charAt(8);
    }
}
