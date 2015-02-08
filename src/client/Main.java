package client;

/**
 * Initial point of execution.
 */
public class Main {

    public static void main(String[] args) {
        new Controller("resources/client/connection.conf").start();
    }

}
