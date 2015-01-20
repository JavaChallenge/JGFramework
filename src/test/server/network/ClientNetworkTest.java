package test.server.network;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import server.network.ClientNetwork;
import server.network.JsonSocket;
import server.network.data.Message;

import java.net.SocketException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientNetworkTest {

    public static final int port = 12346;
    public static final String host = "localhost";
    private ClientNetwork clientNetwork;

    @Before
    public void createClientNetwork() throws Exception {
        // create server before each test
        clientNetwork = new ClientNetwork();
    }

    @After
    public void terminateClientNetwork() throws Exception {
        // terminate server after each test
        clientNetwork.terminate();
        clientNetwork.omitAllClients();
        // we should wait for completion of the test (results may appear after a delay)
        Thread.sleep(500);
    }

    @Test(timeout = 5000)
    public void test1_single_client_wrong_token() throws Exception {
        String token = "123456";
        // define client
        int id = clientNetwork.defineClient(token);
        // run server
        clientNetwork.listen(port);
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send wrong token (token must be an instance of Message, not a string)
        client.send(token);
        // wait for the server to check the token
        clientNetwork.waitForClient(id, 1000);
        // client must be rejected by the server
        assertEquals(clientNetwork.isConnected(id), false);
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
    public void test2_single_client_correct_token() throws Exception {
        String token = "123456";
        // define client
        int id = clientNetwork.defineClient(token);
        // run server
        clientNetwork.listen(port);
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send correct token
        client.send(new Message("token", new Object[]{token}));
        // wait for the server to check the token
        clientNetwork.waitForClient(id);
        // token is correct, so client must be alive
        client.send(new Message("nothing!", null));
    }

    @Test(timeout = 1000)
    public void test3_send_message_100_clients() throws Exception {
        int n = 100;
        int ids[] = new int[n];
        String tokens[] = new String[n];
        JsonSocket clients[] = new JsonSocket[n];
        // define clients
        for (int i = 0; i < n; i++) {
            tokens[i] = "random double: " + Math.random();
            ids[i] = clientNetwork.defineClient(tokens[i]);
        }
        // run server
        clientNetwork.listen(port);
        // create clients and send tokens
        for (int i = 0; i < n; i++) {
            clients[i] = new JsonSocket(host, port);
            clients[i].send(new Message("token", new Object[]{tokens[i]}));
        }
        // wait for the server to handle incoming messages
        clientNetwork.waitForAllClients();

        long queueTime = System.currentTimeMillis();
        // these random numbers will be sent to clients
        double randoms[] = new double[n];
        for (int i = 0; i < n; i++) {
            randoms[i] = Math.random();
            clientNetwork.queue(ids[i], new Message("test message", new Object[]{"arg0", randoms[i]}));
        }
        queueTime = System.currentTimeMillis() - queueTime;
        long sendTime = System.currentTimeMillis();
        clientNetwork.sendAllBlocking();
        sendTime = System.currentTimeMillis() - sendTime;
        // check received messages
        for (int i = 0; i < n; i++) {
            // get message
            Message msg = clients[i].get(Message.class);
            // check data of received message
            assertEquals(msg.name, "test message");
            assertEquals(msg.args.length, 2);
            assertEquals(msg.args[0], "arg0");
            assertEquals(msg.args[1], randoms[i]);
        }
        System.out.printf("queue time: %dms%n", queueTime);
        System.out.printf("send time: %dms%n", sendTime);
    }

    @Test(timeout = 2000)
    public void test4_10_clients_1000_messages() throws Exception {
        int n = 10;
        int m = 1000;

        int ids[] = new int[n];
        String tokens[] = new String[n];
        JsonSocket clients[] = new JsonSocket[n];
        // define clients
        for (int i = 0; i < n; i++) {
            tokens[i] = "random double: " + Math.random();
            ids[i] = clientNetwork.defineClient(tokens[i]);
        }
        // run server
        clientNetwork.listen(port);
        // create clients and send tokens
        for (int i = 0; i < n; i++) {
            clients[i] = new JsonSocket(host, port);
            clients[i].send(new Message("token", new Object[]{tokens[i]}));
        }
        // wait for the server to handle incoming messages
        clientNetwork.waitForAllClients();

        Message msg[] = generateMessages(n);
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++)
                clientNetwork.queue(ids[i], msg[i]);
            // send to clients
            clientNetwork.sendAllBlocking();
            // check received messages
            for (int i = 0; i < n; i++) {
                // get message
                Message cm = clients[i].get(Message.class);
                // check data of received message
                assertEquals(cm.name, msg[i].name);
                for (int k = 0; k < msg[i].args.length; k++)
                    assertEquals(cm.args[k], msg[i].args[k]);
            }
        }
    }

    @Test(timeout = 1000)
    public void test5_wait_for_clients() throws Exception {
        // takes about 500 seconds!
        long startTime = System.currentTimeMillis();
        int n = 100;
        String tokens[] = new String[n];
        JsonSocket clients[] = new JsonSocket[n];
        // define clients
        for (int i = 0; i < n; i++) {
            tokens[i] = "random double: " + Math.random();
            clientNetwork.defineClient(tokens[i]);
        }
        // run server
        clientNetwork.listen(port);
        // create clients and send tokens
        // wrong token
        clients[0] = new JsonSocket(host, port);
        clients[0].send(tokens[0]);
        // correct token
        for (int i = 1; i < n; i++) {
            clients[i] = new JsonSocket(host, port);
            clients[i].send(new Message("token", new Object[]{tokens[i]}));
        }
        long endTime = System.currentTimeMillis();
        clientNetwork.waitForAllClients(950-(endTime-startTime));
        assertEquals(clientNetwork.getNumberOfConnected(), n-1);
    }

    @Test(timeout = 1000)
    public void test6_simple_send_receive() throws Exception {
        String token = "123456";
        // define client
        int id = clientNetwork.defineClient(token);
        // run server
        clientNetwork.listen(port);
        // create client
        JsonSocket client = new JsonSocket(host, port);
        // send correct token
        client.send(new Message("token", new Object[]{token}));
        // wait for the server to check the token
        clientNetwork.waitForClient(id);

        Message msg[] = generateMessages(10);
        client.send(msg[0]);
        client.send(msg[1]);
        clientNetwork.startReceivingAll();
        client.send(msg[2]);
        // wait for the server to get the message!
        Thread.sleep(10);
        clientNetwork.stopReceivingAll();
        client.send(msg[3]);
        client.send(msg[4]);
        Message m = clientNetwork.getReceived(id);
        assertEquals(m.name, msg[2].name);
        assertEquals(m.args[0], msg[2].args[0]);
        assertEquals(m.args[1], msg[2].args[1]);
    }

    @Test
    public void test7_long_test_garbage_collection() throws Exception {
        int n = 1000;
        // transfer 10000*n messages
        for (int i = 0; i < n; i++) {
            // use the same clientNetwork instance
            test4_10_clients_1000_messages();
            clientNetwork.terminate();
            clientNetwork.omitAllClients();
        }
        clientNetwork.listen(port);
    }

    public static Message generateMessage() {
        return new Message("msg#"+Math.random(), new Object[] {null, Math.random()});
    }

    public static Message[] generateMessages(int n) {
        Message messages[] = new Message[n];
        for (int i = 0; i < n; i++)
            messages[i] = generateMessage();
        return messages;
    }

}