const byte TRIG_PIN = 7;
const byte ECHO_PIN = 6;
const char MATERIAL[] = "MDF";
const float NOMINAL_CM = 5.0;
const int SAMPLE_COUNT = 10;

float samples[SAMPLE_COUNT];

float readDistanceCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 30000UL);
  return duration == 0 ? -1.0 : duration / 58.0;
}

void setup() {
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  Serial.begin(9600);
  Serial.println("material,nominal_cm,amostra,medida_cm,eco");
}

void loop() {
  float sum = 0.0;
  int valid = 0;
  for (int i = 0; i < SAMPLE_COUNT; i++) {
    float value = readDistanceCm();
    samples[i] = value;
    Serial.print(MATERIAL); Serial.print(',');
    Serial.print(NOMINAL_CM, 2); Serial.print(',');
    Serial.print(i + 1); Serial.print(',');
    if (value < 0.0) Serial.print("NA,0");
    else { Serial.print(value, 3); Serial.print(",1"); sum += value; valid++; }
    Serial.println();
    delay(100);
  }

  float mean = valid == 0 ? -1.0 : sum / valid;
  float squares = 0.0;
  if (valid > 1) for (int i = 0; i < SAMPLE_COUNT; i++)
    if (samples[i] >= 0.0) { float delta = samples[i] - mean; squares += delta * delta; }
  float deviation = valid > 1 ? sqrt(squares / (valid - 1)) : 0.0;
  Serial.print("# resumo,"); Serial.print(MATERIAL); Serial.print(',');
  Serial.print(NOMINAL_CM, 2); Serial.print(','); Serial.print(valid); Serial.print(',');
  Serial.print(mean, 3); Serial.print(','); Serial.println(deviation, 3);
  delay(2000);
}
