const byte TRIG_PIN = 7;
const byte ECHO_PIN = 6;

// A lógica de movimento deve agir somente quando esta função retorna true.
bool readDistanceCm(float &distance) {
  digitalWrite(TRIG_PIN, LOW); delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH); delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 30000UL);
  if (duration == 0) return false;
  distance = duration / 58.0;
  return true;
}

void setup() {
  pinMode(TRIG_PIN, OUTPUT); pinMode(ECHO_PIN, INPUT); Serial.begin(9600);
  Serial.println("distance_cm,decision,echo");
}

void loop() {
  float distance;
  if (!readDistanceCm(distance)) {
    // Pare ou mantenha uma estratégia segura; nunca trate timeout como 0 cm.
    Serial.println("NA,STOP_NO_ECHO,0");
  } else {
    Serial.print(distance, 3);
    Serial.println(distance < 25.0 ? ",TURN,1" : ",FORWARD,1");
  }
  delay(100);
}
