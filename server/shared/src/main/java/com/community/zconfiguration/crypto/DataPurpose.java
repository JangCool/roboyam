package com.community.zconfiguration.crypto;

import java.util.regex.Pattern;

/**
 * 암호화·해시하는 값의 <b>용도 이름</b>(예: {@code withdrawn.email}, {@code identity.di}). 업무 쪽이 상수로 정하고 한 번 정하면 바꾸지 않는다.
 *
 * <ul>
 *   <li>{@link DataCipher} 는 이 이름을 GCM 의 추가 인증 데이터(AAD)로 묶는다 — 한 칸의 암호문을 다른 칸에 옮겨 붙이면 풀리지 않는다.
 *       그래서 이름을 바꾸면 이미 저장한 암호문을 읽을 수 없다.</li>
 *   <li>{@link BlindIndex} 는 이 이름을 해시 입력 앞에 붙인다 — 같은 값이라도 용도가 다르면 해시가 달라 표끼리 맞대어 볼 수 없다.</li>
 * </ul>
 *
 * @param name 소문자로 시작하고 소문자·숫자·{@code .}·{@code -} 로 2~60자
 */
public record DataPurpose(String name) {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9.-]{1,59}");

    public DataPurpose {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("용도 이름은 소문자로 시작하는 소문자·숫자·.·- 2~60자여야 한다: " + name);
        }
    }

    public static DataPurpose of(String name) {
        return new DataPurpose(name);
    }
}
