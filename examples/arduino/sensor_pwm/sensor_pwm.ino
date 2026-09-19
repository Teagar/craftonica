// Ligue o sensor em A0/D14 e o motor ou buzzer entre D9 e GND.
void setup() {
  pinMode(9, OUTPUT);
}

void loop() {
  int sensor = analogRead(A0);
  analogWrite(9, sensor / 4);
  delay(20);
}
