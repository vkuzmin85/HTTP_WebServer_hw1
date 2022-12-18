package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
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
                thread.submit(() -> request(socket));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void request(Socket socket) {
        try {
            final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            final var out = new BufferedOutputStream(socket.getOutputStream());
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");
            if (parts.length != 3) {
                socket.close();
            } else {
                response(parts, out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new ConcurrentHashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

    public void response(String[] parts, BufferedOutputStream out) throws IOException {
        final var method = parts[0];
        final var path = parts[1];

        Request request;
        if (method != null) {
            request = new Request(method, path);
        } else {
            notFound(out);
            return;
        }
        if (!handlers.containsKey(request.getMethod())) {
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
                normalResponse(out, path);
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