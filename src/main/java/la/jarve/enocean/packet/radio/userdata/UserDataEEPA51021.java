package la.jarve.enocean.packet.radio.userdata;

import eu.aleon.aleoncean.packet.radio.userdata.UserData4BS;

public class UserDataEEPA51021 extends UserData4BS {
    public UserDataEEPA51021(final byte[] eepData) {
        super(eepData);
    }

    public long getValue() {
        final long value = getDataRange(3, 7, 1, 0);
        final int scale = (int) getDataRange(0, 1, 0, 0);
        return value / divisor(scale) * unitConvert(getDataType());
    }

    private static long unitConvert(final DataType dataType) {
        if (dataType == DataType.CUMULATIVE) {
            return 3600000;
        } else {
            return 1;
        }
    }

    private static long divisor(final int scale) {
        switch (scale) {
            case 0:
                return 1;
            case 1:
                return 10;
            case 2:
                return 100;
            case 3:
                return 1000;
            default:
                return 1;
        }
    }

    public DataType getDataType() {
        switch (getDataBit(0, 2)) {
            case 0:
                return DataType.CUMULATIVE;
            case 1:
                return DataType.CURRENT;
            default:
                return DataType.UNKNOWN;
        }
    }

    public enum DataType {
        CURRENT, CUMULATIVE, UNKNOWN
    }
}
