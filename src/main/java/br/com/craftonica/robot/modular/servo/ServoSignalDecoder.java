package br.com.craftonica.robot.modular.servo;

import br.com.craftonica.tile.RoboBoardState;

/** Converts only the validated Timer1 Servo capability into a temporal command. */
public final class ServoSignalDecoder {
    private ServoSignalDecoder() { }

    public static Integer pulseWidthMicros(RoboBoardState board, String digitalRole) {
        if (board == null || digitalRole == null || !digitalRole.matches("D(?:9|10)")) return null;
        return board.getServoPulseWidthMicros(Integer.parseInt(digitalRole.substring(1)));
    }
}
