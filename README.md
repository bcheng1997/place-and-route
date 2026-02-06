# MS Technical Paper

A study on placement algorithms for heterogenous FPGAs, using the [RapidWright API](https://www.rapidwright.io/docs/Introduction.html#what-is-rapidwright) from AMD/Xilinx.

Read the paper here: [Paper](latex/paper/master.pdf).

## Simulated Annealing (SA)
SA placement of a 2048-order FIR filter using 32 MAC pipelines on a Zynq-7000 (xc7z020).
The synthesized netlist contains 919 FDRE, 1691 LUT, 282 CARRY4, 32 RAMB18E1, and 64 DSP48E1.
![gif](outputs/readme/output.gif)

The HPWL Curve.
![image](outputs/readme/PlacerAnnealHybrid_20000_96_cost_history.png)


## Directories

`hdl/` : these are the HDL designs that we want to perform synthesis, placement, and routing on.  

`java/` : this is the program that runs our custom placement strategies via the RapidWright API.  

`latex/` : documents - proposal, the technical paper itself.  

`notes/` : notes I've taken on other papers I reference.  

`outputs/` : all output files created during the placement program's execution are generated here.   

`tcl/` : Tcl scripts to interface with Vivado.  

`run.sh` : the master script that coordinates `hdl/`, `java/`, `tcl/`, and `outputs/`.

