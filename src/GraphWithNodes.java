import java.util.List;

public class GraphWithNodes {

    private UndirectedNetwork graph;
    private List<Vertex> terminalNodes;

    public GraphWithNodes(UndirectedNetwork graph, List<Vertex> terminalNodes) {
        this.graph = graph;
        this.terminalNodes = terminalNodes;
    }

    public UndirectedNetwork getGraph() {
        return graph;
    }

    public void setGraph(UndirectedNetwork graph) {
        this.graph = graph;
    }

    public List<Vertex> getTerminalNodes() {
        return terminalNodes;
    }

    public void setTerminalNodes(List<Vertex> terminalNodes) {
        this.terminalNodes = terminalNodes;
    }
}
