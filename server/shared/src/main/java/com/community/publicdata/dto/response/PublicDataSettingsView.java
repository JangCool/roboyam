package com.community.publicdata.dto.response;

import com.community.business.nts.NtsProviderKind;
import java.time.Instant;

/**
 * 공공데이터포털 설정 화면 — {@code GET /api/admin/public-data-settings}(admin-web {@code usePublicDataSettingsApi.types.ts}). 키 값은 싣지 않는다.
 *
 * @param ntsProvider         지금 국세청 조회 구현(배포 설정 {@code irobit.business.nts.provider} — 화면에서 바꾸지 않는다)
 * @param cryptoKeyConfigured 암호화 키({@code IROBIT_CRYPTO_KEY})가 있는가 — false 면 저장할 수 없다(지우기는 된다)
 * @param serviceKey          키를 어디서 읽나 + 화면에서 마지막으로 저장·지운 순간
 * @param lastTest            마지막 연결 시험(국세청) — 없으면 null(키를 저장·지우면 지워진다)
 * @param endpoints           API 주소(국세청 기본 주소 · 특일 정보 주소 묶음 — 사용자 지시 2026-11-05)
 */
public record PublicDataSettingsView(NtsProviderKind ntsProvider, boolean cryptoKeyConfigured, KeyState serviceKey, Test lastTest, PublicDataEndpointsView endpoints) {

    /** 키 출처 — 화면 저장값 · 환경 변수 {@code IROBIT_NTS_SERVICE_KEY} · 없음 */
    public enum Source {
        SCREEN, ENV, NONE
    }

    /** 연결 시험 결과 — 성공 · 키가 틀림(401·403) · 키 없음 · 응답 없음 · 그 밖 오류 */
    public enum Result {
        OK, KEY_INVALID, NO_KEY, TIMEOUT, ERROR
    }

    /**
     * @param updatedAt 화면에서 마지막으로 저장·지운 순간 — 한 번도 없으면 null
     * @param updatedBy 그 운영자 이름
     */
    public record KeyState(Source source, Instant updatedAt, String updatedBy) {
    }

    /** {@code POST …/test} 응답이기도 하다 */
    public record Test(Result result, Instant testedAt, String testedBy) {
    }
}
