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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Server {

    private static final List<String> VALID_PATHS = List.of(
            "/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js",
            "/links.html", "/forms.html", "/classic.html",
            "/events.html", "/events.js"
    );

    private static final int PORT = 9999;
    private static final int THREADS_COUNT = 64;

    private final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    public void start() {
        final var executorService = Executors.newFixedThreadPool(THREADS_COUNT);

        try (final var serverSocket = new ServerSocket(PORT)) {
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    executorService.submit(getServerTask(socket));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        HANDLERS.put(method + " " + path, handler);
    }

    private Runnable getServerTask(Socket socket) {
        return () -> {
            try {
                handleConnection(socket);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    private void handleConnection(Socket socket) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            final var request = getRequest(in);
            if (request == null) {
                // just close socket
                return;
            }

            final var handler = HANDLERS.get(request.getMethod() + " " + request.getPath());
            if (handler == null) {
                if (!VALID_PATHS.contains(request.getPath())) {
                    makeNotFoundResponse(out);
                } else {
                    makeResponseWithContent(out, request.getPath());
                }
            } else {
                handler.handle(request, out);
            }
            out.flush();
        }
    }

    private Request getRequest(BufferedReader in) throws IOException {
        // read only request line for simplicity
        // must be in form GET /path HTTP/1.1
        final var requestLine = in.readLine();
        final String [] parts = requestLine.split(" ");

        if (parts.length != 3) {
            // just close socket
            return null;
        }

        final var headers = new StringBuilder();
        final var body = new StringBuilder();
        boolean hasBody = false;

        String inputLine = in.readLine();
        while (inputLine.length() > 0) {
            headers.append(inputLine);
            if (inputLine.startsWith("Content-Length: ")) {
                int index = inputLine.indexOf(':') + 1;
                String len = inputLine.substring(index).trim();
                if (Integer.parseInt(len) > 0) {
                    hasBody = true;
                }
            }
            inputLine = in.readLine();
        }

        if (hasBody) {
            inputLine = in.readLine();
            while (inputLine != null && inputLine.length() > 0) {
                body.append(inputLine);
                inputLine = in.readLine();
            }
        }

        return new Request(parts[0], getCleanPath(parts[1]), headers.toString(), body.toString());
    }

    private String getCleanPath(String path) {
        if (path.contains("?")) {
            return path.substring(0, path.indexOf("?"));
        }
        return path;
    }

    private void makeNotFoundResponse(BufferedOutputStream out) throws IOException {
        final var responseData = new ResponseData(404, "Not Found", null, 0);
        writeStatusAndHeaders(responseData, out);
    }

    private void writeStatusAndHeaders(ResponseData data, BufferedOutputStream out) throws IOException {
        final var respBuilder = new StringBuilder();

        respBuilder.append("HTTP/1.1 ").append(data.getCode()).append(" ").append(data.getDescription()).append("\r\n");

        if (data.getContentType() != null) {
            respBuilder.append("Content-Type: ").append(data.getContentType()).append("\r\n");
        }

        respBuilder.append("Content-Length: ").append(data.getContentLength()).append("\r\n");
        respBuilder.append("Connection: close\r\n");
        respBuilder.append("\r\n");

        out.write(respBuilder.toString().getBytes());
    }

    private void makeResponseWithContent(BufferedOutputStream out, String path) throws IOException {
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            String template = Files.readString(filePath);
            byte[] content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();

            final var responseData = new ResponseData(200, "OK", mimeType, content.length);

            writeStatusAndHeaders(responseData, out);
            out.write(content);

        } else {

            final var length = Files.size(filePath);
            final var responseData = new ResponseData(200, "OK", mimeType, length);

            writeStatusAndHeaders(responseData, out);
            Files.copy(filePath, out);
        }
    }

}

