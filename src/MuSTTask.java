import org.apache.commons.cli.*;
import org.jgrapht.graph.SimpleWeightedGraph;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

//Judith's code


public class MuSTTask {


    private static void createOptions(Options options) {
        options.addRequiredOption("nw", "network", true, "Path to the Network File (String)");
        Option.builder("nw").numberOfArgs(1).type(String.class);

        options.addRequiredOption("s", "seed", true, "Path to the Seed File (String)");
        Option.builder("s").numberOfArgs(1).type(String.class);

        options.addOption("t", "trees", true, "Number of Trees to be returned (Integer)");
        Option.builder("t").numberOfArgs(1).type(Integer.class);

        options.addRequiredOption("on", "outnodes", true, "Path to output file for nodes");
        Option.builder("on").numberOfArgs(1).type(String.class);

        options.addRequiredOption("ont", "outnodesTrees", true, "Path to output file containing all nodes per unique tree");
        Option.builder("ont").numberOfArgs(1).type(String.class);

        options.addRequiredOption("oe", "outedges", true, "Path to output file for edges");
        Option.builder("oe").numberOfArgs(1).type(String.class);

        options.addRequiredOption("oet", "outedgesTrees", true, "Path to output file containing all edges and weights per unique tree");
        Option.builder("oet").numberOfArgs(1).type(String.class);

        options.addOption("m", "multiple", false, "Choose this option if you want to return multiple results");
        Option.builder("m").numberOfArgs(0);

        options.addOption("lcc", "lcc", false, "Choose this option if you only want to work with the largest connected component");
        Option.builder("lcc").numberOfArgs(0);

        options.addOption("hp", "hubpenalty", true, "Specify hub penality between 0.0 and 1.0. If none is specified, there will be no hub penalty");
        Option.builder("hp").numberOfArgs(1).type(Double.class);

        options.addOption("mi", "maxit", true, "The maximum number of iterations is defined as nrOfTrees + x. Here, you can modify x to an integer between 0 and 20. " +
                "If you don't specify this parameter, it will be set to 10");
        Option.builder("mi").numberOfArgs(1).type(Integer.class);

        options.addOption("ncd", "nrOfCoresDijkstra", true, "Specify the number of cores you want to give the Dijkstra computation");
        Option.builder("ncd").numberOfArgs(1).type(Integer.class);

        options.addOption("nct", "nrOfCoresTrees", true, "Specify the number of cores you want to give the parallel tree computation");
        Option.builder("nct").numberOfArgs(1).type(Integer.class);

        options.addOption("pt", "parallelTrees", false, "Parallel tree computation in batches of three");
        Option.builder("pt").numberOfArgs(0);

        options.addOption("pd", "parallelDijkstra", false, "Parallel Dijkstra computation");
        Option.builder("pd").numberOfArgs(0);

        options.addOption("rs", "randomSeed", true, "Optional: Set the seed for the random shuffling (Integer)");
        Option.builder("rs").numberOfArgs(1).type(Integer.class);

        options.addOption("sp", "shuffledProteinList", true, "Optional: Path to a file where are the shuffled protein lists will be written down");
        Option.builder("sp").numberOfArgs(1).type(String.class);

    }

    public static void main(String[] args) {
        Instant before = Instant.now();

        CommandLineParser cmp = new DefaultParser();
        Options opts = new Options();
        createOptions(opts);

        try {
            CommandLine cl = cmp.parse(opts, args);

            String inputNetworkFile = cl.getOptionValue("nw");
            String inputSeedsFile = cl.getOptionValue("s");
            int nrOfTrees = 1;
            boolean penalized = false;
            double hubPenalty = 0;
            int maxit = 10;
            String outputNodesPath = cl.getOptionValue("on");
            String outputEdgesPath = cl.getOptionValue("oe");

            String outputAllTreeNodes = cl.getOptionValue("ont");
            String outputAllTreeEdges = cl.getOptionValue("oet");

            boolean multiple = false;
            if (cl.hasOption("m")) {
                multiple = true;
            }

            if (cl.hasOption("t")) {
                try {
                    nrOfTrees = Integer.parseInt(cl.getOptionValue("t"));
                    if ((nrOfTrees < 1 || nrOfTrees > 50) & multiple) throw new NumberFormatException();
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
                    if (hubPenalty < 0 || hubPenalty > 1) throw new NumberFormatException();
                } catch (NumberFormatException ne) {
                    System.err.println("Please specify a double between 0.0 and 1.0 for the hub penalty!");
                    throw new ParseException("");
                }
            }
            if (cl.hasOption("mi")) {
                try {
                    maxit = Integer.parseInt(cl.getOptionValue("mi"));
                    if (maxit < 0 || maxit > 20) throw new NumberFormatException();
                } catch (NumberFormatException ne) {
                    System.err.println("Please specify an integer between 0 and 20 for the maximal number of iterations!");
                    throw new ParseException("");
                }
            } else {
                int tmp = nrOfTrees + maxit;
                System.out.println("You will get " + tmp + " iterations maximum per default (nrOfTrees + " + maxit + "). ");
            }

            boolean lcc = false;
            if (cl.hasOption("lcc")) {
                lcc = true;
            }

            boolean parallelDijkstra = cl.hasOption("pd");
            boolean parallelTrees = cl.hasOption("pt");


            int availableProcessors = Runtime.getRuntime().availableProcessors();
            System.out.println("Available processors: " + availableProcessors);
            int numberOfCoresDijkstra;
            int numberOfCoresTrees;

            if (cl.hasOption("nct")) {
                numberOfCoresTrees = Integer.parseInt(cl.getOptionValue("nct"));
                if (numberOfCoresTrees > availableProcessors || numberOfCoresTrees < 1) {
                    System.out.println("You don't have so many processors available. Will make a standard ForkJoinPool");
                    numberOfCoresTrees = -1;
                }
            } else {
                if (parallelTrees)
                    System.out.println("Will make a FixedThreadPool with three processors for the tree computation");
                numberOfCoresTrees = -1;
            }
            if (cl.hasOption("ncd")) {
                numberOfCoresDijkstra = Integer.parseInt(cl.getOptionValue("ncd"));
                if (numberOfCoresDijkstra > availableProcessors || numberOfCoresDijkstra < 1) {
                    System.out.println("You don't have so many processors available. Will make a FixedThreadPool with the available processors - 3 (" + (availableProcessors - 3) + ") for the Dijkstra task");
                    numberOfCoresDijkstra = -1;
                }
            } else {
                if (parallelDijkstra)
                    System.out.println("Will make a FixedThreadPool with the available processors - 3 (" + (availableProcessors - 3) + ") for the Dijkstra task");
                numberOfCoresDijkstra = -1;
            }
            if (numberOfCoresTrees > 0 && numberOfCoresDijkstra > 0) {
                System.out.println("Using " + numberOfCoresTrees + " cores for the tree computation and " + numberOfCoresDijkstra + " cores for the Dijkstra task!");
            }
            Random rnd;
            if(cl.hasOption("rs")){
                rnd = new Random(Integer.parseInt(cl.getOptionValue("rs")));
                System.out.println("Using " + Integer.parseInt(cl.getOptionValue("rs")) + " as Random seed for shuffling!");
            }else {
                rnd = new Random(42);
                System.out.println("Using a default of 42 as Random seed for shuffling!");
            }
            String fileShuffledProteins = "";
            if(cl.hasOption("sp")){
                fileShuffledProteins = cl.getOptionValue("sp");
            }

            UndirectedNetwork graph = new UndirectedNetwork();
            List<String> terminalNodesStrings;
            List<Vertex> terminalNodes;
            List<String> proteins;
            Map<String, ParsedEdge> edges;

            //for now it is always a PPI network
            GraphUtils gu = new GraphUtils();
            terminalNodesStrings = gu.parseTerminalNodes(inputSeedsFile);
            ParsedGraph parsedGraph = gu.parseAllNodesAndEdges(inputNetworkFile, penalized);
            proteins = parsedGraph.getNodes();
            edges = parsedGraph.getEdges();

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

            int numberOfCoresDijkstra_2 = 3;
            if (numberOfCoresDijkstra == -1 & parallelDijkstra) {
                numberOfCoresDijkstra_2 = Runtime.getRuntime().availableProcessors() - 3;
            } else if (parallelDijkstra) {
                numberOfCoresDijkstra_2 = numberOfCoresDijkstra;
            } else {
                numberOfCoresDijkstra = 1;
            }


            MultiSteinerTree st = new MultiSteinerTree(graph, terminalNodes, parallelDijkstra, numberOfCoresDijkstra_2);
            SimpleWeightedGraph<Vertex, Link> steiner = st.getSteinerTree();
            System.out.println("The total weight of the first Steiner tree: " + st.getSteinerTreeWeight());
            Set<Link> stEdges = steiner.edgeSet();
            //Set <Vertex> stVertices = steiner.vertexSet();


            Map<String, Integer> participationNumber = Collections.synchronizedMap(new HashMap<>());
            Map<String, Integer> participationNumberEdges = Collections.synchronizedMap(new HashMap<>());
            Map<Integer, Set<Link>> stEdgesMap = Collections.synchronizedMap(new HashMap<>());
            Set<SimpleWeightedGraph<Vertex, Link>> allUniqueTrees = Collections.synchronizedSet(new HashSet<>());
            stEdgesMap.put(1, stEdges);
            allUniqueTrees.add(st.getSteinerTree());
            for (Vertex v : steiner.vertexSet()) {
                participationNumber.put(v.getUniprotID(), 1);
            }
            for (Link l : steiner.edgeSet()) {
                participationNumberEdges.put(l.getUniprotID_Concatinated(), 1);
            }

            try {
                BufferedWriter bw = null;
                if(!fileShuffledProteins.equals("")) {
                    bw = Files.newBufferedWriter(Paths.get(fileShuffledProteins));
                    bw.write(Arrays.toString(proteins.toArray()));
                    bw.newLine();
                }

                if (multiple) {
                    int uniqueTrees = 1;
                    int maximalInterations = nrOfTrees + maxit;
                    int iteration = 1;
                    if (parallelTrees) {
                        ExecutorService threadPoolTrees;


                        if (numberOfCoresTrees == -1) {
                            threadPoolTrees = Executors.newFixedThreadPool(3);
                        } else {
                            threadPoolTrees = Executors.newFixedThreadPool(numberOfCoresTrees);
                        }


                        maximalInterations = maxit;

                        System.out.println("Starting the parallel computation of trees ...");
                        int nrOfDuplicates = 0;

                        int batchsize = Math.min((nrOfTrees - 1), 3);
                        int treesLeft = nrOfTrees - 1;
                        System.out.println("Batchsize: " + batchsize + ", nr of trees left to compute: " + treesLeft);
                        //Set<MultiSteinerTree> distinctTrees = Collections.synchronizedSet(new HashSet<>());
                        //distinctTrees.add(st);
                        while (treesLeft != 0) {
                            Set<List<String>> shuffledProteins = new HashSet<>();
                            int batchsize2 = Math.min(treesLeft, batchsize);
                            System.out.println("Batchsize 2: " + batchsize2);
                            treesLeft -= batchsize2;
                            for (int j = 0; j < batchsize2; j++) {
                                Collections.shuffle(proteins, rnd);
                                if(bw != null) {
                                    try {
                                        bw.write(Arrays.toString(proteins.toArray()));
                                        bw.newLine();
                                    } catch (IOException io) {
                                        System.err.println("Error occured while writing the output");
                                    }
                                }
                                shuffledProteins.add(new ArrayList<>(proteins));

                            }


                            ReentrantLock lock = new ReentrantLock();
                            CountDownLatch countDownLatch = new CountDownLatch(shuffledProteins.size());
                            int numberOfCoresDijkstraPerThread = Math.max(1, numberOfCoresDijkstra_2 / shuffledProteins.size());

                            for (List<String> shuffledProteinList : shuffledProteins) {

                                CompletableFuture
                                        //1. get an UndirectedNetwork graph and a List<Vertex> of terminalNodes
                                        .supplyAsync(() -> getGraphWithNodes(shuffledProteinList, edges, terminalNodesStrings, gu), threadPoolTrees)
                                        //2. Compute the MultiSteinerTree
                                        .thenApply(graphWithNodes -> computeSteinerTree(graphWithNodes, parallelDijkstra, numberOfCoresDijkstraPerThread))
                                        //3. Add the tree to the distinctTrees synchronizedSet and if it was distinct, print the weight and add it to the participationNumbers
                                        //  (because it is a Set and the equals() and hashCode() of MultiSteinerTrees compare the edgeSet this should stay distinct)
                                        .thenAccept(multiSteinerTree -> addTree(multiSteinerTree.getSteinerTree(), allUniqueTrees, participationNumber, participationNumberEdges, countDownLatch, lock));

                            }

                            try {
                                System.out.println("Awaiting termination for parallel tree computation in this batch...");
                                countDownLatch.await();
                                System.out.println("Done with batch!");
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }

                            iteration += batchsize2;

                            uniqueTrees = allUniqueTrees.size();
                            System.out.println("Distinct trees: " + allUniqueTrees.size() + ", iteration: " + iteration);
                            int nrOfDuplicatesHere = (iteration - uniqueTrees) - nrOfDuplicates;
                            nrOfDuplicates += nrOfDuplicatesHere;

                            if(allUniqueTrees.size() >= nrOfTrees){
                                treesLeft = 0;
                            } else if (nrOfDuplicatesHere > 0) {
                                System.out.println("There were " + (iteration - uniqueTrees) + " duplicate trees in these " + iteration + " iterations, " + nrOfDuplicatesHere + " of them in this batch!");
                                System.out.println("Maximal Iterations left: " + maximalInterations);
                                if (maximalInterations > 0) {
                                    int newIts;
                                    if (nrOfDuplicatesHere <= maximalInterations) {
                                        newIts = nrOfDuplicatesHere;
                                        maximalInterations -= newIts;
                                    } else {
                                        newIts = maximalInterations;
                                        maximalInterations = 0;
                                    }
                                    treesLeft += newIts;
                                    System.out.println("Adding iterations");
                                }

                            }
                            System.out.println("Trees left to compute: " + treesLeft);
                        }
                        threadPoolTrees.shutdownNow();
                        threadPoolTrees.awaitTermination(1, TimeUnit.SECONDS);

                    } else {
                        while (uniqueTrees < nrOfTrees & iteration < maximalInterations) {
                            System.out.println("Iteration " + iteration);
                            Collections.shuffle(proteins, rnd);
                            if(bw != null) {
                                try {
                                    bw.write(Arrays.toString(proteins.toArray()));
                                    bw.newLine();
                                } catch (IOException ie) {
                                    ie.printStackTrace();
                                }
                            }
                            UndirectedNetwork graph2 = new UndirectedNetwork();
                            terminalNodes = gu.parseNetwork(graph2, proteins, edges, terminalNodesStrings);
                            st = new MultiSteinerTree(graph2, terminalNodes, parallelDijkstra, numberOfCoresDijkstra);
                            //Check if this is really a new steiner tree: unique edge set?
                            boolean equal = false;
                            int size = allUniqueTrees.size();
                            allUniqueTrees.add(st.getSteinerTree());
                            if(size == allUniqueTrees.size()){
                                equal = true;
                                System.out.println("Already found this tree!!");
                            }
                            if (!equal) {
                                uniqueTrees = allUniqueTrees.size();
                                System.out.println("The total weight of the next Steiner tree: " + st.getSteinerTreeWeight());
                                System.out.println("Number of unique trees: " + uniqueTrees);
                                stEdgesMap.put(iteration, st.getSteinerTree().edgeSet());
                                for (Vertex v : st.getSteinerTree().vertexSet()) {
                                    participationNumber.put(v.getUniprotID(), participationNumber.getOrDefault(v.getUniprotID(), 0) + 1);
                                }
                                for (Link l : st.getSteinerTree().edgeSet()) {
                                    participationNumberEdges.put(l.getUniprotID_Concatinated(), participationNumberEdges.getOrDefault(l.getUniprotID_Concatinated(), 0) + 1);
                                }
                            }
                            iteration++;
                        }
                    }
                    if (uniqueTrees == nrOfTrees) {
                        System.out.println("Stopped because " + nrOfTrees + " unique trees were found!");
                    } else {
                        System.out.println("Stopped because you've reached the maximal number of iterations!");
                    }


                }
                if(bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                BufferedWriter bwnodes = Files.newBufferedWriter(Paths.get(outputNodesPath));
                bwnodes.write("node\tparticipation_number");
                bwnodes.newLine();
                for (String uniprotID : participationNumber.keySet()) {
                    bwnodes.write(uniprotID + "\t" + participationNumber.get(uniprotID));
                    bwnodes.newLine();
                }
                bwnodes.close();

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

                bwnodes = Files.newBufferedWriter(Paths.get(outputAllTreeNodes));
                bwedges = Files.newBufferedWriter(Paths.get(outputAllTreeEdges));
                for(SimpleWeightedGraph<Vertex, Link> mst: allUniqueTrees){
                    StringJoiner sjNodes = new StringJoiner(";");
                    StringJoiner sjEdges = new StringJoiner(";");
                    for(Vertex node: mst.vertexSet()){
                        sjNodes.add(node.getUniprotID());
                    }
                    for(Link edge: mst.edgeSet()){
                        sjEdges.add(edge.getSource().getUniprotID() + "," + edge.getTarget().getUniprotID());
                    }
                    bwnodes.write(sjNodes.toString());
                    bwnodes.newLine();
                    bwedges.write("weight:" + MultiSteinerTree.getSteinerTreeWeight(mst) + "\t");
                    bwedges.write(sjEdges.toString());
                    bwedges.newLine();
                }
                bwnodes.close();
                bwedges.close();

            } catch (IOException ie) {
                System.out.println("Wrong filepath");
            }

        } catch (ParseException | InterruptedException ce) {
            HelpFormatter hf = new HelpFormatter();
            hf.printHelp("args", opts);
            System.exit(1);
        }

        Instant after = Instant.now();
        Duration duration = Duration.between(before, after);
        System.out.println("Everything took: " + (duration.toMillis() / 1000) + "sec!");
    }

    private static GraphWithNodes getGraphWithNodes(List<String> proteins, Map<String, ParsedEdge> parsedEdgeMap, List<String> terminalNodesStrings, GraphUtils gu) {
        UndirectedNetwork graph1 = new UndirectedNetwork();
        List<Vertex> terminalNodes1 = gu.parseNetwork(graph1, proteins, parsedEdgeMap, terminalNodesStrings);
        return new GraphWithNodes(graph1, terminalNodes1);
    }

    private static MultiSteinerTree computeSteinerTree(GraphWithNodes graphWithNodes, boolean parallel, int numberOfCores) {
        return new MultiSteinerTree(graphWithNodes.getGraph(), graphWithNodes.getTerminalNodes(), parallel, numberOfCores);
    }

    private static void addTree(SimpleWeightedGraph<Vertex, Link> tree, Set<SimpleWeightedGraph<Vertex, Link>> distinctTrees, Map<String, Integer> participationNumber, Map<String, Integer> participationNumberEdges, CountDownLatch latch, ReentrantLock lock) {
        try {
            lock.lock();
            int size = distinctTrees.size();
            distinctTrees.add(tree);
            if (size < distinctTrees.size()) {
                System.out.println("Found unique tree");
                getWeightsAndParticipationNumber(tree, participationNumber, participationNumberEdges);
            }else {
                System.out.println("Found duplicate tree");
            }

        } finally {
            lock.unlock();
            System.out.println("Counting down in thread " + Thread.currentThread().getName());
            latch.countDown();
        }
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










