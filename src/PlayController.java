import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public final class PlayController implements ModeController {

    @Override
    public void mouseMoved(BoardPanel panel, int mx, int my) {
        // hover стен не нужен
        panel.setHoverEdge(null);
    }

    @Override
    public void mouseExited(BoardPanel panel) {
        panel.setHoverEdge(null);
    }

    @Override
    public void mouseClicked(BoardPanel panel, MouseEvent e) {
        panel.requestFocusInWindow(); // в play мышь пока не нужна
    }

    @Override
    public void keyPressed(BoardPanel panel, KeyEvent e) {
        if (!panel.hasBoard()) return;

        GameState state = panel.state();
        if (state.gameOver) {
            panel.status(state.gameOverMessage);
            return;
        }

        GameState.PlayerState p = state.currentPlayer();
        int idx = state.currentPlayerIndex;

        if (p.x < 0 || p.y < 0) {
            panel.status("У текущего игрока нет стартовой позиции");
            return;
        }

        // Space/Enter: пропуск хода
        if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
            panel.status("Player " + idx + " skipped");
            panel.endTurn();
            return;
        }

        // E: нож на своей клетке
        if (e.getKeyCode() == KeyEvent.VK_E) {
            panel.performKnife(p, idx, p.x, p.y, "(self)");
            panel.endTurn();
            return;
        }

        Direction dir = null;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W: dir = Direction.UP; break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S: dir = Direction.DOWN; break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A: dir = Direction.LEFT; break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D: dir = Direction.RIGHT; break;
            default: return;
        }

        // Alt + направление = выстрел
        if (e.isAltDown()) {
            panel.performShoot(p, idx, dir);
            panel.endTurn();
            return;
        }

        // Ctrl + направление = нож в соседнюю клетку
        if (e.isControlDown()) {
            if (!state.getBoard().canMove(p.x, p.y, dir)) {
                panel.status("Knife blocked by wall/border");
                panel.endTurn();
                return;
            }
            int[] target = state.getBoard().move(p.x, p.y, dir);
            panel.performKnife(p, idx, target[0], target[1], "(adjacent)");
            panel.endTurn();
            return;
        }

        // иначе: обычное движение
        panel.performMove(p, idx, dir);
    }
}
