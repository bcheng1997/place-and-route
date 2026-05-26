# route.tcl

# set root_dir "/home/bcheng/workspace/dev/place-and-route"

set root_dir [lindex $argv 0]
# You may have to change placed_dcp depending on the placer you choose to run in java Main
# Example: 
# set placed_dcp "$root_dir/outputs/placers/PlacerAnnealHybrid_10000_98/checkpoints/PlacerAnnealHybrid.dcp"
set placed_dcp "$root_dir/outputs/placers/PlacerAnnealRandom/checkpoints/PlacerAnnealRandom.dcp"
set packed_dcp "$root_dir/outputs/checkpoints/packed.dcp"
set routed_dcp "$root_dir/outputs/checkpoints/routed.dcp"
set bitstream_file "$root_dir/outputs/output.bit"

puts "route.tcl: Opening placed .dcp file: $placed_dcp"
open_checkpoint $placed_dcp
report_utilization


set ff_cells [get_cells -hierarchical -filter {PRIMITIVE_TYPE =~ FLOP_LATCH.*.*}]

set ff_nets {}

foreach ff $ff_cells {
    # collect the nets connected to this ff
    set connected_nets [get_nets -of_objects [get_pins -of $ff]]
    # append these nets to ff_nets
    lappend ff_nets $connected_nets
}

# some of the nets will be duplicate, so reduce to only unique nets
set unique_ff_nets [lsort -unique $ff_nets]

# first route the unique ff nets
route_design -verbose -nets $unique_ff_nets

# then route everything else
route_design -verbose
report_route_status -show_all

phys_opt_design -verbose

# # route exclusively non-ff nets
# set all_nets [get_nets]
# set non_ff_nets [lsort -unique [lsearch -inline -not -all $all_nets $unique_ff_nets]]
# route_design -verbose -nets $non_ff_nets

report_route_status -show_all
write_checkpoint -force $routed_dcp

# write_bitstream -force $bitstream_file

exit

# close_project

# puts "Routing and bitstream generation complete. Check $routed_dcp and $bitstream_file"
