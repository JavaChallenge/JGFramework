import java.util.Scanner;

/**
 * Created by Saeed on 1/23/2015.
 */
public class TerminalApp {
    public static void main(String[] args) {
        Terminal terminal = Terminal.getInstance();

        Scanner scanner = new Scanner(System.in);
        System.out.println("<< JGFramework's Terminal >>");

        while (true) {
            String command = scanner.nextLine();
            terminal.handleCommand(command);
        }
    }
}
