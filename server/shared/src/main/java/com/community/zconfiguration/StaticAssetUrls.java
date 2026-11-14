package com.community.zconfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;

/**
 * 앱에 딸린 그림(화면의 {@code public/brand}·{@code public/avatars}·{@code public/emoticons} — 빌드가 jar 의 {@code static/} 에 그대로 싣는다)의 <b>내용 해시를 붙인 주소</b>.
 * {@code /emoticons/robotikon-m/01.gif} → {@code /emoticons/robotikon-m/01.gif?v=9477ab01b5}. 그림이 바뀌면 해시가 바뀌어 주소도 바뀌므로, 해시가 붙은 요청은 1년 동안 다시 묻지 않게
 * 둔다({@link StaticAssetCacheInterceptor}) — 파일 이름에 해시가 붙는 {@code /_nuxt} 와 같은 효과(사용자 결정 2026-11-14).
 *
 * <ul>
 *   <li>해시 = 파일 바이트의 SHA-256 앞 {@value #HASH_LENGTH}자리(16진수). 화면 빌드(`scripts/nuxt/static-asset-versions.mjs`)도 같은 규칙으로 만들어 서버와 화면이 같은 주소를 낸다.</li>
 *   <li>처음 물을 때 jar 안 파일을 읽어 기억한다(서버가 뜬 뒤 파일은 바뀌지 않는다). 없는 파일은 기억하지 않고 주소를 그대로 돌려준다.</li>
 *   <li>DB 는 목록·가격·판매·순서를 맡고(로보티콘 {@code emoticon_images.static_path}, 아바타 품목), 해시는 실제 파일에서 구한다 — 파일만 바뀌고 DB 를 안 고쳐도 틀리지 않는다.</li>
 * </ul>
 */
public final class StaticAssetUrls {

    /** 해시를 붙이는 그림 폴더(앞 경로) */
    public static final List<String> FOLDERS = List.of("/brand/", "/avatars/", "/emoticons/");
    /** 해시를 싣는 질의 이름 */
    public static final String VERSION_PARAM = "v";
    static final int HASH_LENGTH = 10;

    private static final Map<String, String> VERSIONS = new ConcurrentHashMap<>();

    private StaticAssetUrls() {
    }

    /**
     * 해시를 붙인 주소. 그림 폴더가 아니거나 파일이 없으면 받은 그대로(외부 주소·업로드 파일·없는 그림). 뒤의 {@code #조각}(SVG 의 {@code #symbol} 등)은 해시 뒤로 옮긴다
     *
     * @param path 앱 정적 그림 주소({@code /avatars/irobit/basic-default.svg})
     */
    public static String of(String path) {
        if (path == null) {
            return null;
        }
        int hash = path.indexOf('#');
        String bare = hash < 0 ? path : path.substring(0, hash);
        String fragment = hash < 0 ? "" : path.substring(hash);
        if (bare.indexOf('?') >= 0) {
            return path;
        }
        return version(bare).map(v -> bare + "?" + VERSION_PARAM + "=" + v + fragment).orElse(path);
    }

    /**
     * 그 그림의 내용 해시 — 그림 폴더 안의 있는 파일만. 경로 거슬러 오르기({@code ..})는 받지 않는다
     *
     * @param path 앱 정적 그림 주소(질의·조각 없이)
     */
    public static Optional<String> version(String path) {
        if (path == null || path.contains("..") || FOLDERS.stream().noneMatch(path::startsWith)) {
            return Optional.empty();
        }
        String known = VERSIONS.get(path);
        if (known != null) {
            return Optional.of(known);
        }
        Optional<String> computed = compute(path);
        computed.ifPresent(v -> VERSIONS.put(path, v));
        return computed;
    }

    private static Optional<String> compute(String path) {
        ClassPathResource file = new ClassPathResource("static" + path);
        if (!file.exists() || !file.isReadable()) {
            return Optional.empty();
        }
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, read);
            }
            return Optional.of(HexFormat.of().formatHex(digest.digest()).substring(0, HASH_LENGTH));
        } catch (IOException e) {
            return Optional.empty();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
