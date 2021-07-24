set root_dir [lindex $argv 0]
set hardware_root $root_dir/hardware
set output_dir $hardware_root/build
file mkdir $output_dir

open_hw_manager

connect_hw_server -url localhost:3121
current_hw_target [get_hw_targets localhost:3121/xilinx_tcf/Digilent/210276A79436B]
set_property PARAM.FREQUENCY 15000000 [get_hw_targets localhost:3121/xilinx_tcf/Digilent/210276A79436B]
open_hw_target
current_hw_device [get_hw_devices xc7a*]
set_property PROBES.FILE {} [get_hw_devices xc7a*]
set_property FULL_PROBES.FILE {} [get_hw_devices xc7a*]

set program "set_property PROGRAM.FILE \{${output_dir}/top.bit\} \[get_hw_devices xc7a*\]"
eval ${program}
program_hw_devices [get_hw_devices xc7a*]
refresh_hw_device [get_hw_devices xc7a*]

close_hw_manager
