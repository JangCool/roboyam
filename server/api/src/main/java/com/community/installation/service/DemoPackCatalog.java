package com.community.installation.service;

import com.community.installation.dto.TablePack;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이 서버에 들어 있는 <b>데모 팩</b>(설치 때 「데모 데이터 포함」으로 넣는 표본 데이터) — 클래스패스 {@code demo-pack/}(저장소의 {@code seed/demo-pack/} 를 api jar 가 싣는다).
 * 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」 3, 만드는 법은 {@code scripts/build-demo-pack.mjs}.
 *
 * <ul>
 *   <li>{@code manifest.json} — 형식·쓸 수 있는 구성(preset)·만든 시각·만든 DB 의 마이그레이션 번호·만든 DB 의 운영자 ID(설치 때 루트 운영자로 바꾼다)·데이터 파일 이름·올린 파일 목록.</li>
 *   <li>{@code data.json.gz} — 표 단위 행({@link TablePack}, 방식은 늘 {@code UPSERT}).</li>
 *   <li>{@code files/<저장 키>} — 올린 파일(이미지). 설치 때 저장소({@code ImageStorage})에 같은 키로 쓴다.</li>
 * </ul>
 * 서버가 뜰 때 목록만 읽어 모양을 본다 — 없거나 틀리면 「데모 없음」(설치 화면에서 고를 수 없다)이고 서버는 그대로 뜬다. 데이터는 설치 때만 읽는다.
 */
@Component
public class DemoPackCatalog {

    private static final Logger log = LoggerFactory.getLogger(DemoPackCatalog.class);
    static final String BASE = "demo-pack/";
    private static final Pattern FILE_KEY = Pattern.compile("[a-z0-9][a-z0-9._-]*(/[A-Za-z0-9._-]+)+");
    private static final Pattern DATA_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]*\\.json\\.gz");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * 팩 목록.
     *
     * @param operatorIds 만든 DB 의 운영자 ID(루트·데모 운영자) — 팩의 행이 이 값을 가리키면 설치한 곳의 루트 운영자 ID 로 바꾼다
     * @param dataKey     팩의 개인정보 칸(DataCipher 암호문 — 사업자·광고주 연락처 등 가짜 값)을 잠근 키({@code <버전>:<base64>}). 만든 서버의 local 개발 키(저장소에 공개된 값)라
     *                    비밀이 아니다 — 설치 때 이 키로 풀어 설치한 곳의 키로 다시 잠근다. 없으면 팩에 암호문이 없다는 뜻
     * @param files       올린 파일(저장 키·바이트 수)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Manifest(int format, String preset, String builtAt, Map<String, Object> source, List<String> operatorIds, String dataKey, String data,
                           List<FileEntry> files) {

        public Manifest {
            source = source == null ? Map.of() : source;
            operatorIds = operatorIds == null ? List.of() : List.copyOf(operatorIds);
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileEntry(String key, long size) {
    }

    private final Manifest manifest;

    public DemoPackCatalog() {
        this.manifest = load();
    }

    /** 데모 팩이 들어 있는가 */
    public boolean available() {
        return manifest != null;
    }

    /** 그 구성으로 설치할 때 쓸 수 있는가 */
    public boolean availableFor(String presetId) {
        return manifest != null && manifest.preset().equals(presetId);
    }

    public Optional<Manifest> manifest() {
        return Optional.ofNullable(manifest);
    }

    /** 데이터(표 단위 행)를 읽는다 — 설치 때 한 번 */
    public TablePack readData() throws IOException {
        try (InputStream in = new GZIPInputStream(new ClassPathResource(BASE + manifest.data()).getInputStream(), 1 << 16)) {
            return MAPPER.readValue(in, TablePack.class);
        }
    }

    /** 올린 파일 하나의 내용 */
    public byte[] readFile(String key) throws IOException {
        try (InputStream in = new ClassPathResource(BASE + "files/" + key).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static Manifest load() {
        ClassPathResource resource = new ClassPathResource(BASE + "manifest.json");
        if (!resource.exists()) {
            log.info("데모 팩이 없다 — 설치 때 「데모 데이터 포함」을 고를 수 없다");
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            Manifest manifest = MAPPER.readValue(in, Manifest.class);
            String problem = problem(manifest);
            if (problem != null) {
                log.warn("데모 팩 목록이 올바르지 않아 쓰지 않는다 — {}", problem);
                return null;
            }
            if (!new ClassPathResource(BASE + manifest.data()).exists()) {
                log.warn("데모 팩의 데이터 파일이 없어 쓰지 않는다 — {}", manifest.data());
                return null;
            }
            log.info("데모 팩 — 구성 {} · 만든 때 {} · 파일 {}개", manifest.preset(), manifest.builtAt(), manifest.files().size());
            return manifest;
        } catch (IOException | RuntimeException e) {
            log.warn("데모 팩 목록을 읽지 못해 쓰지 않는다 — {}", e.getMessage());
            return null;
        }
    }

    private static String problem(Manifest manifest) {
        if (manifest.format() != 1) {
            return "형식(format)이 1 이 아니다";
        }
        if (manifest.preset() == null || manifest.builtAt() == null) {
            return "preset·builtAt 이 비었다";
        }
        try {
            java.time.Instant.parse(manifest.builtAt());
        } catch (RuntimeException e) {
            return "builtAt 이 ISO-8601 시각이 아니다";
        }
        if (manifest.data() == null || !DATA_NAME.matcher(manifest.data()).matches()) {
            return "data 파일 이름이 틀렸다";
        }
        if (manifest.dataKey() != null && !manifest.dataKey().matches("[0-9]{1,3}:[A-Za-z0-9+/=]{40,}")) {
            return "dataKey 모양이 틀렸다";
        }
        for (String id : manifest.operatorIds()) {
            if (id == null || !id.matches("[0-9]{1,19}")) {
                return "operatorIds 에 숫자가 아닌 값이 있다";
            }
        }
        for (FileEntry file : manifest.files()) {
            if (file.key() == null || !FILE_KEY.matcher(file.key()).matches() || file.key().contains("..")) {
                return "파일 키가 틀렸다: " + file.key();
            }
        }
        return null;
    }
}
