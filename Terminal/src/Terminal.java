import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import network.JsonSocket;
import network.data.Message;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * Created by Saeed on 1/23/2015.
 */
public class Terminal {

    private final Scanner scanner = TerminalApp.globalScanner;

    private static final String CONFIG_PATH = "resources/terminal/ip-port.conf";

    private static final String CMD_TYPE_CONNECT = "connect";
    private static final String CMD_TYPE_DISCONNECT = "disconnect";
    private static final String CMD_TYPE_SET_PORT = "set-port";
    private static final String CMD_TYPE_SET_IP = "set-ip";
    private static final String CMD_TYPE_EXIT = "exit";

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
        FileWriter writer = new FileWriter(CONFIG_PATH);
        writer.write(json);
        writer.close();

    }

    public void setIpAndPortFromFile() throws IOException {


        Gson gson = new Gson();

        try {
            BufferedReader br = new BufferedReader( new FileReader(CONFIG_PATH));
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
                    getReport();
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

            case CMD_TYPE_SET_IP:
                try {
                    handleSetIpCmd(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case CMD_TYPE_SET_PORT:
                try {
                    handleSetPortCmd(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                if (jsonSocket == null) {
                    System.out.println("Command not found. Please connect to server to get more commands available.");
                    return;
                }
                //externalCommand.cmdType = cmdType;
                //command.remove(0);
                //externalCommand.args = command.toArray(new String[command.size()]);
                Object [] args = new Object[2];
                args[0] = cmdType;
                command.remove(0);
                args[1] = command.toArray(new String[command.size()]);
                Message message = new Message("command",args);
                try {
                    jsonSocket.send(message);
                    if (cmdType.equals(CMD_TYPE_EXIT)) {
                        System.exit(0);
                    } else {
                        getReport();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    private void getReport() {
        try {
            Message report = jsonSocket.get(Message.class);
            System.out.println(report.name + ": " + Arrays.deepToString(report.args));
            System.out.flush();
            if (report.name.equals(Message.NAME_WRONG_TOKEN))
                jsonSocket = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnectCmd() throws IOException, NoSuchAlgorithmException {

        String token;
        System.out.print("Enter the password: ");
        System.out.flush();
        String password = scanner.nextLine();

        if(password.equals("")) {
            token = "00000000000000000000000000000000";
        } else {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(password.getBytes());
            byte[] bytes = md5.digest(); //This has bytes in decimal format;
            //Convert it to hexadecimal format
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            //Get complete hashed password in hex format
            token = sb.toString();
        }
        jsonSocket = new JsonSocket(ip, port);

        Object[] args = new Object[1];

        args[0] = token;

        jsonSocket.send(new Message("token", args));
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