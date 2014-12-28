package la.jarve.automata;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import eu.aleon.aleoncean.device.DeviceFactory;
import eu.aleon.aleoncean.device.StandardDevice;

public class DeviceRegistry {
    final Map<String, Class<? extends StandardDevice>> typeDeviceMap = new HashMap<>();

    public void registerDevice(@NotNull final String type,
                               @NotNull final Class<? extends StandardDevice> deviceClass) {
        typeDeviceMap.put(type, deviceClass);
    }

    public Class<? extends StandardDevice> getClassByType(@NotNull final String type) {
        final Class<? extends StandardDevice> deviceClass = typeDeviceMap.get(type);
        if (deviceClass != null) {
            return deviceClass;
        }

        return DeviceFactory.getClassForType(type);
    }
}
