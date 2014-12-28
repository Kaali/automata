package la.jarve.automata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Multimap;

import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import javax.validation.Valid;

import eu.aleon.aleoncean.packet.EnOceanId;

public class Configuration {
    @NotNull
    @NotEmpty
    public final String device;

    @NotNull
    public final EnOceanId senderId;

    @NotNull
    @Valid
    public final Multimap<EnOceanId, String> remoteDevices;

    @NotNull
    @Valid
    public final Map<String, EnOceanId> names;

    @NotEmpty
    public final String rulesFile;

    @JsonCreator
    public Configuration(@NotNull @JsonProperty("device") final String device,
                         @NotNull @JsonProperty("senderId") final EnOceanId senderId,
                         @NotNull @JsonProperty("remoteDevices") final Multimap<EnOceanId, String> remoteDevices,
                         @NotNull @JsonProperty("names") final Map<String, EnOceanId> names,
                         @NotNull @JsonProperty("rulesFile") final String rulesFile) {
        this.device = device;
        this.senderId = senderId;
        this.remoteDevices = remoteDevices;
        this.names = names;
        this.rulesFile = rulesFile;
    }
}
