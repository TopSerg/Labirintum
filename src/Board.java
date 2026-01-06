public class Board {
    private final int size;

    // verticalWalls[x][y] — между (x,y) и (x+1,y), x=0..n-2, y=0..n-1
    private final boolean[][] verticalWalls;

    // horizontalWalls[x][y] — между (x,y) и (x,y+1), x=0..n-1, y=0..n-2
    private final boolean[][] horizontalWalls;

    public Board(int size) {
        if (size < 2) throw new IllegalArgumentException("size must be >= 2");
        this.size = size;
        this.verticalWalls = new boolean[size - 1][size];
        this.horizontalWalls = new boolean[size][size - 1];
    }

    public int getSize() {
        return size;
    }

    public boolean hasVerticalWall(int x, int y) {
        return verticalWalls[x][y];
    }

    public boolean hasHorizontalWall(int x, int y) {
        return horizontalWalls[x][y];
    }

    public boolean toggleVerticalWall(int x, int y) {
        verticalWalls[x][y] = !verticalWalls[x][y];
        return verticalWalls[x][y];
    }

    public boolean toggleHorizontalWall(int x, int y) {
        horizontalWalls[x][y] = !horizontalWalls[x][y];
        return horizontalWalls[x][y];
    }

    public boolean canMove(int x, int y, Direction dir) {
        int n = size;
        switch (dir) {
            case UP:
                if (y == 0) return false;
                // стена между (x,y-1) и (x,y) => horizontalWalls[x][y-1]
                return !horizontalWalls[x][y - 1];
            case DOWN:
                if (y == n - 1) return false;
                // стена между (x,y) и (x,y+1) => horizontalWalls[x][y]
                return !horizontalWalls[x][y];
            case LEFT:
                if (x == 0) return false;
                // стена между (x-1,y) и (x,y) => verticalWalls[x-1][y]
                return !verticalWalls[x - 1][y];
            case RIGHT:
                if (x == n - 1) return false;
                // стена между (x,y) и (x+1,y) => verticalWalls[x][y]
                return !verticalWalls[x][y];
            default:
                return false;
        }
    }

    public int[] move(int x, int y, Direction dir) {
        if (!canMove(x, y, dir)) return new int[]{x, y};
        switch (dir) {
            case UP: return new int[]{x, y - 1};
            case DOWN: return new int[]{x, y + 1};
            case LEFT: return new int[]{x - 1, y};
            case RIGHT: return new int[]{x + 1, y};
            default: return new int[]{x, y};
        }
    }
}
