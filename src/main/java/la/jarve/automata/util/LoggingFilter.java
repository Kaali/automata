package la.jarve.automata.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class LoggingFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(final ILoggingEvent iLoggingEvent) {
        if (iLoggingEvent.getLevel().equals(Level.WARN) &&
                iLoggingEvent.getMessage().contains("Don't know how to handle radio choice")) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
