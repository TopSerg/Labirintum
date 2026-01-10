import java.awt.*;
import java.util.List;

/**
 * Pure rendering (no Swing events, no state mutation).
 * BoardPanel should delegate all drawing here.
 */
public final class BoardRenderer {

    private static final float WALL_STROKE = 4f;
    private static final float HOVER_STROKE = 8f;

    public void render(Graphics2D g2, GameState state, GridMetrics gm, Edge hoverEdge) {
        if (state == null || state.getBoard() == null || gm == null) {
            return;
        }

        Board board = state.getBoard();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1) grid
        drawGrid(g2, gm);

        // 2) borders (red)
        drawBorders(g2, gm);

        // 3) internal walls
        drawInternalWalls(g2, gm, board);

        // 4) hover edge (only BUILD_MAZE)
        if (state.getMode() == Mode.BUILD_MAZE && hoverEdge != null) {
            g2.setColor(new Color(255, 0, 0, 140));
            g2.setStroke(new BasicStroke(HOVER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawEdge(g2, gm, hoverEdge);
        }

        // 5) entities
        drawEntities(g2, gm, state);

        // 6) active player highlight
        drawCurrentPlayerHighlight(g2, gm, state);

        // 7) game over overlay text
        if (state.gameOver) {
            g2.setColor(new Color(0, 0, 0, 160));
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 22f));
            g2.drawString(state.gameOverMessage, gm.startX, gm.startY - 10);
        }
    }

    private void drawGrid(Graphics2D g2, GridMetrics gm) {
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(gm.startX, gm.startY, gm.gridSize, gm.gridSize);

        for (int i = 1; i < gm.n; i++) {
            int x = gm.startX + i * gm.cell;
            g2.drawLine(x, gm.startY, x, gm.startY + gm.gridSize);

            int y = gm.startY + i * gm.cell;
            g2.drawLine(gm.startX, y, gm.startX + gm.gridSize, y);
        }
    }

    private void drawBorders(Graphics2D g2, GridMetrics gm) {
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(WALL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(gm.startX, gm.startY, gm.startX + gm.gridSize, gm.startY);
        g2.drawLine(gm.startX, gm.startY + gm.gridSize, gm.startX + gm.gridSize, gm.startY + gm.gridSize);
        g2.drawLine(gm.startX, gm.startY, gm.startX, gm.startY + gm.gridSize);
        g2.drawLine(gm.startX + gm.gridSize, gm.startY, gm.startX + gm.gridSize, gm.startY + gm.gridSize);
    }

    private void drawInternalWalls(Graphics2D g2, GridMetrics gm, Board board) {
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(WALL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int n = gm.n;

        // vertical walls: x in [0..n-2], y in [0..n-1]
        for (int x = 0; x < n - 1; x++) {
            for (int y = 0; y < n; y++) {
                if (board.hasVerticalWall(x, y)) {
                    drawEdge(g2, gm, new Edge(Edge.Type.VERTICAL, x, y));
                }
            }
        }

        // horizontal walls: x in [0..n-1], y in [0..n-2]
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n - 1; y++) {
                if (board.hasHorizontalWall(x, y)) {
                    drawEdge(g2, gm, new Edge(Edge.Type.HORIZONTAL, x, y));
                }
            }
        }
    }

    private void drawEntities(Graphics2D g2, GridMetrics gm, GameState state) {
        int pad = Math.max(2, gm.cell / 8);
        int size = gm.cell - 2 * pad;

        // portals first (as background)
        drawPortals(g2, gm, state, pad, size);

        // key-on-minotaur special case
        boolean keyOnMinotaur =
                state.keyX == state.minotaurX && state.keyY == state.minotaurY && state.minotaurX >= 0;

        // EXIT
        if (state.exitX >= 0) {
            drawFilledRect(g2, gm, state.exitX, state.exitY, pad, size, new Color(0, 140, 255));
        }

        // HOSPITAL
        if (state.hospitalX >= 0) {
            drawFilledRect(g2, gm, state.hospitalX, state.hospitalY, pad, size, new Color(0, 180, 0));
        }

        // MINOTAUR
        if (state.minotaurX >= 0) {
            drawFilledCircle(g2, gm, state.minotaurX, state.minotaurY, pad, size, new Color(120, 0, 0));

            // if key is on minotaur — show a small gold marker
            if (keyOnMinotaur) {
                int r = Math.max(4, gm.cell / 6);
                int cx = gm.startX + state.minotaurX * gm.cell + gm.cell - pad - r;
                int cy = gm.startY + state.minotaurY * gm.cell + pad;
                g2.setColor(new Color(255, 215, 0));
                g2.fillOval(cx, cy, r, r);
            }
        }

        // KEY (draw once)
        if (state.keyX >= 0 && !keyOnMinotaur) {
            drawFilledCircle(g2, gm, state.keyX, state.keyY, pad, size, new Color(255, 215, 0));
        }

        // PLAYERS
        if (state.p1.x >= 0) {
            drawPlayer(g2, gm, state.p1, pad, size, new Color(160, 0, 200));
        }
        if (state.p2.x >= 0) {
            drawPlayer(g2, gm, state.p2, pad, size, new Color(255, 120, 0));
        }
    }

    private void drawPlayer(Graphics2D g2, GridMetrics gm, GameState.PlayerState p, int pad, int size, Color c) {
        if (!p.alive) {
            c = new Color(140, 140, 140);
        }
        drawFilledCircle(g2, gm, p.x, p.y, pad, size, c);

        if (p.hasKey) {
            int r = Math.max(4, gm.cell / 6);
            int cx = gm.startX + p.x * gm.cell + gm.cell - pad - r;
            int cy = gm.startY + p.y * gm.cell + pad;
            g2.setColor(new Color(255, 215, 0));
            g2.fillOval(cx, cy, r, r);
        }
    }

    private void drawPortals(Graphics2D g2, GridMetrics gm, GameState state, int pad, int size) {
        // Pair portals
        List<int[][]> pairs = state.portals.getPairGroups();
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
        List<int[][]> cycles = state.portals.getCycleGroups();
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

    private void drawCurrentPlayerHighlight(Graphics2D g2, GridMetrics gm, GameState state) {
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
}
