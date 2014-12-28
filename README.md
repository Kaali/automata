Automata
========

enocean based home automation system. Mainly just for playing around with Enocean, houm.io and Java.

The project is under heavy construction, but works as is. The rules system is compiled on every restart, which is really slow on low power devices such as Raspberry PI, so you have to patient for it to start.

The project is structured in Guava services with no error resilience at all. If some of the services won't start in the beginning, the rest might still run.

There is no teaching mode yet, so your devices has to be teached in using another Enocean system. Even though the API the project uses supports it, I just haven't had the time to implement it.

My plan is to fix at least some of these problems, and implement a REST service for reading current states, sending parameter changes to devices, updating the Drools rules.

How to Install
--------------

There is a single dependency which is not in Maven Central, so you have to install it manually:

    https://github.com/aleon-GmbH/aleoncean

It's used for communicating with Enocean devices. Install it in your local Maven repository with:

    git clone https://github.com/aleon-GmbH/aleoncean
    cd alenocean
    mvn install

Now you can build Automata itself with:

    mvn package

The resulting *-with-dependencies.jar* file inside *target/* can now be deployed on your target system.

Usage
-----

Write a configuration file and a rules file using the following examples as basis:

### config.yaml ###

    device: /dev/ttyAMA0
    senderId: FF:F0:C5:7E
    remoteDevices:
            'FE:FD:8F:12':
                    - RD_F6-02-01
            '01:87:00:9F':
                    - RD_D2-01-08
                    - RD_A5-12-01
    names:
            'Buttons': 'FE:FD:8F:12'
            'Switch': '01:87:00:9F'
    rulesFile: rules.drl

### rules.drl ###

    package eu.aleon.aleoncean.device
    
    global la.jarve.automata.service.DeviceService deviceService;
    
    import la.jarve.automata.core.Parameter
    import la.jarve.enocean.device.remote.RemoteDeviceEEPA51201
     import eu.aleon.aleoncean.values.RockerSwitchAction
    
    declare DeviceParameterUpdatedEvent
        @role (event)
    end
    
    rule "Turn off power after 10s"
    when
        $e : Parameter(
           $s : source,
           parameter == DeviceParameter.POWER_W,
           newValue > 5)
        not Parameter(
           source == $s,
           parameter == DeviceParameter.POWER_W,
           newValue == 0,
           this after[0h,2h] $e)
    then
        System.out.println("Timed shutdown");
        deviceService.setDeviceParameter("Switch",
            DeviceParameter.SWITCH, false);
    end
    
    rule "Switch power on, on button 2 pressed"
    when
        Parameter(
            name == "Buttons",
            parameter == DeviceParameter.BUTTON_DIM_B,
            newValue == RockerSwitchAction.DIM_UP_PRESSED
        )
    then
        System.out.println("--- turn on");
        deviceService.setDeviceParameter("Switch",
            DeviceParameter.SWITCH, true);
    end
    
    rule "Switch power off, on button 4 pressed"
    when
        Parameter(
            name == "Buttons",
            parameter == DeviceParameter.BUTTON_DIM_B,
            newValue == RockerSwitchAction.DIM_DOWN_PRESSED
        )
    then
        System.out.println("--- turn off");
        deviceService.setDeviceParameter("Switch",
            DeviceParameter.SWITCH, false);
    end

Rules file is a Drools rules file, which is run in stream-mode with a realtime clock.

### Running ###

Run the .jar file with:

    java -jar automata-with-dependencies.jar config.yaml
