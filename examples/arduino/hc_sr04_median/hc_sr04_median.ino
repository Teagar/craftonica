const byte TRIG_PIN = 7;
const byte ECHO_PIN = 6;
const byte WINDOW = 5;
float history[WINDOW];
byte used = 0;
byte nextSlot = 0;

bool readDistanceCm(float &distance) {
  digitalWrite(TRIG_PIN, LOW); delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH); delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 30000UL);
  if (duration == 0) return false;
  distance = duration / 58.0;
  return true;
}

float pushAndMedian(float value) {
  history[nextSlot] = value;
  nextSlot = (nextSlot + 1) % WINDOW;
  if (used < WINDOW) used++;
  float ordered[WINDOW];
  for (byte i = 0; i < used; i++) ordered[i] = history[i];
  for (byte i = 1; i < used; i++) {
    float current = ordered[i]; byte j = i;
    while (j > 0 && ordered[j - 1] > current) { ordered[j] = ordered[j - 1]; j--; }
    ordered[j] = current;
  }
  return ordered[used / 2];
}

void setup() {
  pinMode(TRIG_PIN, OUTPUT); pinMode(ECHO_PIN, INPUT); Serial.begin(9600);
  Serial.println("raw_cm,median_cm,echo");
}

void loop() {
  float raw;
  if (!readDistanceCm(raw)) Serial.println("NA,NA,0");
  else {
    Serial.print(raw, 3); Serial.print(',');
    Serial.print(pushAndMedian(raw), 3); Serial.println(",1");
  }
  delay(100);
}
