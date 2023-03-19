package bguspl.set.ex;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

import bguspl.set.Env;

import static java.lang.Thread.sleep;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    private final Condition queueNotFull;


    public volatile boolean point_freeze = false;
    public volatile boolean penalty_time = false;
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     *
     */
    public Queue<Integer> keyPress_Queue;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        score = 0;
        keyPress_Queue = new LinkedList<>();
        queueNotFull = table.local_Lock.newCondition();


    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {

            if (point_freeze)
                point();

            if (penalty_time)
                penalty();


            try {
                table.local_Lock.lock();
                if (!keyPress_Queue.isEmpty()) {
                    place_Token_on_Table();
                    queueNotFull.signalAll();
                }

            } finally {
                table.local_Lock.unlock();
            }
        }

        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }


    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
            aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                try {
                    table.local_Lock.lock();

                    //if queue full wait
                    while (keyPress_Queue.size() == 3) {
                        queueNotFull.await();
                    }

                    //create random key press
                    Random random = new Random();
                    keyPress_Queue.add(random.nextInt(env.config.tableSize));



                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    table.local_Lock.unlock();
                }

            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate=true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        try {
            //Take local key
            table.local_Lock.lock();
            keyPress_Queue.add(slot);
            table.hints();
        } finally {
            table.local_Lock.unlock();
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(id, 1000);
        if (score > table.highest_score)
            table.highest_score = score;

        // Count down from 1 to 0 and update the freeze time for the player
        for (int i = 1; i >= 0; i--) {
            env.ui.setFreeze(id, i * 1000L);
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        point_freeze = false;


    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // Count down from 4 to 0 and update the freeze time for the player
        for (int i = 4; i >= 0; i--) {
            env.ui.setFreeze(id, i * 1000L);
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        penalty_time = false;

    }

    public int getScore() {
        return score;
    }


    public void place_Token_on_Table() {
        int slot = keyPress_Queue.remove();

        if (table.playerToSlot[id].contains(slot)) {
            table.removeToken(id, slot);
        } else if (table.playerToSlot[id].size() < 3) {
            table.placeToken(id, slot);
        }

    }

}