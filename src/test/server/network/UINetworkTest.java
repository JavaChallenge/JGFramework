package test.server.network;

import org.junit.*;
import org.junit.runners.MethodSorters;
import server.network.JsonSocket;
import server.network.UINetwork;
import server.network.data.Message;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UINetworkTest {
    public static final int port = 12346;
    public static final String host = "localhost";
    public static final String token = "123456";
    private UINetwork uiNetwork;

    @Before
    public void createUINetwork() throws InterruptedException {
        // create server before each test
        uiNetwork = new UINetwork(token);
        uiNetwork.listen(port);
    }

    @After
    public void terminateUINetwork() throws InterruptedException {
        // terminate server after each test
        uiNetwork.terminate();
        // we should wait for completion of the test (results may appear after a delay)
        Thread.sleep(500);
    }

    @Test(timeout = 2000)
    public void test1_wrong_token() throws IOException, InterruptedException {
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send wrong token (token must be an instance of Message, not a string)
        client.send(token);
        // wait for the server to check the token
        uiNetwork.waitForClient(1000);
        // client must be rejected by the server
        assertEquals(uiNetwork.hasClient(), false);
        // client must be closed due to incorrect token
        try {
            client.send("nothing!");
            fail();
        } catch (SocketException ignored) {
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test(timeout = 1000)
    public void test2_correct_token() throws IOException, InterruptedException {
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send correct token
        client.send(new Message("token", new Object[]{token}));
        // wait for the server to check the token
        uiNetwork.waitForClient();
        // token is correct, so client must be alive
        client.send("nothing!");
    }

    @Test(timeout = 1000)
    public void test3_send_message() throws IOException, InterruptedException {
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send correct token
        client.send(new Message("token", new Object[] {token}));
        // wait for the server to check the token
        uiNetwork.waitForClient();
        // send message from UINetwork to the client
        uiNetwork.sendNonBlocking(new Message("name", new Object[]{"arg0", "arg1"}));
        // get message
        Message msg = client.get(Message.class);
        // check data of received message
        assertEquals(msg.name, "name");
        assertEquals(msg.args.length, 2);
        assertEquals(msg.args[0], "arg0");
        assertEquals(msg.args[1], "arg1");
    }

    @Test(timeout = 1000)
    public void test4_10000_messages() throws IOException, InterruptedException {
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send correct token
        client.send(new Message("token", new Object[] {token}));
        // wait for the server to check the token
        uiNetwork.waitForClient();
        // init data to send
        int total = 10000;
        double data[] = new double[total];
        for (int i = 0; i < total; i++)
            data[i] = Math.random();
        // send messages from server
        long start = System.currentTimeMillis();
        for (int i = 0; i < total; i++) {
            uiNetwork.sendNonBlocking(new Message("msg", new Object[]{data[i]}));
        }
        long end = System.currentTimeMillis();
        System.out.printf("total sending time: %d ms/%d msg%n", end-start, total);
        // get messages on client side
        start = System.currentTimeMillis();
        for (int i = 0; i < total; i++)
            assertEquals(client.get(Message.class).args[0], data[i]);
        end = System.currentTimeMillis();
        System.out.printf("total receiving time: %d ms/%d msg%n", end-start, total);
    }

    @Test(timeout = 2000)
    public void test5_multiple_clients() throws IOException, InterruptedException {
        // client1
        JsonSocket client1 = new JsonSocket(host, port);
        client1.send(new Message("token", new Object[]{token}));
        uiNetwork.waitForClient();
        uiNetwork.sendNonBlocking(new Message("to client1", null));
        // client2
        JsonSocket client2 = new JsonSocket(host, port);
        client2.send(new Message("token", new Object[]{token}));
        uiNetwork.waitForNewClient();
        uiNetwork.sendNonBlocking(new Message("to client2", null));
        // client3 (wrong token)
        JsonSocket client3 = new JsonSocket(host, port);
        client3.send(token);
        uiNetwork.waitForNewClient(1000);
        uiNetwork.sendNonBlocking(new Message("to client2 again", null));
        // get messages
        Message msg1 = client1.get(Message.class);
        Message msg2 = client2.get(Message.class);
        Message msg3 = client2.get(Message.class);
        assertEquals(msg1.name, "to client1");
        assertEquals(msg2.name, "to client2");
        assertEquals(msg3.name, "to client2 again");
    }

    @Test
    public void test6_token_timeout() throws IOException, InterruptedException {
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // timeout (11 seconds)
        Thread.sleep(11000);
        // client must be closed due to timeout
        try {
            client.send(new Message("token", new Object[] {token}));
            fail();
        } catch (SocketException ignored) {
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

}