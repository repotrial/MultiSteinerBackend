import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsedGraph {

    private List<String> nodes;
    private Map<String, ParsedEdge> edges;

    public ParsedGraph(){
        this.nodes = new ArrayList<>();
        this.edges = new HashMap<>();
    }


    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public Map<String, ParsedEdge> getEdges() {
        return edges;
    }

    public void setEdges(Map<String, ParsedEdge> edges) {
        this.edges = edges;
    }
}
