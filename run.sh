#!/bin/bash

# ==========================================================================
# Update ROOT_DIR, DESIGN, TOP_LEVEL, XILINX_VIVADO, and RAPIDWRIGHT_PATH to
# reflect your environment.

# This repository's directory.
ROOT_DIR="/home/bcheng/workspace/dev/place-and-route"

# Name of your Verilog project.
DESIGN="fir_filter"

# Name of the top level module of your Verilog project.
TOP_LEVEL="top_level"

# Directories of your Vivado 2025.1.1 and RapidWright installations.
export XILINX_VIVADO=/home/bcheng/workspace/tools/Xilinx/2025.1.1/Vivado
export RAPIDWRIGHT_PATH=/home/bcheng/workspace/tools/RapidWright

# (end of user's variables)
# ==========================================================================

export PATH="$PATH:$XILINX_VIVADO/bin"
export JAVA_HOME=$XILINX_VIVADO/tps/lnx64/jre21.0.1_12
export PATH="$JAVA_HOME/bin:$PATH"
export PATH="$PATH:$RAPIDWRIGHT_PATH/bin"
export CLASSPATH=$RAPIDWRIGHT_PATH/bin:$RAPIDWRIGHT_PATH/jars/*
export _JAVA_OPTIONS=-Xmx32736m

SYNTH_TCL="$ROOT_DIR/tcl/synth.tcl"
RTL_TCL="$ROOT_DIR/tcl/rtl.tcl"
PLACE_TCL="$ROOT_DIR/tcl/place.tcl"
ROUTE_TCL="$ROOT_DIR/tcl/route.tcl"
SIM_TCL="$ROOT_DIR/tcl/sim.tcl"

DESIGN_DIR="$ROOT_DIR/hdl/verilog/${DESIGN}"
TOP_PARAMS_FILE="$DESIGN_DIR/parameters_top_level.txt"
XELAB_TOP_PARAMS=""
SYNTH_TOP_PARAMS=""

if [[ ! -f "$TOP_PARAMS_FILE" ]]; then
    echo "Error: parameter file not found at $TOP_PARAMS_FILE"
    exit 1
fi
# read config file and construct xelab -generic_top arguments
while IFS= read -r line; do
    # skip empty lines or lines starting with comment (#)
    [[ -z "$line" || "$line" == \#* ]] && continue
    # append parameter as a -generic_top argument
    XELAB_TOP_PARAMS+="-generic_top $line "
    SYNTH_TOP_PARAMS+="-generic $line "
done <"$TOP_PARAMS_FILE"

start_stage=${1:-all} # Use first argument or defaults to all
num_args=$#           # Number of arguments into script

check_exit_status() {
    if [ $? -ne 0 ]; then
        echo "$1 failed."
        exit 1
    fi
}

if [ "$start_stage" == "python" ] || [ "$start_stage" == "all" ]; then
    DIR="$DESIGN_DIR/python"

    for f in "$DIR"/*.py; do
        [[ -e "$f" ]] || {
            echo "No Python files in $DIR"
            break
        }
        echo "Running $f"
        python3 "$f"
    done
fi

# Vivado Synthesis Stage
if [ "$start_stage" == "synth" ] || [ "$start_stage" == "all" ]; then
    echo "Running Vivado synthesis..."
    vivado -mode batch -source $SYNTH_TCL -nolog -nojournal -tclargs $ROOT_DIR $DESIGN $SYNTH_TOP_PARAMS
    check_exit_status "Vivado synthesis"
    echo "Vivado synthesis completed. Check 'synthesized.dcp'."
fi

# Vivado RTL Synthesis Stage
if [ "$start_stage" == "rtl" ]; then
    echo "Running Vivado RTL synthesis..."
    cd "$DESIGN_DIR/src"
    cd $ROOT_DIR
    vivado -mode batch -source $RTL_TCL -nolog -nojournal -tclargs $ROOT_DIR $DESIGN $SYNTH_TOP_PARAMS
    check_exit_status "Vivado RTL"
    echo "Vivado synthesis completed. Starting GUI."
fi

# Functional Simulation
if [ "$start_stage" == "sim_functional" ]; then
    echo "Running Functional Simulation..."

    cd "$DESIGN_DIR/sim_functional"
    cat <<EOL >xsim_cfg.tcl
    log_wave -recursive *
    run all
    exit
EOL
    cat <<EOL >waveform.tcl
    create_wave_config; add_wave /; set_property needs_save false [current_wave_config]
EOL

    cd "$DESIGN_DIR/sim_functional"
    # Read source files and log
    src_files=("$DESIGN_DIR"/src/*.{v,sv})
    for file in "${src_files[@]}"; do
        if [ -f "$file" ]; then
            if [[ "$file" == *.sv ]]; then
                xvlog -sv "$file"
            else
                xvlog "$file"
            fi
            check_exit_status "xvlog for $file"
        fi
    done

    xvlog "$XILINX_VIVADO/data/verilog/src/glbl.v" -L xil_defaultlib -L uvm --incr --relax
    check_exit_status "xvlog for glbl"

    # Read verification files and log
    verif_files=("$DESIGN_DIR"/verif/*.sv)
    for file in "${verif_files[@]}"; do
        if [ -f "$file" ]; then
            xvlog -sv "$file"
            check_exit_status "xvlog for $file"
        fi
    done

    # Elaboration
    xelab -debug typical -top "tb_$TOP_LEVEL" -snapshot "${TOP_LEVEL}_functional" \
        -timescale 1ns/1ps \
        -L xil_defaultlib -L uvm -L secureip -L xpm -L simprims_ver -L unisims_ver \
        $XELAB_TOP_PARAMS \
        glbl
    # -L xil_defaultlib -L uvm -L secureip -L unisims_ver -L simprims_ver
    check_exit_status "xelab"

    # Simulation
    xsim "${TOP_LEVEL}_functional" --tclbatch xsim_cfg.tcl
    check_exit_status "xsim"

    # Open the wavefile in Vivado
    # xsim "${TOP_LEVEL}_functional".wdb -gui -tclbatch waveform.tcl
fi

# Gradle Build Stage (Java Compilation)
if [ "$start_stage" == "compile" ] || [ "$start_stage" == "all" ]; then
    echo "Building Java project with Gradle..."
    rm -rf "$ROOT_DIR/outputs/placers/*"
    cd $ROOT_DIR/java
    gradle build
    check_exit_status "Gradle build"
    echo "Gradle build completed."
fi

# Java Placement Stage
if [ "$start_stage" == "place" ] || [ "$start_stage" == "all" ]; then
    rm $ROOT_DIR/outputs/placers/* -r
    echo "Running Java placement with Gradle..."
    cd $ROOT_DIR/java
    gradle run --args="$ROOT_DIR"
    check_exit_status "Gradle run"
    cd $ROOT_DIR
    echo "Java placement executed. Check 'logger.txt' for output."
fi

# Return to outer dir
cd $ROOT_DIR

# Render placement graphics
if [ "$start_stage" == "graphics" ] || [ "$start_stage" == "all" ]; then
    cd $ROOT_DIR
    FPS=10
    # INPUT="outputs/placers/PlacerAnnealRandom/graphics/images"
    PLACERS_DIR="outputs/placers"
    INPUT_PATTERN="%08d.png"

    # Loop over all items in PLACERS_DIR
    for placer_dir in "$PLACERS_DIR"/*; do
        # python3 python/plot_convergence.py "$placer_dir"
        # Only proceed if it's a directory
        if [ -d "$placer_dir" ]; then
            images_path="$placer_dir/graphics/images"

            # Check if the images directory exists and has PNG files
            if [ -d "$images_path" ] && ls "$images_path"/*.png &>/dev/null; then
                echo "Generating GIF for: $placer_dir"

                # Go to images directory
                cd "$images_path" || continue

                # Run ffmpeg to create the GIF
                ffmpeg -y \
                    -framerate "$FPS" \
                    -i "$INPUT_PATTERN" \
                    -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
                    -loop 0 \
                    ../output.gif

                # Return to previous directory
                cd - &>/dev/null
            else
                echo "Skipping $placer_dir (no 'graphics/images' folder or no PNG files found)."
            fi
        fi
    done

    # cd $INPUT

    # OUTPUT="../video.mp4"
    # ffmpeg -y -framerate $FPS -i $INPUT_PATTERN -vcodec libx264 "$OUTPUT" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2"

    # OUTPUT="../output.gif"
    # ffmpeg -y -framerate $FPS -i $INPUT_PATTERN -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" -loop 0 "$OUTPUT"

    # height not divisible by 2 error:
    # https://stackoverflow.com/questions/20847674/ffmpeg-libx264-height-not-divisible-by-2
fi

cd $ROOT_DIR

# Vivado Route Stage
if [ "$start_stage" == "route" ] || [ "$start_stage" == "all" ]; then
    echo "Running Vivado route..."
    vivado -mode batch -source $ROUTE_TCL -nolog -nojournal -tclargs $ROOT_DIR
    check_exit_status "Vivado route"
    echo "Vivado route completed. Check 'routed.dcp'."
fi

# Post-Implementation Timing Simulation
if [ "$start_stage" == "sim_postroute" ] || [ "$start_stage" == "all" ]; then
    # cd "$DESIGN_DIR/sim_postroute"
    # rm -r *
    # cd $ROOT_DIR

    echo "Running Post-Implementation Timing Simulation..."
    vivado -mode batch -source $SIM_TCL -nolog -nojournal -tclargs $ROOT_DIR $DESIGN $TOP_LEVEL
    check_exit_status "Vivado sim"

    cd "$DESIGN_DIR/sim_postroute"

    # xsim config tcl
    cat <<EOL >xsim_cfg.tcl
    log_wave -recursive *
    run all
    exit
EOL

    # waveform tcl
    cat <<EOL >waveform.tcl
    create_wave_config; add_wave /; set_property needs_save false [current_wave_config]
EOL

    echo "Beginning xvlog..."
    xvlog "${TOP_LEVEL}_time_impl.v" -L xil_defaultlib -L uvm --incr --relax
    check_exit_status "xvlog"
    xvlog -sv "$DESIGN_DIR/verif/tb_${TOP_LEVEL}.sv" -L xil_defaultlib -L uvm --incr --relax
    check_exit_status "xvlog"
    xvlog "$XILINX_VIVADO/data/verilog/src/glbl.v" -L xil_defaultlib -L uvm --incr --relax
    check_exit_status "xvlog"

    echo "Beginning xelab..."

    # xelab \
    #     -debug typical -relax -mt 8 -maxdelay \
    #     -transport_int_delays \
    #     -pulse_r 0 -pulse_int_r 0 -pulse_int_e 0 \
    #     -timescale 1ns/1ps \
    #     -snapshot "${TOP_LEVEL}_time_impl" -top "tb_${TOP_LEVEL}" \
    #     -sdfroot "$DESIGN_DIR/sim_postroute/${TOP_LEVEL}_time_impl.sdf" \
    #     -log elaborate.log \
    #     -verbose 0 \
    #     $XELAB_TOP_PARAMS \
    #     glbl
    # # -L xpm -L xil_defaultlib -L uvm -L secureip -L unisims_ver -L simprims_ver \

    # original from vivado
    # xelab --incr --debug typical --relax --mt 8 --maxdelay \
    #     -L xil_defaultlib -L uvm -L simprims_ver -L secureip \
    #     --snapshot tb_top_level_time_impl \
    #     -transport_int_delays -pulse_r 0 -pulse_int_r 0 -pulse_e 0 -pulse_int_e 0 \
    #     xil_defaultlib.tb_top_level xil_defaultlib.glbl \
    #     -log elaborate.log

    xelab --incr --debug typical --relax --mt 8 --maxdelay \
        -L xil_defaultlib -L uvm -L simprims_ver -L secureip \
        --snapshot "${TOP_LEVEL}_time_impl" -top "tb_${TOP_LEVEL}" \
        -transport_int_delays -pulse_r 0 -pulse_int_r 0 -pulse_e 0 -pulse_int_e 0 \
        -timescale 1ps/1ps \
        -log elaborate.log \
        -verbose 0 \
        $XELAB_TOP_PARAMS \
        glbl

    check_exit_status "xelab"

    echo "Beginning xsim..."
    xsim ${TOP_LEVEL}_time_impl -tclbatch xsim_cfg.tcl
    check_exit_status "xsim"
    # xsim ${TOP_LEVEL}_time_impl.wdb -gui -tclbatch waveform.tcl
    # source /home/bcheng/workspace/tools/oss-cad-suite/environment
    # gtkwave waveform.vcd
fi

# Return to outer dir
cd $ROOT_DIR

# xvlog --incr --relax -L uvm -prj tb_counter_vlog.prj
# xsim tb_counter_time_impl -key {Post-Implementation:sim_1:Timing:tb_counter} -tclbatch tb_counter.tcl -log simulate.log
# xsim tb_counter_time_impl --tclbatch xsim_cfg.tcl -log simulate.log
# xsim tb_counter_time_impl -gui -downgrade_fatal2warning -downgrade_error2warning -tl -tp -log simulate.log
