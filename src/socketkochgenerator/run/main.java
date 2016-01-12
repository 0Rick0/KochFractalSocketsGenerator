/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package socketkochgenerator.run;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import socketkochgenerator.CacheManager.CacheManager;
import socketkochgenerator.calculate.KochManager;

/**
 *
 * @author Rick
 */
public class main {

    private static final String VERSION = "0.1";

    private static final List<KochManager> activeManagers = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Socket koch genrator version " + VERSION);

        System.out.println("Starting socket");

        Thread socketThread = new Thread(() -> {
            try {
                socketThread();
            } catch (IOException ex) {
                Logger.getLogger(main.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println("Fatal exception! must shut down!");
                System.out.println("Exiting...");
                System.exit(-1);
            }
        });
        socketThread.start();

        System.out.println("Write 'help' for help");

        boolean running = true;
        while (running) {
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("help")) {
                System.out.println("Commands: ");
                System.out.println("\tquit\tstop the server");
                System.out.println("\tstats\tshow statistics of the server");
            } else if (input.equalsIgnoreCase("quit")) {
                CacheManager.getInstance().stop();
                System.exit(0);
            } else if (input.equalsIgnoreCase("stats")) {
                System.out.println("WIP");
                System.out.println("Cleaning up");
                activeManagers.removeIf((m) -> m.notActive());
                System.out.println("Active clients: " + activeManagers.size());
            } else if(input.equalsIgnoreCase("levels")){
                System.out.println("Amount of levels: " + CacheManager.getInstance().getLevels());
            } else {
                System.out.println("Unkown command '" + input + "'");
            }
        }
    }

    private static void socketThread() throws IOException {
        ServerSocket server = new ServerSocket(4568);
        Socket conn;

        while ((conn = server.accept()) != null) {
            System.out.println("New client!" + conn.getInetAddress());
            activeManagers.add(new KochManager(conn));
        }
    }
}
