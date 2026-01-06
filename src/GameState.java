public class GameState {

    public static class PlayerState {
        public int x = -1;
        public int y = -1;
        public boolean hasKey = false;
        public boolean alive = true;

        public int shotsLeft = 2; // NEW
    }

    private Board board;
    private Mode mode = Mode.BUILD_MAZE;
    private PlacementTool placementTool = PlacementTool.KEY;

    // сущности (по одной каждой)
    public int keyX = -1, keyY = -1;
    public int exitX = -1, exitY = -1;
    public int hospitalX = -1, hospitalY = -1;
    public int minotaurX = -1, minotaurY = -1;

    public final PlayerState p1 = new PlayerState();
    public final PlayerState p2 = new PlayerState();

    // portals (dynamic groups)
    public final PortalNetwork portals = new PortalNetwork();

    // placement cursors for portals
    public int pairCursorGroup = 0;
    public int pairCursorIndex = 0;  // 0..1
    public int cycleCursorGroup = 0;
    public int cycleCursorIndex = 0; // 0..2

    // per-turn guard: teleport at most once per active player's turn
    public boolean teleportedThisTurn = false;

    public int currentPlayerIndex = 1; // 1 или 2

    public Board getBoard() { return board; }
    public void setBoard(Board board) { this.board = board; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public PlacementTool getPlacementTool() { return placementTool; }
    public void setPlacementTool(PlacementTool tool) { this.placementTool = tool; }

    public PlayerState currentPlayer() {
        return (currentPlayerIndex == 1) ? p1 : p2;
    }

    public void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex == 1) ? 2 : 1;
    }

    public void clearEntitiesAndPlayers() {
        keyX = keyY = -1;
        exitX = exitY = -1;
        hospitalX = hospitalY = -1;
        minotaurX = minotaurY = -1;

        p1.x = p1.y = -1; p1.hasKey = false; p1.alive = true;
        p2.x = p2.y = -1; p2.hasKey = false; p2.alive = true;

        currentPlayerIndex = 1;

        p1.shotsLeft = 2;
        p2.shotsLeft = 2;

        portals.clear();
        pairCursorGroup = 0;
        pairCursorIndex = 0;
        cycleCursorGroup = 0;
        cycleCursorIndex = 0;
        teleportedThisTurn = false;
    }

    public boolean gameOver = false;
    public String gameOverMessage = "";

    public void resetRunStateForPlay() {
        gameOver = false;
        gameOverMessage = "";
        currentPlayerIndex = 1;

        p1.shotsLeft = 2; p1.hasKey = false; p1.alive = true;
        p2.shotsLeft = 2; p2.hasKey = false; p2.alive = true;

        teleportedThisTurn = false;
    }

    public PlacementTool nextPlacementTool() {
        PlacementTool[] v = PlacementTool.values();
        int i = placementTool.ordinal();
        if (i + 1 < v.length) return v[i + 1];
        return v[i]; // на последнем остаёмся (можно сделать циклом, если захочешь)
    }

    public PlacementTool advancePlacementTool() {
        placementTool = nextPlacementTool();
        return placementTool;
    }
}
