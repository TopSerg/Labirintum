import java.util.*;

/**
 * Portal storage with dynamic groups:
 *  - PAIR groups: size 2, teleport goes to (index+1) mod 2
 *  - CYCLE groups: size 3 (for now), teleport goes to (index+1) mod 3
 *
 * Also maintains fast lookup from a board cell to a portal reference.
 */
public final class PortalNetwork {

    public enum Type { PAIR, CYCLE }

    /** Reference to a portal inside the network: (type, group, index-in-group). */
    public static final class Ref {
        public final Type type;
        public final int group;
        public final int index;

        public Ref(Type type, int group, int index) {
            this.type = type;
            this.group = group;
            this.index = index;
        }
    }

    // Each group is an array of positions; position = int[2]{x,y}. Unplaced = {-1,-1}.
    private final ArrayList<int[][]> pairGroups = new ArrayList<>();   // each group is [2][2]
    private final ArrayList<int[][]> cycleGroups = new ArrayList<>();  // each group is [3][2] (for now)

    // cell -> portal ref
    private final HashMap<Long, Ref> cellToPortal = new HashMap<>();

    private static long key(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static int[][] newGroup(int size) {
        int[][] g = new int[size][2];
        for (int i = 0; i < size; i++) {
            g[i][0] = -1;
            g[i][1] = -1;
        }
        return g;
    }

    public void clear() {
        pairGroups.clear();
        cycleGroups.clear();
        cellToPortal.clear();
    }

    public int addPairGroup() {
        pairGroups.add(newGroup(2));
        return pairGroups.size() - 1;
    }

    public int addCycleGroup3() {
        cycleGroups.add(newGroup(3));
        return cycleGroups.size() - 1;
    }

    public List<int[][]> getPairGroups() {
        return Collections.unmodifiableList(pairGroups);
    }

    public List<int[][]> getCycleGroups() {
        return Collections.unmodifiableList(cycleGroups);
    }

    /** Returns portal ref at cell, or null. */
    public Ref portalAt(int x, int y) {
        return cellToPortal.get(key(x, y));
    }

    /** Removes portal from cell if present. */
    public void removeAt(int x, int y) {
        Ref ref = cellToPortal.remove(key(x, y));
        if (ref == null) return;
        int[][] g = groupArray(ref.type, ref.group);
        g[ref.index][0] = -1;
        g[ref.index][1] = -1;
    }

    /** Returns true if any portal already occupies cell (x,y). */
    public boolean hasPortalAt(int x, int y) {
        return cellToPortal.containsKey(key(x, y));
    }

    /**
     * Places portal for (type, group, index) into (x,y).
     * Fails if (x,y) already has ANY portal.
     */
    public boolean place(Type type, int group, int index, int x, int y) {
        long k = key(x, y);
        if (cellToPortal.containsKey(k)) return false;

        int[][] g = groupArray(type, group);

        // if this portal already placed elsewhere -> remove old mapping
        int oldX = g[index][0], oldY = g[index][1];
        if (oldX >= 0 && oldY >= 0) {
            cellToPortal.remove(key(oldX, oldY));
        }

        g[index][0] = x;
        g[index][1] = y;
        cellToPortal.put(k, new Ref(type, group, index));
        return true;
    }

    /**
     * Computes destination cell for portal you are standing on.
     * Rule: nextIndex = (index + 1) mod groupSize.
     * Returns null if destination portal is not placed yet.
     */
    public int[] destinationFrom(int x, int y) {
        Ref from = portalAt(x, y);
        if (from == null) return null;

        int[][] g = groupArray(from.type, from.group);
        int size = g.length;

        int next = from.index + 1;
        if (next >= size) next = 0;

        int tx = g[next][0], ty = g[next][1];
        if (tx < 0 || ty < 0) return null;
        return new int[]{tx, ty};
    }

    private int[][] groupArray(Type type, int groupIndex) {
        if (type == Type.PAIR) return pairGroups.get(groupIndex);
        return cycleGroups.get(groupIndex);
    }
}
