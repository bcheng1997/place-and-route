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

## Limitations

- This placer is currently limited to Xilinx **7-Series** architecture and has only been tested on the `xc7z020clg400-1` part (Zynq). 
- Only the following basic primitive cells are supported:
    * `FDRE`
    * `FDSE`
    * `BUGFCTRL`
    * `LUT` (1-6)
    * `DSP48E1`
    * `RAMB18E1`
    * `CARRY4`
- All `LUT`s and `FDRE`s are all mapped to `SLICEL` Sites. `SLICEM` heterogeneity utilization is not yet supported.
- `RAMB36E1` utilization is not supported.
- You may need to edit the `synth_design` command in the file `/tcl/synth.tcl` (line 38) to force the Vivado placer to synthesize the netlist using only the supported primitive cells.
    * `synth_design` flags: https://docs.amd.com/r/en-US/ug835-vivado-tcl-commands/synth_design
- Support for the above cells is hard-coded into the prepacker and packer by name. Future work could involve generalizing the packer to support all primitive cells within a given architecture family.
- Generally speaking, this means only modest-sized inference-based designs are supported. Designs that instantiate any unsupported macros or primitives will eventually lead to errors in the placer.

## Usage

- Requirements: 
    * RapidWright 2024.2.1-beta
    * Vivado 2025.1.1
    * gradle
    * tcl
    * ffmpeg
- Update the first five variables in the `run.sh` file to reflect your environment.
```
# ==========================================================================
# Update PROJ_DIR, DESIGN, TOP_LEVEL, XILINX_VIVADO, and RAPIDWRIGHT_PATH to
# reflect your environment.

# This repository's directory.
PROJ_DIR="/home/bcheng/workspace/dev/place-and-route"

# Name of your Verilog project.
DESIGN="fir_filter"

# Name of the top level module of your Verilog project.
TOP_LEVEL="top_level"

# Directories of your Vivado 2025.1.1 and RapidWright installations.
export XILINX_VIVADO=/home/bcheng/workspace/tools/Xilinx/2025.1.1/Vivado
export RAPIDWRIGHT_PATH=/home/bcheng/workspace/tools/RapidWright

# (end of user's variables)
# ==========================================================================
```

- To place your own design, create your new HDL project directory in:  
    * `/hdl/verilog/your_proj/`  

- With the following subdirectories:  
    * `../your_proj/src/`  
    * `../your_proj/verif/`  
    * `../your_proj/constrs/`  
    * `../your_proj/sim_functional/`  
    * `../your_proj/sim_postroute/`  
    * (Optional) `../your_proj/python/`  

- Put your Verilog source (.v files) in `/your_proj/src/`
- Put your SystemVerilog testbench (.sv files) in `/your_proj/verif/`
- Put your constraints file (.xdc file) in `/your_proj/constrs/`
- (Optional) Put any Python scripts necessary for synthesis in `/your_proj/python/`. You might do this if, for example, you need to generate an input signal or weights for a FIR Filter design.  
- Vivado's simulation workspace files will be automatically generated in `/your_proj/sim_functional/` and `/your_proj/sim_postroute/` whenever `./run.sh sim_functional` or `./run.sh sim_postroute` are run, respectively. No need to delete them for after each simulation run.

- Your project directory should look something like this:
```
fir_filter
├── constrs
│   └── constraints.xdc
├── python
│   ├── generate_weights.py
│   ├── generate_input_signal.py
│   └── plot_weights.py
├── sim_functional
├── sim_postroute
├── src
│   ├── fir_tap.v
│   ├── fir_filter.v
│   └── top_level.v
└── verif
    └── tb_top_level.sv
```

- Using the script commands (each command assumes all of the previous commands have already run once, in this order):  
    1) `./run.sh synth`: First, runs all of your `/your_proj/python/` scripts, if any. Then, synthesizes your design using default Vivado synthesis from your Verilog `/src/` files.  
    2) `./run.sh sim_functional`: (Optional) Runs your testbench simulation using Vivado's **Functional** Simulation.  
    3) `./run.sh compile`: Compiles your Java placement program.  
    4) `./run.sh place`: Executes your Java placement program.  
    5) `./run.sh graphics`: (Optional) After placement, generates graphical figures showing the placement performance and timeline progress. Generates the HPWL Cost History plot as well as an animated GIF of the physical placement progress on the chip. Individual images are generated using the `ImageMaker` class during placement. `ffmpeg` is used to compile the images into a GIF.  
    6) `./run.sh route`: (Optional) Routes the placed design using Vivado's default router.  
    7) `./run.sh sim_postroute`: (Optional) Runs your testbench simulation using Vivado's **Post-route** simulation. The routing and simulations are mostly just here as a sanity check for the placer.  




