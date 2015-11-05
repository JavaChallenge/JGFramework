import java.util.Scanner;

/**
 * Created by Saeed on 1/23/2015.
 */
public class TerminalApp {

    public static final Scanner globalScanner = new Scanner(System.in);

    public static void main(String[] args) {
        Terminal terminal = Terminal.getInstance();

        System.out.println("<< JGFramework's Terminal >>");

        while (globalScanner.hasNext()) {
            String command = globalScanner.nextLine();
            terminal.handleCommand(command);
        }
    }

}
