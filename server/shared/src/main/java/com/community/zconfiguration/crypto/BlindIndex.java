package com.community.zconfiguration.crypto;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 원문을 남기지 않고 <b>같은 값인지만 확인하는 해시</b>(블라인드 인덱스) — HMAC-SHA256, 16진 64자. 본인인증 DI·CI 의 1인 1계정 확인, 탈퇴 후 재가입 제한(이메일·소셜 계정·
 * 본인인증 값) 같은 곳에 쓴다(docs/domain/commerce.md 12-1·17-2, docs/features/commerce-foundation-contract.md).
 *
 * <ul>
 *   <li>키는 {@code ROBOYAM_INDEX_KEY}(base64 32바이트) — 데이터 키({@link DataCipher})와 <b>따로</b>다. 원문을 남기지 않으므로 해시를 다시 계산할 수 없어
 *       <b>이 키는 바꿀 수 없다</b>(바꾸면 이미 저장한 해시와 맞대어 볼 수 없다). 잃으면 중복 확인·재가입 제한이 처음부터 다시 쌓인다 — 백업한다.</li>
 *   <li>입력은 {@code 용도 이름 + NUL + 값} 이라 같은 값이라도 용도가 다르면 해시가 다르다. <b>값의 정규화(소문자·공백 정리 등)는 부르는 쪽이 한다</b> — 같은 사람의 값이
 *       같은 글자로 들어와야 같은 해시가 된다.</li>
 *   <li>키가 없어도 앱은 뜨고, 해시가 필요한 요청만 {@code 503 INDEX_KEY_MISSING} 이다. 형식이 틀리면 기동을 멈춘다. 키 값은 로그·오류에 싣지 않는다.</li>
 * </ul>
 */
@Component
public class BlindIndex {

    private static final Logger log = LoggerFactory.getLogger(BlindIndex.class);

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;

    public BlindIndex(@Value("${roboyam.data-crypto.index-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            log.warn("해시 키(ROBOYAM_INDEX_KEY)가 없다 — 본인인증·재가입 제한처럼 해시가 필요한 요청은 503 INDEX_KEY_MISSING 이 된다");
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ROBOYAM_INDEX_KEY 가 base64 형식이 아닙니다.");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("ROBOYAM_INDEX_KEY 는 32바이트(base64)여야 합니다. 현재 " + raw.length + "바이트");
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    /** 해시 키가 설정돼 있는가 */
    public boolean isConfigured() {
        return key != null;
    }

    /**
     * 용도별 해시(16진 64자). {@code value} 는 부르는 쪽이 정규화한 값이다.
     *
     * @throws CommonException 키가 없을 때({@code 503 INDEX_KEY_MISSING})
     */
    public String hash(DataPurpose purpose, String value) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance(ALGORITHM); // 스레드 사이에 나눠 쓸 수 없어 부를 때마다 만든다(가볍다)
            mac.init(key);
            mac.update(purpose.name().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.", e); // 모든 JVM 이 HmacSHA256 을 갖는다
        }
    }

    /** 키가 없으면 {@code 503 INDEX_KEY_MISSING} */
    public void requireConfigured() {
        if (key == null) {
            throw new CommonException(ErrorCode.INDEX_KEY_MISSING);
        }
    }
}
