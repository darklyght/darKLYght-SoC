#include <stdlib.h>
#include <memory>
#include "Vtb.h"
#include "verilated.h"
#include "clocks.h"

int main(int argc, char** argv, char** env) {
    const std::unique_ptr<VerilatedContext> contextp(new VerilatedContext);
    contextp->traceEverOn(true);
    contextp->commandArgs(argc, argv);
    const std::unique_ptr<Vtb> tb(new Vtb(contextp.get(), "TOP"));

    Clocks clocks;
    const std::shared_ptr<Clock> top_clock(new Clock(5000));
    const std::shared_ptr<Clock> uart_clock(new Clock(4238));
    const std::shared_ptr<Clock> ethernet_clock(new Clock(4000));
    const std::shared_ptr<Clock> ethernet_clock_90(new Clock(4000, 270));
    clocks.add_clock("top_clock", top_clock);
    clocks.add_clock("uart_clock", uart_clock);
    clocks.add_clock("ethernet_clock", ethernet_clock);
    clocks.add_clock("ethernet_clock_90", ethernet_clock_90);

    tb->i_reset = 1;
    tb->i_clock = top_clock->get_state();
    tb->i_uart_clock = uart_clock->get_state();
    tb->i_ethernet_clock = ethernet_clock->get_state();
    tb->i_ethernet_clock_90 = ethernet_clock_90->get_state();
    tb->eval();
    // Tick the clock until we are done
    while(!contextp->gotFinish()) {
        contextp->timeInc(clocks.next_edge());
        if (contextp->time() >= 100000) {
            tb->i_reset = 0;
        }
        tb->i_clock = top_clock->get_state();
        tb->i_uart_clock = uart_clock->get_state();
        tb->i_ethernet_clock = ethernet_clock->get_state();
        tb->i_ethernet_clock_90 = ethernet_clock_90->get_state();
        tb->eval();
    }
    
    tb->final();
    return 0;
}
