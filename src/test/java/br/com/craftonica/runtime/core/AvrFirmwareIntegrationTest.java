package br.com.craftonica.runtime.core;

import br.com.craftonica.firmware.CompilationRequest;
import br.com.craftonica.firmware.CompilationResult;
import br.com.craftonica.firmware.ProcessCompilerSupervisor;
import br.com.craftonica.firmware.SourceBundle;
import br.com.craftonica.showcase.UltrasonicLabGenerator;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

public final class AvrFirmwareIntegrationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void executesPinnedBlinkAndAnalogToPwmSketches() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("craftonica.runtime.firmware.integration"));

        byte[] blink = compile("Blink", "void setup(){pinMode(LED_BUILTIN,OUTPUT);}\n"
                + "void loop(){digitalWrite(LED_BUILTIN,HIGH);delay(500);"
                + "digitalWrite(LED_BUILTIN,LOW);delay(500);}\n");
        AvrMachineState blinkState = new AvrMachineState();
        AvrInterpreter blinkInterpreter = new AvrInterpreter(blink);
        boolean high = false;
        boolean lowAfterHigh = false;
        for (int slice = 0; slice < 400 && !lowAfterHigh; slice++) {
            AvrExecutionResult result = blinkInterpreter.executeToAbsoluteTarget(
                    blinkState, blinkState.getCycles() + AvrInterpreter.QUANTUM_CYCLES, AvrInputs.allLow());
            for (GpioChange change : result.getGpioChanges()) {
                if (change.getPin() == 13 && change.isOutput()) {
                    if (change.isHigh()) high = true;
                    else if (high) lowAfterHigh = true;
                }
            }
        }
        assertTrue("Blink never drove D13 high", high);
        assertTrue("Blink never drove D13 low after the delay", lowAfterHigh);

        byte[] analogPwm = compile("AnalogPwm", "void setup(){}\n"
                + "void loop(){int sample=analogRead(A0);analogWrite(5,sample>>2);}\n");
        int[] analog = new int[AvrInputs.ANALOG_CHANNEL_COUNT];
        analog[0] = 2500000;
        AvrInputs inputs = new AvrInputs(new boolean[AvrInputs.DIGITAL_PIN_COUNT], analog);
        AvrMachineState analogState = new AvrMachineState();
        AvrInterpreter analogInterpreter = new AvrInterpreter(analogPwm);
        PwmDescriptor observed = null;
        for (int slice = 0; slice < 40 && observed == null; slice++) {
            AvrExecutionResult result = analogInterpreter.executeToAbsoluteTarget(
                    analogState, analogState.getCycles() + AvrInterpreter.QUANTUM_CYCLES, inputs);
            for (PwmDescriptor descriptor : result.getPwmDescriptors()) {
                if (descriptor.getPin() == 5 && descriptor.getCompare() == 128) observed = descriptor;
            }
        }
        assertTrue("analogRead(A0) was not published as PWM on D5", observed != null);

        byte[] serial = compile("SerialTx", "void setup(){Serial.begin(9600);Serial.println(\"ok\");}\n"
                + "void loop(){}\n");
        AvrMachineState serialState = new AvrMachineState();
        AvrInterpreter serialInterpreter = new AvrInterpreter(serial);
        java.io.ByteArrayOutputStream transmitted = new java.io.ByteArrayOutputStream();
        for (int slice = 0; slice < 40 && transmitted.size() < 4; slice++) {
            AvrExecutionResult result = serialInterpreter.executeToAbsoluteTarget(
                    serialState, serialState.getCycles() + AvrInterpreter.QUANTUM_CYCLES, AvrInputs.allLow());
            transmitted.write(result.getTransmittedBytes());
        }
        assertArrayEquals(new byte[]{'o', 'k', '\r', '\n'}, transmitted.toByteArray());

        byte[] sonar = compile("Sonar", "const byte T=7,E=6; void setup(){pinMode(T,OUTPUT);pinMode(E,INPUT);Serial.begin(9600);}\n"
                + "void loop(){digitalWrite(T,LOW);delayMicroseconds(2);digitalWrite(T,HIGH);"
                + "delayMicroseconds(10);digitalWrite(T,LOW);Serial.println(pulseIn(E,HIGH,30000UL));delay(100);}\n");
        AvrMachineState sonarState = new AvrMachineState();
        AvrInterpreter sonarInterpreter = new AvrInterpreter(sonar);
        AvrInputs sonarInputs = new AvrInputs(new boolean[AvrInputs.DIGITAL_PIN_COUNT],
                new int[AvrInputs.ANALOG_CHANNEL_COUNT], new UltrasonicPeripheral(true, 7, 6, 46400L));
        java.io.ByteArrayOutputStream sonarTx = new java.io.ByteArrayOutputStream();
        for (int slice = 0; slice < 200 && sonarTx.size() == 0; slice++) {
            AvrExecutionResult result = sonarInterpreter.executeToAbsoluteTarget(sonarState,
                    sonarState.getCycles() + AvrInterpreter.QUANTUM_CYCLES, sonarInputs);
            sonarTx.write(result.getTransmittedBytes());
        }
        String duration = new String(sonarTx.toByteArray(), StandardCharsets.US_ASCII).trim();
        assertTrue("pulseIn did not receive an ultrasonic ECHO: " + duration,
                Long.parseLong(duration) >= 2800L && Long.parseLong(duration) <= 3000L);

        byte[] laboratory = compile("SonarLab", UltrasonicLabGenerator.sketch("PLASTICO"));
        AvrMachineState labState = new AvrMachineState();
        AvrInterpreter labInterpreter = new AvrInterpreter(laboratory);
        java.io.ByteArrayOutputStream labTx = new java.io.ByteArrayOutputStream();
        for (int slice = 0; slice < 400; slice++) {
            AvrExecutionResult result = labInterpreter.executeToAbsoluteTarget(labState,
                    labState.getCycles() + AvrInterpreter.QUANTUM_CYCLES, sonarInputs);
            labTx.write(result.getTransmittedBytes());
            String text = new String(labTx.toByteArray(), StandardCharsets.US_ASCII);
            if (text.contains("PLASTICO,5.00,10,")) break;
        }
        String laboratoryCsv = new String(labTx.toByteArray(), StandardCharsets.US_ASCII);
        assertTrue(laboratoryCsv.startsWith("material,nominal_cm,amostra,medida_cm,eco\r\n"));
        assertTrue(laboratoryCsv.contains("PLASTICO,5.00,10,"));

        Path metrologySource = Paths.get("examples/arduino/hc_sr04_metrology/hc_sr04_metrology.ino");
        byte[] metrologyFirmware = compile("MetrologyExample", new String(
                Files.readAllBytes(metrologySource), StandardCharsets.UTF_8));
        assertTrue(executeSerialUntil(metrologyFirmware, sonarInputs, 500,
                "MDF,5.00,10,").contains("MDF,5.00,10,"));

        String moving = executeSerial(compileExample("MovingAverage", "hc_sr04_moving_average"), sonarInputs, 250);
        assertTrue(moving.startsWith("raw_cm,moving_mean_cm,echo\r\n"));
        assertTrue(moving.contains(",1\r\n"));
        String median = executeSerial(compileExample("Median", "hc_sr04_median"), sonarInputs, 250);
        assertTrue(median.startsWith("raw_cm,median_cm,echo\r\n"));
        assertTrue(median.contains(",1\r\n"));
        byte[] timeoutFirmware = compileExample("TimeoutRejection", "hc_sr04_timeout_rejection");
        assertTrue(executeSerial(timeoutFirmware, sonarInputs, 250).contains(",FORWARD,1\r\n"));
        assertTrue(executeSerial(timeoutFirmware, AvrInputs.allLow(), 700).contains("NA,STOP_NO_ECHO,0\r\n"));
    }

    private byte[] compileExample(String name, String directory) throws Exception {
        Path source = Paths.get("examples/arduino", directory, directory + ".ino");
        return compile(name, new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
    }

    private String executeSerial(byte[] firmware, AvrInputs inputs, int slices) throws Exception {
        AvrMachineState state = new AvrMachineState();
        AvrInterpreter interpreter = new AvrInterpreter(firmware);
        java.io.ByteArrayOutputStream tx = new java.io.ByteArrayOutputStream();
        for (int slice = 0; slice < slices; slice++) {
            AvrExecutionResult result = interpreter.executeToAbsoluteTarget(state,
                    state.getCycles() + AvrInterpreter.QUANTUM_CYCLES, inputs);
            tx.write(result.getTransmittedBytes());
            if (new String(tx.toByteArray(), StandardCharsets.US_ASCII).split("\\r\\n").length >= 2) break;
        }
        return new String(tx.toByteArray(), StandardCharsets.US_ASCII);
    }

    private String executeSerialUntil(byte[] firmware, AvrInputs inputs, int slices,
                                      String expected) throws Exception {
        AvrMachineState state = new AvrMachineState();
        AvrInterpreter interpreter = new AvrInterpreter(firmware);
        java.io.ByteArrayOutputStream tx = new java.io.ByteArrayOutputStream();
        for (int slice = 0; slice < slices; slice++) {
            AvrExecutionResult result = interpreter.executeToAbsoluteTarget(state,
                    state.getCycles() + AvrInterpreter.QUANTUM_CYCLES, inputs);
            tx.write(result.getTransmittedBytes());
            if (new String(tx.toByteArray(), StandardCharsets.US_ASCII).contains(expected)) break;
        }
        return new String(tx.toByteArray(), StandardCharsets.US_ASCII);
    }

    private byte[] compile(String name, String source) throws Exception {
        Path script = java.nio.file.Paths.get("scripts/firmware/compile-sketch.sh").toAbsolutePath();
        Path work = temporary.newFolder(name + "-jobs").toPath();
        ProcessCompilerSupervisor compiler = new ProcessCompilerSupervisor(script, work);
        SourceBundle sources = new SourceBundle(name, Collections.singletonMap(name + ".ino",
                source.getBytes(StandardCharsets.UTF_8)));
        CompilationResult result = compiler.compile(new CompilationRequest(
                "runtime-integration", UUID.randomUUID(), 1, sources));
        assertEquals(result.getDiagnostics().getEntries().toString(), CompilationResult.Status.SUCCESS,
                result.getStatus());
        return result.getFirmware().getFlash();
    }
}
