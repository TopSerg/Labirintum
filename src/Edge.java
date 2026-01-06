import java.util.Objects;

public class Edge {
    public enum Type { VERTICAL, HORIZONTAL }

    public final Type type;
    public final int x; // индекс стенки (см. Board)
    public final int y;

    public Edge(Type type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge edge = (Edge) o;
        return x == edge.x && y == edge.y && type == edge.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, x, y);
    }

    @Override
    public String toString() {
        return type + "(" + x + "," + y + ")";
    }
}
