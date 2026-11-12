package com.community.business.nts;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * 공공데이터포털 구현 — 호출할 때마다 서비스 키를 읽는다(화면에서 저장한 값 → 환경 변수 {@code IROBIT_NTS_SERVICE_KEY} → 없음 —
 * {@code PublicDataSettingsService#ntsServiceKey}). 화면에서 키를 바꾸면 재기동 없이 다음 조회부터 쓴다. 키가 없으면 부르지 않고 조회 불가({@code NO_KEY}).
 * HTTP·응답 해석은 {@link OdcloudNtsApi}.
 */
public class OdcloudNtsStatusClient implements NtsStatusClient {

    private final OdcloudNtsApi api;
    private final Supplier<String> serviceKey;

    public OdcloudNtsStatusClient(OdcloudNtsApi api, Supplier<String> serviceKey) {
        this.api = api;
        this.serviceKey = serviceKey;
    }

    @Override
    public NtsProviderKind kind() {
        return NtsProviderKind.ODCLOUD;
    }

    @Override
    public NtsLookup lookup(String bizNo) {
        String key = key();
        return key == null ? NtsLookup.unavailable("NO_KEY") : api.status(key, bizNo);
    }

    @Override
    public NtsLookup validate(String bizNo, LocalDate openedOn, String ownerName, String companyName) {
        String key = key();
        return key == null ? NtsLookup.validationUnavailable("NO_KEY") : api.validate(key, bizNo, openedOn, ownerName, companyName);
    }

    private String key() {
        String key = serviceKey.get();
        return key == null || key.isBlank() ? null : key;
    }
}
