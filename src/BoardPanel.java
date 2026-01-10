import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class BoardPanel extends JPanel {

    private GameState state;
    private Consumer<String> statusConsumer = s -> {};
    private Consumer<PlacementTool> placementToolConsumer = t -> {};

    private Edge hoverEdge = null;
    private final BoardRenderer renderer = new BoardRenderer();
    private final GridGeometry geometry = new GridGeometry();
    private final GameEngine engine = new GameEngine();

    private final ModeController buildMazeController = new BuildMazeController();
    private final ModeController placementController = new PlacementController();
    private final ModeController playController = new PlayController();

    public BoardPanel() {
        setFocusable(true);
        setBackground(Color.WHITE);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { onMouseMoved(e.getX(), e.getY()); }
            @Override public void mouseExited(MouseEvent e) { clearHover(); }
            @Override public void mouseClicked(MouseEvent e) { onMouseClicked(e); }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);

        // управление в режиме PLAY
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                onKeyPressed(e);
            }
        });
    }

    public void setGameState(GameState state) {
        this.state = state;
        engine.setState(state);
    }

    public void setPlacementToolConsumer(Consumer<PlacementTool> consumer) {
        this.placementToolConsumer = (consumer != null) ? consumer : (t -> {});
    }

    public void setStatusConsumer(Consumer<String> consumer) {
        this.statusConsumer = (consumer != null) ? consumer : (s -> {});
        engine.setStatusConsumer(this.statusConsumer);
    }

    private ModeController controller() {
        if (state == null) return buildMazeController; // безопасный дефолт
        return switch (state.getMode()) {
            case BUILD_MAZE -> buildMazeController;
            case PLACE_ENTITIES -> placementController;
            case PLAY -> playController;
        };
    }

    private void onMouseMoved(int mx, int my) {
        controller().mouseMoved(this, mx, my);
    }

    private void clearHover() {
        controller().mouseExited(this);
    }

    void performMove(GameState.PlayerState p, int idx, Direction dir) {
        engine.performMove(p, idx, dir);
        repaint();
    }

    void performShoot(GameState.PlayerState shooter, int shooterIndex, Direction dir) {
        engine.performShoot(shooter, shooterIndex, dir);
        repaint();
    }

    void performKnife(GameState.PlayerState attacker, int attackerIndex, int tx, int ty, String label) {
        engine.performKnife(attacker, attackerIndex, tx, ty, label);
        repaint();
    }

    void endTurn() {
        engine.endTurn();
        repaint();
    }

    private void onMouseClicked(MouseEvent e) {
        controller().mouseClicked(this, e);
    }

    private void onKeyPressed(KeyEvent e) {
        if (state == null || state.getMode() != Mode.PLAY) return;
        controller().keyPressed(this, e);
    }


    protected boolean placeAtCell(int x, int y) {
        PlacementTool tool = state.getPlacementTool();

        // helper-флаги "кто уже в клетке"
        boolean hasKey = (state.keyX == x && state.keyY == y);
        boolean hasExit = (state.exitX == x && state.exitY == y);
        boolean hasHospital = (state.hospitalX == x && state.hospitalY == y);

        boolean hasP1 = (state.p1.x == x && state.p1.y == y);
        boolean hasP2 = (state.p2.x == x && state.p2.y == y);
        boolean hasPlayer = hasP1 || hasP2;

        boolean hasMinotaur = (state.minotaurX == x && state.minotaurY == y);

        boolean hasPortal = state.portals.hasPortalAt(x, y);

        boolean hasAnyEntity = hasKey || hasExit || hasHospital || hasMinotaur || hasPlayer;

        if ((tool == PlacementTool.PORTAL_PAIR || tool == PlacementTool.PORTAL_CYCLE3) && hasAnyEntity) {
            statusConsumer.accept("Нельзя ставить портал в клетку с сущностями/игроками.");
            return false;
        }
        if ((tool != PlacementTool.PORTAL_PAIR && tool != PlacementTool.PORTAL_CYCLE3) && hasPortal) {
            statusConsumer.accept("Нельзя ставить сущности/игроков в клетку с порталом.");
            return false;
        }


        // RULE 1: key/exit/hospital mutually exclusive
        if (tool == PlacementTool.KEY || tool == PlacementTool.EXIT || tool == PlacementTool.HOSPITAL) {
            boolean cellHasSpecial = hasKey || hasExit || hasHospital;
            // если ставим "спешл" и там уже другой "спешл" — запрет
            if (cellHasSpecial && !(
                    (tool == PlacementTool.KEY && hasKey) ||
                            (tool == PlacementTool.EXIT && hasExit) ||
                            (tool == PlacementTool.HOSPITAL && hasHospital)
            )) {
                statusConsumer.accept("Нельзя ставить ключ/выход/больницу в одну клетку.");
                return false;
            }
        }

        // RULE 2: players <-> minotaur нельзя вместе
        if (tool == PlacementTool.MINOTAUR && hasPlayer) {
            statusConsumer.accept("Нельзя ставить минотавра в клетку с игроком.");
            return false;
        }
        if ((tool == PlacementTool.PLAYER_1 || tool == PlacementTool.PLAYER_2) && hasMinotaur) {
            statusConsumer.accept("Нельзя ставить игрока в клетку с минотавром.");
            return false;
        }

        // RULE 3: players нельзя на ключ
        if ((tool == PlacementTool.PLAYER_1 || tool == PlacementTool.PLAYER_2) && hasKey) {
            statusConsumer.accept("Нельзя ставить игрока на ключ.");
            return false;
        }

        // --- если всё ок — ставим ---
        switch (tool) {
            case KEY: state.keyX = x; state.keyY = y; break;
            case EXIT: state.exitX = x; state.exitY = y; break;
            case HOSPITAL: state.hospitalX = x; state.hospitalY = y; break;
            case MINOTAUR: state.minotaurX = x; state.minotaurY = y; break;
            case PLAYER_1: state.p1.x = x; state.p1.y = y; state.p1.alive = true; break;
            case PLAYER_2: state.p2.x = x; state.p2.y = y; state.p2.alive = true; break;
            case PORTAL_PAIR: {
                while (state.portals.getPairGroups().size() <= state.pairCursorGroup) {
                    state.portals.addPairGroup();
                }
                int g = state.pairCursorGroup;
                int i = state.pairCursorIndex;

                if (hasPortal || !state.portals.place(PortalNetwork.Type.PAIR, g, i, x, y)) {
                    statusConsumer.accept("Клетка уже занята порталом.");
                    return false;
                }

                statusConsumer.accept("Placed PORTAL_PAIR group " + g + " index " + i + " at (" + x + "," + y + ")");

                state.pairCursorIndex++;
                if (state.pairCursorIndex >= 2) {
                    state.pairCursorIndex = 0;
                    state.pairCursorGroup++;
                }
                break;
            }

            case PORTAL_CYCLE3: {
                while (state.portals.getCycleGroups().size() <= state.cycleCursorGroup) {
                    state.portals.addCycleGroup3();
                }
                int g = state.cycleCursorGroup;
                int i = state.cycleCursorIndex;

                if (hasPortal || !state.portals.place(PortalNetwork.Type.CYCLE, g, i, x, y)) {
                    statusConsumer.accept("Клетка уже занята порталом.");
                    return false;
                }

                statusConsumer.accept("Placed PORTAL_CYCLE3 group " + g + " index " + i + " at (" + x + "," + y + ")");

                state.cycleCursorIndex++;
                if (state.cycleCursorIndex >= 3) {
                    state.cycleCursorIndex = 0;
                    state.cycleCursorGroup++;
                }
                break;
            }
        }
        return true;
    }

    protected void eraseAtCell(int x, int y) {
        state.portals.removeAt(x, y);
        if (state.keyX == x && state.keyY == y) { state.keyX = state.keyY = -1; }
        if (state.exitX == x && state.exitY == y) { state.exitX = state.exitY = -1; }
        if (state.hospitalX == x && state.hospitalY == y) { state.hospitalX = state.hospitalY = -1; }
        if (state.minotaurX == x && state.minotaurY == y) { state.minotaurX = state.minotaurY = -1; }
        if (state.p1.x == x && state.p1.y == y) { state.p1.x = state.p1.y = -1; state.p1.hasKey = false; }
        if (state.p2.x == x && state.p2.y == y) { state.p2.x = state.p2.y = -1; state.p2.hasKey = false; }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (state == null || state.getBoard() == null) {
            g.setColor(Color.DARK_GRAY);
            g.drawString("Создай поле", 20, 30);
            return;
        }

        GridMetrics gm = geometry.compute(getWidth(), getHeight(), state.getBoard().getSize());
        if (gm == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            renderer.render(g2, state, gm, hoverEdge);
        } finally {
            g2.dispose();
        }
    }

    GameState state() { return state; }
    Board board() { return state.getBoard(); }
    boolean hasBoard() { return state != null && state.getBoard() != null; }

    void status(String s) { statusConsumer.accept(s); }

    void pushPlacementToolToUI(PlacementTool t) { placementToolConsumer.accept(t); }

    GridMetrics metrics() { return geometry.compute(getWidth(), getHeight(), state.getBoard().getSize()); }
    Edge edgeAt(int mx, int my, GridMetrics gm) { return geometry.pickEdgeAt(mx, my, gm); }
    int[] cellAt(int mx, int my, GridMetrics gm) { return geometry.pickCellAt(mx, my, gm); }

    void setHoverEdge(Edge newHover) {
        if ((newHover == null && hoverEdge != null) || (newHover != null && !newHover.equals(hoverEdge))) {
            hoverEdge = newHover;
            repaint();
        }
    }

}
