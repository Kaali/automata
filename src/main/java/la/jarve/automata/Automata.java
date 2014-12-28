package la.jarve.automata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import eu.aleon.aleoncean.device.Device;
import eu.aleon.aleoncean.device.DeviceParameter;
import la.jarve.automata.service.DeviceService;
import la.jarve.automata.service.RadioService;
import la.jarve.automata.service.RulesService;
import la.jarve.enocean.device.remote.RemoteDeviceEEPA51201;
import la.jarve.enocean.jssc.TCM310;

/**
 * Plan:
 *
 * 1. Proxy to and from houm.io for fun, local controls could skip houm.io to reduce latency.
 * 2. When coffee machine is powered up, shut it down after 2 hours.
 * 3. Collect power data from coffee machine? Maybe only just in-memory to reduce writes.
 * 4. Local Web/REST service for looking at the data etc.
 *
 * Actionable:
 *
 * Implement a service which handles TCM310 in a thread. Registers devices to listen:
 *   A MultiMap of device id's to aleoncean RemoteDevice's. On radio packet send it to
 *   the proper RemoteDevice by id.
 * Device registration could come from a configuration file or something, but would be
 * complicated to instantiate (?).
 *
 * The service sends data changes to a bus which can be listened by the actual automation
 * code. Also maybe send all radio communication raw packets to be forwared to houm.io.
 *
 * aleoncean change system could be used here. It's quite complicated, but workable.
 * I think it can be used to map the generic Objects to the proper value type. And thus
 * could be workable even in the bus.
 *
 * Another service would subscribe to the bus, and run the actual logic in it. For fun
 * maybe use Drools rule engine.
  */
public final class Automata {
    private static final Logger LOGGER = LoggerFactory.getLogger(Automata.class);

    @SuppressWarnings("MethodCanBeVariableArityMethod")
    public static void main(String[] args) {
        if (args.length != 1) {
            LOGGER.error("Missing configuration file");
            System.exit(-1);
        }

        final Configuration configuration = parseConfiguration(args[0]);
        if (configuration == null) {
            System.exit(-1);
        }

        final DeviceRegistry deviceRegistry = new DeviceRegistry();
        deviceRegistry.registerDevice("RD_A5-12-01", RemoteDeviceEEPA51201.class);

        final RadioService radioService = new RadioService(new TCM310(), configuration.device);
        final DeviceService deviceService = new DeviceService(radioService, deviceRegistry,
                configuration.senderId, configuration.remoteDevices, configuration.names);
        deviceService.parameterObservable.retry().subscribe(event -> {
            final DeviceParameter parameter = event.getParameter();
            if (parameter != null && event.getSource() instanceof Device) {
                final Device device = (Device) event.getSource();
                LOGGER.debug("{}: {} set to {}", device.getAddressRemote(), parameter.name(), event.getNewValue());
            }
        });

        final RulesService rulesService = new RulesService(deviceService, configuration.rulesFile);

        final ImmutableList<Service> services = ImmutableList.of(radioService, deviceService, rulesService);
        final ServiceManager serviceManager = new ServiceManager(services);
        serviceManager.startAsync().awaitHealthy();
        serviceManager.awaitStopped();
    }

    @Nullable
    private static Configuration parseConfiguration(@NotNull final String filename) {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new GuavaModule());
        final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        final Validator validator = factory.getValidator();
        try (InputStream inputStream = Files.newInputStream(Paths.get(filename))) {
            try {
                final Configuration configuration = objectMapper.readValue(inputStream, Configuration.class);
                final Set<ConstraintViolation<Configuration>> violations = validator.validate(configuration);
                if (!violations.isEmpty()) {
                    for (ConstraintViolation<Configuration> violation : violations) {
                        LOGGER.error(String.format("Invalid configuration file '%s': %s %s", filename,
                                violation.getPropertyPath(), violation.getMessage()));
                    }
                    return null;
                } else {
                    return configuration;
                }
            } catch (RuntimeException e) {
                LOGGER.error("Problem reading configuration file " + filename, e);
            }
        } catch (IOException e) {
            LOGGER.error("Cannot read configuration file " + filename, e);
        }
        return null;
    }
}
