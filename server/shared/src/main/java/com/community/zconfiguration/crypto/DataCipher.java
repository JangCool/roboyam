package com.community.zconfiguration.crypto;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <b>개인정보 칸</b>(배송지·환불 계좌·현금영수증 번호·탈퇴 회원 연락용 이메일 등)을 암호화해 저장하는 빈 — AES-256-GCM, 키 버전을 붙인 암호문
 * (docs/domain/commerce.md 10-9, docs/features/commerce-foundation-contract.md).
 *
 * <ul>
 *   <li><b>키는 설정 비밀값용 {@code ROBOYAM_CRYPTO_KEY}({@link SecretEncryptor})와 따로</b>다 — 한 키가 새도 피해가 나뉘고 교체 주기를 따로 가져간다.
 *       지금 쓰는 키는 {@code ROBOYAM_DATA_KEY}({@code <버전>:<base64 32바이트>}, 예 {@code 2:AbC…}), 예전 키는 {@code ROBOYAM_DATA_KEY_PREVIOUS}
 *       (쉼표로 여럿, 풀기만 한다)다. 키를 바꿀 때는 새 버전을 지금 키로 두고 옛 키를 예전 키로 옮긴 뒤, 키 교체 배치가 옛 암호문을 모두 새 키로 바꾸면 예전 키를 뺀다.</li>
 *   <li>암호문 형식: {@code d<버전>:} + base64(IV 12바이트 ‖ 암호문 ‖ 태그 16바이트). 설정 비밀값의 {@code v1:} 과 앞머리가 달라 섞이지 않는다.
 *       용도 이름({@link DataPurpose})을 추가 인증 데이터로 묶어 다른 칸에 옮겨 붙인 암호문은 풀리지 않는다.</li>
 *   <li><b>키가 없어도 앱은 정상 기동</b>하고 이 빈을 쓰는 요청만 {@code 503 DATA_KEY_MISSING} 이다(10-9 「키가 없으면 참여·계좌 환불 신청만 503」).
 *       값이 있는데 형식이 틀리면 잘못된 키로 저장하지 않도록 기동을 멈춘다. 키 값은 로그·오류 어디에도 싣지 않는다.</li>
 *   <li>키를 잃으면 저장된 값을 영영 읽을 수 없다 — 설치 안내가 백업을 요구한다.</li>
 * </ul>
 * 평문·암호문을 로그·감사 detail·예외 문구에 싣지 않는다.
 */
@Component
public class DataCipher {

    private static final Logger log = LoggerFactory.getLogger(DataCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_VERSION = 999;

    /** 설정의 키 한 개: {@code <버전>:<base64>} */
    private static final Pattern KEY_ENTRY = Pattern.compile("(\\d{1,3}):(.+)");
    /** 암호문 앞머리: {@code d<버전>:} */
    private static final Pattern CIPHER_TEXT = Pattern.compile("d(\\d{1,3}):(.+)", Pattern.DOTALL);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 버전 → 키. 지금 키와 예전 키가 모두 들어 있다(풀기는 둘 다, 잠그기는 지금 키만) */
    private final Map<Integer, SecretKeySpec> keys;
    /** 지금 키의 버전. 키가 없으면 0 */
    private final int activeVersion;

    public DataCipher(@Value("${roboyam.data-crypto.key:}") String activeKey, @Value("${roboyam.data-crypto.previous-keys:}") String previousKeys) {
        Map<Integer, SecretKeySpec> parsed = new TreeMap<>();
        boolean hasActive = activeKey != null && !activeKey.isBlank();
        boolean hasPrevious = previousKeys != null && !previousKeys.isBlank();
        if (!hasActive) {
            if (hasPrevious) {
                throw new IllegalArgumentException("ROBOYAM_DATA_KEY_PREVIOUS 만 있고 지금 키 ROBOYAM_DATA_KEY 가 없습니다.");
            }
            this.keys = Map.of();
            this.activeVersion = 0;
            log.warn("데이터 암호화 키(ROBOYAM_DATA_KEY)가 없다 — 개인정보 칸을 암호화·복호화하는 요청은 503 DATA_KEY_MISSING 이 된다");
            return;
        }
        int active = put(parsed, activeKey, "ROBOYAM_DATA_KEY");
        if (hasPrevious) {
            for (String entry : previousKeys.split(",")) {
                if (!entry.isBlank()) {
                    put(parsed, entry, "ROBOYAM_DATA_KEY_PREVIOUS");
                }
            }
        }
        this.keys = Collections.unmodifiableMap(parsed);
        this.activeVersion = active;
        log.info("데이터 암호화 키를 읽었다 — 지금 버전 {}, 예전 키 {}개", active, parsed.size() - 1);
    }

    /** 데이터 키가 설정돼 있는가 */
    public boolean isConfigured() {
        return activeVersion > 0;
    }

    /** 지금 키의 버전(설정돼 있지 않으면 0) */
    public int activeVersion() {
        return activeVersion;
    }

    /**
     * 지금 키로 암호화한다.
     *
     * @return {@code d<버전>:} 로 시작하는 암호문
     * @throws CommonException 키가 없을 때({@code 503 DATA_KEY_MISSING})
     */
    public String encrypt(DataPurpose purpose, String plain) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(purpose.name().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return "d" + activeVersion + ":" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("데이터 암호화에 실패했습니다.", e);
        }
    }

    /**
     * 복호화한다. 암호문의 버전에 맞는 키(지금 키 또는 예전 키)를 쓴다.
     *
     * @throws CommonException          키가 없거나 그 버전의 키가 설정에서 빠졌을 때({@code 503 DATA_KEY_MISSING})
     * @throws IllegalArgumentException 형식이 틀렸거나, 키·용도가 다르거나, 암호문이 깨졌을 때(원인 상세는 담지 않는다)
     */
    public String decrypt(DataPurpose purpose, String cipherText) {
        requireConfigured();
        Matcher matcher = parse(cipherText);
        int version = Integer.parseInt(matcher.group(1));
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            log.error("암호문의 키 버전 {} 이 설정에 없다 — ROBOYAM_DATA_KEY_PREVIOUS 에서 빠뜨렸는지 확인", version);
            throw new CommonException(ErrorCode.DATA_KEY_MISSING);
        }
        try {
            byte[] in = Base64.getDecoder().decode(matcher.group(2));
            if (in.length <= IV_BYTES) {
                throw new IllegalArgumentException("암호문이 너무 짧습니다.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            cipher.updateAAD(purpose.name().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 키가 다르거나, 다른 용도의 암호문이거나, 변조된 경우. 원인 상세는 담지 않는다
            throw new IllegalArgumentException("복호화에 실패했습니다. 키·용도 또는 암호문을 확인하세요.");
        }
    }

    /**
     * 지금 키로 잠긴 암호문인가 — 키 교체 배치가 다시 암호화할 대상을 고를 때 쓴다(DB 에서는 {@code NOT LIKE 'd<지금 버전>:%'} 로 같은 판정을 한다).
     *
     * @throws IllegalArgumentException 암호문 형식이 아닐 때
     */
    public boolean isCurrent(String cipherText) {
        return isConfigured() && Integer.parseInt(parse(cipherText).group(1)) == activeVersion;
    }

    /**
     * 지금 키가 아닌 암호문을 지금 키로 다시 암호화한다. 이미 지금 키면 그대로 돌려준다.
     *
     * @throws CommonException          키가 없을 때({@code 503 DATA_KEY_MISSING})
     * @throws IllegalArgumentException 풀 수 없는 암호문일 때
     */
    public String reencrypt(DataPurpose purpose, String cipherText) {
        if (isCurrent(cipherText)) {
            return cipherText;
        }
        return encrypt(purpose, decrypt(purpose, cipherText));
    }

    /** 키가 없으면 {@code 503 DATA_KEY_MISSING} */
    public void requireConfigured() {
        if (!isConfigured()) {
            throw new CommonException(ErrorCode.DATA_KEY_MISSING);
        }
    }

    private static Matcher parse(String cipherText) {
        Matcher matcher = cipherText == null ? null : CIPHER_TEXT.matcher(cipherText);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("지원하지 않는 암호문 형식입니다.");
        }
        return matcher;
    }

    /** 키 한 개를 읽어 넣고 버전을 돌려준다. 형식이 틀리거나 같은 버전에 다른 키가 있으면 기동을 멈춘다(키 값은 문구에 싣지 않는다) */
    private static int put(Map<Integer, SecretKeySpec> keys, String entry, String source) {
        Matcher matcher = KEY_ENTRY.matcher(entry.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(source + " 는 「<버전>:<base64 32바이트>」 형식이어야 합니다(예: 1:AbC…).");
        }
        int version = Integer.parseInt(matcher.group(1));
        if (version < 1 || version > MAX_VERSION) {
            throw new IllegalArgumentException(source + " 의 키 버전은 1~" + MAX_VERSION + " 이어야 합니다. 현재 " + version);
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(matcher.group(2).trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(source + " 의 버전 " + version + " 키가 base64 형식이 아닙니다.");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException(source + " 의 버전 " + version + " 키는 32바이트(base64)여야 합니다. 현재 " + raw.length + "바이트");
        }
        SecretKeySpec key = new SecretKeySpec(raw, "AES");
        SecretKeySpec existing = keys.get(version);
        if (existing != null && !MessageDigest.isEqual(existing.getEncoded(), raw)) {
            throw new IllegalArgumentException("키 버전 " + version + " 에 서로 다른 키가 둘 있습니다(ROBOYAM_DATA_KEY · ROBOYAM_DATA_KEY_PREVIOUS 를 확인하세요).");
        }
        keys.put(version, key);
        return version;
    }
}
