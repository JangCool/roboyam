package com.community.address.service;

import com.community.address.exception.AddressImportFailure;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 올린 zip 을 훑어 주소 파일 줄을 덩어리로 넘긴다(DB 를 모른다 — 시험이 표본 zip 으로 바로 부른다). zip 안의 폴더·안내 문서처럼 모르는 항목은 건너뛴다.
 * 글자 집합이 맞지 않는 바이트를 만나면 깨진 글자로 넣지 않고 멈춘다({@code address.import.badCharset}).
 */
public final class AddressFileReader {

    static final int BATCH_SIZE = 2000;
    static final String ROAD_PREFIX = "rnaddrkor_";
    static final String JIBUN_PREFIX = "jibun_rnaddrkor_";
    static final String ENGLISH_PREFIX = "rneng_";

    /** 올린 파일 하나 — 서버 임시 파일과 사용자가 고른 이름 */
    public record Source(Path path, String name) {
    }

    /** 넣은 줄 수 */
    public record Counts(long road, long jibun, long english) {
    }

    /** 덩어리를 받는 쪽(적재 작업이 스테이징 표에 넣는다) */
    public interface Sink {
        void roads(List<AddressFileParser.RoadRow> batch);

        void jibuns(List<AddressFileParser.JibunRow> batch);

        void englishes(List<AddressFileParser.EnglishRow> batch);
    }

    enum Kind { ROAD, JIBUN, ENGLISH }

    private AddressFileReader() {
    }

    /** 진행률의 분모 — 알아보는 항목의 줄 수(칸은 읽지 않는다) */
    public static long countLines(List<Source> sources, Charset charset) {
        long[] total = {0};
        each(sources, (source, entry, kind, reader) -> {
            long lineNo = 0;
            try {
                while (reader.readLine() != null) {
                    lineNo++;
                }
            } catch (CharacterCodingException e) {
                throw new AddressImportFailure("address.import.badCharset", baseName(entry.getName()), lineNo + 1);
            }
            total[0] += lineNo;
        }, charset);
        return total[0];
    }

    /** 모든 항목을 읽어 덩어리로 넘긴다. 도로명 파일이 하나도 없으면 실패({@code address.import.noRoadFile}) */
    public static Counts read(List<Source> sources, Charset charset, Sink sink) {
        long[] counts = new long[3];
        each(sources, (source, entry, kind, reader) -> {
            String file = baseName(entry.getName());
            switch (kind) {
                case ROAD -> counts[0] += lines(reader, file, AddressFileParser::road, sink::roads);
                case JIBUN -> counts[1] += lines(reader, file, AddressFileParser::jibun, sink::jibuns);
                case ENGLISH -> counts[2] += lines(reader, file, AddressFileParser::english, sink::englishes);
            }
        }, charset);
        if (counts[0] == 0) {
            throw new AddressImportFailure("address.import.noRoadFile");
        }
        return new Counts(counts[0], counts[1], counts[2]);
    }

    private interface LineParser<T> {
        T parse(String line, String file, long lineNo);
    }

    private static <T> long lines(BufferedReader reader, String file, LineParser<T> parser, Consumer<List<T>> sink) throws IOException {
        List<T> batch = new ArrayList<>(BATCH_SIZE);
        long lineNo = 0;
        long taken = 0;
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                T row = parser.parse(line, file, lineNo);
                if (row == null) {
                    continue;
                }
                batch.add(row);
                taken++;
                if (batch.size() == BATCH_SIZE) {
                    sink.accept(batch);
                    batch = new ArrayList<>(BATCH_SIZE);
                }
            }
        } catch (CharacterCodingException e) {
            throw new AddressImportFailure("address.import.badCharset", file, lineNo + 1);
        }
        if (!batch.isEmpty()) {
            sink.accept(batch);
        }
        return taken;
    }

    private interface EntryVisitor {
        void visit(Source source, ZipEntry entry, Kind kind, BufferedReader reader) throws IOException;
    }

    private static void each(List<Source> sources, EntryVisitor visitor, Charset charset) {
        for (Source source : sources) {
            try (ZipFile zip = new ZipFile(source.path().toFile(), charset)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Kind kind = entry.isDirectory() ? null : kindOf(baseName(entry.getName()));
                    if (kind == null) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset.newDecoder()
                                 .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
                        visitor.visit(source, entry, kind, reader);
                    }
                }
            } catch (ZipException | IllegalArgumentException e) {
                // 압축이 깨졌거나 zip 이 아니다(항목 이름을 고른 글자 집합으로 읽지 못해도 여기로 온다)
                throw new AddressImportFailure("address.import.badZip", source.name());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static Kind kindOf(String baseName) {
        String name = baseName.toLowerCase();
        if (!name.endsWith(".txt")) {
            return null;
        }
        if (name.startsWith(JIBUN_PREFIX)) {
            return Kind.JIBUN;
        }
        if (name.startsWith(ROAD_PREFIX)) {
            return Kind.ROAD;
        }
        if (name.startsWith(ENGLISH_PREFIX)) {
            return Kind.ENGLISH;
        }
        return null;
    }

    private static String baseName(String entryName) {
        return entryName.substring(entryName.lastIndexOf('/') + 1);
    }
}
