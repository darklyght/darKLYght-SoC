#ifndef CLOCKS_H_
#define CLOCKS_H_

#include <stdint.h>
#include <assert.h>
#include <stdio.h>
#include <map>
#include <string>
#include <memory>
#include <vector>

class Clock {
    private:
        uint32_t state;
        uint32_t half_period;
        uint32_t time_to_next_edge;
    public:
        Clock(uint32_t half_period, uint32_t phase);
        Clock(uint32_t half_period) : Clock(half_period, 0) {};
        ~Clock();
        uint32_t get_state();
        uint32_t get_time_to_next_edge();
        void advance_time(uint32_t increment);
};

class Clocks {
    private:
        std::map<std::string, std::shared_ptr<Clock>> clocks;
    public:
        Clocks();
        ~Clocks();
        void add_clock(std::string name, std::shared_ptr<Clock> clock);
        uint32_t next_edge();
};

#endif  // CLOCKS_H_
