#include "clocks.h"

Clock::Clock(uint32_t half_period, uint32_t phase) : half_period(half_period) {
    this->state = (phase % 360) >= 180;
    this->time_to_next_edge =  (half_period - (phase % 180) * half_period / 180) == 0 ? half_period : (half_period - (phase % 180) * half_period / 180);
}

Clock::~Clock() {
}

uint32_t Clock::get_state() {
    return this->state;
}

uint32_t Clock::get_time_to_next_edge() {
    return this->time_to_next_edge;
}

void Clock::advance_time(uint32_t increment) {
    assert(this->time_to_next_edge - increment < this->time_to_next_edge);
    if (this->time_to_next_edge - increment == 0) {
        if (this->state == 0) {
            // Positive edge
            this->state = 1;
            this->time_to_next_edge = this->half_period;
        } else {
            // Negative edge
            this->state = 0;
            this->time_to_next_edge = this->half_period;
        }
    } else {
        this->time_to_next_edge -= increment;
    }

    return;
}

Clocks::Clocks() {
}

Clocks::~Clocks() {
}

void Clocks::add_clock(std::string name, std::shared_ptr<Clock> clock) {
    this->clocks.insert(std::pair<std::string, std::shared_ptr<Clock>>(name, clock));
    
    return;
}

uint32_t Clocks::next_edge() {
    uint32_t min_time_to_next_edge = 0xFFFFFFFF;
    // Find the smallest time to the next edge of all clocks
    for (std::map<std::string, std::shared_ptr<Clock>>::iterator it = this->clocks.begin(); it != this->clocks.end(); it++) {
        uint32_t time = (it->second)->get_time_to_next_edge();
        if (time < min_time_to_next_edge) {
            min_time_to_next_edge = time;
        }
    }
    // Advance all clocks by the smallest time
    for (std::map<std::string, std::shared_ptr<Clock>>::iterator it = (this->clocks).begin(); it != (this->clocks).end(); it++) {
        (it->second)->advance_time(min_time_to_next_edge);
    }
    //printf("%d\n", min_time_to_next_edge);
    return min_time_to_next_edge;
}