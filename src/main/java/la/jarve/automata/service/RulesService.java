package la.jarve.automata.service;

import com.google.common.util.concurrent.AbstractIdleService;

import org.jetbrains.annotations.NotNull;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;

import eu.aleon.aleoncean.device.Device;
import eu.aleon.aleoncean.device.DeviceParameterUpdatedEvent;
import eu.aleon.aleoncean.packet.EnOceanId;
import la.jarve.automata.core.Parameter;
import rx.Subscription;
import rx.schedulers.Schedulers;

public class RulesService extends AbstractIdleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesService.class);

    @NotNull
    private final String rulesFile;
    @NotNull
    private final DeviceService deviceService;
    private Subscription subscription;
    private KieSession kSession;

    public RulesService(@NotNull DeviceService deviceService,
                        @NotNull final String rulesFile) {
        this.deviceService = deviceService;
        this.rulesFile = rulesFile;
    }

    @Override
    protected void startUp() throws Exception {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kFileSystem = ks.newKieFileSystem();
        try (FileInputStream fis = new FileInputStream(rulesFile)) {
            kFileSystem.write("src/main/resources/automata.drl",
                    ks.getResources().newInputStreamResource(fis));
        }

        KieBuilder kBuilder = ks.newKieBuilder(kFileSystem);
        kBuilder.buildAll();
        if (kBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Build time Errors: " + kBuilder.getResults());
        }
        KieContainer kContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());

        KieBaseConfiguration config = ks.newKieBaseConfiguration();
        config.setOption(EventProcessingOption.STREAM);

        KieBase kieBase = kContainer.newKieBase(config);
        kSession = kieBase.newKieSession();

//        kSession.addEventListener(new DebugAgendaEventListener());
//        kSession.addEventListener(new DebugRuleRuntimeEventListener());
        kSession.fireAllRules();
        LOGGER.info("Rules engine running");

        deviceService.awaitRunning(5, TimeUnit.MINUTES);
        kSession.setGlobal("deviceService", deviceService);
        subscription = deviceService.parameterObservable
                .observeOn(Schedulers.computation())
                .retry()
                .map(event -> {
                    final String name = deviceService.nameForId(remoteOfEvent(event));
                    return new Parameter(event, name);
                })
                .subscribe(event -> {
                    kSession.insert(event);
                    kSession.fireAllRules();
                });
    }

    private static EnOceanId remoteOfEvent(final DeviceParameterUpdatedEvent event) {
        final Object source = event.getSource();
        if (source instanceof Device) {
            Device device = (Device) source;
            return device.getAddressRemote();
        } else {
            return null;
        }
    }

    @Override
    protected void shutDown() throws Exception {
        if (!subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        kSession.destroy();
    }
}
