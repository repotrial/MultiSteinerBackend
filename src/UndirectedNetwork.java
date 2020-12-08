import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.List;

public class UndirectedNetwork extends SimpleWeightedGraph<Vertex, Link> {
    public UndirectedNetwork() {
        super(Link.class);
    }

}
