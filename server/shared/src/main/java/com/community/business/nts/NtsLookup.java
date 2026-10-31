package com.community.business.nts;

import com.community.business.entity.NtsBizStatus;
import java.time.LocalDate;

/**
 * 국세청 상태조회 한 번의 결과.
 *
 * @param taxType  과세 유형(일반과세자·간이과세자 …) — 조회 불가·없는 번호면 null
 * @param closedOn 휴·폐업 일자
 * @param message  조회 불가 사유 코드(번들 {@code business.nts.message.<코드>} — {@code NO_KEY}·{@code TIMEOUT}·{@code ERROR}·{@code FAKE_DOWN})
 */
public record NtsLookup(NtsBizStatus status, String taxType, LocalDate closedOn, String message) {

    public static NtsLookup unavailable(String message) {
        return new NtsLookup(NtsBizStatus.UNAVAILABLE, null, null, message);
    }
}
