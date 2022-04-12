# MultiSteinerBackend
MuST command line tool for NeDRex backend

<h3> Options for the jar: </h3> 
<b> Input Files: </b> 
<br> <br>
<b> -nw,--network (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp; Path to the Network File (String)
<br>
<b> -s,--seed (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Path to the Seed File (String)
<br> <br>
<b> Output Files: </b>
<br> <br>
<b> -oe,--outedges (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp; Path to output file for edges (String)
<br>
<b> -on,--outnodes (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp; Path to output file for nodes (String)
<br> <br>
<b> Parameters for the tree computation: </b>
<br> <br>
<b>-hp,--hubpenalty (arg)</b> &nbsp;&nbsp;&nbsp;&nbsp; Specify hub penality between 0.0 and 1.0. If none is specified, there will be no hub penalty
<br>
<b>-m,--multiple  </b> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Choose this option if you want to return multiple results
<br>
<b> -t,--trees (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Number of Trees to be returned (Integer). Also choose -m for this parameter. 
<br>
<b> -mi,--maxit (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp; The maximum number of iterations is defined as nrOfTrees + x. Here, you can modify x to an integer between 0 and 20. If you don't specify this parameter, it will be set to 10
<br>
<b> -nlcc,--nolcc </b> &nbsp;&nbsp;&nbsp;&nbsp; Choose this option if you do not want to work with only the largest connected component
<br> <br>
<b> Parameters for the parallelization of the Dijkstra computation </b>
<br> <br>
<b> -pd,--parallelDijkstra</b> &nbsp;&nbsp;&nbsp;&nbsp; Choose this option for parallel Dijkstra computation
<br>
<b> -ncd,--nrOfCoresDijkstra (arg) </b> &nbsp;&nbsp;&nbsp;&nbsp; Specify the number of cores you want to give the Dijkstra computation. If this is not specified, all available processors will be used.

