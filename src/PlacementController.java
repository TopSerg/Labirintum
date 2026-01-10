import javax.swing.*;
import java.awt.event.MouseEvent;

public final class PlacementController implements ModeController {

    @Override
    public void mouseMoved(BoardPanel panel, int mx, int my) {
        // в placement hover стен не нужен
        panel.setHoverEdge(null);
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

        int[] cell = panel.cellAt(e.getX(), e.getY(), gm);
        if (cell == null) return;

        int x = cell[0], y = cell[1];

        GameState state = panel.state();

        if (SwingUtilities.isRightMouseButton(e)) {
            panel.eraseAtCell(x, y);
            panel.status("Erased at cell: (" + x + "," + y + ")");
        } else {
            PlacementTool placed = state.getPlacementTool();
            if (panel.placeAtCell(x, y)) {
                PlacementTool next = state.advancePlacementTool();
                if (next != placed) panel.pushPlacementToolToUI(next);
                panel.status("Placed " + placed + " at (" + x + "," + y + "). Next: " + next);
            }
        }

        panel.repaint();
        panel.requestFocusInWindow();
    }
}
