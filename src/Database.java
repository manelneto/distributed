import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Database {
    private final File file;
    private final ArrayList<Player> players;

    /**
     * Constructs a new Database object stored in a given file. If the file does not exist, this constructor creates it.
     * @param filename new Database file
     * @throws IOException If an I/O error occurred
     */
    public Database(String filename) throws IOException {
        this.file = new File(filename);
        this.players = new ArrayList<>();

        if (this.file.createNewFile()) {
            System.out.println("The provided file did not exist, so it will be created.");
        }

        BufferedReader bufferedReader = new BufferedReader(new FileReader(this.file));

        String line = bufferedReader.readLine();
        while (line != null) {
            // reads the database file, line by line
            String[] array = line.split(",");
            String username = array[0];
            String password = array[1];
            int ranking = Integer.parseInt(array[2]);

            Player player = new Player(username, password, ranking);
            this.players.add(player);

            line = bufferedReader.readLine();
        }

        bufferedReader.close();
    }

    /**
     * Tries to log in the Player corresponding to a given pair of username and password.
     * Checks if the given username exists in this database and, if it does, if the password is correct.
     * @param username username to find in this database
     * @param password password to check
     * @return the corresponding Player if the username exists in this database and the password is correct; null otherwise
     * @throws NoSuchAlgorithmException If the encryption algorithm requested is not available in the environment
     */
    public Player login(String username, String password) throws NoSuchAlgorithmException {
        for (Player player : this.players) {
            if (player.getUsername().equals(username) && player.verifyPassword(password)) {
                return player;
            }
        }

        return null;
    }

    /**
     * Tries to register the Player corresponding to a given pair of username and password.
     * Checks if the given username exists in this database and, if it does not, creates a new record in this database.
     * @param username username to register
     * @param password password to register
     * @return the corresponding Player if the username does not already exist in this database; null otherwise
     * @throws IOException If an error occurs when writing to this database's file
     * @throws NoSuchAlgorithmException If the encryption algorithm requested is not available in the environment
     */
    public Player register(String username, String password) throws IOException, NoSuchAlgorithmException {
        if (this.players.stream().anyMatch(player -> player.getUsername().equals(username))) {
            // the username already exists in the database
            return null;
        }

        Player player = new Player(username, Player.hashPassword(password), 0);
        this.players.add(player);

        // writes the registered Player to the database file
        FileWriter fileWriter = new FileWriter(this.file, true);
        fileWriter.write(player.toString());
        fileWriter.close();

        return player;
    }

    /**
     * Saves this database in its file.
     * Writes all players in the database to the file.
     * @throws IOException If an error occurs when writing to this database's file
     */
    public void save() throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(this.file));

        for (Player player : this.players) {
            bufferedWriter.write(player.toString());
        }

        bufferedWriter.close();
    }
}
