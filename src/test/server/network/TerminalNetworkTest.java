package test.server.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import server.core.model.Event;
import server.network.JsonSocket;
import server.network.TerminalNetwork;
import server.network.data.Message;

import java.io.IOException;

import static org.junit.Assert.*;

public class TerminalNetworkTest {
    public static final int port = 12345;
    public static final String host = "localhost";
    public static final String token = "123456";
    private TerminalNetwork terminal;

    @Before
    public void createTerminalNetwork() {
        terminal = new TerminalNetwork(token);
        terminal.setHandler(new TerminalNetwork.TerminalInterface() {
            @Override
            public void putEvent(Event event) {
                System.out.println("event received");
            }

            @Override
            public Message runCommand(Message command) {
                return new Message("report", null);
            }
        });
        terminal.listen(port);
    }

    @After
    public void terminateTerminalNetwork() {
        terminal.terminate();
    }

    @Test(timeout = 500)
    public void wrongToken() {
        JsonSocket client = null;
        try {
            client = new JsonSocket(host, port);
            client.send("123456"); // token must be of type Message, not an integer
        } catch (IOException e) {
            fail(e.getMessage());
        }
        try {
            Message init = client.get(Message.class); // socket must be closed by the server
            fail();
        } catch (IOException ignored) {}
    }

    @Test(timeout = 500)
    public void initMessage() {
        JsonSocket client;
        try {
            client = new JsonSocket(host, port);
            client.send(new Message("token", new Object[] {token}));
            Message init = client.get(Message.class);
            assertEquals("init", init.name);
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test(timeout = 500)
    public void sendCommand() {
        JsonSocket client;
        try {
            client = new JsonSocket(host, port);
            client.send(new Message("token", new Object[] {token}));
            Message init = client.get(Message.class);
            client.send(new Message("command", new Object[] {}));
            Message report = client.get(Message.class);
            assertEquals(report.name, "report");
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }
}