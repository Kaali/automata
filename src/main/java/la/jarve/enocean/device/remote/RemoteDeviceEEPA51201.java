package la.jarve.enocean.device.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import eu.aleon.aleoncean.device.DeviceParameter;
import eu.aleon.aleoncean.device.DeviceParameterUpdatedInitiation;
import eu.aleon.aleoncean.device.IllegalDeviceParameterException;
import eu.aleon.aleoncean.device.RemoteDevice;
import eu.aleon.aleoncean.device.StandardDevice;
import eu.aleon.aleoncean.packet.EnOceanId;
import eu.aleon.aleoncean.packet.RadioPacket;
import eu.aleon.aleoncean.packet.radio.RadioPacket4BS;
import eu.aleon.aleoncean.rxtx.ESP3Connector;
import la.jarve.enocean.packet.radio.userdata.UserDataEEPA51021;

public class RemoteDeviceEEPA51201 extends StandardDevice implements RemoteDevice {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDeviceEEPA51201.class);

    private Long energy; // kwh
    private Long power; // kw

    public RemoteDeviceEEPA51201(final ESP3Connector conn,
                                 final EnOceanId addressRemote,
                                 final EnOceanId addressLocal) {
        super(conn, addressRemote, addressLocal);
    }

    public Long getEnergy() {
        return energy;
    }

    public void setEnergy(final DeviceParameterUpdatedInitiation initiation, final Long energy) {
        final Long oldEnergy = this.energy;
        this.energy = energy;
        fireParameterChanged(DeviceParameter.ENERGY_WS, initiation, oldEnergy, energy);
    }

    public Long getPower() {
        return power;
    }

    public void setPower(final DeviceParameterUpdatedInitiation initiation, final Long power) {
        final Long oldPower = this.power;
        this.power = power;
        fireParameterChanged(DeviceParameter.POWER_W, initiation, oldPower, power);
    }

    private void parseRadioPacket4BS(final RadioPacket4BS packet) {
        if (packet.isTeachIn()) {
            LOGGER.debug("Ignore teach-in packets.");
            return;
        }

        final UserDataEEPA51021 userData = new UserDataEEPA51021(packet.getUserDataRaw());
        switch (userData.getDataType()) {
            case CUMULATIVE:
                setEnergy(DeviceParameterUpdatedInitiation.RADIO_PACKET, userData.getValue());
                break;
            case CURRENT:
                setPower(DeviceParameterUpdatedInitiation.RADIO_PACKET, userData.getValue());
                break;
            case UNKNOWN:
                LOGGER.error("Unknown data type");
                break;
        }
    }

    @Override
    public void parseRadioPacket(final RadioPacket packet) {
        if (packet instanceof RadioPacket4BS) {
            parseRadioPacket4BS((RadioPacket4BS) packet);
        } else {
            LOGGER.warn("Don't know how to handle radio choice {}.",
                    String.format("0x%02X", packet.getChoice()));
        }
    }

    @Override
    protected void fillParameters(final Set<DeviceParameter> params) {
        params.add(DeviceParameter.ENERGY_WS);
        params.add(DeviceParameter.POWER_W);
    }

    @Override
    public Object getByParameter(final DeviceParameter parameter) throws IllegalDeviceParameterException {
        switch (parameter) {
            case ENERGY_WS:
                return getEnergy();
            case POWER_W:
                return getPower();
            default:
                return super.getByParameter(parameter);
        }
    }
}
