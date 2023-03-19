package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;
    public final Condition cards_on_Table;

    public int highest_score;

    public Queue<ArrayList<Integer>> claimed_Set;
    public Queue<Integer> claimed_Set_player_Id;
    /**
     * General for Lock for the table
     */
    public ReentrantLock local_Lock;

    /**
     * Mapping between a slot and the player placed token on it (null if none).
     */
    protected ArrayList<Integer>[] SlotToPlayer;


    /**
     * Mapping between a player and the slot he placed token on it (null if none).
     */
    protected ArrayList<Integer>[] playerToSlot;


    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        claimed_Set = new LinkedList<>();
        claimed_Set_player_Id = new LinkedList<>();
        local_Lock = new ReentrantLock(true);
        playerToSlot = new ArrayList[env.config.players];
        SlotToPlayer = new ArrayList[env.config.tableSize];
        cards_on_Table = local_Lock.newCondition();


        for (int i = 0; i < playerToSlot.length; i++) {
            playerToSlot[i] = new ArrayList<>();
        }

        for (int i = 0; i < SlotToPlayer.length; i++) {
            SlotToPlayer[i] = new ArrayList<>();
        }
    }


    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        //Take current slot key
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        //Show card on table
        env.ui.placeCard(card, slot);


    }

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        //Take current slot key
        System.out.println("the card" + slot + "Is removed");

        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        int card = slotToCard[slot];
        cardToSlot[card] = null;
        slotToCard[slot] = null;
        env.ui.removeCard(slot);


    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        try {
            //Take local key
            local_Lock.lock();

            playerToSlot[player].add(slot);
            SlotToPlayer[slot].add(player);
            env.ui.placeToken(player, slot);

            //Update list of claimed sets
            if (playerToSlot[player].size() == 3) {
                claimed_Set.add(playerToSlot[player]);
                claimed_Set_player_Id.add(player);
            }
        } finally {
            local_Lock.unlock();
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        boolean removed;
        removed = playerToSlot[player].remove((Integer) slot);
        SlotToPlayer[slot].remove((Integer) player);
        if (removed)
            env.ui.removeToken(player, slot);


        return removed;

    }


    public ArrayList<Integer>[] getSlotToPlayer() {
        ArrayList<Integer>[] copy = new ArrayList[SlotToPlayer.length];
        for (int i = 0; i < SlotToPlayer.length; i++) {
            copy[i] = new ArrayList<>(SlotToPlayer[i]);
        }

        return copy;
    }

    public ArrayList<Integer>[] getPlayerToSlot() {
        ArrayList<Integer>[] copy = new ArrayList[playerToSlot.length];
        for (int i = 0; i < playerToSlot.length; i++) {
            copy[i] = new ArrayList<>(playerToSlot[i]);
        }

        return copy;
    }

}


