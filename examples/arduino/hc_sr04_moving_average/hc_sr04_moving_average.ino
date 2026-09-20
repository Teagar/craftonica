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

float pushAndMean(float value) {
  history[nextSlot] = value;
  nextSlot = (nextSlot + 1) % WINDOW;
  if (used < WINDOW) used++;
  float sum = 0.0;
  for (byte i = 0; i < used; i++) sum += history[i];
  return sum / used;
}

void setup() {
  pinMode(TRIG_PIN, OUTPUT); pinMode(ECHO_PIN, INPUT); Serial.begin(9600);
  Serial.println("raw_cm,moving_mean_cm,echo");
}

void loop() {
  float raw;
  if (!readDistanceCm(raw)) Serial.println("NA,NA,0");
  else {
    Serial.print(raw, 3); Serial.print(',');
    Serial.print(pushAndMean(raw), 3); Serial.println(",1");
  }
  delay(100);
}
