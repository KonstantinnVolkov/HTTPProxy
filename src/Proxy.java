import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

public class Proxy extends Thread {

    private static final ServerSocket serverSocket;
    private final int bufferSize = 65536;

    private static final int PORT = 8080;

    static {
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(){
        while (true) {
            try {
                Socket client = serverSocket.accept();
                new Thread(() ->
                        listenClient(client))
                        .start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void listenClient(Socket client) {
        DataInputStream browserInput = null;
        DataOutputStream browserOutput = null;
        try {
            browserInput = new DataInputStream(client.getInputStream());
            browserOutput = new DataOutputStream(client.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] buffer = new byte[bufferSize];
        while (client.isConnected()) {
            try {
                int readCount = browserInput.read(buffer);
                processRequest(buffer, browserInput, browserOutput, readCount);
            } catch (Exception e) {
                try {
                    client.close();
                    return;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processRequest(byte[] request, DataInputStream browserInput, DataOutputStream browserOutput, int length) {
        try {
            String inputStr = new String(request, StandardCharsets.UTF_8);
            String rawHostName = parseHostName(inputStr);
            List<String> hostName = List.of(rawHostName.split(":"));
            Socket server;
            if (hostName.size() == 1) {
                server = new Socket(hostName.get(0), 80);
            } else {
                server = new Socket(hostName.get(0), Integer.parseInt(hostName.get(1)));
            }
            var serverInput = server.getInputStream();
            var serverOutput = server.getOutputStream();
            byte[] deletedHostName = deleteHostName(inputStr).getBytes();

            try {
                serverOutput.write(deletedHostName, 0, deletedHostName.length);
                byte[] answerBuffer = new byte[bufferSize];
                int readCount = serverInput.read(answerBuffer);
                if (readCount <= 0) {
                    return;
                }
                String answer = new String(answerBuffer, StandardCharsets.UTF_8);
                String responseCode = parseResponseCode(answer);
                if (responseCode != null) {
                    System.out.println(hostName.get(0) + " " + responseCode);
                }
                browserOutput.write(answerBuffer, 0, readCount);
                serverInput.transferTo(browserOutput);
            } catch (Exception e) {
//                e.printStackTrace();
            }
            finally {
                serverOutput.flush();
                serverInput.close();
                serverOutput.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                browserOutput.flush();
                browserOutput.close();
                browserInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String parseHostName(String input) {
        List<String> strings = List.of(input.trim().split("\r\n"));
        String hostName = strings.stream()
                .filter(s -> s.startsWith("Host: "))
                .map(s -> s.substring(6))
                .findFirst()
                .orElse("");
        return hostName;
    }

    private String parseResponseCode(String input) {
        List<String> strings = List.of(input.trim().split("\r\n"));
        String header = strings.get(0);
        List<String> response = List.of(header.split(" "));
        if (response.size() < 2) {
            return null;
        }
        return response.get(1);
    }

    private String deleteHostName(String input) {
        Pattern pattern = Pattern.compile("http://[a-z\\dа-я:.]*");
        return pattern.matcher(input).replaceAll("");
    }
}

