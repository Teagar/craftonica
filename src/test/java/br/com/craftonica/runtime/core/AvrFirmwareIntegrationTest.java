package br.com.craftonica.runtime.core;

import br.com.craftonica.firmware.CompilationRequest;
import br.com.craftonica.firmware.CompilationResult;
import br.com.craftonica.firmware.ProcessCompilerSupervisor;
import br.com.craftonica.firmware.SourceBundle;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
