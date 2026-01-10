public final class GridGeometry {

    public static final int DEFAULT_PADDING = 20;
    public static final int DEFAULT_HIT_MARGIN_PX = 8;

    private final int padding;
    private final int hitMarginPx;

    public GridGeometry() {
        this(DEFAULT_PADDING, DEFAULT_HIT_MARGIN_PX);
    }

    public GridGeometry(int padding, int hitMarginPx) {
        this.padding = padding;
        this.hitMarginPx = hitMarginPx;
    }

    public GridMetrics compute(int panelWidth, int panelHeight, int boardSize) {
        if (boardSize <= 0) return null;

        int w = panelWidth - 2 * padding;
        int h = panelHeight - 2 * padding;
        int side = Math.min(w, h);
        if (side <= 0) return null;

        int cell = side / boardSize;
        if (cell <= 0) return null;

        int gridSize = cell * boardSize;
        int startX = (panelWidth - gridSize) / 2;
        int startY = (panelHeight - gridSize) / 2;

        return new GridMetrics(boardSize, cell, gridSize, startX, startY);
    }

    public int[] pickCellAt(int mx, int my, GridMetrics gm) {
        if (gm == null) return null;
        if (mx < gm.startX || my < gm.startY || mx >= gm.startX + gm.gridSize || my >= gm.startY + gm.gridSize) {
            return null;
        }
        int x = (mx - gm.startX) / gm.cell;
        int y = (my - gm.startY) / gm.cell;
        return new int[]{x, y};
    }

    public Edge pickEdgeAt(int mx, int my, GridMetrics gm) {
        if (gm == null) return null;
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

        if (distV > hitMarginPx && distH > hitMarginPx) return null;

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
}
