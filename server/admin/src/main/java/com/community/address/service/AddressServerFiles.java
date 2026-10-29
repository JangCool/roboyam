package com.community.address.service;

import com.community.address.dto.response.AddressServerFileView;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서버 폴더의 주소 DB 파일(「지정 경로에서 적재」 — address-search-contract). 운영자가 서버에 직접 둔 zip 을 올리지 않고 그대로 넣는다(수백 MB 를 브라우저로 보내지 않게).
 * 폴더는 배포 설정 {@code roboyam.address.import-dir}(환경 변수 {@code ROBOYAM_ADDRESS_IMPORT_DIR}) — 비우면 이 기능은 꺼진다.
 *
 * <p>고를 수 있는 것은 그 폴더 <b>바로 아래</b>의 {@code .zip} 파일뿐이다(숨김 파일·하위 폴더·링크 밖 경로 제외). 요청은 경로가 아니라 이 목록에 있는 <b>이름</b>을 보내고,
 * 서버는 목록에서 같은 이름을 찾아 쓴다 — {@code ../} 같은 경로로 폴더 밖 파일을 고를 수 없다. 적재가 끝나도 파일은 지우지 않는다(운영자 것).
 */
@Component
public class AddressServerFiles {

    private final Path dir;

    public AddressServerFiles(@Value("${roboyam.address.import-dir:}") String dir) {
        this.dir = dir == null || dir.isBlank() ? null : Paths.get(dir.strip()).toAbsolutePath().normalize();
    }

    public boolean enabled() {
        return dir != null;
    }

    /** 고를 수 있는 파일 — 이름 순. 꺼져 있거나 폴더가 없으면 빈 목록 */
    public List<AddressServerFileView> list() {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(this::selectableInside)
                    .map(path -> {
                        try {
                            return new AddressServerFileView(path.getFileName().toString(), Files.size(path), Files.getLastModifiedTime(path).toInstant());
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(AddressServerFileView::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("주소 DB 서버 폴더를 읽지 못했다", e);
        }
    }

    /**
     * 고른 이름을 목록에서 찾아 적재 원천으로 — 1~2개, 목록에 있는 이름만(없으면 400 {@code fields.fileNames}), 빈 파일은 안 된다. 꺼져 있으면 400 {@code fields.fileNames}.
     */
    public List<AddressFileReader.Source> resolve(List<String> names) {
        if (dir == null) {
            throw ValidationException.of("fileNames", new MessageRef("address.import.serverDirOff"));
        }
        List<String> wanted = names == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(names));
        if (wanted.isEmpty() || wanted.size() > AddressImportService.MAX_FILES) {
            throw ValidationException.of("fileNames", new MessageRef("address.import.filesCount", AddressImportService.MAX_FILES));
        }
        Map<String, AddressServerFileView> listed = new HashMap<>();
        list().forEach(file -> listed.put(file.name(), file));
        List<AddressFileReader.Source> sources = new ArrayList<>();
        for (String name : wanted) {
            AddressServerFileView file = Optional.ofNullable(name).map(listed::get)
                    .orElseThrow(() -> ValidationException.of("fileNames", new MessageRef("address.import.serverFileMissing")));
            if (file.size() <= 0) {
                throw ValidationException.of("fileNames", new MessageRef("address.import.fileEmpty"));
            }
            sources.add(new AddressFileReader.Source(dir.resolve(file.name()), file.name(), false));
        }
        return sources;
    }

    /** 폴더 바로 아래의 보통 파일 · .zip · 숨김 아님 · 실제 위치도 폴더 안(링크로 밖을 가리키지 않음) */
    private boolean selectableInside(Path path) {
        try {
            return selectable(path) && path.toRealPath().getParent().equals(dir.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean selectable(Path path) {
        String name = path.getFileName().toString();
        return Files.isRegularFile(path) && !name.startsWith(".") && name.toLowerCase().endsWith(".zip");
    }
}
