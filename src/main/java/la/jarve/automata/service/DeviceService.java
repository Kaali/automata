package la.jarve.automata.service;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AbstractIdleService;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import eu.aleon.aleoncean.device.Device;
import eu.aleon.aleoncean.device.DeviceFactory;
import eu.aleon.aleoncean.device.DeviceParameter;
import eu.aleon.aleoncean.device.DeviceParameterUpdatedEvent;
import eu.aleon.aleoncean.device.IllegalDeviceParameterException;
import eu.aleon.aleoncean.device.StandardDevice;
import eu.aleon.aleoncean.packet.EnOceanId;
import eu.aleon.aleoncean.packet.RadioPacket;
import la.jarve.automata.DeviceRegistry;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class DeviceService extends AbstractIdleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceService.class);

    @NotNull
    private final RadioService radioService;
    private Multimap<EnOceanId, Device> deviceMap;
    @NotNull
    private final PublishSubject<DeviceParameterUpdatedEvent> parameterSubject = PublishSubject.create();
    @NotNull
    public final Observable<DeviceParameterUpdatedEvent> parameterObservable =
            parameterSubject.asObservable().observeOn(Schedulers.computation());
    private final Multimap<EnOceanId, String> inputDeviceMap;
    private Subscription radioSubscription;
    @NotNull
    private final DeviceRegistry deviceRegistry;
    @NotNull
    private final EnOceanId senderId;
    @NotNull
    private final BiMap<String, EnOceanId> names;

    public DeviceService(@NotNull final RadioService radioService,
                         @NotNull final DeviceRegistry deviceRegistry,
                         @NotNull final EnOceanId senderId,
                         @NotNull final Multimap<EnOceanId, String> inputDeviceMap,
                         @NotNull final Map<String, EnOceanId> names) {
        this.radioService = radioService;
        this.deviceRegistry = deviceRegistry;
        this.senderId = senderId;
        this.inputDeviceMap = ImmutableMultimap.copyOf(inputDeviceMap);
        this.names = ImmutableBiMap.copyOf(names);
    }

    public EnOceanId idForName(final String name) {
        if (name != null) {
            return names.get(name);
        } else {
            return null;
        }
    }

    public String nameForId(final EnOceanId enOceanId) {
        if (enOceanId != null) {
            return names.inverse().get(enOceanId);
        } else {
            return null;
        }
    }

    public void setDeviceParameter(final String idOrName, final DeviceParameter parameter,
                                   Object value) {
        EnOceanId enOceanId = idForName(idOrName);
        if (enOceanId == null) {
            enOceanId = new EnOceanId(idOrName);
        }
        setDeviceParameter(enOceanId, parameter, value);
    }

    public void setDeviceParameter(final EnOceanId enOceanId, final DeviceParameter parameter,
                                   Object value) {
        final Collection<Device> devices = deviceMap.get(enOceanId);
        boolean didSucceed = false;
        for (Device device : devices) {
            try {
                device.setByParameter(parameter, value);
                didSucceed = true;
            } catch (IllegalDeviceParameterException ignored) {
            }
        }
        if (!didSucceed) {
            throw new RuntimeException(String.format("Unknown device parameter: %s", parameter));
        }
    }

    private void setupDeviceMap() {
        Preconditions.checkState(deviceMap == null);

        final ImmutableMultimap.Builder<EnOceanId, Device> deviceMapBuilder = ImmutableMultimap.<EnOceanId, Device>builder();
        for (Map.Entry<EnOceanId, String> entry : inputDeviceMap.entries()) {
            final EnOceanId enOceanId = entry.getKey();
            final String type = entry.getValue();

            Class<? extends StandardDevice> deviceClass = deviceRegistry.getClassByType(type);
            if (deviceClass != null) {
                StandardDevice device = DeviceFactory.createFromClass(
                        deviceClass, radioService.getConnector(), enOceanId, senderId);
                deviceMapBuilder.put(enOceanId, device);
            } else {
                LOGGER.error("Cannot find device type '{}", type);
                throw new RuntimeException(String.format("Cannot find device type %s", type));
            }
        }
        deviceMap = deviceMapBuilder.build();
    }

    private void handlePacket(@NotNull final RadioPacket radioPacket) {
        Preconditions.checkNotNull(deviceMap);
        LOGGER.debug("Handling radioPacket {}", radioPacket);
        deviceMap.get(radioPacket.getSenderId()).stream()
                .forEach(remoteDevice -> remoteDevice.parseRadioPacket(radioPacket));
    }

    private void subscribeToParameters() {
        Preconditions.checkNotNull(deviceMap);
        deviceMap.values().stream()
                .forEach(rd -> rd.addParameterUpdatedListener(parameterSubject::onNext));
    }

    @Override
    protected void startUp() throws Exception {
        LOGGER.debug("Starting up DeviceService");
        setupDeviceMap();
        subscribeToParameters();
        radioService.awaitRunning(5, TimeUnit.MINUTES);
        radioSubscription = radioService.radioPacketObservable.subscribe(this::handlePacket);
    }

    @Override
    protected void shutDown() throws Exception {
        LOGGER.debug("Shutting down DeviceService");
        if (radioSubscription != null && !radioSubscription.isUnsubscribed()) {
            radioSubscription.unsubscribe();
        }
    }
}
