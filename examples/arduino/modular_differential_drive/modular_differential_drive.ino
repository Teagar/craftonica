const int LEFT_DIRECTION = 2;
const int LEFT_PWM = 3;
const int RIGHT_DIRECTION = 4;
const int RIGHT_PWM = 5;

void stopMotors() {
  analogWrite(LEFT_PWM, 0);
  analogWrite(RIGHT_PWM, 0);
  digitalWrite(LEFT_DIRECTION, LOW);
  digitalWrite(RIGHT_DIRECTION, LOW);
}

void setup() {
  pinMode(LEFT_DIRECTION, OUTPUT);
  pinMode(LEFT_PWM, OUTPUT);
  pinMode(RIGHT_DIRECTION, OUTPUT);
  pinMode(RIGHT_PWM, OUTPUT);
  stopMotors();
  delay(500);
}

void loop() {
  digitalWrite(LEFT_DIRECTION, HIGH);
  digitalWrite(RIGHT_DIRECTION, HIGH);
  analogWrite(LEFT_PWM, 128);
  analogWrite(RIGHT_PWM, 128);
  delay(2000);

  stopMotors();
  delay(500);

  digitalWrite(LEFT_DIRECTION, LOW);
  digitalWrite(RIGHT_DIRECTION, HIGH);
  analogWrite(LEFT_PWM, 96);
  analogWrite(RIGHT_PWM, 96);
  delay(750);

  stopMotors();
  delay(1000);
}
