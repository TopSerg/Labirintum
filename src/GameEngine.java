import java.util.function.Consumer;

/**
 * Pure game rules / state mutations for PLAY mode.
 * No Swing, no rendering.
 */
public final class GameEngine {

    private GameState state;
    private Consumer<String> status = s -> {};

    public GameEngine() {
    }

    public GameEngine(GameState state, Consumer<String> status) {
        this.state = state;
        if (status != null) this.status = status;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public void setStatusConsumer(Consumer<String> consumer) {
        this.status = (consumer != null) ? consumer : (s -> {});
    }

    public boolean isReady() {
        return state != null && state.getBoard() != null;
    }

    /** Move consumes turn only if actual move happened (same as your current logic). */
    public void performMove(GameState.PlayerState p, int idx, Direction dir) {
        if (!isReady()) return;
        if (state.gameOver) {
            status.accept(state.gameOverMessage);
            return;
        }

        int[] next = state.getBoard().move(p.x, p.y, dir);
        if (next[0] == p.x && next[1] == p.y) {
            status.accept("Blocked by wall/border. Player " + idx);
            return; // move not done, do not spend the turn
        }

        p.x = next[0];
        p.y = next[1];

        // portals: instant teleport on landing (at most once per active turn)
        resolvePortalIfNeeded(p, idx, "landing");

        // key
        if (p.x == state.keyX && p.y == state.keyY) {
            p.hasKey = true;
            state.keyX = -1;
            state.keyY = -1;
            status.accept("Player " + idx + " picked up KEY");
        }

        // minotaur
        if (p.x == state.minotaurX && p.y == state.minotaurY) {
            killPlayer(p, state.minotaurX, state.minotaurY, "Player " + idx + " died (MINOTAUR)");
        }

        // exit
        if (p.x == state.exitX && p.y == state.exitY) {
            if (p.hasKey) {
                state.gameOver = true;
                state.gameOverMessage = "Player " + idx + " WIN (exit + key)";
                status.accept(state.gameOverMessage);
                return;
            } else {
                status.accept("Need KEY to exit!");
            }
        }

        endTurn();
    }

    public void performShoot(GameState.PlayerState shooter, int shooterIndex, Direction dir) {
        if (!isReady()) return;
        if (state.gameOver) {
            status.accept(state.gameOverMessage);
            return;
        }

        if (shooter.shotsLeft <= 0) {
            status.accept("Player " + shooterIndex + ": no shots left");
            return;
        }

        shooter.shotsLeft--;

        int x = shooter.x;
        int y = shooter.y;

        // ray until first wall/border
        while (state.getBoard().canMove(x, y, dir)) {
            int[] next = state.getBoard().move(x, y, dir);
            x = next[0];
            y = next[1];

            // minotaur
            if (x == state.minotaurX && y == state.minotaurY) {
                killMinotaur("Player " + shooterIndex + " shot MINOTAUR at (" + x + "," + y + ")");
                return;
            }

            // other player (first on line)
            GameState.PlayerState other = getOtherPlayerAt(x, y, shooter);
            if (other != null) {
                killPlayer(other, x, y, "Player " + shooterIndex + " shot player at (" + x + "," + y + ")");
                return;
            }
        }

        status.accept("Player " + shooterIndex + " shot: MISS (shots left " + shooter.shotsLeft + ")");
    }

    public void performKnife(GameState.PlayerState attacker, int attackerIndex, int targetX, int targetY, String label) {
        if (!isReady()) return;
        if (state.gameOver) {
            status.accept(state.gameOverMessage);
            return;
        }

        // minotaur
        if (targetX == state.minotaurX && targetY == state.minotaurY) {
            killMinotaur("Player " + attackerIndex + " knifed MINOTAUR " + label + " at (" + targetX + "," + targetY + ")");
            return;
        }

        // other player (can be on same cell)
        GameState.PlayerState other = getOtherPlayerAt(targetX, targetY, attacker);
        if (other != null) {
            killPlayer(other, targetX, targetY,
                    "Player " + attackerIndex + " knifed player " + label + " at (" + targetX + "," + targetY + ")");
            return;
        }

        status.accept("Player " + attackerIndex + " knife: no target " + label);
    }

    public void endTurn() {
        if (!isReady()) return;

        // portals: teleport on end-turn (at most once per turn)
        resolvePortalIfNeeded(state.currentPlayer(), state.currentPlayerIndex, "end-turn");

        state.nextTurn();
        if (!state.currentPlayer().alive) state.nextTurn(); // skip dead

        state.teleportedThisTurn = false;

        status.accept("Turn: Player " + state.currentPlayerIndex +
                " (shots " + state.currentPlayer().shotsLeft + ")");
    }

    private void resolvePortalIfNeeded(GameState.PlayerState p, int idx, String why) {
        if (p == null) return;
        if (state == null) return;
        if (state.teleportedThisTurn) return;
        if (p.x < 0 || p.y < 0) return;

        int[] dest = state.portals.destinationFrom(p.x, p.y);
        if (dest == null) return;

        state.teleportedThisTurn = true;
        p.x = dest[0];
        p.y = dest[1];
        status.accept("Player " + idx + " portal (" + why + ") -> (" + p.x + "," + p.y + ")");
    }

    private void killPlayer(GameState.PlayerState p, int deathX, int deathY, String reason) {
        // 1) drop key to death cell
        if (p.hasKey) {
            p.hasKey = false;
            state.keyX = deathX;
            state.keyY = deathY;
        }

        // if current player died, block extra teleport logic this turn
        if (p == state.currentPlayer()) {
            state.teleportedThisTurn = true;
        }

        // 2) respawn to hospital if exists
        if (state.hospitalX >= 0 && state.hospitalY >= 0) {
            p.x = state.hospitalX;
            p.y = state.hospitalY;
            p.alive = true;
            status.accept(reason + " -> respawn to HOSPITAL (" + p.x + "," + p.y + ")");
        } else {
            p.alive = false;
            status.accept(reason + " -> NO HOSPITAL (player removed)");
        }
    }

    private void killMinotaur(String reason) {
        // if key lies on minotaur cell - it stays there
        state.minotaurX = -1;
        state.minotaurY = -1;
        status.accept(reason);
    }

    private GameState.PlayerState getOtherPlayerAt(int x, int y, GameState.PlayerState me) {
        if (state.p1 != me && state.p1.alive && state.p1.x == x && state.p1.y == y) return state.p1;
        if (state.p2 != me && state.p2.alive && state.p2.x == x && state.p2.y == y) return state.p2;
        return null;
    }
}
