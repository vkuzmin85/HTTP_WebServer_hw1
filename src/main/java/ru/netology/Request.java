package ru.netology;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

public class Request {
    public static final String GET = "GET";
    public static final String POST = "POST";
    private String path;
    private String method;
    private List<String> headers;
    private List<NameValuePair> queryParams;
    private String protocolVersion;
    private String body;

    private Request() {
    }

    private Request(String method, String path) {
        this.method = method;
        this.path = path;
    }

    private Request(String method, String path, String protocolVersion, List<String> headers, String body, List<NameValuePair> queryParams) {
        this.method = method;
        this.path = path;
        this.protocolVersion = protocolVersion;
        this.headers = headers;
        this.body = body;
        this.queryParams = queryParams;
        printRequest();
    }

    public static Request makeRequest(BufferedInputStream in) throws IOException, URISyntaxException {
        final var allowedMethods = List.of(GET, POST);

        // лимит на request line + заголовки
        final var limit = 4096;

        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        // ищем request line
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            return null;
        }

        // читаем request line
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            return null;
        }

        final var method = requestLine[0];
        if (!allowedMethods.contains(method)) {
            return null;
        }
        System.out.println(method);

        final var path = requestLine[1];
        if (!path.startsWith("/")) {
            return null;
        }
        final var protocolVersion = requestLine[2];
        List<NameValuePair> queryParams = URLEncodedUtils.parse(new URI(path), String.valueOf(StandardCharsets.UTF_8));

        // ищем заголовки
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            return null;
        }

        // отматываем на начало буфера
        in.reset();
        // пропускаем requestLine
        in.skip(headersStart);

        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
        System.out.println(headers);

        // для GET тела нет
        String body = null;
        if (!method.equals(GET)) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = in.readNBytes(length);

                body = new String(bodyBytes);
                System.out.println(body);
            }
        }
        return new Request(method, path, protocolVersion, headers, body, queryParams);
    }

    private void printRequest() {
        System.out.println("method: " + method);
        System.out.println("path: " + path);
        System.out.println("protocolVersion " + protocolVersion);
        System.out.println("headers " + headers);
        System.out.println("body " + body);
        System.out.println("queryParams: " + getPostParam());
        System.out.println("userID" + ":  " + getPostParam("userID"));
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public List<NameValuePair> getPostParam() {
        return queryParams;
    }

    public List<NameValuePair> getPostParam(String name) {
        return queryParams.stream()
                .filter(str -> Objects.equals(str.getName(), name))
                .collect(Collectors.toList());
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }
}
