package com.community.installation.service;

import com.community.installation.exception.InstallationAlreadyInstalledException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * 설치 토큰 — 만들고, 해시만 저장하고, 비교한다.
 *
 * <ul>
 *   <li>무작위 토큰은 <b>24자</b>, 혼동되는 글자(0·O·1·I)를 뺀 32글자 중에서 <b>보안 난수</b>로 뽑는다(글자당 5비트 = 120비트).
 *       32 는 2 의 거듭제곱이라 한쪽으로 치우치지 않는다.</li>
 *   <li>DB 에는 <b>SHA-256 해시</b>만 둔다. 원문은 만들 때 한 번 돌려줄 뿐 어디에도 저장하지 않는다 — 환경 변수로 준 토큰도 같다.</li>
 *   <li>비교는 <b>상수 시간</b>이다({@link MessageDigest#isEqual}) — 앞에서 몇 글자가 맞았는지가 응답 시간에 묻어나지 않는다.</li>
 * </ul>
 * 이 클래스는 토큰 값을 로그에 남기지 않는다(무작위 토큰을 한 번 출력하는 것은 {@link InstallationBootstrap} 의 일이다).
 */
@Service
public class InstallTokenService {

    /** 혼동되는 글자(0 O 1 I)를 뺀 32글자 */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int TOKEN_LENGTH = 24;

    private final InstallationStore store;
    private final SecureRandom random = new SecureRandom();

    public InstallTokenService(InstallationStore store) {
        this.store = store;
    }

    /**
     * 새 무작위 토큰을 만들어 해시를 저장하고 <b>원문을 돌려준다</b>(부르는 쪽이 로그에 한 번만 출력한다). 이전 토큰은 무효가 된다.
     *
     * @throws InstallationAlreadyInstalledException 설치가 이미 끝났을 때
     */
    public String issueRandom() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        String value = token.toString();
        saveHash(value);
        return value;
    }

    /** 환경 변수로 정한 토큰의 해시를 저장한다. 이전 토큰은 무효가 된다 */
    public void useConfigured(String token) {
        saveHash(token);
    }

    /** 입력한 토큰이 저장된 해시와 맞는가(상수 시간). 저장된 토큰이 없으면(설치가 끝났거나 아직 안 만들었으면) 맞지 않는다 */
    public boolean matches(String input) {
        return store.tokenHash().map(stored -> MessageDigest.isEqual(
                sha256Hex(input).getBytes(StandardCharsets.US_ASCII), stored.getBytes(StandardCharsets.US_ASCII))).orElse(false);
    }

    private void saveHash(String token) {
        if (!store.saveTokenHash(sha256Hex(token))) {
            throw new InstallationAlreadyInstalledException();
        }
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e); // 모든 JVM 이 갖는 알고리즘이다
        }
    }
}
