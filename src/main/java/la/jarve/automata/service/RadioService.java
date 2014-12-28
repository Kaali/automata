package la.jarve.automata.service;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import eu.aleon.aleoncean.packet.ESP3Packet;
import eu.aleon.aleoncean.packet.RadioPacket;
import eu.aleon.aleoncean.rxtx.ESP3Connector;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class RadioService extends AbstractExecutionThreadService {
    private final ESP3Connector connector;
    private final String device;
    private final PublishSubject<RadioPacket> packetPublishSubject = PublishSubject.create();
    public final Observable<RadioPacket> radioPacketObservable =
            packetPublishSubject.asObservable().observeOn(Schedulers.computation());

    public RadioService(@NotNull final ESP3Connector connector, @NotNull final String device) {
        this.connector = connector;
        this.device = device;
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        connector.connect(device);
    }

    @Override
    protected void shutDown() throws Exception {
        connector.disconnect();
        packetPublishSubject.onCompleted();
        super.shutDown();
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            final ESP3Packet packet = connector.read(15, TimeUnit.SECONDS);
            if (packet instanceof RadioPacket) {
                packetPublishSubject.onNext((RadioPacket) packet);
            }
        }
    }

    public ESP3Connector getConnector() {
        return connector;
    }
}
