import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    /**
     * Two types of matchmaking: simple and rank.
     */
    enum MatchmakingMode {
        SIMPLE,
        RANK
    }

    private final ServerSocket socket;
    private final Database database;
    private final MatchmakingMode matchmakingMode;
    private final int playersPerGame;

    private final ArrayList<Client> waitingQueue;
    private final ReentrantLock waitingQueueLock;
    private final ReentrantLock databaseLock;
    private final ReentrantLock timeLock;

    private int rankingDifference = 5;
    private long lastUpdateTime = 0;
    private long oldestCheckIfAliveTime = 0;

    /**
     * Constructs a new Server with a port number, a database file name, a matchmaking mode, and a number of players per game.
     * @param port new Server's Socket port
     * @param databaseFile name of the Server's database file
     * @param matchmakingMode 0 if simple mode, 1 if rank mode
     * @param playersPerGame number of players to play a game
     * @throws IOException If an I/O error occurred when creating the database
     */
    public Server(int port, String databaseFile, int matchmakingMode, int playersPerGame) throws IOException {
        this.socket = new ServerSocket();
        this.socket.bind(new InetSocketAddress(port));

        this.database = new Database(databaseFile);

        this.matchmakingMode = matchmakingMode == 0 ? MatchmakingMode.SIMPLE : MatchmakingMode.RANK;

        this.playersPerGame = playersPerGame;

        this.waitingQueue = new ArrayList<>();
        this.waitingQueueLock = new ReentrantLock();
        this.databaseLock = new ReentrantLock();
        this.timeLock = new ReentrantLock();

        System.out.println("Server is listening on port " + port + ".");
        System.out.println("The database is being stored on the file " + databaseFile + ".");

        if (this.matchmakingMode == MatchmakingMode.SIMPLE) {
            System.out.println("Starting simple mode matchmaking with teams of " + playersPerGame + " players.\n");
        } else {
            System.out.println("Starting rank mode matchmaking with an initial ranking difference of " + this.rankingDifference + " and teams of " + playersPerGame + " players.\n");
        }
    }

    /**
     * Runs a new Server object, connecting it to a socket with a given port, database stored in a given file, a matchmaking mode and a number of players per game.
     * @param args PORT DATABASE_FILE MATCHMAKING_MODE PLAYERS_PER_GAME
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: Server <PORT (>0)> <DATABASE FILE: (*.csv)> <MATCHMAKING MODE (0/1)> <PLAYERS PER GAME (>0)>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        if (port < 0) {
            System.out.println("Invalid port number: " + port + ". The port number must be greater than 0.");
            return;
        }

        String databaseFile = args[1];
        if (!databaseFile.endsWith(".csv")) {
            System.out.println("Invalid database file: " + databaseFile + ". The database file must end with .csv.");
            return;
        }

        int matchmakingMode = Integer.parseInt(args[2]);
        if (matchmakingMode < 0 || matchmakingMode > 1) {
            System.out.println("Invalid matchmaking mode: " + matchmakingMode + ". The matchmaking mode must be either 0 or 1.");
            return;
        }

        int playersPerGame = Integer.parseInt(args[3]);
        if (playersPerGame < 0) {
            System.out.println("Invalid number of players per game: " + playersPerGame + ". The number of players per game must be greater than 0.");
            return;
        }

        try {
            Server server = new Server(port, databaseFile, matchmakingMode, playersPerGame);
            server.execute();
        } catch (IOException | InterruptedException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }

    /**
     * Executes this Server by starting the authentication and matchmaking threads and waiting for them to finish.
     * @throws InterruptedException If either the matchmaking or the authentication thread is interrupted
     */
    public void execute() throws InterruptedException {
        Thread authenticationThread = Thread.ofVirtual().start(() -> {
            while (!Thread.interrupted()) {
                try {
                    Socket socket = this.socket.accept();
                    Thread.ofVirtual().start(() -> {
                        System.out.println("New client arrived.");
                        try {
                            this.dealWithClient(socket);
                        } catch (IOException e) {
                            System.out.println("New client exception: " + e.getMessage() + ".");
                        }
                    });
                } catch (IOException e) {
                    System.out.println("Authentication exception: " + e.getMessage() + ".");
                }
            }
        });

        Thread matchmakingThread = Thread.ofVirtual().start(() -> {
            while (!Thread.interrupted()) {
                this.checkIfAlive();
                if (this.matchmakingMode == MatchmakingMode.SIMPLE) {
                    this.simpleModeMatchmaking();
                } else {
                    this.rankModeMatchmaking();
                }
            }
        });

        authenticationThread.join();
        matchmakingThread.join();
    }

    /**
     * Handles the new Client's arrival by showing a menu for login, registration or reconnection and performing the action selected by the Client.
     * @param socket the Client's socket channel
     * @throws IOException If the Client disconnected while in the menu
     */
    private void dealWithClient(Socket socket) throws IOException {
        Client client = new Client(socket);
        boolean reconnection = false;

        client.sendMessage("--------------------------------------------------------------------");
        client.sendMessage("                   Welcome to the TypeRacer Game!");
        client.sendMessage("--------------------------------------------------------------------");

        while (!client.hasPlayer()) {
            client.sendMessage("Menu\nLOG: Login\nREG: Register\nREC: Reconnect\nEND");
            String response = client.receiveMessage().toUpperCase();

            switch (response) {
                case "LOG":
                    client = this.authentication(socket, true);
                    reconnection = false;
                    break;
                case "REG":
                    client = this.authentication(socket, false);
                    reconnection = false;
                    break;
                case "REC":
                    client = this.reconnect(socket);
                    reconnection = true;
                    break;
                default:
                    client.sendMessage("The selected option does not exist.");
                    break;
            }
        }

        if (reconnection) {
            client.sendMessage("You reentered the waiting queue with ranking " + client.getPlayer().getRanking() + ".\nIn case the connection breaks, your token to reconnect is \"" + client.getPlayer().getToken() + "\".\nEND");
            System.out.println("Client " + client.getPlayer().getUsername() + " reconnected.");
            return;
        }

        dealWithWaitingQueue(client);
    }

    /**
     * Adds a Client to the waiting queue and sends a message with the Client's token for reconnection.
     * @param client the Client to be added to the waiting queue
     * @throws IOException If the Client disconnected while in the process of being added to the waiting queue
     */
    private void dealWithWaitingQueue(Client client) throws IOException {
        StringBuilder queue = new StringBuilder();
        this.waitingQueueLock.lock();
        try {
            if (this.waitingQueue.stream().anyMatch((c) -> c.equals(client))) {
                client.sendMessage("You are already in the waiting queue.\nEND");
                client.getSocket().close();
                return;
            }

            if (this.waitingQueue.isEmpty()) {
                this.lastUpdateTime = client.getQueueArrivalTime();
                this.oldestCheckIfAliveTime = client.getQueueArrivalTime();
            }

            this.waitingQueue.add(client);

            for (int i = 0; i < this.waitingQueue.size(); i++) {
                Player player = this.waitingQueue.get(i).getPlayer();
                queue.append(i + 1).append(". ").append(player.getUsername()).append(" (ranking: ").append(player.getRanking()).append(")\n");
            }
        } finally {
            this.waitingQueueLock.unlock();
        }

        client.sendMessage("You entered the waiting queue with ranking " + client.getPlayer().getRanking() + ".\nIn case the connection breaks, your token to reconnect is \"" + client.getPlayer().getToken() + "\".\nEND");
        System.out.println("Queue:\n" + queue);
    }

    /**
     * Authenticates a Client, either by logging in or registering a new account, and sets up the Client's Player details.
     * @param socket the Client's socket channel
     * @param isLogin true if the Client is logging in, false if it is registering a new account
     * @return the authenticated Client
     * @throws IOException If an I/O error occurred when creating the database or the Client disconnected while in the authentication process
     */
    private Client authentication(Socket socket, boolean isLogin) throws IOException {
        // creates a client with only a socket
        Client client = new Client(socket);
        client.sendMessage("Enter your username!\nEND");
        String username = client.receiveMessage();
        client.sendMessage("Enter your password!\nEND");
        String password = client.receiveMessage();

        this.databaseLock.lock();
        try {
            Player player;

            if (isLogin) {
                player = this.database.login(username, password);
            } else {
                player = this.database.register(username, password);
            }

            // associates the player and the time he arrived to queue with the previously created client
            client.setPlayer(player);
            client.setQueueArrivalTime(System.currentTimeMillis());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Authentication exception: " + e.getMessage() + ".");
        } finally {
            this.databaseLock.unlock();
        }

        if (client.hasPlayer()) {
            client.sendMessage("Authentication successful.\nEND");
        } else {
            if (isLogin) {
                client.sendMessage("The provided credentials do not match our records.\n");
            } else {
                client.sendMessage("Username already exists.\n");
            }
        }

        return client;
    }

    /**
     * Reconnects a Client to this Server using a previously generated token.
     * @param socket the Client's socket channel
     * @return the reconnected Client
     * @throws IOException If the Client disconnected while in the reconnection process
     */
    private Client reconnect(Socket socket) throws IOException {
        Client client = new Client(socket);
        client.sendMessage("Enter your token!\nEND");
        String token = client.receiveMessage();

        this.waitingQueueLock.lock();
        try {
            for (Client c : this.waitingQueue) {
                if (c.getPlayer().getToken().equals(token)) {
                    // the token is correct and belongs to a client already in the waiting queue
                    c.getSocket().close();
                    c.setSocket(socket);
                    client.setPlayer(c.getPlayer());
                    break;
                }
            }
        } finally {
            this.waitingQueueLock.unlock();
        }

        if (client.hasPlayer()) {
            client.sendMessage("Reconnect successful.\nEND");
        } else {
            client.sendMessage("You were not in the queue. You have to login or register first.\n");
        }

        return client;
    }

    /**
     * Checks if the Clients in the waiting queue are still connected to this Server and removes those who are not.
     */
    private void checkIfAlive() {
        long currentTime = System.currentTimeMillis();
        long checkIfAliveFrequency = 30000; // 30 seconds
        if (this.oldestCheckIfAliveTime > 0 && currentTime - this.oldestCheckIfAliveTime > checkIfAliveFrequency) {
            // if 30 seconds passed, checks if all clients are alive (connected)
            this.waitingQueueLock.lock();

            try {
                for (int i = 0; i < this.waitingQueue.size(); ) {
                    Client client = this.waitingQueue.get(i);
                    try {
                        client.sendMessage("Checking if you are alive...\nEND");
                        System.out.println("Client " + client.getPlayer().getUsername() + " is alive.");
                        i++;
                    } catch (IOException e) {
                        System.out.println("Client " + client.getPlayer().getUsername() + " is not alive and will be removed from the waiting queue.");
                        this.waitingQueue.remove(i);
                    }
                }
                this.oldestCheckIfAliveTime = currentTime;
            } finally {
                this.waitingQueueLock.unlock();
            }
        }
    }

    /**
     * Handles matchmaking in simple mode by grouping clients into teams if enough clients are available.
     */
    private void simpleModeMatchmaking() {
        this.waitingQueueLock.lock();
        try {
            if (this.waitingQueue.size() >= this.playersPerGame) {
                ArrayList<Client> clients = new ArrayList<>();

                for (int i = 0; i < this.playersPerGame; i++) {
                    // removes the first players per game from the waiting queue
                    Client client = this.waitingQueue.removeFirst();
                    clients.add(client);
                    client.getPlayer().generateToken();
                }

                Game game = new Game(clients);
                // starts a new virtual thread with the created game
                Thread.ofVirtual().start(() -> this.play(game));
            }
        } finally {
            this.waitingQueueLock.unlock();
        }
    }

    /**
     * Handles matchmaking in rank mode by grouping clients into teams based on their rankings if enough clients are available.
     */
    private void rankModeMatchmaking() {
        this.updateRankingDifference();
        this.waitingQueueLock.lock();

        try {
            if (this.waitingQueue.size() >= this.playersPerGame) {
                // sorts the waiting queue by ascending ranking to compare the ranking of the players
                this.waitingQueue.sort(Comparator.comparingInt(c -> c.getPlayer().getRanking()));

                ArrayList<Client> clients = new ArrayList<>();
                boolean hasTeam = false;

                // for each client, tries to form a team that respects the maximum ranking difference
                for (int i = 0; i + this.playersPerGame - 1 < this.waitingQueue.size(); i++) {
                    Client firstClient = waitingQueue.get(i);

                    for (int j = i + this.playersPerGame - 1; j < this.waitingQueue.size(); j++) {
                        Client lastClient = this.waitingQueue.get(j);

                        if (lastClient.getPlayer().getRanking() - firstClient.getPlayer().getRanking() <= this.rankingDifference) {
                            for (int k = i; k <= j; k++) {
                                clients.add(this.waitingQueue.remove(i));
                            }
                            hasTeam = true;
                            break;
                        }
                    }

                    if (hasTeam) {
                        break;
                    }
                }

                if (hasTeam) {
                    // when a team is formed, the maximum ranking differnce is reset
                    this.rankingDifference = 5;
                    System.out.println("Ranking difference reset to " + this.rankingDifference + ".");

                    this.timeLock.lock();
                    try {
                        if (this.waitingQueue.isEmpty()) {
                            this.lastUpdateTime = 0;
                        } else {
                            this.lastUpdateTime = this.waitingQueue.stream()
                                    .min(Comparator.comparingLong(Client::getQueueArrivalTime)).get().getQueueArrivalTime();
                        }
                    } finally {
                        this.timeLock.unlock();
                    }

                    Game game = new Game(clients);
                    Thread.ofVirtual().start(() -> this.play(game));
                }
            }
        } finally {
            this.waitingQueueLock.unlock();
        }
    }

    /**
     * Updates the ranking difference for matchmaking in rank mode if the specified time has passed.
     */
    private void updateRankingDifference() {
        this.timeLock.lock();
        try {
            long updateRankingDifferenceFrequency = 60000; // 60 seconds
            long currentTime = System.currentTimeMillis();
            if (this.lastUpdateTime > 0 && currentTime - this.lastUpdateTime > updateRankingDifferenceFrequency) {
                this.rankingDifference += 5;
                this.lastUpdateTime = currentTime;
                System.out.println("Updated ranking difference: " + this.rankingDifference + ".");
            }
        } finally {
            this.timeLock.unlock();
        }
    }

    /**
     * Handles the gameplay by starting a new game with a set of clients, updating the database and the waiting queue after the game ends.
     * @param game the game to be played
     */
    private void play(Game game) {
        ArrayList<Client> newClients = game.play();

        this.databaseLock.lock();
        try {
            // updates the database with all player's ranking that resulted from the last game played
            this.database.save();
            System.out.println("Updated database.");
        } catch (IOException e) {
            System.out.println("The database could not be updated after the game. Trying again after the next game ends.");
        } finally {
            this.databaseLock.unlock();
        }

        // adds the clients who want to play again to the waiting queue
        StringBuilder queue = new StringBuilder();
        this.waitingQueueLock.lock();
        try {
            for (int i = 0; i < newClients.size(); i++) {
                Client client = newClients.get(i);
                client.setQueueArrivalTime(System.currentTimeMillis());

                if (this.waitingQueue.isEmpty()) {
                    this.lastUpdateTime = client.getQueueArrivalTime();
                }

                this.waitingQueue.add(client);
                try {
                    client.sendMessage("You reentered the waiting queue with ranking " + client.getPlayer().getRanking() + ".\nIn case the connection breaks, your new token to reconnect is \"" + client.getPlayer().getToken() + "\".\nEND");
                    queue.append(i + 1).append(". ").append(client.getPlayer().getUsername()).append(" (ranking: ").append(client.getPlayer().getRanking()).append(")\n");
                } catch (IOException e) {
                    System.out.println("Client " + client.getPlayer().getUsername() + " disconnected when reentering the queue. It will be kept in the queue until the next alive check in case he reconnects.");
                }
            }
        } finally {
            this.waitingQueueLock.unlock();
            System.out.println("Queue updated\n" + queue);
        }
    }
}
