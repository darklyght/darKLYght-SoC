set root_dir [lindex $argv 0]
set hardware_root $root_dir/hardware
set output_dir $hardware_root/build
file mkdir $output_dir

read_verilog [ glob $hardware_root/src/syn.v ]
read_verilog [ glob $hardware_root/src/hdl/*.v ]
read_verilog [ glob $hardware_root/src/blackboxes/*.v ]
read_xdc [ glob $hardware_root/src/constraints/*.xdc ]

set_property part xc7a200tsbg484-1 [ current_project ]

#read_ip $hardware_root/src/ip/MemoryController/MemoryController.xci
#generate_target all [ get_files *MemoryController.xci ]
#synth_ip [ get_files *MemoryController.xci ]

synth_design -top syn -part xc7a200tsbg484-1
write_checkpoint -force $output_dir/post_synth.dcp
report_timing_summary -file $output_dir/post_synth_timing_summary.rpt
report_utilization -file $output_dir/post_synth_util.rpt
