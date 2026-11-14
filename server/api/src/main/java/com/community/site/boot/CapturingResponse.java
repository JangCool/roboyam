package com.community.site.boot;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;

/**
 * 서버 안에서 API 하나를 부를 때({@link BootPreloadRenderer}) 그 응답을 <b>메모리에 받아 두는</b> 응답 — 상태 코드·본문·Content-Type 만 기억하고 나머지 머리글은 버린다.
 * 쿠키({@code Set-Cookie} — 로그인 정보 API 가 세션 쿠키 기한을 늘린다)만은 원래 응답으로 넘긴다(화면이 그 API 를 직접 불렀을 때와 같게).
 *
 * <p>컨테이너는 include 된 쪽이 원래 응답의 상태·머리글을 바꾸지 못하게 이 객체 아래에 자기 포장을 끼운다 — 그래서 쿠키는 처음 받은 원래 응답({@link #origin})에 직접 쓴다.
 */
final class CapturingResponse extends HttpServletResponseWrapper {

    private final HttpServletResponse origin;
    private final ByteArrayOutputStream body = new ByteArrayOutputStream(1024);
    private ServletOutputStream stream;
    private PrintWriter writer;
    private int status = SC_OK;
    private String contentType;

    CapturingResponse(HttpServletResponse origin) {
        super(origin);
        this.origin = origin;
    }

    int status() {
        return status;
    }

    String contentType() {
        return contentType;
    }

    byte[] body() {
        if (writer != null) {
            writer.flush();
        }
        return body.toByteArray();
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void sendError(int sc) {
        this.status = sc;
    }

    @Override
    public void sendError(int sc, String msg) {
        this.status = sc;
    }

    @Override
    public void sendRedirect(String location) {
        this.status = SC_FOUND;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        // 본문은 UTF-8 로 받는다
    }

    @Override
    public void setContentLength(int len) {
        // 길이는 받은 본문으로 안다
    }

    @Override
    public void setContentLengthLong(long len) {
        // 길이는 받은 본문으로 안다
    }

    @Override
    public void addCookie(Cookie cookie) {
        origin.addCookie(cookie);
    }

    @Override
    public void setHeader(String name, String value) {
        if (HttpHeaders.SET_COOKIE.equalsIgnoreCase(name)) {
            origin.addHeader(name, value);
        } else if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
            this.contentType = value;
        }
    }

    @Override
    public void addHeader(String name, String value) {
        setHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        // 버린다
    }

    @Override
    public void addIntHeader(String name, int value) {
        // 버린다
    }

    @Override
    public void setDateHeader(String name, long date) {
        // 버린다
    }

    @Override
    public void addDateHeader(String name, long date) {
        // 버린다
    }

    @Override
    public boolean containsHeader(String name) {
        return HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) && contentType != null;
    }

    @Override
    public String getHeader(String name) {
        return HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) ? contentType : null;
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void reset() {
        resetBuffer();
        this.status = SC_OK;
        this.contentType = null;
    }

    @Override
    public void resetBuffer() {
        body.reset();
    }

    @Override
    public void flushBuffer() {
        // 원래 응답으로 내보내지 않는다
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (stream == null) {
            stream = new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    body.write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    throw new UnsupportedOperationException("비동기 쓰기는 받지 않는다");
                }
            };
        }
        return stream;
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8));
        }
        return writer;
    }
}
