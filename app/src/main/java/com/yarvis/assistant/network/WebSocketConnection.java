package com.yarvis.assistant.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

public class WebSocketConnection {

    private static final String TAG = "WebSocketConnection";
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final URI uri;
    private final Callback callback;
    private final ExecutorService executor;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    public interface Callback {
        void onOpen();
        void onMessage(String message);
        void onClose(int code, String reason);
        void onError(Exception ex);
    }

    public WebSocketConnection(URI uri, Callback callback) {
        this.uri = uri;
        this.callback = callback;
        this.executor = Executors.newCachedThreadPool();
    }

    public void connect() {
        executor.execute(this::doConnect);
    }

    public void send(String message) {
        if (!connected.get()) return;

        executor.execute(() -> {
            try {
                sendFrame(message);
            } catch (IOException e) {
                Log.e(TAG, "Send error: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (closing.getAndSet(true)) return;

        executor.execute(() -> {
            try {
                if (connected.get()) {
                    sendCloseFrame();
                }
            } catch (IOException e) {
                Log.e(TAG, "Close error: " + e.getMessage());
            } finally {
                cleanup();
            }
        });
    }

    public boolean isConnected() {
        return connected.get();
    }

    private void doConnect() {
        try {
            boolean ssl = "wss".equalsIgnoreCase(uri.getScheme());
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = ssl ? 443 : 80;
            }

            if (ssl) {
                socket = SSLSocketFactory.getDefault().createSocket(host, port);
            } else {
                socket = new Socket(host, port);
            }

            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);

            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            if (!performHandshake(host, port)) {
                throw new IOException("WebSocket handshake failed");
            }

            connected.set(true);
            callback.onOpen();

            readFrames();

        } catch (Exception e) {
            Log.e(TAG, "Connection error: " + e.getMessage());
            callback.onError(e);
            cleanup();
        }
    }

    private boolean performHandshake(String host, int port) throws IOException {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getQuery() != null) path += "?" + uri.getQuery();

        String request = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + (port != 80 && port != 443 ? ":" + port : "") + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n";

        outputStream.write(request.getBytes());
        outputStream.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();

        if (line == null || !line.contains("101")) {
            Log.e(TAG, "Handshake failed: " + line);
            return false;
        }

        String acceptKey = null;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("sec-websocket-accept:")) {
                acceptKey = line.substring(21).trim();
            }
        }

        String expectedKey = computeAcceptKey(key);
        if (!expectedKey.equals(acceptKey)) {
            Log.e(TAG, "Invalid accept key");
            return false;
        }

        return true;
    }

    private String computeAcceptKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest((key + WEBSOCKET_GUID).getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private void readFrames() {
        try {
            while (connected.get() && !closing.get()) {
                int firstByte = inputStream.read();
                if (firstByte == -1) break;

                int secondByte = inputStream.read();
                if (secondByte == -1) break;

                boolean fin = (firstByte & 0x80) != 0;
                int opcode = firstByte & 0x0F;
                boolean masked = (secondByte & 0x80) != 0;
                int payloadLength = secondByte & 0x7F;

                if (payloadLength == 126) {
                    payloadLength = (inputStream.read() << 8) | inputStream.read();
                } else if (payloadLength == 127) {
                    inputStream.read(); inputStream.read();
                    inputStream.read(); inputStream.read();
                    payloadLength = (inputStream.read() << 24) | (inputStream.read() << 16) |
                            (inputStream.read() << 8) | inputStream.read();
                }

                byte[] maskKey = null;
                if (masked) {
                    maskKey = new byte[4];
                    inputStream.read(maskKey);
                }

                byte[] payload = new byte[payloadLength];
                int bytesRead = 0;
                while (bytesRead < payloadLength) {
                    int read = inputStream.read(payload, bytesRead, payloadLength - bytesRead);
                    if (read == -1) break;
                    bytesRead += read;
                }

                if (masked && maskKey != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= maskKey[i % 4];
                    }
                }

                switch (opcode) {
                    case 0x1:
                        String message = new String(payload, "UTF-8");
                        callback.onMessage(message);
                        break;

                    case 0x8:
                        int code = payloadLength >= 2 ?
                                ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF) : 1000;
                        String reason = payloadLength > 2 ?
                                new String(payload, 2, payloadLength - 2, "UTF-8") : "";
                        callback.onClose(code, reason);
                        cleanup();
                        return;

                    case 0x9:
                        sendPongFrame(payload);
                        break;

                    case 0xA:
                        break;
                }
            }
        } catch (Exception e) {
            if (!closing.get()) {
                Log.e(TAG, "Read error: " + e.getMessage());
                callback.onError(e);
            }
        } finally {
            cleanup();
        }
    }

    private void sendFrame(String message) throws IOException {
        byte[] payload = message.getBytes("UTF-8");
        sendFrame(0x1, payload);
    }

    private void sendCloseFrame() throws IOException {
        sendFrame(0x8, new byte[0]);
    }

    private void sendPongFrame(byte[] payload) throws IOException {
        sendFrame(0xA, payload);
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        if (outputStream == null) return;

        outputStream.write(0x80 | opcode);

        int length = payload.length;
        if (length < 126) {
            outputStream.write(0x80 | length);
        } else if (length < 65536) {
            outputStream.write(0x80 | 126);
            outputStream.write((length >> 8) & 0xFF);
            outputStream.write(length & 0xFF);
        } else {
            outputStream.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                outputStream.write((length >> (8 * i)) & 0xFF);
            }
        }

        byte[] maskKey = new byte[4];
        new SecureRandom().nextBytes(maskKey);
        outputStream.write(maskKey);

        for (int i = 0; i < payload.length; i++) {
            outputStream.write(payload[i] ^ maskKey[i % 4]);
        }

        outputStream.flush();
    }

    private void cleanup() {
        connected.set(false);

        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {}

        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {}

        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        inputStream = null;
        outputStream = null;
        socket = null;
    }
}
