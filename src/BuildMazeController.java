import java.awt.event.MouseEvent;

public final class BuildMazeController implements ModeController {

    @Override
    public void mouseMoved(BoardPanel panel, int mx, int my) {
        if (!panel.hasBoard()) return;

        GridMetrics gm = panel.metrics();
        if (gm == null) return;

        Edge newHover = panel.edgeAt(mx, my, gm);
        panel.setHoverEdge(newHover);
    }

    @Override
    public void mouseExited(BoardPanel panel) {
        panel.setHoverEdge(null);
    }

    @Override
    public void mouseClicked(BoardPanel panel, MouseEvent e) {
        if (!panel.hasBoard()) return;

        GridMetrics gm = panel.metrics();
        if (gm == null) return;

        Edge edge = panel.edgeAt(e.getX(), e.getY(), gm);
        if (edge != null) {
            Board board = panel.board();
            boolean nowWall = (edge.type == Edge.Type.VERTICAL)
                    ? board.toggleVerticalWall(edge.x, edge.y)
                    : board.toggleHorizontalWall(edge.x, edge.y);

            panel.status("Wall toggled: " + edge + " -> " + (nowWall ? "ON" : "OFF"));
            panel.repaint();
        }

        panel.requestFocusInWindow();
    }
}
