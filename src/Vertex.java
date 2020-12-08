import java.util.Objects;

public class Vertex {

    private String uniprotID;

    public Vertex (String uniprotID) {
        this.uniprotID = uniprotID;

    }

    @Override
    public String toString()
    {
        return this.uniprotID;
    }

    public String getUniprotID() {
        return uniprotID;
    }

    public void setUniprotID(String uniprotID) {
        this.uniprotID = uniprotID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vertex vertex = (Vertex) o;
        return Objects.equals(uniprotID, vertex.uniprotID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniprotID);
    }
}
