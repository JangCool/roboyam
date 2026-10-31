package com.community.business.nts;

/**
 * 국세청 사업자 상태조회(공공데이터포털 「사업자등록정보 진위확인 및 상태조회」, B1) — 공공데이터포털 구현({@link OdcloudNtsStatusClient})과 가짜 구현
 * ({@link FakeNtsStatusClient}, local·시험 전용) 중 배포 설정 {@code roboyam.business.nts.provider} 가 하나를 고른다({@link NtsConfig}).
 * <b>던지지 않는다</b> — 장애·제한 시간·키 없음은 {@code UNAVAILABLE} 결과다(운영자가 사유를 적고 승인할 수 있다). 트랜잭션 밖에서 부른다(외부 호출).
 */
public interface NtsStatusClient {

    NtsProviderKind kind();

    /**
     * @param bizNo 숫자 10자리(체크섬을 통과한 값)
     */
    NtsLookup lookup(String bizNo);
}
