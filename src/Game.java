import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Game {
    private final ArrayList<Client> clients;

    private final ArrayList<String> goals = new ArrayList<>(Arrays.asList(
            "Actions speak louder than words.",
            "When life gives you lemons, make lemonade.",
            "Knowledge is power.",
            "Time flies when you are having fun.",
            "There is no place like home.",
            "A journey of a thousand miles begins with a single step."
    ));

    float bestTime = Float.MAX_VALUE;
    Client winner = null;

    /**
     * Creates a new Game object with the given clients (players).
     * @param clients new Game clients (who will play this game)
     */
    public Game(ArrayList<Client> clients) {
        this.clients = clients;
    }

    /**
     * Plays this game from start to finish.
     * Starts the game, lets every client play its turn, shows the results and asks who want to play again.
     * @return the clients who want to play again
     */
    public ArrayList<Client> play() {
        System.out.println("Starting game with " + this.clients.size() + " players.");

        this.start();
        this.typeRacer();
        this.showResults();
        ArrayList<Client> newClients = this.playAgain();

        System.out.println("Finished game.");

        return newClients;
    }

    /**
     * Starts this game.
     * Notifies each client about the team who will play this game.
     */
    private void start() {
        // creates a String with the team that will play this game, to show to every player
        String team = clients.stream().map(Client::getPlayer).map(Player::getUsername).collect(Collectors.joining(", "));
        for (Client client : clients) {
            try {
                client.sendMessage("The game started. The team for this game is: " + team + ".\nEND");
            } catch (IOException e) {
                System.out.println("Client " + client.getPlayer().getUsername() + " was disconnected after entering the game.");
            }
        }
    }

    /**
     * Selects the goal randomly and lets each client try to write the phrase in the less time possible.
     * In the end of each turn, notifies each client about the time it took to write the phrase correctly.
     * Calculates the winner of this game (the client who wrote the phrase in the less time possible) and sets it.
     */
    private void typeRacer() {
        // selects the goal randomly
        int goalNumber = (int) (Math.random() * this.goals.size());
        String goal = this.goals.get(goalNumber);

        for (Client client : this.clients) {
            try {
                client.sendMessage("Write this sentence in the less time possible:\n\"" + goal + "\"\nEND");

                long start = System.currentTimeMillis();

                String play = client.receiveMessage();

                while (!play.equals(goal)) {
                    client.sendMessage("Input does not match with goal. Try again!\nEND");
                    play = client.receiveMessage();
                }

                long end = System.currentTimeMillis();

                // measures how much time the player took to write the sentence correctly
                float duration = (float) (end - start) / 1000;

                client.getPlayer().setPlayTime(duration);

                client.sendMessage("Your time is " + duration + " seconds.\nEND");

                if (duration < this.bestTime) {
                    // the winner is the fastest client to write the sentence
                    this.bestTime = duration;
                    this.winner = client;
                }
            } catch (IOException e) {
                client.getPlayer().setPlayTime(Float.MAX_VALUE);
                System.out.println("Client " + client.getPlayer().getUsername() + " was disconnected when it was its turn to play.");
            }
        }
    }

    /**
     * Sorts the clients by ascending play time and updates each player's ranking.
     * Then, displays this game results for each client, distinguishing between who won and who lost.
     */
    private void showResults() {
        // sorts the clients by ascending play time
        this.clients.sort((c1, c2) -> Float.compare(c1.getPlayer().getPlayTime(), c2.getPlayer().getPlayTime()));

        StringBuilder results = new StringBuilder();
        for (int i = 0; i < this.clients.size(); i++) {
            // builds the string with the results
            Client client = this.clients.get(i);

            client.getPlayer().incrementRanking(this.clients.size() - i - 1);

            results.append(i + 1).append(". ").append(client.getPlayer().getUsername()).append(": ");

            float playTime = client.getPlayer().getPlayTime();

            if (playTime == Float.MAX_VALUE) {
                // the player disconnected when it was its turn to play
                results.append("disconnected\n");
            } else {
                results.append(playTime).append(" seconds\n");
            }

            client.getPlayer().setPlayTime(-1);
        }

        assert this.winner != null;

        try {
            winner.sendMessage("You won!\n" + results + "\nEND");
        } catch (IOException e) {
            System.out.println("Client " + this.winner.getPlayer().getUsername() + " (winner) was disconnected before knowing results.");
        }

        for (Client client : this.clients) {
            if (!client.equals(winner)) {
                try {
                    client.sendMessage("You lost!\n" + results + "\nEND");
                } catch (IOException e) {
                    System.out.println("Client " + client.getPlayer().getUsername() + " disconnected before knowing results.");
                }
            }
        }
    }

    /**
     * Asks every client if it wants to play again and waits for each one's answer.
     * Closes the sockets of the players who do not want to play again.
     * @return clients who want to play again
     */
    private ArrayList<Client> playAgain() {
        ArrayList<Client> newClients = new ArrayList<>();

        for (Client client : this.clients) {
            try {
                client.sendMessage("Do you want to try again? (Yes/No)\nEND");
                String answer = client.receiveMessage().toUpperCase();
                if (answer.equals("YES") || answer.equals("Y")) {
                    newClients.add(client);
                } else {
                    client.sendMessage("Thank you for playing our game!\nEND");
                    client.getSocket().close();
                }
            } catch (IOException e) {
                System.out.println("Client " + client.getPlayer().getUsername() + " was disconnected after the game ended.");
            }
        }

        return newClients;
    }
}
