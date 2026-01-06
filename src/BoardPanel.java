import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class BoardPanel extends JPanel {

    private GameState state;
    private Consumer<String> statusConsumer = s -> {};
    private Consumer<PlacementTool> placementToolConsumer = t -> {};

    private static final int PADDING = 20;
    private static final int HIT_MARGIN_PX = 8;
    private static final float WALL_STROKE = 4f;
    private static final float HOVER_STROKE = 8f;

    private Edge hoverEdge = null;

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

    public void setPlacementToolConsumer(Consumer<PlacementTool> consumer) {
        this.placementToolConsumer = (consumer != null) ? consumer : (t -> {});
    }

    public void setGameState(GameState state) {
        this.state = state;
    }

    public void setStatusConsumer(Consumer<String> consumer) {
        this.statusConsumer = (consumer != null) ? consumer : (s -> {});
    }

    private void onMouseMoved(int mx, int my) {
        if (state == null || state.getBoard() == null) return;

        if (state.getMode() == Mode.BUILD_MAZE) {
            GridMetrics gm = computeGridMetrics();
            if (gm == null) return;
            Edge newHover = pickEdgeAt(mx, my, gm);
            if ((newHover == null && hoverEdge != null) || (newHover != null && !newHover.equals(hoverEdge))) {
                hoverEdge = newHover;
                repaint();
            }
        } else {
            // в других режимах hover стен не нужен
            if (hoverEdge != null) {
                hoverEdge = null;
                repaint();
            }
        }
    }

    private void clearHover() {
        if (hoverEdge != null) {
            hoverEdge = null;
            repaint();
        }
    }

    private void performMove(GameState.PlayerState p, int idx, Direction dir) {
        int[] next = state.getBoard().move(p.x, p.y, dir);
        if (next[0] == p.x && next[1] == p.y) {
            statusConsumer.accept("Blocked by wall/border. Player " + idx);
            // движение не произошло — но ход всё равно можно считать потраченным или нет
            // Я делаю, что ход НЕ тратится? Если хочешь тратить — вызывай endTurn() в onKeyPressed.
            return;
        }

        p.x = next[0];
        p.y = next[1];

        // portals: instant teleport on landing (at most once per active turn)
        resolvePortalIfNeeded(p, idx, "landing");

        // ключ
        if (p.x == state.keyX && p.y == state.keyY) {
            p.hasKey = true;
            state.keyX = state.keyY = -1;
            statusConsumer.accept("Player " + idx + " picked up KEY");
        }

        // минотавр (как у тебя сейчас: больница или смерть)
        if (p.x == state.minotaurX && p.y == state.minotaurY) {
            killPlayer(p, state.minotaurX, state.minotaurY, "Player " + idx + " died (MINOTAUR)");
        }

        // выход
        if (p.x == state.exitX && p.y == state.exitY) {
            if (p.hasKey) {
                state.gameOver = true;
                state.gameOverMessage = "Player " + idx + " WIN (exit + key)";
                statusConsumer.accept(state.gameOverMessage);
                repaint();
                return;
            } else {
                statusConsumer.accept("Need KEY to exit!");
            }
        }

        // если дошли сюда — ход потрачен
        endTurn();
    }

    private void onMouseClicked(MouseEvent e) {
        if (state == null || state.getBoard() == null) return;

        Board board = state.getBoard();
        GridMetrics gm = computeGridMetrics();
        if (gm == null) return;

        int mx = e.getX();
        int my = e.getY();

        // BUILD_MAZE: кликаем по стенкам
        if (state.getMode() == Mode.BUILD_MAZE) {
            Edge edge = pickEdgeAt(mx, my, gm);
            if (edge != null) {
                boolean nowWall = (edge.type == Edge.Type.VERTICAL)
                        ? board.toggleVerticalWall(edge.x, edge.y)
                        : board.toggleHorizontalWall(edge.x, edge.y);

                statusConsumer.accept("Wall toggled: " + edge + " -> " + (nowWall ? "ON" : "OFF"));
                repaint();
            }
            requestFocusInWindow();
            return;
        }

        // PLACE_ENTITIES: кликаем по клеткам (левая — поставить, правая — удалить)
        if (state.getMode() == Mode.PLACE_ENTITIES) {
            int[] cell = pickCellAt(mx, my, gm);
            if (cell == null) return;

            int x = cell[0], y = cell[1];

            if (SwingUtilities.isRightMouseButton(e)) {
                eraseAtCell(x, y);
                statusConsumer.accept("Erased at cell: (" + x + "," + y + ")");
            } else {
                PlacementTool placed = state.getPlacementTool();
                if (placeAtCell(x, y)) {
                    PlacementTool next = state.advancePlacementTool();
                    if (next != placed) {
                        placementToolConsumer.accept(next);
                    }
                    statusConsumer.accept("Placed " + placed + " at (" + x + "," + y + "). Next: " + next);
                }
            }
            repaint();
            requestFocusInWindow();
            return;
        }

        // PLAY: мышь пока не нужна
        requestFocusInWindow();
    }

    private void endTurn() {
        // portals: if the active player ends the turn standing on a portal,
// teleport them (at most once per turn)
        resolvePortalIfNeeded(state.currentPlayer(), state.currentPlayerIndex, "end-turn");
        state.nextTurn();
        if (!state.currentPlayer().alive) state.nextTurn(); // пропустить мёртвого
        state.teleportedThisTurn = false;
        repaint();
        statusConsumer.accept("Turn: Player " + state.currentPlayerIndex +
                " (shots " + state.currentPlayer().shotsLeft + ")");
    }

    private void onKeyPressed(KeyEvent e) {
        if (state == null || state.getBoard() == null) return;
        if (state.getMode() != Mode.PLAY) return;

        if (state.gameOver) {
            statusConsumer.accept(state.gameOverMessage);
            return;
        }

        GameState.PlayerState p = state.currentPlayer();
        int idx = state.currentPlayerIndex;

        if (p.x < 0 || p.y < 0) {
            statusConsumer.accept("У текущего игрока нет стартовой позиции");
            return;
        }

        // Space/Enter: пропуск хода
        if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
            statusConsumer.accept("Player " + idx + " skipped");
            endTurn();
            return;
        }

        // E: нож на своей клетке
        if (e.getKeyCode() == KeyEvent.VK_E) {
            performKnife(p, idx, p.x, p.y, "(self)");
            endTurn();
            return;
        }

        Direction dir = null;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                dir = Direction.UP; break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                dir = Direction.DOWN; break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                dir = Direction.LEFT; break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                dir = Direction.RIGHT; break;
            default:
                return; // не наше
        }

// Alt + направление = выстрел
        if (e.isAltDown()) {
            performShoot(p, idx, dir);
            endTurn();
            return;
        }

// Ctrl + направление = нож в соседнюю клетку
        if (e.isControlDown()) {
            // нож на соседнюю клетку должен учитывать стену/границу
            if (!state.getBoard().canMove(p.x, p.y, dir)) {
                statusConsumer.accept("Knife blocked by wall/border");
                endTurn();
                return;
            }
            int[] target = state.getBoard().move(p.x, p.y, dir);
            performKnife(p, idx, target[0], target[1], "(adjacent)");
            endTurn();
            return;
        }

// иначе: обычное движение
        performMove(p, idx, dir); // см. следующий пункт

        return;

    }

    private void killPlayer(GameState.PlayerState p, int deathX, int deathY, String reason) {
        // 1) ключ падает в клетку смерти
        if (p.hasKey) {
            p.hasKey = false;
            state.keyX = deathX;
            state.keyY = deathY;
        }

        if (p == state.currentPlayer()) {
            state.teleportedThisTurn = true;
        }

        // 2) телепорт в больницу (она обязана быть по вашим правилам)
        if (state.hospitalX >= 0 && state.hospitalY >= 0) {
            p.x = state.hospitalX;
            p.y = state.hospitalY;
            // игрок остаётся живым/в игре
            p.alive = true;
            statusConsumer.accept(reason + " -> respawn to HOSPITAL (" + p.x + "," + p.y + ")");
        } else {
            // на всякий случай (если вдруг потом сделаешь больницу необязательной)
            p.alive = false;
            statusConsumer.accept(reason + " -> NO HOSPITAL (player removed)");
        }
    }


    private boolean placeAtCell(int x, int y) {
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

    private void eraseAtCell(int x, int y) {
        state.portals.removeAt(x, y);
        if (state.keyX == x && state.keyY == y) { state.keyX = state.keyY = -1; }
        if (state.exitX == x && state.exitY == y) { state.exitX = state.exitY = -1; }
        if (state.hospitalX == x && state.hospitalY == y) { state.hospitalX = state.hospitalY = -1; }
        if (state.minotaurX == x && state.minotaurY == y) { state.minotaurX = state.minotaurY = -1; }
        if (state.p1.x == x && state.p1.y == y) { state.p1.x = state.p1.y = -1; state.p1.hasKey = false; }
        if (state.p2.x == x && state.p2.y == y) { state.p2.x = state.p2.y = -1; state.p2.hasKey = false; }
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
        statusConsumer.accept("Player " + idx + " portal (" + why + ") -> (" + p.x + "," + p.y + ")");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (state == null || state.getBoard() == null) {
            g.setColor(Color.DARK_GRAY);
            g.drawString("Создай поле", 20, 30);
            return;
        }

        Board board = state.getBoard();
        GridMetrics gm = computeGridMetrics();
        if (gm == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1) сетка (тонкая)
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(gm.startX, gm.startY, gm.gridSize, gm.gridSize);

            for (int i = 1; i < gm.n; i++) {
                int x = gm.startX + i * gm.cell;
                g2.drawLine(x, gm.startY, x, gm.startY + gm.gridSize);

                int y = gm.startY + i * gm.cell;
                g2.drawLine(gm.startX, y, gm.startX + gm.gridSize, y);
            }

            // 2) границы (красные стены)
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(WALL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(gm.startX, gm.startY, gm.startX + gm.gridSize, gm.startY);
            g2.drawLine(gm.startX, gm.startY + gm.gridSize, gm.startX + gm.gridSize, gm.startY + gm.gridSize);
            g2.drawLine(gm.startX, gm.startY, gm.startX, gm.startY + gm.gridSize);
            g2.drawLine(gm.startX + gm.gridSize, gm.startY, gm.startX + gm.gridSize, gm.startY + gm.gridSize);

            // 3) внутренние стены (красные)
            drawInternalWalls(g2, gm, board);

            // 4) hover стенки (только в BUILD_MAZE)
            if (state.getMode() == Mode.BUILD_MAZE && hoverEdge != null) {
                g2.setColor(new Color(255, 0, 0, 140));
                g2.setStroke(new BasicStroke(HOVER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawEdge(g2, gm, hoverEdge);
            }

            // 5) сущности/игроки
            drawEntities(g2, gm);

            // 6) активный игрок (рамка)
            drawCurrentPlayerHighlight(g2, gm);

        } finally {
            g2.dispose();
        }
        if (state.gameOver) {
            g2.setColor(new Color(0, 0, 0, 160));
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 22f));
            g2.drawString(state.gameOverMessage, gm.startX, gm.startY - 10);
        }
    }

    private void drawInternalWalls(Graphics2D g2, GridMetrics gm, Board board) {
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(WALL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int n = gm.n;

        for (int x = 0; x < n - 1; x++) {
            for (int y = 0; y < n; y++) {
                if (board.hasVerticalWall(x, y)) {
                    drawEdge(g2, gm, new Edge(Edge.Type.VERTICAL, x, y));
                }
            }
        }

        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n - 1; y++) {
                if (board.hasHorizontalWall(x, y)) {
                    drawEdge(g2, gm, new Edge(Edge.Type.HORIZONTAL, x, y));
                }
            }
        }
    }

    private void drawEntities(Graphics2D g2, GridMetrics gm) {
        // простая отрисовка: кружочки/квадратики
        int pad = Math.max(2, gm.cell / 8);
        int size = gm.cell - 2 * pad;

        drawPortals(g2, gm, pad, size);

        // KEY
        if (state.keyX >= 0) drawFilledCircle(g2, gm, state.keyX, state.keyY, pad, size, new Color(255, 215, 0));
        // EXIT
        if (state.exitX >= 0) drawFilledRect(g2, gm, state.exitX, state.exitY, pad, size, new Color(0, 140, 255));
        // HOSPITAL
        if (state.hospitalX >= 0) drawFilledRect(g2, gm, state.hospitalX, state.hospitalY, pad, size, new Color(0, 180, 0));
        // MINOTAUR
        boolean keyOnMinotaur =
                state.keyX == state.minotaurX && state.keyY == state.minotaurY && state.minotaurX >= 0;

// рисуем минотавра
        if (state.minotaurX >= 0) {
            drawFilledCircle(g2, gm, state.minotaurX, state.minotaurY, pad, size, new Color(120, 0, 0));

            // если у него ключ — маленькая золотая метка поверх
            if (keyOnMinotaur) {
                int r = Math.max(4, gm.cell / 6);
                int cx = gm.startX + state.minotaurX * gm.cell + gm.cell - pad - r;
                int cy = gm.startY + state.minotaurY * gm.cell + pad;
                g2.setColor(new Color(255, 215, 0));
                g2.fillOval(cx, cy, r, r);
            }
        }

// рисуем ключ только если он НЕ на минотавре
        if (state.keyX >= 0 && !keyOnMinotaur) {
            drawFilledCircle(g2, gm, state.keyX, state.keyY, pad, size, new Color(255, 215, 0));
        }

        // PLAYER 1 / 2
        if (state.p1.x >= 0) drawPlayer(g2, gm, state.p1, pad, size, new Color(160, 0, 200));
        if (state.p2.x >= 0) drawPlayer(g2, gm, state.p2, pad, size, new Color(255, 120, 0));
    }

    private void drawPlayer(Graphics2D g2, GridMetrics gm, GameState.PlayerState p, int pad, int size, Color c) {
        if (!p.alive) {
            // мертвый — серый
            c = new Color(140, 140, 140);
        }
        drawFilledCircle(g2, gm, p.x, p.y, pad, size, c);

        if (p.hasKey) {
            // маленькая "точка" ключа на игроке
            int cx = gm.startX + p.x * gm.cell + gm.cell - pad - Math.max(4, gm.cell / 6);
            int cy = gm.startY + p.y * gm.cell + pad;
            int r = Math.max(4, gm.cell / 6);
            g2.setColor(new Color(255, 215, 0));
            g2.fillOval(cx, cy, r, r);
        }
    }

    private void drawPortals(Graphics2D g2, GridMetrics gm, int pad, int size) {
        // Pair portals
        java.util.List<int[][]> pairs = state.portals.getPairGroups();
        for (int g = 0; g < pairs.size(); g++) {
            int[][] group = pairs.get(g); // [2][2]
            for (int i = 0; i < group.length; i++) {
                int x = group[i][0];
                int y = group[i][1];
                if (x < 0 || y < 0) continue;
                drawFilledCircle(g2, gm, x, y, pad, size, new Color(110, 210, 255));
                drawPortalLabel(g2, gm, x, y, "P" + g + ":" + i);
            }
        }

        // Cycle portals (3)
        java.util.List<int[][]> cycles = state.portals.getCycleGroups();
        for (int g = 0; g < cycles.size(); g++) {
            int[][] group = cycles.get(g); // [3][2]
            for (int i = 0; i < group.length; i++) {
                int x = group[i][0];
                int y = group[i][1];
                if (x < 0 || y < 0) continue;
                drawFilledCircle(g2, gm, x, y, pad, size, new Color(180, 120, 255));
                drawPortalLabel(g2, gm, x, y, "C" + g + ":" + i);
            }
        }
    }

    private void drawPortalLabel(Graphics2D g2, GridMetrics gm, int x, int y, String text) {
        Font old = g2.getFont();
        g2.setFont(old.deriveFont(Math.max(10f, gm.cell / 4f)));
        g2.setColor(new Color(0, 0, 0, 170));
        int px = gm.startX + x * gm.cell + (gm.cell / 6);
        int py = gm.startY + y * gm.cell + (gm.cell / 2);
        g2.drawString(text, px, py);
        g2.setFont(old);
    }

    private void drawCurrentPlayerHighlight(Graphics2D g2, GridMetrics gm) {
        if (state.getMode() != Mode.PLAY) return;

        GameState.PlayerState p = state.currentPlayer();
        if (p.x < 0 || p.y < 0) return;

        int x = gm.startX + p.x * gm.cell;
        int y = gm.startY + p.y * gm.cell;

        g2.setColor(new Color(0, 0, 0, 120));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRect(x + 2, y + 2, gm.cell - 4, gm.cell - 4);
    }

    private void drawFilledCircle(Graphics2D g2, GridMetrics gm, int x, int y, int pad, int size, Color c) {
        int px = gm.startX + x * gm.cell + pad;
        int py = gm.startY + y * gm.cell + pad;
        g2.setColor(c);
        g2.fillOval(px, py, size, size);
    }

    private void drawFilledRect(Graphics2D g2, GridMetrics gm, int x, int y, int pad, int size, Color c) {
        int px = gm.startX + x * gm.cell + pad;
        int py = gm.startY + y * gm.cell + pad;
        g2.setColor(c);
        g2.fillRect(px, py, size, size);
    }

    private void drawEdge(Graphics2D g2, GridMetrics gm, Edge edge) {
        if (edge.type == Edge.Type.VERTICAL) {
            int xPix = gm.startX + (edge.x + 1) * gm.cell;
            int y1 = gm.startY + edge.y * gm.cell;
            int y2 = y1 + gm.cell;
            g2.drawLine(xPix, y1, xPix, y2);
        } else {
            int yPix = gm.startY + (edge.y + 1) * gm.cell;
            int x1 = gm.startX + edge.x * gm.cell;
            int x2 = x1 + gm.cell;
            g2.drawLine(x1, yPix, x2, yPix);
        }
    }

    private int[] pickCellAt(int mx, int my, GridMetrics gm) {
        if (mx < gm.startX || my < gm.startY || mx >= gm.startX + gm.gridSize || my >= gm.startY + gm.gridSize) {
            return null;
        }
        int x = (mx - gm.startX) / gm.cell;
        int y = (my - gm.startY) / gm.cell;
        return new int[]{x, y};
    }

    private Edge pickEdgeAt(int mx, int my, GridMetrics gm) {
        if (mx < gm.startX || my < gm.startY || mx >= gm.startX + gm.gridSize || my >= gm.startY + gm.gridSize) {
            return null;
        }

        int localX = mx - gm.startX;
        int localY = my - gm.startY;

        int col = localX / gm.cell;
        int row = localY / gm.cell;

        int inCellX = localX % gm.cell;
        int inCellY = localY % gm.cell;

        int distToLeft = inCellX;
        int distToRight = gm.cell - inCellX;
        int distToTop = inCellY;
        int distToBottom = gm.cell - inCellY;

        int distV = Math.min(distToLeft, distToRight);
        int distH = Math.min(distToTop, distToBottom);

        if (distV > HIT_MARGIN_PX && distH > HIT_MARGIN_PX) return null;

        if (distV <= distH) {
            int lineIndex = (distToLeft <= distToRight) ? col : col + 1;
            if (lineIndex <= 0 || lineIndex >= gm.n) return null;
            return new Edge(Edge.Type.VERTICAL, lineIndex - 1, row);
        } else {
            int lineIndex = (distToTop <= distToBottom) ? row : row + 1;
            if (lineIndex <= 0 || lineIndex >= gm.n) return null;
            return new Edge(Edge.Type.HORIZONTAL, col, lineIndex - 1);
        }
    }

    private GridMetrics computeGridMetrics() {
        if (state == null || state.getBoard() == null) return null;

        int n = state.getBoard().getSize();
        int w = getWidth() - 2 * PADDING;
        int h = getHeight() - 2 * PADDING;
        int side = Math.min(w, h);
        if (side <= 0) return null;

        int cell = side / n;
        if (cell <= 0) return null;

        int gridSize = cell * n;
        int startX = (getWidth() - gridSize) / 2;
        int startY = (getHeight() - gridSize) / 2;

        return new GridMetrics(n, cell, gridSize, startX, startY);
    }

    private void killMinotaur(String reason) {
        // если ключ лежит на клетке минотавра — он там и останется
        state.minotaurX = -1;
        state.minotaurY = -1;
        statusConsumer.accept(reason);
    }

    private boolean hasOtherPlayerAt(int x, int y, GameState.PlayerState me) {
        if (state.p1 != me && state.p1.alive && state.p1.x == x && state.p1.y == y) return true;
        if (state.p2 != me && state.p2.alive && state.p2.x == x && state.p2.y == y) return true;
        return false;
    }

    private GameState.PlayerState getOtherPlayerAt(int x, int y, GameState.PlayerState me) {
        if (state.p1 != me && state.p1.alive && state.p1.x == x && state.p1.y == y) return state.p1;
        if (state.p2 != me && state.p2.alive && state.p2.x == x && state.p2.y == y) return state.p2;
        return null;
    }

    private void performShoot(GameState.PlayerState shooter, int shooterIndex, Direction dir) {
        if (shooter.shotsLeft <= 0) {
            statusConsumer.accept("Player " + shooterIndex + ": no shots left");
            return;
        }

        shooter.shotsLeft--;

        int x = shooter.x;
        int y = shooter.y;

        // идём лучом до первой стены/границы
        while (state.getBoard().canMove(x, y, dir)) {
            int[] next = state.getBoard().move(x, y, dir);
            x = next[0];
            y = next[1];

            // 1) минотавр
            if (x == state.minotaurX && y == state.minotaurY) {
                killMinotaur("Player " + shooterIndex + " shot MINOTAUR at (" + x + "," + y + ")");
                return;
            }

            // 2) другой игрок (первый по линии)
            GameState.PlayerState other = getOtherPlayerAt(x, y, shooter);
            if (other != null) {
                killPlayer(other, x, y, "Player " + shooterIndex + " shot player at (" + x + "," + y + ")");
                return;
            }
        }

        statusConsumer.accept("Player " + shooterIndex + " shot: MISS (shots left " + shooter.shotsLeft + ")");
    }

    private void performKnife(GameState.PlayerState attacker, int attackerIndex, Integer targetX, Integer targetY, String label) {
        int tx = targetX;
        int ty = targetY;

        // минотавр
        if (tx == state.minotaurX && ty == state.minotaurY) {
            killMinotaur("Player " + attackerIndex + " knifed MINOTAUR " + label + " at (" + tx + "," + ty + ")");
            return;
        }

        // другой игрок (на своей клетке тоже можно, если они пересеклись)
        GameState.PlayerState other = getOtherPlayerAt(tx, ty, attacker);
        if (other != null) {
            killPlayer(other, tx, ty, "Player " + attackerIndex + " knifed player " + label + " at (" + tx + "," + ty + ")");
            return;
        }

        statusConsumer.accept("Player " + attackerIndex + " knife: no target " + label);
    }

    private static class GridMetrics {
        final int n, cell, gridSize, startX, startY;
        GridMetrics(int n, int cell, int gridSize, int startX, int startY) {
            this.n = n; this.cell = cell; this.gridSize = gridSize;
            this.startX = startX; this.startY = startY;
        }
    }
}
