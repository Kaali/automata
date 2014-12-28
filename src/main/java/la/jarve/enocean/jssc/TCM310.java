package la.jarve.enocean.jssc;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import eu.aleon.aleoncean.packet.ESP3Packet;
import eu.aleon.aleoncean.packet.ESP3PacketBuilder;
import eu.aleon.aleoncean.packet.ESP3PacketFactory;
import eu.aleon.aleoncean.packet.ESP3Timeout;
import eu.aleon.aleoncean.packet.PacketType;
import eu.aleon.aleoncean.packet.ResponsePacket;
import eu.aleon.aleoncean.rxtx.ESP3Connector;
import eu.aleon.aleoncean.rxtx.ReaderShutdownException;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class TCM310 implements ESP3Connector {

    private static final Logger LOGGER = LoggerFactory.getLogger(TCM310.class);
    private SerialPort serialPort;
    private String device;

    private final BlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<byte[]> inputResponseQueue = new LinkedBlockingQueue<>();

    @Override
    public boolean connect(@NotNull final String device) {
        Preconditions.checkState(serialPort == null);
        Preconditions.checkArgument(!device.isEmpty());

        this.device = device;
        serialPort = new SerialPort(device);

        try {
            serialPort.openPort();
        } catch (SerialPortException ignored) {
            LOGGER.error("Could not open port for device {}", device);
            this.device = null;
            return false;
        }

        try {
            serialPort.setParams(SerialPort.BAUDRATE_57600, SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.addEventListener(new MySerialPortEventListener(device));
        } catch (SerialPortException e) {
            LOGGER.error("Could not set port params for device " + device, e);
            this.device = null;
            return false;
        }

        return true;
    }

    @Override
    public void disconnect() {
        Preconditions.checkState(serialPort != null);
        Preconditions.checkState(serialPort.isOpened());
        try {
            serialPort.closePort();
        } catch (SerialPortException e) {
            LOGGER.error("Could not close device " + device, e);
        }
        device = null;
        serialPort = null;
    }

    @Nullable
    @Override
    public ResponsePacket write(@NotNull final ESP3Packet packet) {
        Preconditions.checkState(serialPort != null && serialPort.isOpened());

        // TODO: Should write happen in it's own thread?
        final byte[] raw = packet.generateRaw();
        try {
            serialPort.writeBytes(raw);
        } catch (SerialPortException e) {
            LOGGER.error("Could not write to device " + device, e);
            return null;
        }

        try {
            final byte[] responseRaw = inputResponseQueue.poll(
                    ESP3Timeout.RESPONSE, TimeUnit.MILLISECONDS);
            if (responseRaw != null) {
                return (ResponsePacket) ESP3PacketFactory.fromRaw(responseRaw);
            } else {
                return null;
            }
        } catch (InterruptedException ignored) {
            LOGGER.debug("Interrupted while waiting for response");
            return null;
        }
    }

    @Nullable
    @Override
    public ESP3Packet read(final long timeout, final TimeUnit unit) throws ReaderShutdownException {
        try {
            final byte[] raw = inputQueue.poll(timeout, unit);
            if (raw == null) {
                return null;
            }
            if (raw.length == 0) {
                throw new ReaderShutdownException();
            }

            return ESP3PacketFactory.fromRaw(raw);
        } catch (InterruptedException ignored) {
            LOGGER.debug("Read poll interrupted");
            return null;
        }
    }

    private class MySerialPortEventListener implements SerialPortEventListener, PropertyChangeListener {
        private final ESP3PacketBuilder packetBuilder;
        private final String device;

        private MySerialPortEventListener(@NotNull final String device) {
            this.device = device;
            packetBuilder = new ESP3PacketBuilder();
            packetBuilder.addPropertyChangeListener(this);
        }

        @Override
        public void serialEvent(final SerialPortEvent serialPortEvent) {
            if (serialPortEvent.isRXCHAR()) {
                final int byteCount = serialPortEvent.getEventValue();
                try {
                    final byte[] bytes = serialPort.readBytes(byteCount);
                    for (byte b : bytes) {
                        packetBuilder.add(b);
                    }
                } catch (SerialPortException e) {
                    LOGGER.error("Could not read bytes for device" + device, e);
                }
            }
        }

        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
            final byte[] raw = (byte[]) evt.getNewValue();
            if (ESP3Packet.getPacketType(raw) == PacketType.RESPONSE) {
                inputResponseQueue.add(raw);
            } else {
                inputQueue.add(raw);
            }
        }
    }
}
