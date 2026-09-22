#include <Servo.h>

Servo arm;

void setup() {
  arm.attach(9);
  arm.write(90);
}

void loop() {
  arm.write(30);
  delay(1000);
  arm.write(150);
  delay(1000);
}
