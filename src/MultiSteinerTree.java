import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.SimpleWeightedGraph;

public class MultiSteinerTree {

    private SimpleWeightedGraph<Vertex, Link> graph;
    private SimpleWeightedGraph<Vertex, Link> tree;
    private List<Vertex> steinerNodes;
    private boolean parallel;
    private int numberOfCores;

    public MultiSteinerTree(SimpleWeightedGraph<Vertex, Link> graph, List<Vertex> steinerNodes, boolean parallel, int numberOfCores) {
        this.graph = graph;
        this.steinerNodes = steinerNodes;
        this.parallel = parallel;
        this.numberOfCores = numberOfCores;

        runAlgorithm();

    }

    // Using SimpleWeightedGraph instead of WeightedPseudograph, it's better since it doesn't have selfloop and multiple edges
    private SimpleWeightedGraph<Vertex, Link> step1(Map<Link, List<Link>> spMap, boolean parallel, int numberOfCores) {

        SimpleWeightedGraph<Vertex, Link> g = new SimpleWeightedGraph<>(Link.class);

        for (Vertex n : this.steinerNodes) {
            g.addVertex(n);
        }

        if (parallel) {
            //Dijkstra computation: make a FixedThreadPool with the specified number of cores
            ExecutorService threadPoolDijkstra = Executors.newFixedThreadPool(numberOfCores);
            System.out.println("Made a special FixedThreadPool for the Dijkstra task of thread: " + Thread.currentThread().getName() + " with " + numberOfCores + " places for threads. ");

            //CountDownLatch to guarantee that the algorithm waits for the termination of the Dijkstra computation
            CountDownLatch countDownLatch = new CountDownLatch(this.steinerNodes.size() * this.steinerNodes.size());

            Set<String> linkSet = Collections.synchronizedSet(new HashSet<>());
            Map<String, Link> linkMap = Collections.synchronizedMap(new HashMap<>());

            for(Vertex n1: this.steinerNodes){
                for(Vertex n2: this.steinerNodes){
                    String link = n1.getUniprotID() + "(-)" + n2.getUniprotID();
                    String linkReverse = n2.getUniprotID() + "(-)" + n2.getUniprotID();

                    if(!n1.equals(n2) && !linkSet.contains(link) && !linkSet.contains(linkReverse)){
                        CompletableFuture
                                .supplyAsync(() -> DijkstraShortestPath.findPathBetween(this.graph, n1, n2), threadPoolDijkstra)
                                .thenAccept(vertexLinkGraphPath -> memorizeWeights(n1, n2, vertexLinkGraphPath, spMap, linkMap, countDownLatch));
                    }else{
                        countDownLatch.countDown();
                    }

                    linkSet.add(n1.getUniprotID() + "(-)" + n2.getUniprotID());
                }
            }
            //Wait for the countDownLatches
            try {
                System.out.println("Awaiting termination for Dijkstra in Thread " + Thread.currentThread().getName() + "...");
                countDownLatch.await();
                System.out.println("Done with Dijkstra parallel computation in Thread " + Thread.currentThread().getName() + "!");
                threadPoolDijkstra.shutdownNow();
            }catch (InterruptedException ie){
                ie.printStackTrace();
            }
            //add the edges to the graph with their dijkstra weights
            for(Vertex n1: this.steinerNodes){
                for(Vertex n2: this.steinerNodes){
                    String uniProtConcat = n1.getUniprotID()+ " (-) " + n2.getUniprotID();
                    String uniProtConcatReverse = n2.getUniprotID()+ " (-) " + n1.getUniprotID();
                    Link l;
                    if(linkMap.containsKey(uniProtConcat)){
                        l = linkMap.get(uniProtConcat);
                    }else
                        l = linkMap.getOrDefault(uniProtConcatReverse, null);

                    if(l != null){
                        g.addEdge(n1, n2, l);
                        g.setEdgeWeight(l, l.getWeight());
                    }

                }
            }
        //now: not parallel computation
        } else {
            for (Vertex n1 : this.steinerNodes) {
                for (Vertex n2 : this.steinerNodes) {
                    if (g.containsEdge(n1, n2) || g.containsEdge(n2, n1) || n1.equals(n2))
                        continue;
                    if (!n1.equals(n2)) {
                        GraphPath<Vertex, Link> gpath = DijkstraShortestPath.findPathBetween(this.graph, n1, n2);
                        Link e = new Link(n1, n2, gpath.getWeight());
                        g.addEdge(n1, n2, e);
                        g.setEdgeWeight(e, gpath.getWeight());

                        // Keep the shortest path between steiner nodes to avoid computing them again at step 3, Attention: not all of these shortest paths will be used
                        spMap.put(e, gpath.getEdgeList());
                    }
                }
            }
        }

        return g;

    }

    private void memorizeWeights(Vertex n1, Vertex n2, GraphPath<Vertex, Link> gpath, Map<Link, List<Link>> spMap, Map<String, Link> linkMap, CountDownLatch countDownLatch){
        try {
            Link e = new Link(n1, n2, gpath.getWeight());
            // Keep the shortest path between steiner nodes to avoid computing them again at step 3, Attention: not all of these shortest paths will be used
            spMap.put(e, gpath.getEdgeList());
            linkMap.put(e.getUniprotID_Concatinated(), e);
        }finally {
            countDownLatch.countDown();
        }
    }


    private SimpleWeightedGraph<Vertex, Link> step2(SimpleWeightedGraph<Vertex, Link> g1) {

        KruskalMinimumSpanningTree<Vertex, Link> mst = new KruskalMinimumSpanningTree<>(g1);
        SpanningTreeAlgorithm.SpanningTree<Link> krst = mst.getSpanningTree();

        Set<Link> edges = krst.getEdges();

        SimpleWeightedGraph<Vertex, Link> g2 = new SimpleWeightedGraph<>(Link.class);

        for (Link edge : edges) {
            g2.addVertex(edge.getSource());
            g2.addVertex(edge.getTarget());
            g2.addEdge(edge.getSource(), edge.getTarget(), edge);
        }

        return g2;
    }

    private SimpleWeightedGraph<Vertex, Link> step3(SimpleWeightedGraph<Vertex, Link> g2, Map<Link, List<Link>> spMap) {


        SimpleWeightedGraph<Vertex, Link> g3 = new SimpleWeightedGraph<>(Link.class);

///////// Can be source of randomness!!!!!! for deterministic iteration maybe needs to be sorted and kept as list
        Set<Link> edges = g2.edgeSet();
        Vertex source, target;

        for (Link edge : edges) {

            List<Link> pathEdges = spMap.get(edge);
            if (pathEdges == null)
                continue;

            for (Link pathEdge : pathEdges) {

                if (g3.edgeSet().contains(pathEdge))
                    continue;

                source = pathEdge.getSource();
                target = pathEdge.getTarget();

                if (!g3.vertexSet().contains(source))
                    g3.addVertex(source);

                if (!g3.vertexSet().contains(target))
                    g3.addVertex(target);

                g3.addEdge(source, target, pathEdge);
            }
        }


        return g3;
    }

    //step 4 is equal to step 2
    private SimpleWeightedGraph<Vertex, Link> step4(SimpleWeightedGraph<Vertex, Link> g3) {
        return step2(g3);
    }

    private SimpleWeightedGraph<Vertex, Link> step5(SimpleWeightedGraph<Vertex, Link> g4) {

        List<Vertex> nonSteinerLeaves = new ArrayList<>();

        Set<Vertex> vertexSet = g4.vertexSet();
        for (Vertex vertex : vertexSet) {
            if (g4.degreeOf(vertex) == 1 && steinerNodes.indexOf(vertex) == -1) {
                nonSteinerLeaves.add(vertex);
            }
        }

        Vertex source, target;
        for (Vertex nonSteinerLeaf : nonSteinerLeaves) {
            source = nonSteinerLeaf;
            do
            {
                Link e = g4.edgesOf(source).toArray(new Link[0])[0];
                target = this.graph.getEdgeTarget(e);

                // this should not happen, but just in case of ...
                if (target.equals(source))
                    target = e.getSource();

                g4.removeVertex(source);
                source = target;
            } while (g4.degreeOf(source) == 1 && steinerNodes.indexOf(source) == -1);

        }

        return g4;
    }

    private void runAlgorithm() {

        System.out.println("Computing the MultiSteinerTree in Thread " + Thread.currentThread().getName() + "...");

        Map<Link, List<Link>> spMap = Collections.synchronizedMap(new HashMap<>());
        SimpleWeightedGraph<Vertex, Link> g1 = step1(spMap, this.parallel, this.numberOfCores);

        if (g1.vertexSet().size() < 2) {
            this.tree = new SimpleWeightedGraph<>(Link.class);
            for (Vertex n : g1.vertexSet()) this.tree.addVertex(n);
            return;
        }

        SimpleWeightedGraph<Vertex, Link> g2 = step2(g1);

        SimpleWeightedGraph<Vertex, Link> g3 = step3(g2, spMap);

        SimpleWeightedGraph<Vertex, Link> g4 = step4(g3);

        this.tree = step5(g4);
        System.out.println("Done with the MultiSteinerTree in Thread " + Thread.currentThread().getName() + "!");


    }

    public SimpleWeightedGraph<Vertex, Link> getSteinerTree() {
        return this.tree;
    }

    public double getSteinerTreeWeight() {
        double weight = 0D;
        for (Link l : this.tree.edgeSet()) {
            weight += l.getWeight();
        }
        return weight;
    }

    public static double getSteinerTreeWeight(SimpleWeightedGraph<Vertex, Link> tree){
        double weight = 0D;
        for (Link l: tree.edgeSet()){
            weight += l.getWeight();
        }
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiSteinerTree that = (MultiSteinerTree) o;
        return tree.edgeSet().equals(that.getSteinerTree().edgeSet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(tree.edgeSet());
    }
}