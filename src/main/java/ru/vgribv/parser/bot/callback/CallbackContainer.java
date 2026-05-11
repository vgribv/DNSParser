package ru.vgribv.parser.bot.callback;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CallbackContainer {
    private final Map<String, Callback> callbackMap;
    private final UnknownCallback unknownCallback;

    public CallbackContainer(List<Callback> callbacks, UnknownCallback unknownCallback) {
        this.callbackMap = callbacks.stream().collect(Collectors.toMap(Callback::getCallbackName,
                callback -> callback,(existing, replacement) -> existing));
        this.unknownCallback = unknownCallback;
    }

    public Callback retrieveCallback(String callbackIdentifier) {
        return callbackMap.getOrDefault(callbackIdentifier, unknownCallback);
    }
}
