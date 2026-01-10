import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public interface ModeController {
    default void mouseMoved(BoardPanel panel, int mx, int my) {}
    default void mouseExited(BoardPanel panel) {}
    default void mouseClicked(BoardPanel panel, MouseEvent e) {}
    default void keyPressed(BoardPanel panel, KeyEvent e) {}
}
