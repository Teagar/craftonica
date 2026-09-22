#ifndef CRAFTONICA_SENSORS_H
#define CRAFTONICA_SENSORS_H

#include <Arduino.h>

class CraftonicaEncoder {
public:
    CraftonicaEncoder(uint8_t channelA, uint8_t channelB)
        : a(channelA), b(channelB), previous(0), count(0), started(false) { }
    void begin() { pinMode(a, INPUT); pinMode(b, INPUT); previous = state(); started = true; }
    long update() {
        uint8_t current = state();
        if (!started) { previous = current; started = true; return count; }
        static const int8_t transitions[16] = { 0,1,-1,0, -1,0,0,1, 1,0,0,-1, 0,-1,1,0 };
        count += transitions[(previous << 2) | current]; previous = current; return count;
    }
    long read() const { return count; }
    void zero() { count = 0; }
private:
    uint8_t state() const { return (digitalRead(a) ? 2 : 0) | (digitalRead(b) ? 1 : 0); }
    uint8_t a, b, previous; long count; bool started;
};

inline bool craftonicaLimitPressed(uint8_t pin) { pinMode(pin, INPUT); return digitalRead(pin) == HIGH; }

class CraftonicaImu3 {
public:
    CraftonicaImu3(uint8_t gyroZChannel, uint8_t accelXChannel, uint8_t accelZChannel)
        : gyro(gyroZChannel), ax(accelXChannel), az(accelZChannel) { }
    float gyroZRadiansPerSecond() const { return decode(analogRead(gyro), 4.36332313F); }
    float accelerationX() const { return decode(analogRead(ax), 19.6133F); }
    float accelerationZ() const { return decode(analogRead(az), 19.6133F); }
private:
    static float decode(int sample, float range) { return ((float) sample / 1023.0F * 2.0F - 1.0F) * range; }
    uint8_t gyro, ax, az;
};

#endif
