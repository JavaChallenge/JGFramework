import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import server.network.JsonSocket;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * Created by Saeed on 1/23/2015.
 */
public class Terminal {
    private static final String FILE_PATH = "../JGFramework/Terminal/Resources/ip-port.conf";

    private static final String CMD_TYPE_CONNECT = "connect";
    private static final String CMD_TYPE_DISCONNECT = "disconnect";
    private static final String CMD_TYPE_SETPORT = "set-port";
    private static final String CMD_TYPE_SETIP = "set-ip";

    private static Terminal terminal = null;
    private String ip;
    private int port;
    private JsonSocket jsonSocket;

    public Terminal() {
        try {
            setIpAndPortFromFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Terminal getInstance() {
        if (terminal == null) {
            terminal = new Terminal();
        }
        return terminal;
    }

    public void writeIpAndPortToFile(String ip, int port) throws IOException {
        Gson gson = new Gson();
        String json = gson.toJson(new TerminalAppConfig(ip,port));
        FileWriter writer = new FileWriter(FILE_PATH);
        writer.write(json);
        writer.close();

    }

    public void setIpAndPortFromFile() throws IOException {


        Gson gson = new Gson();

        try {
            BufferedReader br = new BufferedReader( new FileReader(FILE_PATH));
            TerminalAppConfig terminalAppConfig= gson.fromJson(br, TerminalAppConfig.class);
            this.port = terminalAppConfig.getPort();
            this.ip = terminalAppConfig.getIp();
        } catch (JsonParseException e) {
            throw new RuntimeException("Terminal App config file does not meet expected syntax");
        }
    }

    public void handleCommand(String cmd) {
        StringTokenizer tokenizer = new StringTokenizer(cmd, " ");
        ArrayList<String> command = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            command.add(tokenizer.nextToken());
        }

        String cmdType = command.get(0);

        switch (cmdType) {
            case CMD_TYPE_CONNECT:
                try {
                    handleConnectCmd();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                break;

            case CMD_TYPE_DISCONNECT:
                try {
                    handleDisconnectCmd();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case CMD_TYPE_SETIP:
                try {
                    handleSetIpCmd(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case CMD_TYPE_SETPORT:
                try {
                    handleSetPortCmd(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            default:
                //TODO it's an external command ...
        }

    }

    private void handleConnectCmd() throws IOException, NoSuchAlgorithmException {

        System.out.print("Enter the password: ");
        Scanner scanner = new Scanner(System.in);
        String password = scanner.nextLine();
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(password.getBytes());
        byte[] bytes = md5.digest(); //This has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        //Get complete hashed password in hex format
        String token = sb.toString();

        jsonSocket = new JsonSocket(ip, port);
        jsonSocket.send(token);

        System.out.println("Successfully connected!");
    }

    private void handleDisconnectCmd() throws IOException {
        if (jsonSocket != null) {// it's connected
            jsonSocket.close();
            System.out.println("Successfully disconnected!");
        }
        else System.out.println("You are not connected yet!");
    }

    private void handleSetIpCmd(ArrayList<String> command) throws IOException {
        if (jsonSocket != null) {
            System.out.println("Cannot change IP or Port while connecting to server.");
            return;
        }
        if (command.get(1).equals("-s")) {
            this.ip = command.get(2);
            writeIpAndPortToFile(this.ip, this.port);
        } else {
            this.ip = command.get(1);
        }

        System.out.println("IP changed successfully.");
    }

    private void handleSetPortCmd(ArrayList<String> command) throws IOException {
        if (jsonSocket != null) {
            System.out.println("Cannot change IP or Port while connecting to server.");
            return;
        }
        if (command.get(1).equals("-s")) {
            String port = command.get(2);
            System.out.println(port);
            this.port = Integer.parseInt(port);
            writeIpAndPortToFile(this.ip, this.port);
        } else {
            String port = command.get(1);
            this.port = Integer.parseInt(port);
        }

        System.out.println("Port changed successfully.");
    }

    private static class TerminalAppConfig {
        private String ip;
        private int port;

        public TerminalAppConfig(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            if (port > 0 && port <= 65535) {
                return port;
            } else {
                throw new RuntimeException("Invalid ui port number in config file");
            }
        }
    }

}