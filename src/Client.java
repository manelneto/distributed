import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private Player player;
    private long queueArrivalTime;

    /**
     * Constructs a new Client object with a given socket.
     * @param socket new Client's socket
     */
    Client(Socket socket) {
        this.socket = socket;
    }

    /**
     * Runs a new Client object, connecting it to a socket with a given hostname and a given port.
     * @param args HOSTNAME PORT
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("usage: Client <HOSTNAME> <PORT (>0)>");
            return;
        }

        String hostname = args[0];

        int port = Integer.parseInt(args[1]);
        if (port < 0) {
            System.out.println("Invalid port number: " + port + ". The port number must be greater than 0.");
            return;
        }

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(hostname, port));

            Client client = new Client(socket);
            client.waiting();
        } catch (IOException e) {
            System.out.println("Client exception: " + e.getMessage() + ".");
        }
    }

    /**
     * @return the Player associated with this Client
     */
    public Player getPlayer() {
        return this.player;
    }

    /**
     * @return the Socket associated with this Client
     */
    public Socket getSocket() {
        return this.socket;
    }

    /**
     * @return the time when this Client arrived at the waiting queue
     */
    public long getQueueArrivalTime() {
        return this.queueArrivalTime;
    }

    /**
     * @param player Player to associate with this Client
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /**
     * @param socket Socket to associate with this Client
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /**
     * @param queueArrivalTime time when this Client arrived at the waiting queue
     */
    public void setQueueArrivalTime(long queueArrivalTime) {
        this.queueArrivalTime = queueArrivalTime;
    }

    /**
     * Checks if this Client has a Player associated.
     * @return true if this Client has a Player associated; false if otherwise
     */
    public boolean hasPlayer() {
        return this.player != null;
    }

    /**
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return player.getUsername();
    }

    /**
     * @return a hash code value for this Client
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.player);
    }

    /**
     * Indicates whether some other object is "equal to" this Client.
     * @param o the reference object with which to compare
     * @return true if this Client is the same as the object argument; false if otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (getClass() != o.getClass()) return false;

        Client client = (Client) o;
        return this.player.equals(client.getPlayer());
    }

    /**
     * Sends a message to this Client's socket.
     * @param message message to send
     * @throws IOException If this Client has disconnected
     */
    public void sendMessage(String message) throws IOException {
        OutputStream output = this.socket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);
        writer.println(message);
        writer.flush();
    }

    /**
     * Receives a message from this Client's socket.
     * @return message received
     * @throws IOException If this Client has disconnected
     */
    public String receiveMessage() throws IOException {
        InputStream input = this.socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder message = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            message.append(line).append("\n");

            if (line.endsWith("END")) {
                break;
            }
        }

        if (!message.isEmpty()) {
            message.setLength(message.length() - 5); // trims "\nEND\n" from the message
        } else {
            this.socket.close();
        }

        return message.toString();
    }

    /**
     * Creates and runs a new virtual thread that waits in loop for messages to send, coming from the system input stream.
     * Also waits in loop for messages received and prints them.
     */
    public void waiting() {
        Thread.ofVirtual().start(() -> {
            Scanner scanner = new Scanner(System.in);
            while (!Thread.interrupted()) {
                if (scanner.hasNextLine()) {
                    String message = scanner.nextLine() + "\nEND";
                    try {
                        this.sendMessage(message);
                    } catch (IOException e) {
                        System.out.println("Error writing to socket: " + e.getMessage() + ".");
                        break;
                    }
                }
            }
        });

        while (true) {
            try {
                String message = this.receiveMessage();
                System.out.println(message);
            } catch (IOException e) {
                break;
            }
        }
    }
}
