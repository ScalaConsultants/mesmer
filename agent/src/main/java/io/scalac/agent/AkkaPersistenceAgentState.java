package io.scalac.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AkkaPersistenceAgentState {

    public static Map<String, Long> recoveryStarted = new ConcurrentHashMap<>(100);
    public static Map<String, Long> recoveryMeasurements = new ConcurrentHashMap<>(100);
}
