#ifndef CRAFTONICA_SERVO_H
#define CRAFTONICA_SERVO_H

#include <Arduino.h>
#include <avr/io.h>
#include <avr/interrupt.h>

#define MIN_PULSE_WIDTH 1000
#define MAX_PULSE_WIDTH 2000
#define DEFAULT_PULSE_WIDTH 1500
#define REFRESH_INTERVAL 20000
#define INVALID_SERVO 255

class Servo {
public:
    Servo() : pin_(INVALID_SERVO), minimum_(MIN_PULSE_WIDTH), maximum_(MAX_PULSE_WIDTH),
              pulse_(DEFAULT_PULSE_WIDTH), attached_(false) { }

    uint8_t attach(int pin) { return attach(pin, MIN_PULSE_WIDTH, MAX_PULSE_WIDTH); }

    uint8_t attach(int pin, int minimum, int maximum) {
        if ((pin != 9 && pin != 10) || (claimed() & channelBit(pin)) != 0) return INVALID_SERVO;
        if (minimum < MIN_PULSE_WIDTH) minimum = MIN_PULSE_WIDTH;
        if (maximum > MAX_PULSE_WIDTH) maximum = MAX_PULSE_WIDTH;
        if (minimum >= maximum) return INVALID_SERVO;
        pin_ = (uint8_t) pin; minimum_ = minimum; maximum_ = maximum; attached_ = true;
        claimed() |= channelBit(pin_); pinMode(pin_, OUTPUT); configureTimer(); setChannel(true);
        writeMicroseconds(pulse_);
        return pin_ == 9 ? 0 : 1;
    }

    void detach() {
        if (!attached_) return;
        setChannel(false); claimed() &= (uint8_t) ~channelBit(pin_); attached_ = false; pin_ = INVALID_SERVO;
    }

    void write(int value) {
        if (value < MIN_PULSE_WIDTH) {
            if (value < 0) value = 0; if (value > 180) value = 180;
            writeMicroseconds(minimum_ + (long) value * (maximum_ - minimum_) / 180L);
        } else writeMicroseconds(value);
    }

    void writeMicroseconds(int value) {
        if (value < minimum_) value = minimum_; if (value > maximum_) value = maximum_;
        pulse_ = value; if (!attached_) return;
        uint16_t ticks = (uint16_t) value * 2U;
        if (pin_ == 9) OCR1A = ticks; else OCR1B = ticks;
    }

    int read() const { return (int) ((long) (pulse_ - minimum_) * 180L / (maximum_ - minimum_)); }
    int readMicroseconds() const { return pulse_; }
    bool attached() const { return attached_; }

private:
    uint8_t pin_; int minimum_, maximum_, pulse_; bool attached_;
    static uint8_t &claimed() { static uint8_t value = 0; return value; }
    static uint8_t channelBit(uint8_t pin) { return pin == 9 ? 1 : 2; }

    static void configureTimer() {
        uint8_t saved = SREG; cli();
        ICR1 = 39999;
        TCCR1A = (TCCR1A & (_BV(COM1A1) | _BV(COM1B1))) | _BV(WGM11);
        TCCR1B = _BV(WGM13) | _BV(WGM12) | _BV(CS11);
        SREG = saved;
    }

    void setChannel(bool enabled) {
        uint8_t saved = SREG; cli();
        if (pin_ == 9) {
            if (enabled) TCCR1A = (TCCR1A | _BV(COM1A1)) & (uint8_t) ~_BV(COM1A0);
            else TCCR1A &= (uint8_t) ~(_BV(COM1A1) | _BV(COM1A0));
        } else {
            if (enabled) TCCR1A = (TCCR1A | _BV(COM1B1)) & (uint8_t) ~_BV(COM1B0);
            else TCCR1A &= (uint8_t) ~(_BV(COM1B1) | _BV(COM1B0));
        }
        SREG = saved;
    }
};

#endif
