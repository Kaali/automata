package la.jarve.automata.core;

import eu.aleon.aleoncean.device.Device;
import eu.aleon.aleoncean.device.DeviceParameter;
import eu.aleon.aleoncean.device.DeviceParameterUpdatedEvent;
import eu.aleon.aleoncean.device.DeviceParameterUpdatedInitiation;
import eu.aleon.aleoncean.packet.EnOceanId;

public class Parameter {

    private final DeviceParameterUpdatedEvent parameterUpdatedEvent;
    private final String name;

    public Parameter(final DeviceParameterUpdatedEvent parameterUpdatedEvent, final String name) {
        this.parameterUpdatedEvent = parameterUpdatedEvent;
        this.name = name;
    }

    public EnOceanId getAddressRemote() {
        final Object source = getSource();
        if (source instanceof Device) {
            Device device = (Device) source;
            return device.getAddressRemote();
        } else {
            return null;
        }
    }

    public String getName() {
        return name;
    }

    public Object getSource() {
        return parameterUpdatedEvent.getSource();
    }

    public DeviceParameter getParameter() {
        return parameterUpdatedEvent.getParameter();
    }

    public DeviceParameterUpdatedInitiation getInitiation() {
        return parameterUpdatedEvent.getInitiation();
    }

    public Object getOldValue() {
        return parameterUpdatedEvent.getOldValue();
    }

    public Object getNewValue() {
        return parameterUpdatedEvent.getNewValue();
    }
}
