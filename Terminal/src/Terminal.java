import server.network.JsonSocket;
import server.network.data.Command;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * Created by Saeed on 1/23/2015.
 */
public class Terminal {
    private static final String FILE_PATH = "../JGFramework/Terminal/Resources/ip-port.txt";

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
        PrintWriter pw = new PrintWriter(FILE_PATH);
        pw.println(ip);
        pw.println(port);
        pw.close();
    }

    public void setIpAndPortFromFile() throws IOException {
        BufferedReader bf = new BufferedReader(new FileReader(FILE_PATH));
        String ip;
        String port;

        if ((ip = bf.readLine()) != null)
            this.ip = ip;

        if ((port = bf.readLine()) != null)
            this.port = Integer.parseInt(port);
        bf.close();
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
                } catch (IOException | NoSuchAlgorithmException e) {
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
                Command externalCommand = new Command();
                externalCommand.cmdType = cmdType;
                command.remove(0);
                externalCommand.args = command.toArray(new String[command.size()]);

                try {
                    jsonSocket.send(externalCommand);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
}
