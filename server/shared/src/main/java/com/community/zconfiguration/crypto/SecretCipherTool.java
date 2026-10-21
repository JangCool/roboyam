package com.community.zconfiguration.crypto;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 설정값 암호문을 만드는 명령줄 도구. 운영자가 DB 비밀번호 등을 암호화해 설정 파일에 넣을 때 쓴다.
 *
 * <pre>
 * 새 키 만들기            java -cp ... com.community.zconfiguration.crypto.SecretCipherTool keygen
 * 값 암호화 (키는 환경 변수) java -cp ... com.community.zconfiguration.crypto.SecretCipherTool encrypt
 * 값 복호화 확인           java -cp ... com.community.zconfiguration.crypto.SecretCipherTool decrypt
 * </pre>
 *
 * <p>값은 인자로 받지 않고 프롬프트(터미널) 또는 표준 입력(파이프)으로 받는다 — 셸 기록과 프로세스 목록에 비밀번호가 남지 않게 한다.
 * {@code encrypt} 는 {@code ENC(...)} 까지 붙여 출력하므로 그대로 설정 파일에 붙인다.
 */
public final class SecretCipherTool {

    private SecretCipherTool() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            usage();
            return;
        }
        switch (args[0]) {
            case "keygen" -> System.out.println(SecretCipher.generateKey());
            case "encrypt" -> System.out.println("ENC(" + cipher().encrypt(prompt("암호화할 값: ")) + ")");
            case "decrypt" -> {
                String text = prompt("암호문(ENC(...) 또는 v1:...): ").trim();
                if (text.startsWith("ENC(") && text.endsWith(")")) {
                    text = text.substring(4, text.length() - 1);
                }
                System.out.println(cipher().decrypt(text));
            }
            default -> usage();
        }
    }

    private static SecretCipher cipher() {
        String key = System.getenv("ROBOYAM_CRYPTO_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("환경 변수 ROBOYAM_CRYPTO_KEY 를 설정하세요. 새 키는 keygen 으로 만듭니다.");
        }
        return new SecretCipher(key);
    }

    /** 터미널이면 입력을 숨겨 받고, 파이프면 표준 입력의 첫 줄을 받는다(설치 스크립트가 쓴다 — 인자·셸 기록에 값이 남지 않는다). */
    private static String prompt(String message) {
        Console console = System.console();
        // JDK 22+ 는 입력이 파이프여도 System.console() 이 null 이 아니다 — 실제 터미널일 때만 쓴다
        if (console != null && console.isTerminal()) {
            return new String(console.readPassword("%s", message));
        }
        try {
            String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            if (line == null) {
                throw new IllegalStateException("표준 입력이 비어 있습니다.");
            }
            return line;
        } catch (IOException e) {
            throw new IllegalStateException("표준 입력을 읽지 못했습니다.", e);
        }
    }

    private static void usage() {
        System.err.println("사용법: SecretCipherTool keygen | encrypt | decrypt");
    }
}
