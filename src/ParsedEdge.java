import java.util.Objects;

public class ParsedEdge {

    private String sourceNode;
    private String targetNode;
    private String uniprotIDsConcat;
    private double weight;

    public ParsedEdge(String sourceNode, String targetNode, double weight){
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.uniprotIDsConcat = sourceNode + " (-) " + targetNode;
        this.weight = weight;
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(String sourceNode) {
        this.sourceNode = sourceNode;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(String targetNode) {
        this.targetNode = targetNode;
    }

    public String getUniprotIDsConcat() {
        return uniprotIDsConcat;
    }

    public void setUniprotIDsConcat(String uniprotIDsConcat) {
        this.uniprotIDsConcat = uniprotIDsConcat;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedEdge that = (ParsedEdge) o;
        return Objects.equals(uniprotIDsConcat, that.uniprotIDsConcat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniprotIDsConcat);
    }
}
