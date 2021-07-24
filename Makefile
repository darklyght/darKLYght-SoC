ROOT = $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))

HARD_SBT_DIR = $(ROOT)/hardware/chisel
HARD_SBT_LIST = $(wildcard ${HARD_SBT_DIR}/src/main/scala/*.scala)

HARD_SRC_DIR = $(ROOT)/hardware/src
HARD_SRC_LIST = $(wildcard ${HARD_SRC_DIR}/*/*.v)

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

$(HARD_SRC_DIR)/hdl/top.v: $(HARD_SBT_LIST)
	cd $(HARD_SBT_DIR) && $(SBT_BIN) 'runMain project.Instance --top-name top --target-dir $(HARD_SRC_DIR)/hdl'

sbt: $(HARD_SRC_DIR)/hdl/top.v

sim: sbt $(HARD_SIM_CLIST)
	$(VERILATOR_BIN) -LDFLAGS "-lutil" -CFLAGS "-I${HARD_SIM_DIR}/include" --cc --trace-fst $(HARD_SRC_LIST) $(HARD_SIM_LIST) --Mdir $(HARD_SIM_DIR)/build -I$(HARD_SIM_DIR)/include --top-module tb --exe $(HARD_SIM_CLIST) --build
	cd $(HARD_SIM_DIR)/build/ && ./Vtb

$(HARD_BUILD_DIR)/post_synth.dcp: $(HARD_SRC_DIR)/syn.v $(HARD_SRC_DIR)/hdl/top.v $(HARD_SRC_LIST) $(HARD_SYN_CON) $(HARD_SYN_TCL)
	$(VIVADO_BIN) -mode batch -source $(HARD_SYN_TCL) -tclargs $(ROOT) | tee $(HARD_BUILD_DIR)/syn.log

syn: $(HARD_BUILD_DIR)/post_synth.dcp

$(HARD_BUILD_DIR)/post_route.dcp: $(HARD_BUILD_DIR)/post_synth.dcp $(HARD_PNR_TCL)
	$(VIVADO_BIN) -mode batch -source $(HARD_PNR_TCL) -tclargs $(ROOT) | tee $(HARD_BUILD_DIR)/pnr.log

pnr: $(BUILD_HARD)/post_route.dcp

$(HARD_BUILD_DIR)/top.bit: $(HARD_BUILD_DIR)/post_route.dcp
	$(VIVADO_BIN) -mode batch -source $(HARD_PROGRAM_TCL) -tclargs $(ROOT) | tee $(HARD_BUILD_DIR)/program.log

program: $(HARD_BUILD_DIR)/top.bit

clean:
	-rm -f $(ROOT)/vivado*
	-rm -f $(ROOT)/*webtalk*
	-rm -rf $(ROOT)/.hbs
	-rm -rf $(ROOT)/.Xil
	-rm -rf $(ROOT)/.vscode
	-rm -rf $(HARD_SBT_DIR)/project
	-rm -rf $(HARD_SBT_DIR)/target
	-rm -rf $(HARD_SRC_DIR)/hdl/*
	-rm -f $(HARD_BUILD_DIR)/*
	-rm -rf $(HARD_SIM_BUILD)/*
	-find $(HARD_SRC_DIR)/ip/*/* ! \( -name "*.xci" -o -name "*.prj" \) -exec rm -rf "{}" \;

.PHONY: sbt sim syn pnr clean
