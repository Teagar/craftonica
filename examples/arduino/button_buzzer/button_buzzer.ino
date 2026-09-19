// Ligue um botao entre D2 e GND e o buzzer entre D8 e GND.
void setup() {
  pinMode(2, INPUT_PULLUP);
  pinMode(8, OUTPUT);
}

void loop() {
  digitalWrite(8, digitalRead(2) == LOW ? HIGH : LOW);
  delay(10);
}
