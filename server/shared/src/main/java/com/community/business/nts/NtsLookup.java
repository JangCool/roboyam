package com.community.business.nts;

import com.community.business.entity.NtsBizStatus;
import com.community.business.entity.NtsValidity;
import java.time.LocalDate;

/**
 * 국세청 조회 한 번의 결과 — 상태조회({@link NtsStatusClient#lookup})면 진위는 {@code UNCHECKED}, 진위확인({@link NtsStatusClient#validate})이면 진위 결과와
 * (일치일 때) 같은 응답에 온 상태.
 *
 * @param taxType  과세 유형(일반과세자·간이과세자 …) — 조회 불가·없는 번호면 null
 * @param closedOn 휴·폐업 일자
 * @param message  조회 불가 사유 코드(번들 {@code business.nts.message.<코드>} — {@code NO_KEY}·{@code KEY_INVALID}·{@code TIMEOUT}·{@code ERROR}·{@code FAKE_DOWN})
 * @param validity 진위확인 결과
 */
public record NtsLookup(NtsBizStatus status, String taxType, LocalDate closedOn, String message, NtsValidity validity) {

    /** 상태조회 결과(진위는 확인하지 않았다) */
    public NtsLookup(NtsBizStatus status, String taxType, LocalDate closedOn, String message) {
        this(status, taxType, closedOn, message, NtsValidity.UNCHECKED);
    }

    /** 상태조회 불가 — 진위는 확인하지 않았다 */
    public static NtsLookup unavailable(String message) {
        return new NtsLookup(NtsBizStatus.UNAVAILABLE, null, null, message, NtsValidity.UNCHECKED);
    }

    /** 진위확인 불가(장애·제한 시간·키 없음) — 상태도 모른다 */
    public static NtsLookup validationUnavailable(String message) {
        return new NtsLookup(NtsBizStatus.UNAVAILABLE, null, null, message, NtsValidity.UNAVAILABLE);
    }

    /** 진위 불일치 — 국세청은 상태를 함께 주지 않는다(상태가 필요하면 부르는 쪽이 상태조회를 따로 한다) */
    public static NtsLookup mismatch() {
        return new NtsLookup(NtsBizStatus.UNAVAILABLE, null, null, null, NtsValidity.MISMATCH);
    }

    /** 이 결과의 진위로 바꾼 사본 */
    public NtsLookup withValidity(NtsValidity value) {
        return new NtsLookup(status, taxType, closedOn, message, value);
    }
}
