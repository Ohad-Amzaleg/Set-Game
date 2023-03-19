package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    private final Env env;


    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */

    private List<Integer> empty_Slots;
    private long reshuffleTime = 60000;

    private long start_time;

    private boolean set_Found = false;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        empty_Slots = new ArrayList<>();

        //At first all slots are empty
        for (int i = 0; i < env.config.tableSize; i++) {
            empty_Slots.add(i);
        }

    }


    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        env.ui.setCountdown(reshuffleTime, false);
        for (Player player : players) {
            Thread player_Thread = new Thread(player, "" + player.id);
            player_Thread.start();
        }

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        start_time = System.currentTimeMillis();
        while (!terminate && System.currentTimeMillis() - start_time < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(set_Found);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // Set the terminate flag to true to stop the main loop in the run() method.
        terminate = true;

        // Wake up the dealer thread if it is sleeping.
        Thread.currentThread().interrupt();

        // Terminate all player threads.
        for (Player player : players) {
            player.terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        try {
            table.local_Lock.lock();
            for (int i = 0; i < table.claimed_Set.size(); i++) {
                ArrayList<Integer> slot_set = table.claimed_Set.remove();
                int player_Id = table.claimed_Set_player_Id.remove();
                set_Checker(new ArrayList<>(slot_set), player_Id);
            }
        } finally {
            table.local_Lock.unlock();
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        try {
            table.local_Lock.lock();
            if (empty_Slots.size() > 0 && deck.size() > 0) {
                Collections.shuffle(empty_Slots);

                for (Integer slot : empty_Slots) {
                    Random random = new Random();
                    table.placeCard(deck.remove(random.nextInt(deck.size())), slot);
                }

                empty_Slots = new ArrayList<>();
            }
            else if (shouldFinish())
                terminate();


        } finally {
            table.local_Lock.unlock();
        }


    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long curr_time = System.currentTimeMillis();
        if (!set_Found) {
            env.ui.setCountdown(reshuffleTime - (curr_time - start_time), false);

        } else {
            start_time = curr_time;
            env.ui.setCountdown(reshuffleTime, false);
            set_Found = false;
        }

        if (reshuffleTime - (curr_time - start_time) < 10000) {
            env.ui.setCountdown(reshuffleTime - (curr_time - start_time), true);
        }


    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        try {
            table.local_Lock.lock();
            for (Integer slot : table.cardToSlot) {
                if (slot != null) {
                    for (int i = 0; i < players.length; i++) {
                        table.removeToken(i, slot);
                    }
                    deck.add(table.slotToCard[slot]);
                    empty_Slots.add(slot);
                    table.removeCard(slot);


                }
            }
            start_time = System.currentTimeMillis();
        } finally {
            table.local_Lock.unlock();
        }

    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        ArrayList<Integer> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getScore() >= table.highest_score)
                winners.add(player.id);
        }
        env.ui.announceWinner(winners.stream().mapToInt(Integer::intValue).toArray());
    }


    private void set_Checker(List<Integer> slot_set, int player_id) {
        if (slot_set != null && slot_set.size() == 3) {

            //Transfer slots to cards
            int[] cards = slot_set.stream()
                    .mapToInt(slot -> table.slotToCard[slot])
                    .toArray();


            if (env.util.testSet(cards)) {
                //set_Found = true;
                //SET point flag to true
                //players[player_id].point_freeze = true;

                for (Integer slot : slot_set) {
                    for (Integer id : table.getSlotToPlayer()[slot]) {
                        table.removeToken(id, slot);
                    }
                    deck.remove(table.slotToCard[slot]);
                    table.removeCard(slot);
                    empty_Slots.add(slot);
                }

            } else {
                //SET freeze of wrong set flag to false
                // players[player_id].penalty_time= true;
            }

        }
    }

}