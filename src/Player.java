import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public class Player {
    private final String username;
    private final String password;
    private String token;
    private int ranking;
    private float playTime = -1;

    /**
     * Constructs a new Player object with a username, a password and a ranking.
     * @param username new Player's username
     * @param password new Player's password
     * @param ranking new Player's ranking
     */
    Player(String username, String password, int ranking) {
        this.username = username;
        this.password = password;
        this.ranking = ranking;
        this.generateToken();
    }

    /**
     * Encrypts the password to save it in the database file using the SHA-256 algorithm.
     * @param password decrypted Player's password
     * @return encrypted Player's password
     * @throws NoSuchAlgorithmException If the SHA-256 algorithm is not available in the environment
     */
    public static String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedBytes = digest.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hashedBytes);
    }

    /**
     * @return this Player's username
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * @return this Player's token to reconnect
     */
    public String getToken() {
        return this.token;
    }

    /**
     * @return this Player's ranking
     */
    public int getRanking() {
        return this.ranking;
    }

    /**
     * @return the time spent when this Player was writing the sentence
     */
    public float getPlayTime() {
        return this.playTime;
    }

    /**
     * @param playTime the time spent when this Player was writing the sentence
     */
    public void setPlayTime(float playTime) {
        this.playTime = playTime;
    }

    /**
     * Gets a random token for reconnection with the format username-number of 4 digits.
     */
    public void generateToken() {
        this.token = this.username + "-" + (new SecureRandom()).nextInt(1000, 9999);
    }

    /**
     * Increments this Player's ranking with the score of the last game played.
     * @param increment score
     */
    public void incrementRanking(int increment) {
        this.ranking += increment;
    }

    /**
     * Checks if the given password in authentication is the same as the one associated to this PLayer.
     * @param password password to compare with this Player's password
     * @return true if the passwords match; false if otherwise
     * @throws NoSuchAlgorithmException If the SHA-256 algorithm is not available in the environment
     */
    public boolean verifyPassword(String password) throws NoSuchAlgorithmException {
        String hashedAttempt = Player.hashPassword(password);
        return hashedAttempt.equals(this.password);
    }

    /**
     * Formats the username, password and ranking of this Player to save it in the database file.
     * @return string with comma as separator
     */
    @Override
    public String toString() {
        return this.username + "," + this.password + "," + this.ranking + "\n";
    }

    /**
     * @return a hash code value for this Player
     */
    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    /**
     * Indicates whether some other object is "equal to" this Player.
     * @param o the reference object with which to compare
     * @return true if this Player is the same as the object argument; false if otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (getClass() != o.getClass()) return false;

        Player player = (Player) o;
        return this.username.equals(player.getUsername());
    }
}
