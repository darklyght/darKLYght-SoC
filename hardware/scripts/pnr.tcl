set root_dir [lindex $argv 0]
set hardware_root $root_dir/hardware
set output_dir $hardware_root/build

open_checkpoint $output_dir/post_synth.dcp
opt_design -directive Explore
place_design -directive Explore
write_checkpoint -force $output_dir/post_place.dcp
report_utilization -file $output_dir/post_place_util.rpt
report_timing_summary -file $output_dir/post_place_timing_summary.rpt

phys_opt_design -directive Explore
route_design -directive Explore
write_checkpoint -force $output_dir/post_route.dcp
report_route_status -file $output_dir/post_route_status.rpt
report_timing_summary -file $output_dir/post_route_timing_summary.rpt
report_power -file $output_dir/post_route_power.rpt
report_drc -file $output_dir/post_imp_drc.rpt

write_bitstream -force $output_dir/top.bit
write_debug_probes -force $output_dir/top.ltx