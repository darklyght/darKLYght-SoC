#include <stdlib.h>
#include <signal.h>
#include <memory>
#include "Vtb.h"
#include "verilated.h"
#include "verilated_vcd_c.h"
#include "clocks.h"

Vtb* tb;
VerilatedVcdC* tfp;

void signal_callback_handler(int signum) {
    tfp->close();
    tb->final();
    // Terminate program
    exit(signum);
}

int main(int argc, char** argv, char** env) {
    signal(SIGINT, signal_callback_handler);

    const std::unique_ptr<VerilatedContext> contextp(new VerilatedContext);
    contextp->traceEverOn(true);
    contextp->commandArgs(argc, argv);
    tb = new Vtb(contextp.get(), "TOP");
    tfp = new VerilatedVcdC;
    tb->trace(tfp, 99);
    tfp->open("tb.vcd");

    Clocks clocks;
    const std::shared_ptr<Clock> top_clock(new Clock(5000));
    const std::shared_ptr<Clock> uart_clock(new Clock(4238));
    const std::shared_ptr<Clock> ethernet_clock(new Clock(4000));
    const std::shared_ptr<Clock> ethernet_clock_90(new Clock(4000, 270));
    const std::shared_ptr<Clock> hdmi_pixel_clock(new Clock(3367));
    const std::shared_ptr<Clock> hdmi_audio_clock(new Clock(104167));
    const std::shared_ptr<Clock> cpu_clock(new Clock(10000));
    clocks.add_clock("top_clock", top_clock);
    clocks.add_clock("uart_clock", uart_clock);
    clocks.add_clock("ethernet_clock", ethernet_clock);
    clocks.add_clock("ethernet_clock_90", ethernet_clock_90);
    clocks.add_clock("hdmi_pixel_clock", hdmi_pixel_clock);
    clocks.add_clock("hdmi_audio_clock", hdmi_audio_clock);
    clocks.add_clock("cpu_clock", cpu_clock);

    tb->i_reset = 1;
    tb->i_clock = top_clock->get_state();
    tb->i_uart_clock = uart_clock->get_state();
    tb->i_ethernet_clock = ethernet_clock->get_state();
    tb->i_ethernet_clock_90 = ethernet_clock_90->get_state();
    tb->i_hdmi_pixel_clock = hdmi_pixel_clock->get_state();
    tb->i_hdmi_audio_clock = hdmi_audio_clock->get_state();
    tb->i_cpu_clock = cpu_clock->get_state();
    tb->eval();
    tfp->dump(contextp->time());
    tfp->flush();
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
        tb->i_hdmi_pixel_clock = hdmi_pixel_clock->get_state();
        tb->i_hdmi_audio_clock = hdmi_audio_clock->get_state();
        tb->i_cpu_clock = cpu_clock->get_state();
        tb->eval();
        tfp->dump(contextp->time());
        tfp->flush();
    }
    tfp->close();
    tb->final();
    return 0;
}
