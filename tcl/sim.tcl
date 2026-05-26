# set design "fir_filter"
# set top_level "top_level"
# set root_dir "/home/bcheng/workspace/dev/place-and-route"

set root_dir [lindex $argv 0]
set design [lindex $argv 1]
set top_level [lindex $argv 2]

set dcp_file "$root_dir/outputs/checkpoints/routed.dcp" 

open_checkpoint $dcp_file

set sim_dir "$root_dir/hdl/verilog/${design}/sim_postroute"
set sdf_file "${sim_dir}/${top_level}_time_impl.sdf"
set verilog_file "${sim_dir}/${top_level}_time_impl.v"
# https://docs.amd.com/r/en-US/ug835-vivado-tcl-commands/write_verilog
write_verilog $verilog_file -force -mode timesim -include_xilinx_libs -sdf_anno true
write_sdf $sdf_file -force -mode timesim -process_corner slow 

# launch_simulation 
exit

# https://adaptivesupport.amd.com/s/article/63988?language=en_US
# https://docs.amd.com/r/en-US/ug835-vivado-tcl-commands/write_verilog
