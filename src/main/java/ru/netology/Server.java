package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private ConcurrentHashMap<String, ConcurrentHashMap<String, Handler>> handlers = new ConcurrentHashMap<>();
    final List validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private ExecutorService thread;

    public void run(int port) {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            thread = Executors.newFixedThreadPool(64);
            while (true) {
                final Socket socket = serverSocket.accept();
                thread.submit(() -> {
                    try {
                        response(socket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new ConcurrentHashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

    public void response(Socket socket) throws IOException {
        final var in = new BufferedInputStream(socket.getInputStream());
        final var out = new BufferedOutputStream(socket.getOutputStream());
        Request request = null;
        try {
            request = Request.makeRequest(in);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        if (request == null && !handlers.containsKey(request.getMethod())) {
            notFound(out);
            return;
        }
        ConcurrentHashMap<String, Handler> hMap = handlers.get(request.getMethod());
        String reqPath = request.getPath();
        if (hMap.containsKey(reqPath)) {
            Handler handler = hMap.get(reqPath);
            handler.handle(request, out);
        } else {
            if (!validPaths.contains(request.getPath())) {
                notFound(out);
            } else {
                normalResponse(out, request.getPath());
            }
        }
    }

    void normalResponse(BufferedOutputStream out, String path) throws IOException {
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            return;
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    public void notFound(BufferedOutputStream out) {
        try {
            out.write((
                    "HTTP/1.1 400 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}