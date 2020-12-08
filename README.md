# MultiSteinerBackend
MuST command line tool for RepoScape backend

<h4> Options for the jar: </h4> 
<b> Input Files: <b> 
<br>
<b> -nw,--network (arg) </b>      Path to the Network File (String)
<br>
<b> -s,--seed (arg) </b>         Path to the Seed File (String)
<br>
<b> Output Files: </b>
<br>
<b> -oe,--outedges (arg) </b>     Path to output file for edges (String)
<br>
<b> -on,--outnodes (arg) </b>     Path to output file for nodes (String)
<br>
<b> Parameters for the tree computation: </b>
<br>
<b>-hp,--hubpenalty (arg)</b>     Specify hub penality between 0.0 and 1.0. If none is specified, there will be no hub penalty
<br>
<b>-m,--multiple  </b>            Choose this option if you want to return multiple results
<br>
<b> -t,--trees (arg) </b>         Number of Trees to be returned (Integer). Also choose -m for this parameter. 
<br>
<b> -mi,--maxit (arg) </b>        The maximum number of iterations is defined as nrOfTrees + x. Here, you can modify x to an integer between 0 and 20. If you don't specify this parameter, it will be set to 10
<br>
<b> -nlcc,--nolcc </b>            Choose this option if you do not want to work with only the largest connected component
<br>
<b> Parameters for the parallelization of the Dijkstra computation </b>
<br>
<b> -pd,--parallelDijkstra</b>   Choose this option for parallel Dijkstra computation
<br>
<b> -ncd,--nrOfCoresDijkstra (arg) </b>   Specify the number of cores you want to give the Dijkstra computation. If this is not specified, all available processors will be used.

