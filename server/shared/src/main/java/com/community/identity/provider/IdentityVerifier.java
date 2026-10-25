package com.community.identity.provider;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.IdentityStartInfo;
import com.community.identity.dto.VerifiedPerson;
import com.community.identity.dto.request.FakeIdentityInput;

/**
 * 본인인증 업체 — 포트원 구현({@link PortOneIdentityVerifier})과 가짜 구현({@link FakeIdentityVerifier}, local·시험 전용) 중 배포 설정 {@code roboyam.identity.provider} 가 하나를
 * 고른다({@code IdentityConfig}). 업무 규칙(1인 1계정·재인증 제한·만료)은 {@code IdentityVerificationService} 가 지키고, 여기는 「이 인증 번호의 결과가 누구인가」만 답한다.
 * 트랜잭션 밖에서 부른다(외부 호출).
 */
public interface IdentityVerifier {

    IdentityProviderKind kind();

    /**
     * 인증을 시작할 때 화면에 줄 값.
     *
     * @throws com.community.identity.exception.IdentityNotConfiguredException 포트원인데 설정 칸이 비었을 때(503)
     */
    IdentityStartInfo startInfo();

    /**
     * 그 인증 번호의 결과를 확인한다.
     *
     * @param verificationId 서버가 발급한 인증 번호
     * @param fake           가짜 구현의 입력(포트원은 읽지 않는다)
     * @throws com.community.identity.exception.IdentityProviderFailedException 업체 오류·인증 미완료·결과를 읽지 못함(502)
     * @throws com.community.zconfiguration.exception.ValidationException      가짜 입력이 틀렸을 때(400 {@code fields.fake.*})
     */
    VerifiedPerson verify(String verificationId, FakeIdentityInput fake);
}
