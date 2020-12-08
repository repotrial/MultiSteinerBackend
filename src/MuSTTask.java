import org.apache.commons.cli.*;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;


//Judith's code


public class MuSTTask {

    public static void main(String[] args) {
        //measures the time for the whole task
        Instant before = Instant.now();

        CommandLineParser cmp = new DefaultParser();
        Options opts = new Options();
        createOptions(opts);

        try {
            CommandLine cl = cmp.parse(opts, args);

            //Read in the paths
            String inputNetworkFile = cl.getOptionValue("nw");
            String inputSeedsFile = cl.getOptionValue("s");
            String outputNodesPath = cl.getOptionValue("on");
            String outputEdgesPath = cl.getOptionValue("oe");

            //set the defaults
            int nrOfTrees = 1;
            boolean multiple = false;
            boolean penalized = false;
            double hubPenalty = 0;
            int maxit = 10;
            Random rnd = new Random(42);
            boolean lcc = true;

            if (cl.hasOption("m")) {
                multiple = true;
            }

            if (cl.hasOption("t")) {
                try {
                    nrOfTrees = Integer.parseInt(cl.getOptionValue("t"));
                    if ((nrOfTrees < 1 || nrOfTrees > 50) & multiple)
                        throw new NumberFormatException();
                } catch (NumberFormatException ne) {
                    System.err.println("Please specify a number between 1 and 50 for your number of trees!");
                    throw new ParseException("");
                }
            } else if (!cl.hasOption("t") & multiple) {
                System.err.println("If you want to have multiple results, please specify the number of trees");
                throw new ParseException("");
            }

            if (cl.hasOption("hp")) {
                penalized = true;
                try {
                    hubPenalty = Double.parseDouble(cl.getOptionValue("hp"));
                    if (hubPenalty < 0 || hubPenalty > 1)
                        throw new NumberFormatException();
                } catch (NumberFormatException ne) {
                    System.err.println("Please specify a double between 0.0 and 1.0 for the hub penalty!");
                    throw new ParseException("");
                }
            }

            if (cl.hasOption("mi")) {
                try {
                    maxit = Integer.parseInt(cl.getOptionValue("mi"));
                    if (maxit < 0 || maxit > 20)
                        throw new NumberFormatException();
                } catch (NumberFormatException ne) {
                    System.err.println("Please specify an integer between 0 and 20 for the maximal number of iterations!");
                    throw new ParseException("");
                }
            } else {
                int tmp = nrOfTrees + maxit;
                System.out.println("You will get " + tmp + " iterations maximum per default (nrOfTrees + " + maxit + "). ");
            }

            if (cl.hasOption("nlcc")) {
                lcc = false;
                System.out.println("Performing the anlaysis considering everything and not only the largest connected component");
            }

            boolean parallelDijkstra = cl.hasOption("pd");

            int availableProcessors = Runtime.getRuntime().availableProcessors();
            System.out.println("Available processors: " + availableProcessors);
            int numberOfCoresDijkstra;

            if (cl.hasOption("ncd")) {
                numberOfCoresDijkstra = Integer.parseInt(cl.getOptionValue("ncd"));
                if (numberOfCoresDijkstra > availableProcessors || numberOfCoresDijkstra < 1) {
                    System.out.println("You don't have so many processors available. Will make a FixedThreadPool with the available processors (" + availableProcessors + ") for the Dijkstra task");
                    numberOfCoresDijkstra = -1;
                }
            } else {
                if (parallelDijkstra)
                    System.out.println("Will make a FixedThreadPool with the available processors (" + availableProcessors + ") for the Dijkstra task");
                numberOfCoresDijkstra = -1;
            }
            if (numberOfCoresDijkstra > 0) {
                System.out.println("Using " + numberOfCoresDijkstra + " cores for the Dijkstra task!");
            }
            //adjust the number of cores used for Dijkstra computation
            int numberOfCoresDijkstra_2;
            if (numberOfCoresDijkstra == -1 & parallelDijkstra) {
                numberOfCoresDijkstra_2 = availableProcessors;
            } else if (parallelDijkstra) {
                numberOfCoresDijkstra_2 = numberOfCoresDijkstra;
            } else {
                numberOfCoresDijkstra_2 = 1;
            }

            List<String> terminalNodesStrings;
            List<Vertex> terminalNodes;
            List<String> proteins;
            Map<String, ParsedEdge> edges;

            //for now it is always a PPI network
            GraphUtils gu = new GraphUtils();
            //parse the terminals and the whole graph from the input files specified over -s and -nw
            terminalNodesStrings = gu.parseTerminalNodes(inputSeedsFile);
            ParsedGraph parsedGraph = gu.parseAllNodesAndEdges(inputNetworkFile, penalized);
            proteins = parsedGraph.getNodes();
            edges = parsedGraph.getEdges();

            //parsing the network into the UndirectedNetwork
            UndirectedNetwork graph = new UndirectedNetwork();
            terminalNodes = gu.parseNetwork(graph, proteins, edges, terminalNodesStrings);

            if (lcc) {
                gu.inLCC(terminalNodesStrings, terminalNodes, graph);
                System.out.println("The number of selected terminal nodes in LCC: " + terminalNodesStrings.size());
            }

            if (penalized) {
                double totalAvDeg = GraphUtils.getAvDeg(graph);
                GraphUtils.setEdgeWeight(graph, edges, hubPenalty, totalAvDeg);
                System.out.println("The average degree of nodes in the graph is: " + totalAvDeg);

            }

            //Compute the first MultiSteinerTree
            MultiSteinerTree st = new MultiSteinerTree(graph, terminalNodes, parallelDijkstra, numberOfCoresDijkstra_2);
            SimpleWeightedGraph<Vertex, Link> steiner = st.getSteinerTree();
            System.out.println("The total weight of the first Steiner tree: " + st.getSteinerTreeWeight());
            Set<Link> stEdges = steiner.edgeSet();

            //make participation number maps
            Map<String, Integer> participationNumber = Collections.synchronizedMap(new HashMap<>());
            Map<String, Integer> participationNumberEdges = Collections.synchronizedMap(new HashMap<>());

            //I check whether the tree is unique by adding it to a HashSet -> if the size changes, it was a unique one
            Set<SimpleWeightedGraph<Vertex, Link>> allUniqueTrees = Collections.synchronizedSet(new HashSet<>());
            allUniqueTrees.add(st.getSteinerTree());

            for (Vertex v : steiner.vertexSet()) {
                participationNumber.put(v.getUniprotID(), 1);
            }
            for (Link l : steiner.edgeSet()) {
                participationNumberEdges.put(l.getUniprotID_Concatinated(), 1);
            }

            //if multiple: run more Steiner tree computations
            if (multiple) {
                int uniqueTrees = 1;
                int maximalInterations = nrOfTrees + maxit;
                int iteration = 1;

                while (uniqueTrees < nrOfTrees & iteration < maximalInterations) {
                    System.out.println("Iteration " + iteration);
                    //shuffle the proteins and re-parse them into an Undirected Network to get new Steiner trees
                    Collections.shuffle(proteins, rnd);
                    GraphWithNodes graphWithNodes = getGraphWithNodes(proteins, edges, terminalNodesStrings, gu);
                    //Compute the new Steiner tree
                    st = computeSteinerTree(graphWithNodes, parallelDijkstra, numberOfCoresDijkstra_2);
                    //Check if this is really a new steiner tree: unique edge set?
                    boolean equal = false;
                    int size = allUniqueTrees.size();
                    allUniqueTrees.add(st.getSteinerTree());
                    if (size == allUniqueTrees.size()) {
                        equal = true;
                        System.out.println("Already found this tree!!");
                    }
                    if (!equal) {
                        uniqueTrees = allUniqueTrees.size();
                        System.out.println("Number of unique trees: " + uniqueTrees);
                        getWeightsAndParticipationNumber(st.getSteinerTree(), participationNumber, participationNumberEdges);

                    }
                    iteration++;
                }

                if (uniqueTrees == nrOfTrees) {
                    System.out.println("Stopped because " + nrOfTrees + " unique trees were found!");
                } else {
                    System.out.println("Stopped because you've reached the maximal number of iterations!");
                }

            }

            try {
                //Write the file containing the nodes and their participation numbers
                BufferedWriter bwnodes = Files.newBufferedWriter(Paths.get(outputNodesPath));
                bwnodes.write("node\tparticipation_number");
                bwnodes.newLine();
                for (String uniprotID : participationNumber.keySet()) {
                    bwnodes.write(uniprotID + "\t" + participationNumber.get(uniprotID));
                    bwnodes.newLine();
                }
                bwnodes.close();

                //Write the file containing the edges and their participation numbers
                BufferedWriter bwedges = Files.newBufferedWriter(Paths.get(outputEdgesPath));
                bwedges.write("srcNode\ttargetNode\tparticipation_number");
                bwedges.newLine();
                for (String uniprotConcat : participationNumberEdges.keySet()) {
                    //example: P1234 (-) Q2345
                    String[] ids = uniprotConcat.split(" ");
                    bwedges.write(ids[0] + "\t" + ids[2] + "\t" + participationNumberEdges.get(uniprotConcat));
                    bwedges.newLine();
                }
                bwedges.close();

            } catch (IOException ie) {
                System.out.println("Wrong filepath");
            }

        } catch (ParseException pe) {
            HelpFormatter hf = new HelpFormatter();
            hf.printHelp("args", opts);
            System.exit(1);
        }

        Instant after = Instant.now();
        Duration duration = Duration.between(before, after);
        System.out.println("Everything took: " + (duration.toMillis() / 1000) + "sec!");
    }

    private static void createOptions(Options options) {
        options.addRequiredOption("nw", "network", true, "Path to the Network File (String)");
        Option.builder("nw").numberOfArgs(1).type(String.class);

        options.addRequiredOption("s", "seed", true, "Path to the Seed File (String)");
        Option.builder("s").numberOfArgs(1).type(String.class);

        options.addOption("t", "trees", true, "Number of Trees to be returned (Integer)");
        Option.builder("t").numberOfArgs(1).type(Integer.class);

        options.addRequiredOption("on", "outnodes", true, "Path to output file for nodes");
        Option.builder("on").numberOfArgs(1).type(String.class);

        options.addRequiredOption("oe", "outedges", true, "Path to output file for edges");
        Option.builder("oe").numberOfArgs(1).type(String.class);

        options.addOption("m", "multiple", false, "Choose this option if you want to return multiple results");
        Option.builder("m").numberOfArgs(0);

        options.addOption("nlcc", "nolcc", false, "Choose this option if you do not want to work with only the largest connected component");
        Option.builder("nlcc").numberOfArgs(0);

        options.addOption("hp", "hubpenalty", true, "Specify hub penality between 0.0 and 1.0. If none is specified, there will be no hub penalty");
        Option.builder("hp").numberOfArgs(1).type(Double.class);

        options.addOption("mi", "maxit", true, "The maximum number of iterations is defined as nrOfTrees + x. Here, you can modify x to an integer between 0 and 20. " +
                "If you don't specify this parameter, it will be set to 10");
        Option.builder("mi").numberOfArgs(1).type(Integer.class);

        options.addOption("ncd", "nrOfCoresDijkstra", true, "Specify the number of cores you want to give the Dijkstra computation");
        Option.builder("ncd").numberOfArgs(1).type(Integer.class);

        options.addOption("pd", "parallelDijkstra", false, "Parallel Dijkstra computation");
        Option.builder("pd").numberOfArgs(0);

    }

    private static GraphWithNodes getGraphWithNodes(List<String> proteins, Map<String, ParsedEdge> parsedEdgeMap, List<String> terminalNodesStrings, GraphUtils gu) {
        UndirectedNetwork graph1 = new UndirectedNetwork();
        List<Vertex> terminalNodes1 = gu.parseNetwork(graph1, proteins, parsedEdgeMap, terminalNodesStrings);
        return new GraphWithNodes(graph1, terminalNodes1);
    }

    private static MultiSteinerTree computeSteinerTree(GraphWithNodes graphWithNodes, boolean parallel, int numberOfCores) {
        return new MultiSteinerTree(graphWithNodes.getGraph(), graphWithNodes.getTerminalNodes(), parallel, numberOfCores);
    }


    private static void getWeightsAndParticipationNumber(SimpleWeightedGraph<Vertex, Link> tree, Map<String, Integer> participationNumber, Map<String, Integer> participationNumberEdges) {
        System.out.println("Tree weight: " + MultiSteinerTree.getSteinerTreeWeight(tree));
        for (Vertex v : tree.vertexSet()) {
            participationNumber.put(v.getUniprotID(), participationNumber.getOrDefault(v.getUniprotID(), 0) + 1);
        }
        for (Link l : tree.edgeSet()) {
            participationNumberEdges.put(l.getUniprotID_Concatinated(), participationNumberEdges.getOrDefault(l.getUniprotID_Concatinated(), 0) + 1);
        }
    }

}










