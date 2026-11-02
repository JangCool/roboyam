package com.community.business.nts;

import java.time.LocalDate;

/**
 * 국세청 사업자 조회(공공데이터포털 「국세청_사업자등록정보 진위확인 및 상태조회」, B1) — 공공데이터포털 구현({@link OdcloudNtsStatusClient})과 가짜 구현
 * ({@link FakeNtsStatusClient}, local·시험 전용) 중 배포 설정 {@code roboyam.business.nts.provider} 가 하나를 고른다({@link NtsConfig}).
 * <b>던지지 않는다</b> — 장애·제한 시간·키 없음은 {@code UNAVAILABLE} 결과다(운영자가 사유를 적고 승인할 수 있다). 트랜잭션 밖에서 부른다(외부 호출).
 */
public interface NtsStatusClient {

    NtsProviderKind kind();

    /**
     * 상태조회 — 진위는 {@code UNCHECKED}.
     *
     * @param bizNo 숫자 10자리(체크섬을 통과한 값)
     */
    NtsLookup lookup(String bizNo);

    /**
     * 진위확인 — 번호·개업일·대표자명·상호가 국세청 등록 정보와 <b>정확히</b> 같은가. 일치면 같은 응답의 상태를 함께 돌려준다(상태조회를 따로 부르지 않아도 된다).
     * 불일치면 상태는 {@code UNAVAILABLE}(국세청이 주지 않는다 — {@link NtsLookup#mismatch()}).
     *
     * @param bizNo       숫자 10자리(체크섬을 통과한 값)
     * @param openedOn    개업일
     * @param ownerName   대표자명
     * @param companyName 상호(등록증 그대로 — (주)·띄어쓰기까지)
     */
    NtsLookup validate(String bizNo, LocalDate openedOn, String ownerName, String companyName);
}
