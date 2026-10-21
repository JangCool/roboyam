package com.community.site.service;

import com.community.media.exception.ImageBusyException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.DocumentLimits;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.XMLInput;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import com.github.weisj.jsvg.view.ViewBox;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.stream.XMLInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 검사를 마친 SVG({@link SvgInspector})를 정사각 PNG 재료({@link BufferedImage})로 그린다(사이트 아이콘).
 *
 * <p><b>서버 안에서만 그림으로 쓴다</b> — 그리기 라이브러리는 JSVG(순수 Java2D, MIT, 의존 없음)이고 SVG 를 밖으로 내보내지 않는다.
 * 외부 자원(네트워크·파일)을 읽지 않도록 {@link ResourcePolicy#DENY_EXTERNAL}(내장 {@code data:} 만 허용)로 열고, XML 은 DTD·외부 엔티티를 끈 StAX 로 읽는다.
 * 스크립트·{@code foreignObject} 는 이 라이브러리가 지원하지 않는다(검사에서 이미 거절한다). 문서 중첩·{@code use} 중첩·도형 수는 라이브러리 기본 한도({@link DocumentLimits#DEFAULT})다.
 *
 * <p>크기마다 <b>벡터로 직접</b> 그린다(큰 그림을 줄이지 않는다 — 작은 파비콘도 또렷하다). 문서의 {@code viewBox}(없으면 {@code width}×{@code height})를 정사각 한 변에 맞춘다. 투명은 그대로 둔다.
 *
 * <p><b>시간 제한</b>: 그리기는 별도 스레드에서 {@value #TIMEOUT_SECONDS}초만 기다린다. Java2D 는 중간에 멈출 수 없어 시간을 넘긴 작업은 스스로 끝날 때까지 남을 수 있으므로
 * 스레드를 {@value #MAX_THREADS}개로 막아 두고, 자리가 없으면 {@link ImageBusyException}(503 {@code IMAGE_BUSY}) 이다.
 */
final class SvgRasterizer {

    private static final Logger log = LoggerFactory.getLogger(SvgRasterizer.class);

    static final int TIMEOUT_SECONDS = 5;
    static final int MAX_THREADS = 4;

    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadFactory THREADS = task -> {
        Thread thread = new Thread(task, "svg-raster-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(0, MAX_THREADS, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), THREADS);

    private SvgRasterizer() {
    }

    /**
     * {@code source}(검사를 통과한 글)를 각 {@code sides} 크기의 정사각 ARGB 그림으로 그린다. 못 그리면 {@link ValidationException}({@code fields.svgSource}),
     * 그리는 자리가 없으면 {@link ImageBusyException}.
     */
    static List<BufferedImage> rasterize(String source, int[] sides) {
        Future<List<BufferedImage>> future;
        try {
            future = EXECUTOR.submit(() -> draw(source, sides));
        } catch (RejectedExecutionException e) {
            log.warn("SVG 그리기 스레드 자리가 없다 max={}", MAX_THREADS);
            throw new ImageBusyException();
        }
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("SVG 그리기가 {}초를 넘겨 포기했다(스레드는 스스로 끝날 때까지 남을 수 있다)", TIMEOUT_SECONDS);
            throw invalid("site.svg.tooSlow");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw invalid("site.svg.cannotDraw");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ValidationException validation) {
                throw validation;
            }
            if (cause instanceof OutOfMemoryError oom) {
                throw oom;
            }
            log.warn("SVG 를 그리지 못했다", cause);
            throw invalid(looksLikeFontProblem(cause) ? "site.svg.fontProblem" : "site.svg.cannotDraw");
        }
    }

    /** 그리는 스레드 안에서 도는 일 — 읽고, 크기마다 그리고, 보이는 것이 있는지 본다 */
    private static List<BufferedImage> draw(String source, int[] sides) {
        SVGDocument document = load(source);
        List<BufferedImage> images = new ArrayList<>(sides.length);
        for (int side : sides) {
            images.add(render(document, side));
        }
        if (isBlank(images.get(images.size() - 1))) {
            throw invalid("site.svg.nothingDrawn");
        }
        return images;
    }

    private static SVGDocument load(String source) {
        LoaderContext context = LoaderContext.builder()
                .externalResourcePolicy(ResourcePolicy.DENY_EXTERNAL)
                .documentLimits(DocumentLimits.DEFAULT)
                .build();
        XMLInput input = () -> {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            return factory.createXMLEventReader(new StringReader(source));
        };
        SVGDocument document = new SVGLoader().load(input, null, context);
        if (document == null) {
            throw invalid("site.svg.cannotDraw");
        }
        return document;
    }

    private static BufferedImage render(SVGDocument document, int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            document.render(null, g, new ViewBox(0, 0, side, side));
        } finally {
            g.dispose();
        }
        return image;
    }

    /** 모든 화소가 완전히 투명한가 */
    private static boolean isBlank(BufferedImage image) {
        int width = image.getWidth();
        int[] row = new int[width];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int argb : row) {
                if ((argb >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 글꼴 환경(글꼴이 하나도 없는 서버 등)에서 글자를 그리다 난 오류인가 */
    private static boolean looksLikeFontProblem(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("font")) {
                return true;
            }
            for (StackTraceElement frame : t.getStackTrace()) {
                String name = frame.getClassName();
                if (name.startsWith("sun.font.") || name.startsWith("java.awt.Font") || name.startsWith("sun.awt.FontConfiguration")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ValidationException invalid(String key) {
        return ValidationException.of(SvgInspector.FIELD, new MessageRef(key));
    }
}
