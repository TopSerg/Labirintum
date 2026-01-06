import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {

    private final GameState state = new GameState();
    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel status = new JLabel("Ready");

    public MainWindow() {
        super("Labyrinth Editor / Game");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 700));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildTopPanel(), BorderLayout.NORTH);

        boardPanel.setGameState(state);
        boardPanel.setStatusConsumer(status::setText);

        add(boardPanel, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(status, BorderLayout.SOUTH);
    }

    private JComponent buildTopPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));

        // ===== Row 1: board size + create/clear =====
        row1.add(new JLabel("Размер поля:"));

        Integer[] sizes = {4, 5, 6, 7, 8, 9, 10, 12, 15};
        JComboBox<Integer> sizeCombo = new JComboBox<>(sizes);
        sizeCombo.setSelectedItem(4);
        row1.add(sizeCombo);

        JButton createBtn = new JButton("Создать поле");
        row1.add(createBtn);

        JButton clearBtn = new JButton("Очистить сущности");
        row1.add(clearBtn);

        // ===== Row 2: modes + placement tool =====
        JToggleButton buildWalls = new JToggleButton("Стены");
        JToggleButton placeEntities = new JToggleButton("Объекты");
        JToggleButton play = new JToggleButton("Игра");

        ButtonGroup group = new ButtonGroup();
        group.add(buildWalls);
        group.add(placeEntities);
        group.add(play);
        buildWalls.setSelected(true);

        row2.add(buildWalls);
        row2.add(placeEntities);
        row2.add(play);

        row2.add(new JLabel("Ставим:"));
        JComboBox<PlacementTool> toolCombo = new JComboBox<>(PlacementTool.values());
        toolCombo.setSelectedItem(PlacementTool.KEY);
        row2.add(toolCombo);

        // ===== handlers (как у тебя было) =====
        createBtn.addActionListener(e -> {
            int n = (Integer) sizeCombo.getSelectedItem();
            state.setBoard(new Board(n));
            state.clearEntitiesAndPlayers();
            boardPanel.repaint();
            status.setText("Board created: " + n + " x " + n);
            boardPanel.requestFocusInWindow();
        });

        clearBtn.addActionListener(e -> {
            state.clearEntitiesAndPlayers();
            boardPanel.repaint();
            status.setText("Entities cleared");
            boardPanel.requestFocusInWindow();
        });

        buildWalls.addActionListener(e -> {
            state.setMode(Mode.BUILD_MAZE);
            status.setText("Mode: BUILD_MAZE (клик по стенкам)");
            boardPanel.repaint();
            boardPanel.requestFocusInWindow();
        });

        placeEntities.addActionListener(e -> {
            state.setMode(Mode.PLACE_ENTITIES);
            status.setText("Mode: PLACE_ENTITIES (клик по клеткам)");
            boardPanel.repaint();
            boardPanel.requestFocusInWindow();
        });

        play.addActionListener(e -> {
            String err = validateReadyForPlay();
            if (err != null) {
                status.setText(err);
                buildWalls.setSelected(true);
                state.setMode(Mode.BUILD_MAZE);
                boardPanel.repaint();
                boardPanel.requestFocusInWindow();
                return;
            }

            state.resetRunStateForPlay();
            state.setMode(Mode.PLAY);
            status.setText("Mode: PLAY (стрелки/WASD, Space/Enter — пропуск хода)");
            boardPanel.repaint();
            boardPanel.requestFocusInWindow();
        });

        toolCombo.addActionListener(e -> {
            PlacementTool tool = (PlacementTool) toolCombo.getSelectedItem();
            state.setPlacementTool(tool);
            status.setText("Placement tool: " + tool);
            boardPanel.requestFocusInWindow();
        });

        // Собираем верхнюю панель
        root.add(row1);
        root.add(row2);

        return root;
    }


    private String validateReadyForPlay() {
        if (state.getBoard() == null) return "Сначала создай поле.";

        // Требуем всё, как ты описал: выход, ключ, больница, минотавр, игроки
        if (state.exitX < 0) return "Не поставлен EXIT.";
        if (state.keyX < 0) return "Не поставлен KEY.";
        if (state.hospitalX < 0) return "Не поставлен HOSPITAL.";
        if (state.minotaurX < 0) return "Не поставлен MINOTAUR.";
        if (state.p1.x < 0 || state.p1.y < 0) return "Не поставлен PLAYER_1.";
        if (state.p2.x < 0 || state.p2.y < 0) return "Не поставлен PLAYER_2.";

        return null;
    }

}
