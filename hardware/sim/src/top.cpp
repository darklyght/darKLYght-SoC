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
    clocks.add_clock("top_clock", top_clock);
    clocks.add_clock("uart_clock", uart_clock);

    tb->i_reset = 1;
    tb->i_clock = top_clock->get_state();
    tb->i_uart_clock = uart_clock->get_state();
    tb->eval();
    // Tick the clock until we are done
    while(!contextp->gotFinish()) {
        contextp->timeInc(clocks.next_edge());
        if (contextp->time() >= 1000000000) {
            tb->i_reset = 0;
        }
        tb->i_clock = top_clock->get_state();
        tb->i_uart_clock = uart_clock->get_state();
        tb->eval();
    }
    
    tb->final();
    return 0;
}
