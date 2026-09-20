const byte TRIG_PIN = 7;
const byte ECHO_PIN = 6;
const byte LEFT_IN1 = 2;
const byte LEFT_IN2 = 4;
const byte LEFT_PWM = 5;
const byte RIGHT_IN1 = 8;
const byte RIGHT_IN2 = 10;
const byte RIGHT_PWM = 9;
const float CLEAR_CM = 70.0;
const unsigned int TURN_90_MS = 1000;
const unsigned int TURN_180_MS = 1970;

void channel(byte in1, byte in2, byte enable, bool forward, byte pwm) {
  digitalWrite(in1, forward ? HIGH : LOW);
  digitalWrite(in2, forward ? LOW : HIGH);
  analogWrite(enable, pwm);
}

void forward(byte pwm) {
  channel(LEFT_IN1, LEFT_IN2, LEFT_PWM, true, pwm);
  channel(RIGHT_IN1, RIGHT_IN2, RIGHT_PWM, true, pwm);
}

void reverse(byte pwm) {
  channel(LEFT_IN1, LEFT_IN2, LEFT_PWM, false, pwm);
  channel(RIGHT_IN1, RIGHT_IN2, RIGHT_PWM, false, pwm);
}

void turnRight(byte pwm) {
  channel(LEFT_IN1, LEFT_IN2, LEFT_PWM, true, pwm);
  channel(RIGHT_IN1, RIGHT_IN2, RIGHT_PWM, false, pwm);
}

void turnLeft(byte pwm) {
  channel(LEFT_IN1, LEFT_IN2, LEFT_PWM, false, pwm);
  channel(RIGHT_IN1, RIGHT_IN2, RIGHT_PWM, true, pwm);
}

void brake() {
  digitalWrite(LEFT_IN1, HIGH); digitalWrite(LEFT_IN2, HIGH);
  digitalWrite(RIGHT_IN1, HIGH); digitalWrite(RIGHT_IN2, HIGH);
  analogWrite(LEFT_PWM, 255); analogWrite(RIGHT_PWM, 255);
}

bool oneReading(float &distance) {
  digitalWrite(TRIG_PIN, LOW); delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH); delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 30000UL);
  if (duration == 0) return false;
  distance = duration / 58.0;
  return true;
}

// Mediana de três tentativas; exige ao menos dois ecos e nunca reutiliza valor antigo.
bool readFiltered(float &distance) {
  float values[3]; byte valid = 0;
  for (byte attempt = 0; attempt < 3; attempt++) {
    float value;
    if (oneReading(value)) values[valid++] = value;
    delay(20);
  }
  if (valid < 2) return false;
  if (values[0] > values[1]) { float swap = values[0]; values[0] = values[1]; values[1] = swap; }
  if (valid == 3) {
    if (values[1] > values[2]) { float swap = values[1]; values[1] = values[2]; values[2] = swap; }
    if (values[0] > values[1]) { float swap = values[0]; values[0] = values[1]; values[1] = swap; }
  }
  distance = values[valid / 2];
  return true;
}

void setup() {
  pinMode(TRIG_PIN, OUTPUT); pinMode(ECHO_PIN, INPUT);
  pinMode(LEFT_IN1, OUTPUT); pinMode(LEFT_IN2, OUTPUT); pinMode(LEFT_PWM, OUTPUT);
  pinMode(RIGHT_IN1, OUTPUT); pinMode(RIGHT_IN2, OUTPUT); pinMode(RIGHT_PWM, OUTPUT);
  Serial.begin(9600); brake(); Serial.println("action,distance_cm");
}

void loop() {
  float front;
  if (!readFiltered(front)) {
    brake(); Serial.println("TIMEOUT_STOP,NA"); delay(200);
    turnRight(165); Serial.println("TIMEOUT_SCAN_RIGHT,NA"); delay(TURN_90_MS);
    brake(); delay(150);
    float side; bool sensorVerified = readFiltered(side);
    if (sensorVerified) {
      turnLeft(165); delay(TURN_90_MS); brake(); delay(150);
      forward(110); Serial.println("VERIFIED_TIMEOUT_PROBE,NA"); delay(200); return;
    }
    turnLeft(165); Serial.println("TIMEOUT_SCAN_LEFT,NA"); delay(TURN_180_MS);
    brake(); delay(150); sensorVerified = readFiltered(side);
    if (sensorVerified) {
      turnRight(165); delay(TURN_90_MS); brake(); delay(150);
      forward(110); Serial.println("VERIFIED_TIMEOUT_PROBE,NA"); delay(200); return;
    }
    brake(); Serial.println("TIMEOUT_ALL_STOP,NA"); delay(300); return;
  }
  if (front >= CLEAR_CM) {
    forward(165); Serial.print("FORWARD,"); Serial.println(front, 2); delay(100); return;
  }

  brake(); Serial.print("BRAKE,"); Serial.println(front, 2); delay(200);
  reverse(145); Serial.println("REVERSE,NA"); delay(350);
  brake(); delay(150);

  turnRight(165); Serial.println("SCAN_RIGHT,NA"); delay(TURN_90_MS);
  brake(); delay(150);
  float right; bool rightValid = readFiltered(right);
  if (rightValid && right >= CLEAR_CM) {
    forward(165); Serial.print("RIGHT_CLEAR_FORWARD,"); Serial.println(right, 2); delay(100); return;
  }

  turnLeft(165); Serial.println("SCAN_LEFT,NA"); delay(TURN_180_MS);
  brake(); delay(150);
  float left; bool leftValid = readFiltered(left);
  if (leftValid && left >= CLEAR_CM) {
    forward(165); Serial.print("LEFT_CLEAR_FORWARD,"); Serial.println(left, 2); delay(100); return;
  }

  if (!rightValid || !leftValid) {
    brake(); Serial.println("SCAN_TIMEOUT_STOP,NA"); delay(300); return;
  }
  turnLeft(165); Serial.println("UTURN,NA"); delay(TURN_90_MS);
  forward(145); Serial.println("UTURN_FORWARD,NA"); delay(100);
}
