ROOT = $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))

HARD_SBT_DIR = $(ROOT)/hardware/chisel
HARD_SBT_LIST = $(wildcard ${HARD_SBT_DIR}/src/main/scala/*.scala)

HARD_SRC_DIR = $(ROOT)/hardware/src
HARD_SRC_LIST = $(HARD_SRC_DIR)/hdl/top.v

HARD_SIM_DIR = $(ROOT)/hardware/sim
HARD_SIM_LIST = ${HARD_SIM_DIR}/tb.sv $(wildcard ${HARD_SIM_DIR}/transactor/*/*.v) $(wildcard ${HARD_SIM_DIR}/transactor/*/*.sv) $(wildcard ${HARD_SIM_DIR}/transactor/*/*.c)
HARD_SIM_CLIST = $(wildcard ${HARD_SIM_DIR}/src/*.c) $(wildcard ${HARD_SIM_DIR}/src/*.cpp)
HARD_SIM_BUILD = $(HARD_SIM_DIR)/build

HARD_BUILD_DIR = $(ROOT)/hardware/build
HARD_SYN_CON = $(wildcard ${HARD_SRC_DIR}/constraints/*.xdc)
HARD_SYN_TCL = $(ROOT)/hardware/scripts/syn.tcl
HARD_PNR_TCL = $(ROOT)/hardware/scripts/pnr.tcl
HARD_PROGRAM_TCL = $(ROOT)/hardware/scripts/program.tcl

SBT_BIN = $(shell which sbt)
VERILATOR_BIN = $(shell which verilator)
VIVADO_BIN = $(shell which vivado)

$(HARD_SRC_DIR)/hdl/top.v: $(ROOT)/hardware/chisel/build.sbt $(HARD_SBT_LIST)
	cd $(HARD_SBT_DIR) && $(SBT_BIN) 'runMain project.Instance'

sbt: $(HARD_SRC_DIR)/hdl/top.v

sim: $(HARD_SRC_DIR)/hdl/top.v $(HARD_SRC_LIST) $(HARD_SIM_LIST) $(HARD_SIM_CLIST) $(wildcard ${HARD_SRC_DIR}/blackboxes/*.v)
	$(VERILATOR_BIN) -Wno-lint -LDFLAGS "-g -lutil" -CFLAGS "-g -I${HARD_SIM_DIR}/include" --cc --trace $(HARD_SRC_LIST) $(HARD_SIM_LIST) $(wildcard ${HARD_SRC_DIR}/blackboxes/*.v) --Mdir $(HARD_SIM_DIR)/build -I$(HARD_SIM_DIR)/include +define+SIMULATION --top-module tb --threads 12 --threads-dpi all --exe $(HARD_SIM_CLIST) --build
	cd $(HARD_SIM_DIR)/build/ && sudo ./Vtb

$(HARD_BUILD_DIR)/post_synth.dcp: $(HARD_SRC_DIR)/syn.v $(HARD_SRC_LIST) $(HARD_SYN_CON) $(HARD_SYN_TCL)
	$(VIVADO_BIN) -mode batch -source $(HARD_SYN_TCL) -tclargs $(ROOT) | tee $(HARD_BUILD_DIR)/syn.log

syn: $(HARD_BUILD_DIR)/post_synth.dcp

$(HARD_BUILD_DIR)/top.bit: $(HARD_BUILD_DIR)/post_synth.dcp $(HARD_PNR_TCL)
	$(VIVADO_BIN) -mode batch -source $(HARD_PNR_TCL) -tclargs $(ROOT) | tee $(HARD_BUILD_DIR)/pnr.log

pnr: $(HARD_BUILD_DIR)/top.bit

program: pnr
	$(VIVADO_BIN) -mode batch -source $(HARD_PROGRAM_TCL) -tclargs $(ROOT) | tee $(HARD_BUILD_DIR)/program.log

clean:
	-rm -f $(ROOT)/*.out
	-rm -f $(ROOT)/*.vcd
	-rm -f $(ROOT)/vivado*
	-rm -f $(ROOT)/*webtalk*
	-rm -f $(ROOT)/tight_setup_hold_pins.txt
	-rm -rf $(ROOT)/.hbs
	-rm -rf $(ROOT)/.Xil
	-rm -rf $(ROOT)/.vscode
	-rm -rf $(ROOT)/*.gen
	-rm -rf $(HARD_SBT_DIR)/project
	-rm -rf $(HARD_SBT_DIR)/target
	-rm -rf $(HARD_SRC_DIR)/hdl/*
	-rm -f $(HARD_BUILD_DIR)/*
	-rm -rf $(HARD_SIM_BUILD)/*
	-find $(HARD_SRC_DIR)/ip/*/* ! \( -name "*.xci" -o -name "*.prj" \) -exec rm -rf "{}" \;

.PHONY: program clean
