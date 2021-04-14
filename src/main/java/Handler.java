import org.apache.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Handler extends Thread {
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>() {{
        put("jpg", "image/jpeg");
        put("html", "text/html");
        put("json", "application/json");
        put("txt", "text/plain");
        put("", "text/plain");
    }};
    private static final String NOT_FOUND_MESSAGE = "NOT FOUND";
    private static final String ALLOW_METHODS = "GET, POST, OPTIONS";
    private static final Logger log = Logger.getLogger(Handler.class);
    private List<String> headers;

    private Socket socket;
    private String directory;

    Handler(Socket socket, String directory, List<String> headers) {
        this.socket = socket;
        this.directory = directory;
        this.headers = headers;
    }

    @Override
    public void run() {
        try (InputStream inputStream = socket.getInputStream(); OutputStream outputStream = socket.getOutputStream()) {
            InputStreamReader isReader = new InputStreamReader(inputStream);
            BufferedReader br = new BufferedReader(isReader);
            String[] requestInfo = this.getRequestInfo(br);
            assert requestInfo != null;
            String method = requestInfo[0];
            String url = requestInfo[1];
            if (method.equals("POST")) {
                this.writeInfoFile(br, outputStream);
            }
            Path filePath = Path.of(this.directory + url);
            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String extension = this.getFileExtension(filePath);
                String type = CONTENT_TYPES.get(extension);
                byte[] fileBytes = Files.readAllBytes(filePath);
                this.sendHeader(outputStream, method, 200, "OK", type, fileBytes.length);
                outputStream.write(fileBytes);
                log.info("GET Request OK");
            } else {
                String type = CONTENT_TYPES.get("text");
                this.sendHeader(outputStream, method, 404, "Not Found", type, NOT_FOUND_MESSAGE.length());
                outputStream.write(NOT_FOUND_MESSAGE.getBytes());
                log.info("GET Request Fail");
            }

        } catch (
                IOException e) {
            e.printStackTrace();
        }

    }

    private void writeInfoFile(BufferedReader br, OutputStream outputStream) throws IOException {
        String payload = this.getPayload(br);
        Path filePath = Path.of(this.directory + "/write.txt");
        if (Files.exists(filePath)) {
            try {
                Files.writeString(filePath, payload + ", ", StandardOpenOption.APPEND);
                log.info("POST Request OK");
            } catch (IOException x) {
                System.err.format("IOException: %s%n", x);
            }
        } else {
            String type = CONTENT_TYPES.get("text");
            this.sendHeader(outputStream, "POST", 500, "Not Found", type, NOT_FOUND_MESSAGE.length());
            outputStream.write(NOT_FOUND_MESSAGE.getBytes());
            log.info("POST Request Fail");
        }
    }

    private String[] getRequestInfo(BufferedReader br) throws IOException {
        String line = br.readLine();
        try {
            while (br.readLine().length() != 0) {
            }
        } catch (NullPointerException ignored) {
        }
        try {
            return line.split(" ");
        }catch (NullPointerException e){
            return null;
        }
    }

    private String getPayload(BufferedReader br) throws IOException {
        StringBuilder payload = new StringBuilder();
        while (br.ready()) {
            payload.append((char) br.read());
        }
        if (payload.isEmpty()){
            return null;
        }else{
            System.out.println("Payload data is: " + payload.toString().split("=")[1]);
            return payload.toString().split("=")[1];
        }
    }


    private void sendHeader(OutputStream outputStream, String method, int statusCode, String statusText, String type, long length) {
        PrintStream printStream = new PrintStream(outputStream);
        printStream.printf("HTTP/1.1 %s %s%n", statusCode, statusText);
        if (!headers.isEmpty()){
            for (String head : headers){
                printStream.printf(head+"%n");
            }
        }
        printStream.printf("Access-Control-Allow-Origin: %s%n", "http://localhost:8080");
        if (method.equals("OPTIONS")) printStream.printf("Allow: %s%n", ALLOW_METHODS);
        printStream.printf("Access-Control-Method: %s%n", method);
        printStream.printf("Content-Type: %s%n", type);
        printStream.printf("Content-Length: %s%n%n", length);
    }

    private String getFileExtension(Path path) {
        String name = path.getFileName().toString();
        int extensionStart = name.lastIndexOf(".");
        return extensionStart == -1 ? "" : name.substring(extensionStart + 1);
    }

    public synchronized String getDirectory() {
        return directory;
    }

    public synchronized void setDirectory(String directory) {
        this.directory = directory;
    }
}
