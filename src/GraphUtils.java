import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

//Judith's code

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;

public class GraphUtils {

    public List<String> parseTerminalNodes(String pathToSeedNodes) {
        List<String> terminalNodes = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(pathToSeedNodes));
            System.out.println("Parsing terminal nodes...");
            Instant before = Instant.now();
            String line = br.readLine();
            while (line != null) {
                String tmpSeed = line;
                terminalNodes.add(tmpSeed);
                line = br.readLine();
            }
            br.close();
            Instant after = Instant.now();
            Duration duration = Duration.between(before, after);
            System.out.println("Took " + duration.toMillis() + " ms!");
        } catch (IOException io) {
            System.err.println("Cannot find the seed file!");
        }

        return terminalNodes;
    }

    public ParsedGraph parseAllNodesAndEdges(String pathToNetwork, boolean penalized) {
        ParsedGraph parsedGraph = new ParsedGraph();
        try {
            BufferedReader br = new BufferedReader(new FileReader(pathToNetwork));
            System.out.println("Parsing network from file...");
            Instant before = Instant.now();
            String line = br.readLine();
            boolean firstLine = true;
            boolean weighted = false;
            String header;
            while (line != null) {
                String[] lineIDs = line.split("\t");

                if (lineIDs.length >= 2 & firstLine) {
                    header = line;
                    if (header.contains("weight")) {
                        weighted = true;
                    }
                    firstLine = false;
                } else if (lineIDs.length >= 2 & !firstLine) {
                    String sourceNode = lineIDs[0];
                    String targetNode = lineIDs[1];

                    double weight;

                    boolean selfLoop = false;
                    if (sourceNode.equals(targetNode)) {
                        selfLoop = true;
                    }

                    if (!selfLoop) {
                        if (!penalized & weighted) {
                            weight = Double.parseDouble(lineIDs[2]);
                        } else {
                            weight = 1.0;
                        }
                        ParsedEdge parsedEdge = new ParsedEdge(sourceNode, targetNode, weight);
                        if (!parsedGraph.getNodes().contains(sourceNode)) {
                            parsedGraph.getNodes().add(sourceNode);
                        }
                        if (!parsedGraph.getNodes().contains(targetNode)) {
                            parsedGraph.getNodes().add(targetNode);
                        }
                        parsedGraph.getEdges().put(parsedEdge.getUniprotIDsConcat(), parsedEdge);

                    }

                } else {
                    System.err.println("Wrong network file format. Please put in:\n" +
                            "source_protein target_protein [weight]" +
                            "<UniprotID_source> <UniprotID_target> [weight]\n" +
                            "<UniprotID_source> <UniprotID_target> [weight]\n … ");
                }

                line = br.readLine();

            }
            br.close();
            Instant after = Instant.now();
            Duration duration = Duration.between(before, after);
            System.out.println("Took " + duration.toMillis() + " ms!");
        } catch (IOException io) {
            System.err.println("Cannot find network file!");
        }
        return parsedGraph;
    }


    public List<Vertex> parseNetwork(Graph<Vertex, Link> graph, List<String> proteins, Map<String, ParsedEdge> parsedEdges, List<String> terminalNodesStrings) {
        System.out.println("Parsing Network in Thread " + Thread.currentThread().getName() + "... ");
        Map<String, Vertex> nodeMap = new HashMap<>();
        List<Vertex> terminalNodes = new ArrayList<>();
        for (String uniprotID : proteins) {
            Vertex node = new Vertex(uniprotID);
            graph.addVertex(node);
            nodeMap.put(uniprotID, node);
            if (terminalNodesStrings.contains(uniprotID)) {
                terminalNodes.add(node);
            }

        }
        for (ParsedEdge pe : parsedEdges.values()) {
            String sourceID = pe.getSourceNode();
            String targetID = pe.getTargetNode();
            double weight = pe.getWeight();

            Vertex src = nodeMap.get(sourceID);
            Vertex targ = nodeMap.get(targetID);
            Link edge = new Link(src, targ, weight);
            graph.addEdge(src, targ, edge);
            graph.setEdgeWeight(edge, weight);
        }
        System.out.println("Done with parsing in Thread " + Thread.currentThread().getName() + "!");
        return terminalNodes;

    }

    public void inLCC(List<String> terminalNodesString, List<Vertex> terminalNodes, UndirectedNetwork graph) {
        System.out.println("Finding the largest connected component...");
        ConnectivityInspector<Vertex, Link> cIns = new ConnectivityInspector<>(graph);
        List<Set<Vertex>> cnctdcomps = cIns.connectedSets();
        //System.out.println("The number of connected components: " + cnctdcomps.size());
        int maxcopmSize = 0;
        Set<Vertex> maxCnctdComp = new HashSet<>();
        for (Set<Vertex> s : cnctdcomps) {
            if (s.size() > maxcopmSize) {
                maxcopmSize = s.size();
                maxCnctdComp = s;
            }
        }
        System.out.println("The largest connected component has size " + maxCnctdComp.size() + "!");
        terminalNodes.retainAll(maxCnctdComp);
        Set<String> maxCompStr = new HashSet<>();
        for (Vertex v : maxCnctdComp) {
            String uniprotID = v.getUniprotID();
            maxCompStr.add(uniprotID);
        }
        terminalNodesString.retainAll(maxCompStr);
    }

    public static double getAvDeg(UndirectedNetwork graph) {
        double totalAvDeg;
        double sumDeg = 0;
        for (Vertex v : graph.vertexSet()) {
            sumDeg += graph.degreeOf(v);
        }
        totalAvDeg = sumDeg / (double) graph.vertexSet().size();
        return totalAvDeg;
    }


    public static void setEdgeWeight(UndirectedNetwork graph, Map<String, ParsedEdge> edges, double hubPenalty, double totalAvDeg) {

        for (Link l: graph.edgeSet()) {
            double eAvDeg = ((double)(graph.degreeOf(l.getSource()) + graph.degreeOf(l.getTarget())))/2;
            double w = (1-hubPenalty)*totalAvDeg+(hubPenalty*eAvDeg);
            double weight = BigDecimal.valueOf(w).setScale(5, RoundingMode.HALF_UP).doubleValue();
            edges.get(l.getUniprotID_Concatinated()).setWeight(weight);
            //network.getRow(network.getEdge(l.getSuid())).set("weight", weight);
            graph.setEdgeWeight(l, weight);
        }
    }
}